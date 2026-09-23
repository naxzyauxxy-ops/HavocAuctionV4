package net.eclipse.havocauction.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Uses the native Bukkit 1.20.5+ item serialiser, which is data-fixer aware,
 * so stored orders survive Minecraft version upgrades.
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    /** Single-quantity template, for things keyed by item type. */
    public static String encode(ItemStack item) {
        ItemStack single = item.clone();
        single.setAmount(1);
        return Base64.getEncoder().encodeToString(single.serializeAsBytes());
    }

    /** Keeps the stack size. Auction listings sell an exact stack. */
    public static String encodeFull(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.clone().serializeAsBytes());
    }

    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (Exception ex) {
            return null;
        }
    }
}
