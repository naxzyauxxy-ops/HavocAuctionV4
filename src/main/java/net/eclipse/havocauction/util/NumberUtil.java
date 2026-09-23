package net.eclipse.havocauction.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Number parsing and display.
 *
 * Parsing accepts shorthand suffixes so players can type "1k", "2.5m", "3b" instead of
 * counting zeroes. Display can abbreviate the same way, controlled by config.
 */
public final class NumberUtil {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat PLAIN =
            new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat SHORT =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private static final Pattern INPUT =
            Pattern.compile("(?i)^\\s*\\$?\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*([kmbtq]|qd)?\\s*$");

    /** Suffixes in ascending order. Index 0 is 10^3. */
    private static final String[] SUFFIXES = {"k", "m", "b", "t", "q"};

    /** Set once from config so callers do not have to pass it around. */
    private static boolean abbreviate = true;

    private NumberUtil() {
    }

    public static void setAbbreviate(boolean value) {
        abbreviate = value;
    }

    public static boolean isAbbreviating() {
        return abbreviate;
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Parses user input, accepting commas, a leading $, and k/m/b/t/q suffixes.
     * Returns null if the input is not a number.
     */
    public static Double parse(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replace(",", "").replace("_", "").trim();
        if (cleaned.isEmpty()) return null;

        Matcher matcher = INPUT.matcher(cleaned);
        if (!matcher.matches()) return null;

        double value;
        try {
            value = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }

        String suffix = matcher.group(2);
        if (suffix != null) {
            String lowered = suffix.toLowerCase(Locale.ROOT);
            double multiplier = switch (lowered) {
                case "k" -> 1_000D;
                case "m" -> 1_000_000D;
                case "b" -> 1_000_000_000D;
                case "t" -> 1_000_000_000_000D;
                case "q", "qd" -> 1_000_000_000_000_000D;
                default -> 1D;
            };
            value *= multiplier;
        }

        if (Double.isNaN(value) || Double.isInfinite(value)) return null;
        return value;
    }

    /**
     * Parses a quantity. Also understands "all" / "max" / "everything", which return
     * {@code fallbackMax}, and "half".
     */
    public static Integer parseAmount(String raw, int fallbackMax) {
        if (raw == null) return null;
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        if (lowered.equals("all") || lowered.equals("max") || lowered.equals("everything") || lowered.equals("*")) {
            return fallbackMax;
        }
        if (lowered.equals("half")) {
            return Math.max(1, fallbackMax / 2);
        }
        Double parsed = parse(raw);
        if (parsed == null) return null;
        if (parsed > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (parsed < Integer.MIN_VALUE) return null;
        return (int) Math.floor(parsed);
    }

    // ------------------------------------------------------------------ display

    /** Full precision with thousands separators: 1,234,567.5 */
    public static String exact(double value) {
        return MONEY.format(value);
    }

    public static String exact(long value) {
        return PLAIN.format(value);
    }

    /** 1234567 -> 1.23m. Values under 1000 are printed as-is. */
    public static String abbreviate(double value) {
        double absolute = Math.abs(value);
        if (absolute < 1000D) return MONEY.format(value);

        int index = -1;
        double scaled = absolute;
        while (scaled >= 1000D && index < SUFFIXES.length - 1) {
            scaled /= 1000D;
            index++;
        }
        String sign = value < 0 ? "-" : "";
        return sign + SHORT.format(scaled) + SUFFIXES[index];
    }

    /** Abbreviated when enabled in config, exact otherwise. Use for money. */
    public static String money(double value) {
        return abbreviate ? abbreviate(value) : exact(value);
    }

    /** Abbreviated when enabled in config, exact otherwise. Use for item counts. */
    public static String count(long value) {
        return abbreviate ? abbreviate(value) : exact(value);
    }
}
