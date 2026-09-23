package net.eclipse.havocauction.util;

import org.bukkit.Material;

/** Filter categories used by the orders menu and the item picker. */
public enum Category {

    ALL,
    BLOCKS,
    TOOLS,
    FOOD,
    COMBAT,
    POTIONS,
    BOOKS,
    INGREDIENTS,
    UTILITIES;

    public Category next() {
        Category[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Category previous() {
        Category[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public boolean matches(Material material) {
        return this == ALL || this == of(material);
    }

    public static Category of(Material material) {
        String name = material.name();

        if (name.endsWith("POTION") || name.equals("TIPPED_ARROW")
                || name.equals("GLASS_BOTTLE") || name.equals("EXPERIENCE_BOTTLE")
                || name.equals("DRAGON_BREATH") || name.equals("BREWING_STAND")) {
            return POTIONS;
        }
        if (name.endsWith("BOOK") || name.equals("PAPER") || name.equals("BOOKSHELF")
                || name.equals("LECTERN")) {
            return BOOKS;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.endsWith("_ARROW")
                || name.equals("ARROW") || name.equals("BOW") || name.equals("CROSSBOW")
                || name.equals("TRIDENT") || name.equals("SHIELD") || name.equals("MACE")
                || name.equals("TOTEM_OF_UNDYING") || name.endsWith("_HORSE_ARMOR")
                || name.equals("WOLF_ARMOR") || name.equals("TURTLE_HELMET")) {
            return COMBAT;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE") || name.equals("SHEARS") || name.equals("FLINT_AND_STEEL")
                || name.equals("FISHING_ROD") || name.endsWith("BUCKET") || name.equals("BRUSH")
                || name.equals("SPYGLASS") || name.equals("COMPASS") || name.equals("CLOCK")
                || name.equals("LEAD") || name.equals("NAME_TAG") || name.equals("ELYTRA")) {
            return TOOLS;
        }
        if (material.isEdible() || name.equals("CAKE") || name.equals("MILK_BUCKET")) {
            return FOOD;
        }
        if (name.contains("REDSTONE") || name.contains("MINECART") || name.contains("BOAT")
                || name.contains("RAIL") || name.contains("SHULKER_BOX") || name.contains("BANNER")
                || name.equals("ENDER_PEARL") || name.equals("ENDER_EYE") || name.equals("FIREWORK_ROCKET")
                || name.contains("SPAWN_EGG") || name.contains("MUSIC_DISC") || name.equals("HOPPER")
                || name.equals("CHEST") || name.equals("ENDER_CHEST") || name.equals("BARREL")
                || name.equals("ANVIL") || name.equals("ENCHANTING_TABLE") || name.equals("FURNACE")) {
            return UTILITIES;
        }
        if (material.isBlock()) {
            return BLOCKS;
        }
        return INGREDIENTS;
    }
}
