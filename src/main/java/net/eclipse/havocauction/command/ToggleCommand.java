package net.eclipse.havocauction.command;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Standalone toggles bound by external settings menus:
 *   /toggleauctionalerts  - sale notifications
 *   /togglefastauction    - skip confirmation screens
 */
public class ToggleCommand implements CommandExecutor {

    public enum Kind {
        ALERTS,
        FAST
    }

    private final HavocAuction plugin;
    private final Kind kind;

    public ToggleCommand(HavocAuction plugin, Kind kind) {
        this.plugin = plugin;
        this.kind = kind;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.component(plugin.message("PLAYERS-ONLY")));
            return true;
        }
        if (!player.hasPermission("havocauction.use")) {
            player.sendMessage(Text.component(plugin.message("NO-PERMISSION")));
            return true;
        }

        if (kind == Kind.ALERTS) {
            boolean enabled = plugin.profiles().toggleAlerts(player.getUniqueId());
            player.sendMessage(Text.component(plugin.message(enabled ? "ALERTS-ON" : "ALERTS-OFF")));
        } else {
            boolean enabled = plugin.profiles().toggleFastBuy(player.getUniqueId());
            player.sendMessage(Text.component(plugin.message(enabled ? "FAST-BUY-ON" : "FAST-BUY-OFF")));
        }
        return true;
    }
}
