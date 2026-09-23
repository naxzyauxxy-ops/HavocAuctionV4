package net.eclipse.havocauction.model;

public enum ListingStatus {

    /** On the board, buyable. */
    ACTIVE,
    /** Bought. Item went to the buyer, money to the seller. Kept as history. */
    SOLD,
    /** Pulled by the seller. Item waits for them to collect. */
    CANCELLED,
    /** Ran out of time. Item waits for the seller to collect. */
    EXPIRED,
    /** Item returned to the seller. Kept briefly as history. */
    COLLECTED;

    public boolean isBuyable() {
        return this == ACTIVE;
    }

    /** The seller still has an item sitting here. */
    public boolean awaitsCollection() {
        return this == CANCELLED || this == EXPIRED;
    }

    /** Finished business - only useful as a transaction record. */
    public boolean isHistory() {
        return this == SOLD || this == COLLECTED;
    }
}
