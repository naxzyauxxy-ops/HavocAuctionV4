package net.eclipse.havocauction.manager;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.model.ListingStatus;
import net.eclipse.havocauction.storage.SqlStorage;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every listing lives in memory; the database is only ever written to, in batches.
 *
 * Unlike the orders plugin there is no escrow: the seller keeps nothing until a sale, the
 * buyer pays at the moment of purchase, and the item moves in the same operation. The one
 * thing that must never happen is an item existing in two places, so purchase and
 * collection both mark the listing before handing anything over.
 */
public class AuctionManager {

    private final HavocAuction plugin;
    private final SqlStorage storage;

    private final Map<UUID, Listing> listings = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> bySeller = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> byBuyer = new ConcurrentHashMap<>();

    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingDeletes = ConcurrentHashMap.newKeySet();

    private final AtomicLong version = new AtomicLong();

    /** Collapses a burst of changes into one write instead of one per action. */
    private final java.util.concurrent.atomic.AtomicBoolean flushQueued =
            new java.util.concurrent.atomic.AtomicBoolean();

    public AuctionManager(HavocAuction plugin, SqlStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public long version() {
        return version.get();
    }

    private void touch() {
        version.incrementAndGet();
    }

    // ------------------------------------------------------------------ loading

    public void loadAll() {
        listings.clear();
        bySeller.clear();
        byBuyer.clear();
        for (Listing listing : storage.loadAll()) {
            index(listing);
        }
        touch();
        plugin.getLogger().info("Loaded " + listings.size() + " listings.");
    }

    private void index(Listing listing) {
        listings.put(listing.getId(), listing);
        bySeller.computeIfAbsent(listing.getSeller(), key -> ConcurrentHashMap.newKeySet())
                .add(listing.getId());
        if (listing.getBuyer() != null) {
            byBuyer.computeIfAbsent(listing.getBuyer(), key -> ConcurrentHashMap.newKeySet())
                    .add(listing.getId());
        }
    }

    private void unindex(Listing listing) {
        listings.remove(listing.getId());
        Set<UUID> sold = bySeller.get(listing.getSeller());
        if (sold != null) {
            sold.remove(listing.getId());
            if (sold.isEmpty()) bySeller.remove(listing.getSeller());
        }
        if (listing.getBuyer() != null) {
            Set<UUID> bought = byBuyer.get(listing.getBuyer());
            if (bought != null) {
                bought.remove(listing.getId());
                if (bought.isEmpty()) byBuyer.remove(listing.getBuyer());
            }
        }
    }

    /**
     * Writes pending changes. Synchronized because a scheduled flush and an immediate
     * one can otherwise interleave batches on a single JDBC connection.
     */
    public synchronized void flush() {
        if (!dirty.isEmpty()) {
            List<UUID> ids = new ArrayList<>(dirty);
            dirty.removeAll(ids);
            List<Listing> batch = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                Listing listing = listings.get(id);
                if (listing != null) batch.add(listing);
            }
            if (!batch.isEmpty()) storage.saveAll(batch);
        }
        if (!pendingDeletes.isEmpty()) {
            List<UUID> ids = new ArrayList<>(pendingDeletes);
            pendingDeletes.removeAll(ids);
            storage.deleteAll(ids);
        }
    }

    /**
     * Asks for a write as soon as possible.
     *
     * Anything that hands a player an item or moves money must not sit in memory waiting
     * for the periodic flush: if the server dies before that flush, the player keeps the
     * item and the record comes back on restart, which is a duplication bug. Bursts are
     * collapsed so a busy server still writes in batches.
     */
    private void requestFlush() {
        if (flushQueued.compareAndSet(false, true)) {
            plugin.async(() -> {
                flushQueued.set(false);
                flush();
            });
        }
    }

    private void persist(Listing listing) {
        dirty.add(listing.getId());
        touch();
        requestFlush();
    }

    private void remove(Listing listing) {
        unindex(listing);
        dirty.remove(listing.getId());
        pendingDeletes.add(listing.getId());
        touch();
        requestFlush();
    }

    // ------------------------------------------------------------------ lookups

    public Listing byId(UUID id) {
        return listings.get(id);
    }

    public List<Listing> board() {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.isListed()) result.add(listing);
        }
        return result;
    }

    private List<Listing> ofSeller(UUID seller) {
        Set<UUID> owned = bySeller.get(seller);
        if (owned == null || owned.isEmpty()) return List.of();
        List<Listing> result = new ArrayList<>(owned.size());
        for (UUID id : owned) {
            Listing listing = listings.get(id);
            if (listing != null) result.add(listing);
        }
        return result;
    }

    /** Live listings the player is selling. */
    public List<Listing> activeOf(UUID seller) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : ofSeller(seller)) {
            if (listing.isListed()) result.add(listing);
        }
        result.sort(Comparator.comparingLong(Listing::getCreatedAt).reversed());
        return result;
    }

    /** Cancelled or expired listings whose item is waiting to be picked up. */
    public List<Listing> collectable(UUID seller) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : ofSeller(seller)) {
            if (listing.awaitsCollection()) result.add(listing);
        }
        result.sort(Comparator.comparingLong(Listing::getExpiresAt));
        return result;
    }

    public int collectableCount(UUID seller) {
        int total = 0;
        for (Listing listing : ofSeller(seller)) {
            if (listing.awaitsCollection()) total += listing.getAmount();
        }
        return total;
    }

    public long activeCount(UUID seller) {
        long count = 0;
        for (Listing listing : ofSeller(seller)) {
            if (listing.isListed()) count++;
        }
        return count;
    }

    /** Completed sales and purchases, newest first. */
    public List<Listing> transactionsOf(UUID playerId) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : ofSeller(playerId)) {
            if (listing.getStatus() == ListingStatus.SOLD) result.add(listing);
        }
        Set<UUID> bought = byBuyer.get(playerId);
        if (bought != null) {
            for (UUID id : bought) {
                Listing listing = listings.get(id);
                if (listing != null && listing.getStatus() == ListingStatus.SOLD) result.add(listing);
            }
        }
        result.sort(Comparator.comparingLong(
                listing -> -(listing.getSoldAt() == null ? 0L : listing.getSoldAt())));
        return result;
    }

    public record Stats(double made, double spent, int sales, int purchases) {
    }

    public Stats statsOf(UUID playerId) {
        double made = 0;
        double spent = 0;
        int sales = 0;
        int purchases = 0;
        for (Listing listing : transactionsOf(playerId)) {
            if (listing.getSeller().equals(playerId)) {
                made += listing.getPrice() * (1 - taxRate());
                sales++;
            } else {
                spent += listing.getPrice();
                purchases++;
            }
        }
        return new Stats(made, spent, sales, purchases);
    }

    private double taxRate() {
        return Math.max(0, Math.min(100, plugin.getConfig().getDouble("AUCTION.TAX-PERCENT", 0))) / 100D;
    }

    // ------------------------------------------------------------------ listing

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /** Lists the stack the player is holding, removing it from their hand. */
    public Result list(Player player, ItemStack item, double price) {
        if (item == null || item.getType() == Material.AIR) {
            return Result.fail(plugin.message("NO-ITEM"));
        }
        if (plugin.isBlocked(item.getType())) {
            return Result.fail(plugin.message("ITEM-BLACKLISTED"));
        }

        double min = plugin.getConfig().getDouble("AUCTION.MIN-PRICE", 10D);
        double max = plugin.getConfig().getDouble("AUCTION.MAX-PRICE", 10_000_000_000D);
        if (price < min) {
            return Result.fail(Text.apply(plugin.message("PRICE-LOW"),
                    Map.of("min", NumberUtil.money(min))));
        }
        if (price > max) {
            return Result.fail(Text.apply(plugin.message("PRICE-HIGH"),
                    Map.of("max", NumberUtil.money(max))));
        }

        int limit = plugin.getConfig().getInt("AUCTION.MAX-LISTINGS-PER-PLAYER", 0);
        if (limit > 0 && !player.hasPermission("havocauction.admin")
                && activeCount(player.getUniqueId()) >= limit) {
            return Result.fail(Text.apply(plugin.message("MAX-LISTINGS-LIMIT"),
                    Map.of("limit", String.valueOf(limit))));
        }

        double fee = listingFee(price);
        if (fee > 0) {
            if (!plugin.economy().has(player, fee)) {
                return Result.fail(Text.apply(plugin.message("CANNOT-AFFORD-FEE"),
                        Map.of("fee", NumberUtil.money(fee))));
            }
            if (!plugin.economy().withdraw(player, fee)) {
                return Result.fail(plugin.message("ECONOMY-ERROR-WITHDRAW"));
            }
        }

        // Take the item only once everything else has succeeded.
        ItemStack listed = item.clone();
        long duration = TimeUnit.SECONDS.toMillis(
                plugin.getConfig().getLong("AUCTION.DURATION-SECONDS", 604800L));
        Listing listing = Listing.create(player.getUniqueId(), player.getName(), listed, price, duration);

        index(listing);
        persist(listing);
        broadcast(listing);

        Map<String, String> placeholders = Map.of(
                "item", listing.getItemName(),
                "amount", NumberUtil.count(listing.getAmount()),
                "price", NumberUtil.money(price),
                "fee", NumberUtil.money(fee));
        return Result.ok(Text.apply(plugin.message("LISTED"), placeholders));
    }

    public double listingFee(double price) {
        double percent = plugin.getConfig().getDouble("AUCTION.LISTING-FEE-PERCENT", 0);
        double flat = plugin.getConfig().getDouble("AUCTION.LISTING-FEE-FLAT", 0);
        return Math.max(0, price * (percent / 100D) + flat);
    }

    private void broadcast(Listing listing) {
        double threshold = plugin.getConfig().getDouble("AUCTION.BROADCAST-PRICE-THRESHOLD", 0);
        if (threshold <= 0 || listing.getPrice() < threshold) return;
        String message = Text.apply(plugin.message("LISTING-BROADCAST"), Map.of(
                "player", listing.getSellerName(),
                "item", listing.getItemName(),
                "amount", NumberUtil.count(listing.getAmount()),
                "price", NumberUtil.money(listing.getPrice())));
        if (message.isEmpty()) return;
        Bukkit.broadcast(Text.component(message));
    }

    // ------------------------------------------------------------------ buying

    public Result buy(Player buyer, UUID listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null || !listing.isListed()) {
            return Result.fail(plugin.message("LISTING-UNAVAILABLE"));
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            return Result.fail(plugin.message("OWN-LISTING"));
        }
        if (!plugin.economy().has(buyer, listing.getPrice())) {
            return Result.fail(Text.apply(plugin.message("NOT-ENOUGH-MONEY"),
                    Map.of("price", NumberUtil.money(listing.getPrice()))));
        }
        if (!hasRoom(buyer, listing.getItem())) {
            return Result.fail(plugin.message("INVENTORY-FULL"));
        }

        // Claim it before any money or items move, so two buyers cannot both win.
        listing.markSold(buyer.getUniqueId(), buyer.getName());
        byBuyer.computeIfAbsent(buyer.getUniqueId(), key -> ConcurrentHashMap.newKeySet())
                .add(listing.getId());
        persist(listing);

        if (!plugin.economy().withdraw(buyer, listing.getPrice())) {
            // Roll the claim back rather than hand over a free item.
            listing.setStatus(ListingStatus.ACTIVE);
            persist(listing);
            return Result.fail(plugin.message("ECONOMY-ERROR-WITHDRAW"));
        }

        double tax = listing.getPrice() * taxRate();
        double payout = listing.getPrice() - tax;
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.getSeller());
        plugin.economy().deposit(seller, payout);

        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(listing.getItemCopy());
        for (ItemStack stack : leftover.values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), stack);
        }

        Map<String, String> placeholders = Map.of(
                "item", listing.getItemName(),
                "amount", NumberUtil.count(listing.getAmount()),
                "price", NumberUtil.money(listing.getPrice()),
                "payout", NumberUtil.money(payout),
                "tax", NumberUtil.money(tax),
                "buyer", buyer.getName(),
                "seller", listing.getSellerName());

        Player online = Bukkit.getPlayer(listing.getSeller());
        if (online != null && online.isOnline() && plugin.profiles().alertsEnabled(listing.getSeller())) {
            online.sendMessage(Text.component(Text.apply(plugin.message("SALE-NOTIFY"), placeholders)));
        }
        return Result.ok(Text.apply(plugin.message("BUY-SUCCESS"), placeholders));
    }

    private boolean hasRoom(Player player, ItemStack item) {
        int needed = item.getAmount();
        int space = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                space += item.getMaxStackSize();
            } else if (stack.isSimilar(item)) {
                space += Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            }
            if (space >= needed) return true;
        }
        return space >= needed;
    }

    // ------------------------------------------------------------------ cancel / collect

    public Result cancel(Player player, UUID listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null || !listing.isListed()) {
            return Result.fail(plugin.message("LISTING-UNAVAILABLE"));
        }
        if (!listing.getSeller().equals(player.getUniqueId())
                && !player.hasPermission("havocauction.admin")) {
            return Result.fail(plugin.message("NO-PERMISSION"));
        }
        listing.setStatus(ListingStatus.CANCELLED);
        persist(listing);
        return Result.ok(Text.apply(plugin.message("LISTING-CANCELLED"),
                Map.of("item", listing.getItemName())));
    }

    /** Hands one waiting listing back to its seller. */
    public boolean collect(Player player, UUID listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null || !listing.awaitsCollection()) return false;
        if (!listing.getSeller().equals(player.getUniqueId())) return false;

        if (!hasRoom(player, listing.getItem())) {
            player.sendMessage(Text.component(plugin.message("INVENTORY-FULL")));
            return false;
        }

        // Mark first: a failed add drops at the player's feet rather than duplicating.
        listing.setStatus(ListingStatus.COLLECTED);
        persist(listing);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(listing.getItemCopy());
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        player.sendMessage(Text.component(Text.apply(plugin.message("LISTING-COLLECTED"),
                Map.of("item", listing.getItemName(),
                        "amount", NumberUtil.count(listing.getAmount())))));
        return true;
    }

    public int collectAll(Player player) {
        int collected = 0;
        for (Listing listing : collectable(player.getUniqueId())) {
            if (!hasRoom(player, listing.getItem())) break;
            listing.setStatus(ListingStatus.COLLECTED);
            persist(listing);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(listing.getItemCopy());
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
            collected++;
        }
        return collected;
    }

    /**
     * Drops the given waiting listings at the player's feet, a few stacks per tick.
     * Listings are marked collected up front so nothing can be handed out twice.
     */
    public int dropListings(Player player, List<Listing> subset) {
        int perTick = Math.max(1, plugin.getConfig().getInt("AUCTION.DROP.MAX-STACKS-PER-TICK", 24));
        Deque<DropJob> jobs = new ArrayDeque<>();
        int items = 0;

        for (Listing listing : subset) {
            if (!listing.awaitsCollection()) continue;
            ItemStack stack = listing.getItemCopy();
            jobs.add(new DropJob(stack, stack.getAmount()));
            items += stack.getAmount();
            listing.setStatus(ListingStatus.COLLECTED);
            persist(listing);
        }

        if (jobs.isEmpty()) {
            player.sendMessage(Text.component(plugin.message("NOTHING-TO-COLLECT")));
            return 0;
        }
        plugin.spreadDrop(player, jobs, perTick);
        return items;
    }

    // ------------------------------------------------------------------ upkeep

    /** Expires listings and purges history older than the configured window. */
    public void tick() {
        long now = System.currentTimeMillis();
        long keep = TimeUnit.DAYS.toMillis(
                Math.max(0, plugin.getConfig().getInt("AUCTION.HISTORY-KEEP-DAYS", 30)));
        Map<UUID, Integer> expiredPerSeller = new HashMap<>();

        for (Listing listing : new ArrayList<>(listings.values())) {
            if (listing.getStatus() == ListingStatus.ACTIVE && now >= listing.getExpiresAt()) {
                listing.setStatus(ListingStatus.EXPIRED);
                persist(listing);
                expiredPerSeller.merge(listing.getSeller(), 1, Integer::sum);
                continue;
            }
            if (keep > 0 && listing.getStatus().isHistory()) {
                long stamp = listing.getSoldAt() == null ? listing.getCreatedAt() : listing.getSoldAt();
                if (now - stamp > keep) remove(listing);
            }
        }

        for (Map.Entry<UUID, Integer> entry : expiredPerSeller.entrySet()) {
            Player seller = Bukkit.getPlayer(entry.getKey());
            if (seller == null || !plugin.profiles().alertsEnabled(entry.getKey())) continue;
            seller.sendMessage(Text.component(Text.apply(plugin.message("LISTING-EXPIRED"),
                    Map.of("amount", String.valueOf(entry.getValue())))));
        }
    }

    /** Used by the importer. Returns false when the id is already present. */
    public boolean importListing(Listing listing) {
        if (listings.containsKey(listing.getId())) return false;
        index(listing);
        dirty.add(listing.getId());
        touch();
        return true;
    }

    public List<Listing> snapshot() {
        return new ArrayList<>(listings.values());
    }
}
