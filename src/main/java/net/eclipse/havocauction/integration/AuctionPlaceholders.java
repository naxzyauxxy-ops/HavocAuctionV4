package net.eclipse.havocauction.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.util.NumberUtil;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * PlaceholderAPI expansion for external settings menus and scoreboards.
 *
 * %havocauction_alerts_status%   ON / OFF, styled by config
 * %havocauction_fast_status%     ON / OFF for fast mode
 * %havocauction_alerts_raw%      true / false
 * %havocauction_fast_raw%        true / false
 * %havocauction_listings%        live listings
 * %havocauction_collectable%     listings waiting to collect
 * %havocauction_listed_value%    asking value of your live listings
 * %havocauction_total_made%      lifetime earnings
 * %havocauction_total_spent%     lifetime spend
 * %havocauction_net%             made minus spent
 * %havocauction_board_size%      listings on the board right now
 */
public class AuctionPlaceholders extends PlaceholderExpansion {

    private final HavocAuction plugin;

    public AuctionPlaceholders(HavocAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "havocauction";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Eclipse";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("board_size")) {
            return NumberUtil.count(plugin.auction().board().size());
        }
        if (player == null) return "";

        boolean alerts = plugin.profiles().alertsEnabled(player.getUniqueId());
        boolean fast = plugin.profiles().fastBuy(player.getUniqueId());

        switch (key) {
            case "alerts_status", "alerts" -> {
                return plugin.statusText(alerts);
            }
            case "alerts_raw" -> {
                return String.valueOf(alerts);
            }
            case "fast_status", "fastauction", "fast" -> {
                return plugin.statusText(fast);
            }
            case "fast_raw" -> {
                return String.valueOf(fast);
            }
            case "listings" -> {
                return NumberUtil.count(plugin.auction().activeCount(player.getUniqueId()));
            }
            case "collectable" -> {
                return NumberUtil.count(plugin.auction().collectable(player.getUniqueId()).size());
            }
            case "listed_value" -> {
                double value = plugin.auction().activeOf(player.getUniqueId())
                        .stream().mapToDouble(listing -> listing.getPrice()).sum();
                return NumberUtil.money(value);
            }
            default -> {
            }
        }

        AuctionManager.Stats stats = plugin.auction().statsOf(player.getUniqueId());
        return switch (key) {
            case "total_made" -> NumberUtil.money(stats.made());
            case "total_spent" -> NumberUtil.money(stats.spent());
            case "net" -> NumberUtil.money(stats.made() - stats.spent());
            case "sales" -> NumberUtil.count(stats.sales());
            case "purchases" -> NumberUtil.count(stats.purchases());
            default -> null;
        };
    }
}
