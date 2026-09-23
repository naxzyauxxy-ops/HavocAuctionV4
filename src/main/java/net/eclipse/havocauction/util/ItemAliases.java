package net.eclipse.havocauction.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extra words a search should match for a given item.
 *
 * Players do not search for "Written Book", they search for "signed book". The real type
 * name stays authoritative for display; these are additional handles for finding things.
 */
public final class ItemAliases {

    private static final Map<Material, List<String>> ALIASES = new EnumMap<>(Material.class);

    private ItemAliases() {
    }

    public static void load(ConfigurationSection section) {
        ALIASES.clear();
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null) continue;
            List<String> words = new ArrayList<>();
            for (String word : section.getStringList(key)) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
            if (!words.isEmpty()) ALIASES.put(material, words);
        }
    }

    public static List<String> of(Material material) {
        return ALIASES.getOrDefault(material, List.of());
    }
}
