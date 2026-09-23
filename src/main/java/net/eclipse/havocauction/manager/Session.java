package net.eclipse.havocauction.manager;

import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.model.SortOption;
import net.eclipse.havocauction.util.Category;

import java.util.List;
import java.util.UUID;

/**
 * Per-player dialog state. Dialogs are rebuilt on every interaction, so keeping filters,
 * pages and the in-progress sale here makes a page turn a list slice rather than a
 * recompute of the whole board.
 */
public class Session {

    private final UUID playerId;

    private SortOption sort = SortOption.RECENTLY_LISTED;
    private Category filter = Category.ALL;
    private String query = "";
    private int page;

    private List<Listing> cachedBoard = List.of();
    private long cachedVersion = -1L;
    private String cachedSignature = "";

    private int myListingsPage;
    private int collectPage;
    private int transactionsPage;
    private String transactionQuery = "";

    /** Price typed into the sell dialog, kept across a trip to confirmation. */
    private double draftPrice;

    public Session(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SortOption getSort() {
        return sort;
    }

    public void setSort(SortOption sort) {
        this.sort = sort;
        this.page = 0;
    }

    public Category getFilter() {
        return filter;
    }

    public void setFilter(Category filter) {
        this.filter = filter;
        this.page = 0;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query;
        this.page = 0;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    private String signature() {
        return sort.name() + '|' + filter.name() + '|' + query.toLowerCase();
    }

    public boolean isBoardStale(long version) {
        return version != cachedVersion || !signature().equals(cachedSignature);
    }

    public void cacheBoard(List<Listing> listings, long version) {
        this.cachedBoard = listings;
        this.cachedVersion = version;
        this.cachedSignature = signature();
    }

    public List<Listing> getCachedBoard() {
        return cachedBoard;
    }

    public int getMyListingsPage() {
        return myListingsPage;
    }

    public void setMyListingsPage(int value) {
        this.myListingsPage = Math.max(0, value);
    }

    public int getCollectPage() {
        return collectPage;
    }

    public void setCollectPage(int value) {
        this.collectPage = Math.max(0, value);
    }

    public int getTransactionsPage() {
        return transactionsPage;
    }

    public void setTransactionsPage(int value) {
        this.transactionsPage = Math.max(0, value);
    }

    public String getTransactionQuery() {
        return transactionQuery;
    }

    public void setTransactionQuery(String value) {
        this.transactionQuery = value == null ? "" : value;
        this.transactionsPage = 0;
    }

    public double getDraftPrice() {
        return draftPrice;
    }

    public void setDraftPrice(double draftPrice) {
        this.draftPrice = Math.max(0.0D, draftPrice);
    }
}
