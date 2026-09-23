package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.Text;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.TimeUtil;

import java.util.HashMap;
import java.util.Map;

public final class Placeholders {

    private Placeholders() {
    }

    /**
     * Line templates come from dialogs.yml LINES. They resolve to an empty string when
     * they do not apply, and Dialogs prunes lines that go empty, so a listing with no
     * durability simply has no durability row.
     */
    public static Map<String, String> of(HavocAuction plugin, Listing listing) {
        Map<String, String> map = of(listing);

        String durabilityLine = "";
        if (listing.hasDurability()) {
            durabilityLine = Text.apply(
                    plugin.line("DURABILITY", "&7Durability: &f{durability} &8({durability_percent}%)"),
                    map);
        }
        map.put("durability_line", durabilityLine);

        String renamedLine = "";
        if (listing.isRenamed()) {
            renamedLine = Text.apply(plugin.line("RENAMED", "&8Actually a {type}"), map);
        }
        map.put("renamed_line", renamedLine);

        // Shown inline on the button label, so a renamed item is obvious without hovering.
        map.put("renamed_tag", listing.isRenamed()
                ? Text.apply(plugin.line("RENAMED-TAG", " &8({type})"), map)
                : "");
        return map;
    }

    public static Map<String, String> of(Listing listing) {
        Map<String, String> map = new HashMap<>();
        map.put("seller", listing.getSellerName());
        map.put("player", listing.getSellerName());
        map.put("buyer", listing.getBuyerName());
        map.put("item", listing.getItemName());
        map.put("material", listing.getItemName());
        map.put("type", listing.getTypeName());
        map.put("custom_name", listing.getCustomName() == null ? "" : listing.getCustomName());
        map.put("renamed", String.valueOf(listing.isRenamed()));
        map.put("durability", listing.hasDurability()
                ? NumberUtil.count(listing.getDurabilityRemaining()) + "/"
                        + NumberUtil.count(listing.getMaxDurability())
                : "");
        map.put("durability_percent", listing.hasDurability()
                ? String.valueOf(listing.getDurabilityPercent()) : "");
        map.put("amount", NumberUtil.count(listing.getAmount()));
        map.put("price", NumberUtil.money(listing.getPrice()));
        map.put("unit_price", NumberUtil.money(listing.getUnitPrice()));
        map.put("expires", TimeUtil.shortDuration(listing.getMillisUntilExpiry()));
        map.put("status", listing.getStatus().name());
        map.put("time", listing.getSoldAt() == null
                ? TimeUtil.shortDuration(System.currentTimeMillis() - listing.getCreatedAt())
                : TimeUtil.shortDuration(System.currentTimeMillis() - listing.getSoldAt()));
        return map;
    }
}
