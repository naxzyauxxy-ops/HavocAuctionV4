package net.eclipse.havocauction.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour / placeholder helpers.
 * Supports both legacy codes (&a) and hex codes (&#f40d0d), matching the menu.yml format.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Text() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher matcher = HEX.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char c : matcher.group(1).toCharArray()) {
                replacement.append('\u00a7').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    /** Colours the string and returns it as a non-italic component (for item names / titles). */
    public static Component component(String input) {
        return LEGACY.deserialize(color(input)).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> components(List<String> input) {
        List<Component> out = new ArrayList<>();
        if (input == null) return out;
        for (String line : input) out.add(component(line));
        return out;
    }

    /** Joins lines into one component with newlines - used for dialog button tooltips. */
    public static Component multiline(List<String> lines) {
        if (lines == null || lines.isEmpty()) return component("");
        return component(String.join("\n", lines));
    }

    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }

    public static String apply(String input, Map<String, String> placeholders) {
        if (input == null) return "";
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static List<String> apply(List<String> input, Map<String, String> placeholders) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String line : input) out.add(apply(line, placeholders));
        return out;
    }

    /**
     * Applies placeholders and drops any line that had content before substitution but is
     * blank afterwards. Lets an optional row like {durability_line} vanish entirely
     * instead of leaving an empty gap, while deliberate "" spacer lines are kept.
     */
    public static List<String> applyPruned(List<String> input, Map<String, String> placeholders) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String line : input) {
            String resolved = apply(line, placeholders);
            if (!line.isBlank() && strip(resolved).isBlank()) continue;
            out.add(resolved);
        }
        return out;
    }

    /** DIAMOND_PICKAXE -> Diamond Pickaxe */
    public static String pretty(String constant) {
        if (constant == null || constant.isEmpty()) return "";
        String[] parts = constant.toLowerCase().replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    public static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }
}
