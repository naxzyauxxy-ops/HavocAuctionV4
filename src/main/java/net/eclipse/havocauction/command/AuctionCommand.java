package net.eclipse.havocauction.command;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.dialog.AuctionScreen;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.storage.LegacyImporter;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ah opens the board. /ah sell <price> lists what you are holding without a dialog,
 * which is the one shortcut worth keeping from the old plugin.
 */
public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final HavocAuction plugin;

    public AuctionCommand(HavocAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("havocauction.admin")) {
                sender.sendMessage(Text.component(plugin.message("NO-PERMISSION")));
                return true;
            }
            plugin.reloadEverything();
            sender.sendMessage(Text.component(plugin.message("RELOADED")));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("import")) {
            if (!sender.hasPermission("havocauction.admin")) {
                sender.sendMessage(Text.component(plugin.message("NO-PERMISSION")));
                return true;
            }
            File file = args.length > 1
                    ? new File(plugin.getDataFolder(), args[1])
                    : plugin.importer().defaultFile();
            if (!file.isFile()) {
                sender.sendMessage(Text.component("&cNo such file: " + file.getPath()));
                return true;
            }
            try {
                LegacyImporter.Report report = plugin.importer().importFrom(file);
                for (String line : plugin.importer().summary(report)) {
                    sender.sendMessage(Text.component("&#f40d0d" + line));
                }
                sender.sendMessage(Text.component("&7No money moved and no items were handed out."));
            } catch (Exception ex) {
                sender.sendMessage(Text.component("&cImport failed: " + ex.getMessage()
                        + " - see console. Nothing was changed."));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.component(plugin.message("PLAYERS-ONLY")));
            return true;
        }
        if (!player.hasPermission("havocauction.use")) {
            player.sendMessage(Text.component(plugin.message("NO-PERMISSION")));
            return true;
        }
        if (!plugin.economy().isReady()) {
            player.sendMessage(Text.component(plugin.message("NO-ECONOMY")));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2) {
                player.sendMessage(Text.component(plugin.message("SELL-USAGE")));
                return true;
            }
            Double price = NumberUtil.parse(args[1]);
            if (price == null || price <= 0) {
                player.sendMessage(Text.component(plugin.message("PRICE-INVALID")));
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) {
                player.sendMessage(Text.component(plugin.message("NO-ITEM")));
                return true;
            }
            AuctionManager.Result result = plugin.auction().list(player, held, price);
            player.sendMessage(Text.component(result.message()));
            if (result.success()) player.getInventory().setItemInMainHand(null);
            return true;
        }

        // Anything that is not a sub-command is treated as a search, so "/ah elytra" or
        // "/ah notch signed book" just works. This also covers Bedrock, where typing into
        // a dialog field is unreliable.
        if (args.length > 0) {
            int from = args[0].equalsIgnoreCase("search") ? 1 : 0;
            String query = args.length > from
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length))
                    : "";
            plugin.sessions().get(player).setQuery(query);
            if (!query.isEmpty()) {
                player.sendMessage(Text.component(Text.apply(plugin.message("SEARCHING"),
                        java.util.Map.of("query", query))));
            }
        }

        new AuctionScreen(plugin, player).show();
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("sell");
            options.add("search");
            if (sender.hasPermission("havocauction.admin")) {
                options.add("reload");
                options.add("import");
            }
            options.removeIf(option -> !option.startsWith(args[0].toLowerCase(Locale.ROOT)));
        }
        return options;
    }
}
