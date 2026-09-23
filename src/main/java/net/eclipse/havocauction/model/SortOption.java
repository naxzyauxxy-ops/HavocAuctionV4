package net.eclipse.havocauction.model;

import java.util.Comparator;

public enum SortOption {

    RECENTLY_LISTED("RECENTLY-LISTED", Comparator.comparingLong(Listing::getCreatedAt).reversed()),
    OLDEST_LISTED("OLDEST-LISTED", Comparator.comparingLong(Listing::getCreatedAt)),
    LOWEST_PRICE("LOWEST-PRICE", Comparator.comparingDouble(Listing::getPrice)),
    HIGHEST_PRICE("HIGHEST-PRICE", Comparator.comparingDouble(Listing::getPrice).reversed()),
    BEST_UNIT_PRICE("BEST-UNIT-PRICE", Comparator.comparingDouble(Listing::getUnitPrice)),
    ENDING_SOON("ENDING-SOON", Comparator.comparingLong(Listing::getExpiresAt));

    private final String configKey;
    private final Comparator<Listing> comparator;

    SortOption(String configKey, Comparator<Listing> comparator) {
        this.configKey = configKey;
        this.comparator = comparator;
    }

    public String getConfigKey() {
        return configKey;
    }

    public Comparator<Listing> getComparator() {
        return comparator;
    }

    public SortOption next() {
        SortOption[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public SortOption previous() {
        SortOption[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
