package net.eclipse.havocauction;

import net.eclipse.havocauction.command.AuctionCommand;
import net.eclipse.havocauction.command.ToggleCommand;
import net.eclipse.havocauction.integration.AuctionPlaceholders;
import net.eclipse.havocauction.economy.EconomyHook;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.manager.DropJob;
import net.eclipse.havocauction.manager.MapPreview;
import net.eclipse.havocauction.manager.Profiles;
import net.eclipse.havocauction.manager.SessionManager;
import net.eclipse.havocauction.model.SortOption;
import net.eclipse.havocauction.storage.LegacyImporter;
import net.eclipse.havocauction.storage.SqlStorage;
import net.eclipse.havocauction.ui.Gui;
import net.eclipse.havocauction.ui.GuiListener;
import net.eclipse.havocauction.ui.Prompts;
import net.eclipse.havocauction.util.Category;
import net.eclipse.havocauction.util.ItemAliases;
import net.eclipse.havocauction.util.ConfigUpdater;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.SQLException;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class HavocAuction extends JavaPlugin {

    private static HavocAuction inst;

    private FileConfiguration menus;

    private EconomyHook econ;
    private SqlStorage storage;
    private AuctionManager auction;
    private Profiles profiles;
    private SessionManager sessions;
    private LegacyImporter importer;
    private MapPreview mapPreview;
    private Gui gui;
    private Prompts prompts;

    private final Set<Material> blocked = new HashSet<>();

    public static HavocAuction get() {
        return inst;
    }

    @Override
    public void onEnable() {
        inst = this;

        // resources
        saveDefaultConfig();
        syncConfigFiles();
        reloadMenus();
        loadBlockedItems();
        NumberUtil.setAbbreviate(getConfig().getBoolean("AUCTION.ABBREVIATE-NUMBERS", true));
        // Loaded before listings, because each one bakes its aliases into a search index.
        ItemAliases.load(getConfig().getConfigurationSection("AUCTION.SEARCH-ALIASES"));

        // economy. don't disable if it's missing - the provider may just load after us
        this.econ = new EconomyHook(this);
        if (!econ.setup()) {
            // Do not disable. Economy providers such as EssentialsX register their Vault
            // service during their own enable, so if they load after this plugin the
            // service simply is not there yet. Disabling here is why the plugin appeared
            // dead until it was reloaded by hand. Wait for it instead.
            getLogger().warning("No Vault economy registered yet - waiting for one.");
            waitForEconomy();
        }

        // storage
        this.storage = new SqlStorage(this);
        try {
            storage.initialise();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Could not initialise the database - disabling.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // core
        this.auction  = new AuctionManager(this, storage);
        this.profiles = new Profiles(this, storage);
        this.importer = new LegacyImporter(this);
        auction.loadAll();
        profiles.loadAll();
        runAutoImport();

        sessions = new SessionManager();
        getServer().getPluginManager().registerEvents(sessions, this);

        mapPreview = new MapPreview(this);
        getServer().getPluginManager().registerEvents(mapPreview, this);

        prompts = new Prompts(this);
        getServer().getPluginManager().registerEvents(prompts, this);
        gui = new Gui(this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        PluginCommand command = getCommand("auction");
        if (command != null) {
            AuctionCommand executor = new AuctionCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        PluginCommand alertsToggle = getCommand("toggleauctionalerts");
        if (alertsToggle != null) {
            alertsToggle.setExecutor(new ToggleCommand(this, ToggleCommand.Kind.ALERTS));
        }
        PluginCommand fastToggle = getCommand("togglefastauction");
        if (fastToggle != null) {
            fastToggle.setExecutor(new ToggleCommand(this, ToggleCommand.Kind.FAST));
        }

        registerPlaceholders();

        long tickSeconds = Math.max(5L, getConfig().getLong("AUCTION.UPKEEP-SECONDS", 60L));
        getServer().getScheduler().runTaskTimer(this, auction::tick, tickSeconds * 20L, tickSeconds * 20L);

        long saveTicks = Math.max(20L, getConfig().getLong("AUCTION.SAVE-INTERVAL-SECONDS", 30L) * 20L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            auction.flush();
            profiles.flush();
        }, saveTicks, saveTicks);

        watchConfigFiles();

        getLogger().info("HavocAuction enabled. Dialogs need Paper/Purpur 1.21.7+ and a 1.21.6+ client.");
    }

    /** PlaceholderAPI is optional; skip quietly when it is not installed. */
    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            new AuctionPlaceholders(this).register();
            getLogger().info("Registered PlaceholderAPI expansion 'havocauction'.");
        } catch (Throwable ex) {
            getLogger().warning("Could not register placeholders: " + ex.getMessage());
        }
    }

    /** ON/OFF text used by the placeholders, styled in config. */
    public String statusText(boolean enabled) {
        return getConfig().getString("PLACEHOLDERS." + (enabled ? "ENABLED-TEXT" : "DISABLED-TEXT"),
                enabled ? "ON" : "OFF");
    }

    private void runAutoImport() {
        if (!getConfig().getBoolean("IMPORT.ENABLED", true)) return;
        File file = importer.defaultFile();
        if (!file.isFile()) return;

        getLogger().info("Found " + file.getName() + ", importing legacy auction data...");
        try {
            LegacyImporter.Report report = importer.importFrom(file);
            for (String line : importer.summary(report)) getLogger().info(line);
            if (getConfig().getBoolean("IMPORT.RENAME-WHEN-DONE", true)) importer.markDone(file);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Legacy import failed; nothing was changed.", ex);
        }
    }

    /** Retries the Vault hook until a provider shows up, then stops looking. */
    private void waitForEconomy() {
        new BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                if (econ.isReady()) {
                    cancel();
                    return;
                }
                if (econ.setup()) {
                    getLogger().info("Vault economy found - fully enabled.");
                    cancel();
                    return;
                }
                if (++attempts >= 60) {
                    getLogger().severe("Still no Vault economy after a minute. "
                            + "Install one, then run the reload command.");
                    cancel();
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    /**
     * Reloads config and menus when the files change on disk, so edits apply without a
     * restart or a manual reload.
     */
    private void watchConfigFiles() {
        int seconds = getConfig().getInt("AUCTION.RELOAD-WATCH-SECONDS", 5);
        if (seconds <= 0) return;

        File configFile = new File(getDataFolder(), "config.yml");
        File dialogFile = new File(getDataFolder(), "menus.yml");
        long[] stamps = {configFile.lastModified(), dialogFile.lastModified()};

        getServer().getScheduler().runTaskTimer(this, () -> {
            long config = configFile.lastModified();
            long dialog = dialogFile.lastModified();
            if (config == stamps[0] && dialog == stamps[1]) return;
            stamps[0] = config;
            stamps[1] = dialog;
            reloadEverything();
            getLogger().info("Config changed on disk - reloaded.");
        }, seconds * 20L, seconds * 20L);
    }

    @Override
    public void onDisable() {
        if (profiles != null) profiles.flush();
        if (auction != null) {
            auction.flush();
            storage.saveAll(auction.snapshot());
        }
        if (storage != null) storage.close();
    }

    // ------------------------------------------------------------------ config

    /**
     * Brings config.yml and menus.yml up to date with this build before anything reads
     * them, so a jar update never needs settings pasted in by hand.
     */
    private void syncConfigFiles() {
        if (!getConfig().getBoolean("AUCTION.AUTO-UPDATE-CONFIG", true)) return;

        ConfigUpdater.Result config = ConfigUpdater.update(this, "config.yml");
        if (config.changed()) reloadConfig();
        ConfigUpdater.report(this, config);

        ConfigUpdater.Result menus = ConfigUpdater.update(this, "menus.yml");
        ConfigUpdater.report(this, menus);
    }

    public void reloadMenus() {
        File file = new File(getDataFolder(), "menus.yml");
        if (!file.exists()) saveResource("menus.yml", false);
        menus = YamlConfiguration.loadConfiguration(file);
    }

    private void loadBlockedItems() {
        blocked.clear();
        for (String raw : getConfig().getStringList("BLACKLIST-ITEMS")) {
            Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material == null) {
                getLogger().warning("Unknown blacklisted item: " + raw);
                continue;
            }
            blocked.add(material);
        }
    }

    public void reloadEverything() {
        reloadConfig();
        reloadMenus();
        loadBlockedItems();
        NumberUtil.setAbbreviate(getConfig().getBoolean("AUCTION.ABBREVIATE-NUMBERS", true));
        // Existing listings keep the index built at load; new aliases apply to new
        // listings and after a restart.
        ItemAliases.load(getConfig().getConfigurationSection("AUCTION.SEARCH-ALIASES"));
    }

    public boolean isBlocked(Material material) {
        return material == null || material == Material.AIR || blocked.contains(material);
    }

    public ConfigurationSection menuSection(String path) {
        return menus.getConfigurationSection("MENUS." + path);
    }

    /** Shared menu values such as the border item. */
    public String menuString(String path, String fallback) {
        return menus.getString(path, fallback);
    }

    public String sortName(SortOption option) {
        return menus.getString("NAMES.SORT." + option.getConfigKey(), Text.pretty(option.name()));
    }

    public String categoryName(Category category) {
        return menus.getString("NAMES.FILTER." + category.name(), Text.pretty(category.name()));
    }

    /** Reusable line templates from menus.yml LINES. */
    public String line(String key, String fallback) {
        return menus.getString("LINES." + key, fallback);
    }

    public String message(String path) {
        String prefix = getConfig().getString("MESSAGES.PREFIX", "");
        String message = getConfig().getString("MESSAGES." + path, "");
        return message.isEmpty() ? "" : prefix + message;
    }

    // ------------------------------------------------------------------ accessors

    public EconomyHook economy() {
        return econ;
    }

    public AuctionManager auction() {
        return auction;
    }

    public Profiles profiles() {
        return profiles;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public LegacyImporter importer() {
        return importer;
    }

    public MapPreview mapPreview() {
        return mapPreview;
    }

    public Gui gui() {
        return gui;
    }

    public Prompts prompts() {
        return prompts;
    }


    // ------------------------------------------------------------------ scheduling

    public void sync(Runnable runnable) {
        if (!isEnabled()) return;
        if (Bukkit.isPrimaryThread()) runnable.run();
        else getServer().getScheduler().runTask(this, runnable);
    }

    public void async(Runnable runnable) {
        if (!isEnabled()) {
            runnable.run();
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }

    /** Releases queued loot a few stacks per tick so a big payout cannot stall the server. */
    public void spreadDrop(Player player, Deque<DropJob> jobs, int perTick) {
        Location fallback = player.getLocation();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (jobs.isEmpty()) {
                    cancel();
                    return;
                }
                boolean online = player.isOnline();
                Location target = online ? player.getLocation() : fallback;
                int budget = online ? perTick : Integer.MAX_VALUE;

                for (int index = 0; index < budget && !jobs.isEmpty(); index++) {
                    DropJob job = jobs.peek();
                    ItemStack stack = job.nextStack();
                    if (stack == null) {
                        jobs.poll();
                        index--;
                        continue;
                    }
                    target.getWorld().dropItemNaturally(target, stack);
                }
                if (jobs.isEmpty()) cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }
}
