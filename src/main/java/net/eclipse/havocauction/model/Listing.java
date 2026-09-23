package net.eclipse.havocauction.model;

import net.eclipse.havocauction.util.ItemAliases;
import net.eclipse.havocauction.util.ItemNames;
import net.eclipse.havocauction.util.Text;
import net.eclipse.havocauction.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** One auction listing. Also doubles as the transaction record once sold. */
public class Listing {

    private final UUID id;
    private final UUID seller;
    private String sellerName;

    private final String encodedItem;
    private transient ItemStack cachedItem;
    /** Kept separate so the board can filter and search without decoding every item. */
    private final String itemName;
    /** The real item type. Search uses this, never the display name. */
    private final String typeName;
    /** Non-null only when the seller renamed the item. */
    private final String customName;
    /**
     * Everything trustworthy a search may match, lowercased and pre-joined: the real type,
     * material name, configured aliases, enchantments, and a signed book's author.
     *
     * Built once at load so searching never decodes an item or walks its metadata.
     * Attacker-controlled text - custom names, book titles - is deliberately kept out and
     * matched separately, so renaming a block of dirt cannot answer an elytra search.
     */
    private final String searchIndex;
    /** Who signed the book, when it is one. */
    private final String bookAuthor;
    private final String bookTitle;
    private final Material material;
    private final int amount;
    private final int damage;
    private final short maxDurability;

    private final double price;

    private final long createdAt;
    private long expiresAt;
    private Long soldAt;

    private ListingStatus status;
    private UUID buyer;
    private String buyerName;

    public Listing(UUID id, UUID seller, String sellerName, String encodedItem, double price,
                   long createdAt, long expiresAt, Long soldAt, ListingStatus status,
                   UUID buyer, String buyerName) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.encodedItem = encodedItem;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.soldAt = soldAt;
        this.status = status;
        this.buyer = buyer;
        this.buyerName = buyerName;

        ItemStack item = getItem();
        this.itemName = ItemNames.display(item);
        this.typeName = ItemNames.typeName(item);
        this.customName = ItemNames.customName(item);
        this.material = item.getType();
        this.amount = item.getAmount();
        this.maxDurability = item.getType().getMaxDurability();
        this.damage = item.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()
                ? damageable.getDamage()
                : 0;

        ItemMeta meta = item.getItemMeta();
        BookMeta book = meta instanceof BookMeta bookMeta ? bookMeta : null;
        this.bookAuthor = book != null && book.hasAuthor() ? book.getAuthor() : null;
        this.bookTitle = book != null && book.hasTitle() ? book.getTitle() : null;
        this.searchIndex = buildSearchIndex(item, meta);
    }

    private String buildSearchIndex(ItemStack item, ItemMeta meta) {
        StringBuilder sb = new StringBuilder();
        sb.append(typeName).append(' ');
        sb.append(item.getType().name().replace('_', ' ')).append(' ');
        sb.append(getSellerName()).append(' ');

        for (String alias : ItemAliases.of(item.getType())) {
            sb.append(alias).append(' ');
        }

        Map<Enchantment, Integer> enchants = meta instanceof EnchantmentStorageMeta storage
                ? storage.getStoredEnchants()
                : (meta == null ? Map.of() : meta.getEnchants());
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            sb.append(ItemNames.enchantment(entry.getKey())).append(' ')
              .append(entry.getValue()).append(' ')
              .append(Text.roman(entry.getValue())).append(' ');
        }

        if (bookAuthor != null) sb.append(bookAuthor).append(' ');
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public String getBookAuthor() {
        return bookAuthor;
    }

    /**
     * Every token must match something. "naxzyauxxy signed book" therefore needs the
     * author or seller *and* the item type, rather than anything that mentions "book".
     */
    public boolean matchesSearch(List<String> tokens, boolean includeCustomText) {
        if (tokens.isEmpty()) return true;

        String extra = "";
        if (includeCustomText) {
            extra = ((customName == null ? "" : customName) + ' '
                    + (bookTitle == null ? "" : bookTitle)).toLowerCase(Locale.ROOT);
        }

        for (String token : tokens) {
            if (searchIndex.contains(token)) continue;
            if (!extra.isEmpty() && extra.contains(token)) continue;
            return false;
        }
        return true;
    }

    public static Listing create(UUID seller, String sellerName, ItemStack item,
                                 double price, long durationMillis) {
        long now = System.currentTimeMillis();
        return new Listing(UUID.randomUUID(), seller, sellerName,
                ItemSerializer.encodeFull(item), price,
                now, now + durationMillis, null, ListingStatus.ACTIVE, null, null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSeller() {
        return seller;
    }

    public String getSellerName() {
        return sellerName == null ? "Unknown" : sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getEncodedItem() {
        return encodedItem;
    }

    /** The listed stack, at its real quantity. Never mutate the returned value. */
    public ItemStack getItem() {
        if (cachedItem == null) {
            ItemStack decoded = ItemSerializer.decode(encodedItem);
            cachedItem = decoded == null ? new ItemStack(Material.BARRIER) : decoded;
        }
        return cachedItem;
    }

    public ItemStack getItemCopy() {
        return getItem().clone();
    }

    public String getItemName() {
        return itemName;
    }

    /** Real item type, e.g. "Dirt" even when the seller called it "Elytra". */
    public String getTypeName() {
        return typeName;
    }

    public String getCustomName() {
        return customName;
    }

    public boolean isRenamed() {
        return customName != null && !customName.equalsIgnoreCase(typeName);
    }

    public boolean hasDurability() {
        return maxDurability > 0;
    }

    public int getDurabilityRemaining() {
        return Math.max(0, maxDurability - damage);
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public int getDurabilityPercent() {
        if (maxDurability <= 0) return 100;
        return (int) Math.round(getDurabilityRemaining() * 100.0D / maxDurability);
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    /** Price for one item of the stack, which is what buyers actually compare. */
    public double getUnitPrice() {
        return amount <= 0 ? price : price / amount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getMillisUntilExpiry() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public Long getSoldAt() {
        return soldAt;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public UUID getBuyer() {
        return buyer;
    }

    public String getBuyerName() {
        return buyerName == null ? "Unknown" : buyerName;
    }

    public void markSold(UUID buyer, String buyerName) {
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.soldAt = System.currentTimeMillis();
        this.status = ListingStatus.SOLD;
    }

    public boolean isListed() {
        return status == ListingStatus.ACTIVE && !isExpired();
    }

    public boolean awaitsCollection() {
        return status.awaitsCollection();
    }

    /** True for shulker boxes and other containers worth previewing before buying. */
    public boolean isContainer() {
        return material.name().endsWith("SHULKER_BOX");
    }

    public boolean isMap() {
        return material == Material.FILLED_MAP;
    }

    public boolean isBook() {
        return material == Material.WRITTEN_BOOK || material == Material.WRITABLE_BOOK;
    }

    /**
     * Whether the preview screen has anything to add: contents, map art, enchantments,
     * or durability. Plain items do not get a preview button they would learn nothing from.
     */
    public boolean isPreviewable() {
        if (isContainer() || isMap() || isBook() || hasDurability()) return true;
        ItemStack item = getItem();
        if (item.getItemMeta() instanceof EnchantmentStorageMeta storage) {
            return storage.hasStoredEnchants();
        }
        return item.getItemMeta() != null && !item.getItemMeta().getEnchants().isEmpty();
    }
}
