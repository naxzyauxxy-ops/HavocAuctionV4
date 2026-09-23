package net.eclipse.havocauction.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small fluent builder that understands the menu.yml button format. */
public final class ItemBuilder {

    private final ItemStack item;
    private final Map<String, String> placeholders = new HashMap<>();
    private String name;
    private List<String> lore = new ArrayList<>();

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material == null ? Material.STONE : material));
    }

    public static ItemBuilder of(ItemStack base) {
        return new ItemBuilder(base == null ? new ItemStack(Material.STONE) : base.clone());
    }

    /** Reads MATERIAL / NAME / LORE from a config section. Falls back to the given material. */
    public static ItemBuilder fromSection(ConfigurationSection section, Material fallback) {
        Material material = fallback;
        if (section != null && section.isString("MATERIAL")) {
            Material parsed = Material.matchMaterial(section.getString("MATERIAL", ""));
            if (parsed != null) material = parsed;
        }
        ItemBuilder builder = ItemBuilder.of(material);
        if (section != null) {
            if (section.isString("NAME")) builder.name = section.getString("NAME");
            if (section.isList("LORE")) builder.lore = new ArrayList<>(section.getStringList("LORE"));
        }
        return builder;
    }

    /** Same as {@link #fromSection} but keeps an existing stack (used for order/loot icons). */
    public static ItemBuilder fromSection(ConfigurationSection section, ItemStack base) {
        ItemBuilder builder = ItemBuilder.of(base);
        if (section != null) {
            if (section.isString("NAME")) builder.name = section.getString("NAME");
            if (section.isList("LORE")) builder.lore = new ArrayList<>(section.getStringList("LORE"));
        }
        return builder;
    }

    public ItemBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        this.lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        return this;
    }

    public ItemBuilder appendLore(String line) {
        this.lore.add(line);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return this;
    }

    public ItemBuilder with(String key, Object value) {
        placeholders.put(key, String.valueOf(value));
        return this;
    }

    public ItemBuilder with(Map<String, String> values) {
        placeholders.putAll(values);
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(Text.component(Text.apply(name, placeholders)));
            }
            if (!lore.isEmpty()) {
                meta.lore(Text.components(Text.apply(lore, placeholders)));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
