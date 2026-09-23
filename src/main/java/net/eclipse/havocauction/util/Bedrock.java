package net.eclipse.havocauction.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Bedrock (Geyser/Floodgate) awareness.
 *
 * Geyser translates Java dialogs into Bedrock forms, so the screens themselves work. Two
 * things do not survive the translation:
 *
 *  1. Text input often comes back empty (GeyserMC/Geyser#6377). Every field in this plugin
 *     therefore has a sensible default and a command equivalent, so a Bedrock player is
 *     never stuck at a box that silently returns "".
 *  2. Button tooltips are not rendered in Bedrock forms, so anything important has to be
 *     in the label itself.
 *
 * Detection goes through Floodgate by reflection: no compile-time dependency, and servers
 * without it simply treat everyone as Java.
 */
public final class Bedrock {

    private static Boolean available;
    private static Object apiInstance;
    private static Method isFloodgatePlayer;

    /** Small-caps and box-drawing glyphs used in the configs, mapped to plain ASCII. */
    private static final Map<Character, Character> ASCII = Map.ofEntries(
            Map.entry('\u1D00', 'A'), Map.entry('\u0299', 'B'), Map.entry('\u1D04', 'C'),
            Map.entry('\u1D05', 'D'), Map.entry('\u1D07', 'E'), Map.entry('\uA730', 'F'),
            Map.entry('\u0262', 'G'), Map.entry('\u029C', 'H'), Map.entry('\u026A', 'I'),
            Map.entry('\u1D0A', 'J'), Map.entry('\u1D0B', 'K'), Map.entry('\u029F', 'L'),
            Map.entry('\u1D0D', 'M'), Map.entry('\u0274', 'N'), Map.entry('\u1D0F', 'O'),
            Map.entry('\u1D18', 'P'), Map.entry('\u01EB', 'Q'), Map.entry('\u0280', 'R'),
            Map.entry('\u0455', 'S'), Map.entry('\u1D1B', 'T'), Map.entry('\u1D1C', 'U'),
            Map.entry('\u1D20', 'V'), Map.entry('\u1D21', 'W'), Map.entry('\u028F', 'Y'),
            Map.entry('\u1D22', 'Z'), Map.entry('\u25AA', '-'), Map.entry('\u25B0', '|'),
            Map.entry('\u2192', '>'), Map.entry('\u2190', '<'),
            Map.entry('\u00d7', 'x'), Map.entry('\u00b7', '-'),
            Map.entry('\u25b2', '+'), Map.entry('\u25bc', '-'),
            Map.entry('\u2756', '*')
    );

    private Bedrock() {
    }

    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        return isBedrock(player.getUniqueId());
    }

    public static boolean isBedrock(UUID playerId) {
        if (!hookAvailable()) return false;
        try {
            return (boolean) isFloodgatePlayer.invoke(apiInstance, playerId);
        } catch (Exception ex) {
            return false;
        }
    }

    private static synchronized boolean hookAvailable() {
        if (available != null) return available;
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
                available = false;
                return false;
            }
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            apiInstance = api.getMethod("getInstance").invoke(null);
            isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
            available = apiInstance != null;
        } catch (Throwable ex) {
            available = false;
        }
        return available;
    }

    /** Replaces glyphs Bedrock's font cannot draw. Unknown characters are left alone. */
    public static String ascii(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder out = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            Character replacement = ASCII.get(c);
            out.append(replacement == null ? c : replacement);
        }
        return out.toString();
    }
}
