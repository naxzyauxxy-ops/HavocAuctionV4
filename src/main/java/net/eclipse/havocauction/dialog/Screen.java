package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.manager.Session;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.util.Bedrock;
import net.eclipse.havocauction.util.Text;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Base for every dialog screen: config lookup, sounds, and the show call. */
public abstract class Screen {

    protected final HavocAuction plugin;
    protected final Player player;
    protected final Session session;

    protected Screen(HavocAuction plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.session = plugin.sessions().get(player);
    }

    /** Section name under DIALOGS in dialogs.yml. */
    protected abstract String configPath();

    public abstract String title();

    /** Lines of explanatory text. Dialogs show them as body; chests as item lore. */
    public abstract List<String> bodyLines();

    /** Optional item shown alongside the body. */
    public ItemStack bodyIcon() {
        return null;
    }

    public List<ScreenModel.Input> inputs() {
        return List.of();
    }

    public abstract List<ScreenModel.Button> buttons();

    /** Footer button, shown under the grid. Usually back or close. */
    public ScreenModel.Button exitButton() {
        return null;
    }

    public Player viewer() {
        return player;
    }

    /** Layout for this menu: size, slots, filler. */
    public ConfigurationSection layout() {
        return section();
    }

    /** Resolves configured lines, dropping any that end up empty. */
    protected List<String> resolve(List<String> lines, Map<String, String> placeholders) {
        return Text.applyPruned(lines, common(placeholders));
    }


    /** Placeholders every screen gets, whatever else it adds. */
    protected Map<String, String> common(Map<String, String> placeholders) {
        Map<String, String> merged = new java.util.HashMap<>(placeholders);
        merged.putIfAbsent("separator",
                plugin.line("SEPARATOR", "&8&m                                                  "));
        merged.putIfAbsent("player", player.getName());
        return merged;
    }

    protected ConfigurationSection section() {
        return plugin.menuSection(configPath());
    }

    protected ConfigurationSection button(String key) {
        ConfigurationSection section = section();
        if (section == null) return null;

        // Pattern menus keep their button definitions under ITEMS; older ones use BUTTONS.
        ConfigurationSection items = section.getConfigurationSection("ITEMS");
        if (items != null && items.isConfigurationSection(key)) {
            return items.getConfigurationSection(key);
        }
        ConfigurationSection buttons = section.getConfigurationSection("BUTTONS");
        return buttons == null ? null : buttons.getConfigurationSection(key);
    }

    protected String string(String key, String fallback) {
        ConfigurationSection section = section();
        return section == null ? fallback : section.getString(key, fallback);
    }

    protected List<String> lines(String key) {
        ConfigurationSection section = section();
        return section == null ? List.of() : section.getStringList(key);
    }

    /**
     * Text handling for whoever is looking at this menu. Bedrock's font has no glyphs for
     * the small caps the menus use, so those are rewritten to plain ASCII for those
     * players only. Chest menus show lore natively there, so nothing else is needed.
     */
    public Style style() {
        boolean ascii = Bedrock.isBedrock(player)
                && plugin.getConfig().getBoolean("BEDROCK.ASCII-LABELS", true);
        return new Style(ascii);
    }

    /** How text is adapted for one viewer. */
    public record Style(boolean ascii) {

        public String text(String input) {
            return ascii ? Bedrock.ascii(input) : input;
        }

        public List<String> text(List<String> input) {
            if (!ascii) return input;
            List<String> out = new java.util.ArrayList<>(input.size());
            for (String line : input) out.add(Bedrock.ascii(line));
            return out;
        }
    }

    protected boolean isBedrock() {
        return Bedrock.isBedrock(player);
    }

    protected ScreenModel.Button configButton(String key, Map<String, String> placeholders,
                                              ScreenModel.Action action) {
        return configButton(key, placeholders, null, action);
    }

    /** Same, with an item to show in chest mode. Dialogs ignore the icon. */
    protected ScreenModel.Button configButton(String key, Map<String, String> placeholders,
                                              ItemStack icon, ScreenModel.Action action) {
        ConfigurationSection section = button(key);
        // NAME/LORE and LABEL/TOOLTIP both work. The first pair is the convention most
        // menu configs use for item entries; the second reads better for plain buttons.
        String label = key;
        List<String> tooltip = List.of();
        if (section != null) {
            label = section.getString("NAME", section.getString("LABEL", key));
            tooltip = section.isList("LORE")
                    ? section.getStringList("LORE")
                    : section.getStringList("TOOLTIP");
        }
        Material configured = section == null ? null
                : Material.matchMaterial(section.getString("MATERIAL", ""));
        Material fallback = configured != null ? configured : defaultIcon(key);

        // A placeholder may expand to several lines - the sort and filter buttons use
        // this to list every option with the active one marked.
        List<String> resolved = new java.util.ArrayList<>();
        for (String line : Text.applyPruned(tooltip, common(placeholders))) {
            if (line.indexOf('\n') < 0) {
                resolved.add(line);
                continue;
            }
            java.util.Collections.addAll(resolved, line.split("\n", -1));
        }

        return ScreenModel.Button.of(key,
                style().text(Text.apply(label, common(placeholders))),
                style().text(resolved),
                icon, fallback, action);
    }

    /**
     * Icon used when a button has no MATERIAL in config and carries no item of its own.
     * Paper for everything looks like a bug, so each control gets something that reads
     * as what it does.
     */
    private Material defaultIcon(String key) {
        return switch (key) {
            case "PREVIOUS", "NEXT", "BACK" -> Material.ARROW;
            case "CLOSE" -> Material.BARRIER;
            case "SORT" -> Material.COMPARATOR;
            case "FILTER" -> Material.HOPPER;
            case "SEARCH", "INPUT" -> Material.OAK_SIGN;
            case "CONFIRM", "DELIVER", "DELIVER-ALL" -> Material.LIME_CONCRETE;
            case "CANCEL-ORDER", "CANCEL-LISTING", "REMOVE", "CLEAR" -> Material.RED_CONCRETE;
            case "COLLECT", "COLLECT-ALL", "MY-ORDERS", "MY-LISTINGS" -> Material.CHEST;
            case "SELL", "SELL-ALL" -> Material.EMERALD;
            case "DROP-PAGE", "DROP-PAGES" -> Material.HOPPER;
            case "DROP-ALL" -> Material.DROPPER;
            case "ALERTS" -> Material.BELL;
            case "FAST-BUY" -> Material.SUGAR;
            case "ENCHANTS", "ENCHANT", "LEVEL" -> Material.ENCHANTED_BOOK;
            case "NEW-ORDER", "TRANSACTIONS" -> Material.WRITABLE_BOOK;
            case "CHOOSE-ITEM" -> Material.ITEM_FRAME;
            case "HELD-ITEM" -> Material.DROPPER;
            case "PREVIEW" -> Material.SPYGLASS;
            case "VIEW-MAP" -> Material.FILLED_MAP;
            default -> Material.PAPER;
        };
    }

    /** A button with no action: closes the screen. */
    protected ScreenModel.Button closeButton(String key, Map<String, String> placeholders) {
        return configButton(key, placeholders, null, null);
    }

    /** Footer button that just navigates somewhere else. */
    protected ScreenModel.Button backButton(String key, Map<String, String> placeholders,
                                            Runnable target) {
        return configButton(key, placeholders, responses -> {
            click();
            target.run();
        });
    }

    protected String titleFrom(Map<String, String> placeholders) {
        return style().text(Text.apply(string("TITLE", "Menu"), common(placeholders)));
    }

    public void show() {
        plugin.gui().render(this);
    }

    /** Re-show this screen after an action. Runs on the main thread next tick. */
    protected void reopen() {
        plugin.sync(this::show);
    }

    protected void tell(String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(Text.component(message));
    }

    protected void click() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.2F);
    }

    protected void success() {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.0F);
    }

    protected void deny() {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.6F);
    }

    // ------------------------------------------------------------------ paging

    protected static int totalPages(int elements, int perPage) {
        return Math.max(1, (int) Math.ceil(elements / (double) perPage));
    }

    protected static <T> List<T> slice(List<T> all, int page, int perPage) {
        int total = totalPages(all.size(), perPage);
        int safePage = Math.min(Math.max(0, page), total - 1);
        int from = safePage * perPage;
        int to = Math.min(all.size(), from + perPage);
        return from >= to ? List.of() : all.subList(from, to);
    }
}
