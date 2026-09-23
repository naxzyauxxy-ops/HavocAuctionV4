package net.eclipse.havocauction.ui;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A menu drawn as a picture.
 *
 * STRUCTURE is one line per row, one character per slot, and CHARS says what each
 * character means. You can see the shape of the menu in the file instead of counting
 * slot numbers.
 *
 *   STRUCTURE:
 *     - "g g g g g g g g g"
 *     - "g . . . . . . . g"
 *     - "< # o f a s i # >"
 *   CHARS:
 *     CONTENT: "."
 *     BORDER: "g"
 *     BACK: "<"
 *
 * CONTENT marks the repeating area. BORDER and FILLER are decoration. Every other role
 * names a button and is matched against the keys the screen produces.
 */
public record Layout(List<Integer> content, Map<String, Integer> slots,
                     Map<Integer, String> decoration) {

    private static final Layout EMPTY = new Layout(List.of(), Map.of(), Map.of());

    public static Layout read(ConfigurationSection section, int size) {
        if (section == null) return EMPTY;
        List<String> structure = section.getStringList("STRUCTURE");
        if (structure.isEmpty()) return EMPTY;

        ConfigurationSection chars = section.getConfigurationSection("CHARS");
        if (chars == null) return EMPTY;

        // CHARS reads role -> character; the grid needs the reverse.
        Map<Character, String> roles = new HashMap<>();
        for (String role : chars.getKeys(false)) {
            String value = chars.getString(role, "");
            if (!value.isEmpty()) roles.put(value.charAt(0), role);
        }

        List<Integer> content = new ArrayList<>();
        Map<String, Integer> slots = new LinkedHashMap<>();
        Map<Integer, String> decoration = new LinkedHashMap<>();

        int row = 0;
        for (String line : structure) {
            // Rows read either "g g g" or "ggg"; both turn up in configs.
            String cells = line.replace(" ", "");
            for (int column = 0; column < cells.length() && column < 9; column++) {
                int slot = row * 9 + column;
                if (slot >= size) break;

                String role = roles.get(cells.charAt(column));
                if (role == null) continue;
                switch (role) {
                    case "CONTENT" -> content.add(slot);
                    case "BORDER", "FILLER" -> decoration.put(slot, role);
                    // First occurrence wins, so repeating a character is harmless.
                    default -> slots.putIfAbsent(role, slot);
                }
            }
            row++;
        }
        return new Layout(content, slots, decoration);
    }
}
