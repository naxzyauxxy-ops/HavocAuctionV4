package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.ItemNames;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full preview of a listed item before buying.
 *
 * Shows the item large, and unpacks whatever it is carrying:
 *   - shulker boxes render every stack inside as its own icon
 *   - filled maps render at preview size, where hovering shows the map art itself
 *   - enchanted items list their enchantments
 *
 * Nobody should have to gamble a few million on a box or a map they cannot see into.
 */
public class ContainerPreviewScreen extends Screen {

    private final UUID listingId;
    private final Runnable onBack;

    public ContainerPreviewScreen(HavocAuction plugin, Player player, UUID listingId, Runnable onBack) {
        super(plugin, player);
        this.listingId = listingId;
        this.onBack = onBack;
    }

    @Override
    protected String configPath() {
        return "CONTAINER-PREVIEW";
    }

    private Listing listing() {
        return plugin.auction().byId(listingId);
    }

    private int previewSize() {
        return plugin.getConfig().getInt("DIALOG.PREVIEW-ITEM-SIZE", 96);
    }

    private int contentSize() {
        return plugin.getConfig().getInt("DIALOG.PREVIEW-CONTENT-SIZE", 40);
    }

    private List<ItemStack> containerContents(ItemStack item) {
        List<ItemStack> contents = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) return contents;
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox box)) return contents;
        for (ItemStack stack : box.getInventory().getContents()) {
            if (stack != null && stack.getType() != Material.AIR) contents.add(stack);
        }
        return contents;
    }

    @Override
    public String title() {
        Listing listing = listing();
        return titleFrom(listing == null ? Map.of() : Placeholders.of(plugin, listing));
    }

    @Override
    public List<String> bodyLines() {
        Listing listing = listing();
        if (listing == null) {
            return List.of(plugin.message("LISTING-UNAVAILABLE"));
        }

        ItemStack item = listing.getItemCopy();
        List<String> body = new ArrayList<>();

        // The item itself, large. Tooltips are on, which is what makes a filled map show
        // its actual art on hover rather than just the map icon.
        body.addAll(resolve(lines("BODY"), Placeholders.of(plugin, listing)));

        if (isMap(item)) {
            body.addAll(mapDetails(item));
            return body;
        }

        if (item.getItemMeta() instanceof BookMeta book) {
            body.addAll(bookDetails(item, book));
            return body;
        }

        List<ItemStack> contents = containerContents(item);
        if (!contents.isEmpty()) {
            body.addAll(containerDetails(contents));
            return body;
        }

        List<String> enchants = enchantDetails(item);
        if (!enchants.isEmpty()) {
            body.addAll(enchants);
            return body;
        }

        body.add(style().text(string("EMPTY", "&7Nothing else to show for this item.")));
        return body;
    }

    // ------------------------------------------------------------------ maps

    private boolean isMap(ItemStack item) {
        return item.getType() == Material.FILLED_MAP;
    }

    private List<String> mapDetails(ItemStack item) {
        List<String> body = new ArrayList<>();
        String header = string("MAP-HEADER", "&#f40d0dMap art");
        body.add(style().text(header));

        String hint = string("MAP-HINT", "&7Hover the map above to see the art.");
        body.add(style().text(hint));

        if (!(item.getItemMeta() instanceof MapMeta meta) || !meta.hasMapView()) return body;
        MapView view = meta.getMapView();
        if (view == null) return body;

        Map<String, String> placeholders = Map.of(
                "id", String.valueOf(view.getId()),
                "scale", view.getScale() == null ? "unknown" : view.getScale().name(),
                "locked", String.valueOf(view.isLocked()),
                "world", view.getWorld() == null ? "unknown" : view.getWorld().getName());

        for (String line : Text.applyPruned(lines("MAP-LINES"), placeholders)) {
            body.add(style().text(line));
        }
        return body;
    }

    // ------------------------------------------------------------------ books

    /**
     * Who signed it, and what generation it is.
     *
     * Worth showing before a purchase: a signed original by a known player is a very
     * different thing from a copy of a copy, and the two look identical on the board.
     */
    private List<String> bookDetails(ItemStack item, BookMeta book) {
        List<String> body = new ArrayList<>();
        boolean signed = item.getType() == Material.WRITTEN_BOOK;

        body.add(style().text(
                string(signed ? "BOOK-HEADER" : "BOOK-HEADER-UNSIGNED",
                        signed ? "&#f40d0dSigned book" : "&#f40d0dBook and quill")));

        Map<String, String> placeholders = Map.of(
                "title", book.hasTitle() && book.getTitle() != null ? book.getTitle() : "untitled",
                "author", book.hasAuthor() && book.getAuthor() != null ? book.getAuthor() : "unsigned",
                "generation", book.hasGeneration() && book.getGeneration() != null
                        ? Text.pretty(book.getGeneration().name()) : "Original",
                "pages", String.valueOf(book.getPageCount()));

        for (String line : Text.applyPruned(lines(signed ? "BOOK-LINES" : "BOOK-LINES-UNSIGNED"),
                placeholders)) {
            body.add(style().text(line));
        }

        // A taste of the contents, capped so a long book cannot flood the screen.
        int limit = Math.max(0, plugin.getConfig().getInt("DIALOG.PREVIEW-BOOK-CHARS", 160));
        if (limit > 0 && book.getPageCount() > 0) {
            String first = book.getPage(1);
            if (first != null && !first.isBlank()) {
                String snippet = first.replace("\n", " ").trim();
                if (snippet.length() > limit) snippet = snippet.substring(0, limit) + "...";
                body.add(style().text(
                        Text.apply(string("BOOK-PAGE", "&8\"{page}\""), Map.of("page", snippet))));
            }
        }
        return body;
    }

    // ------------------------------------------------------------------ containers

    private List<String> containerDetails(List<ItemStack> contents) {
        List<String> body = new ArrayList<>();
        int limit = Math.max(1, plugin.getConfig().getInt("DIALOG.PREVIEW-MAX-CONTENTS", 27));
        String format = string("LINE", "&7- &f{amount}x {item}");

        int shown = 0;
        for (ItemStack stack : contents) {
            if (shown++ >= limit) break;
            // Icon plus label, so the contents look like a container rather than a list.
            body.add(style().text(Text.apply(format, Map.of(
                    "amount", NumberUtil.count(stack.getAmount()),
                    "item", ItemNames.display(stack)))));
        }

        if (contents.size() > limit) {
            body.add(style().text(
                    Text.apply(string("MORE", "&8...and {count} more"),
                            Map.of("count", String.valueOf(contents.size() - limit)))));
        }
        return body;
    }

    // ------------------------------------------------------------------ enchantments

    private List<String> enchantDetails(ItemStack item) {
        List<String> body = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return body;

        Map<Enchantment, Integer> enchants = meta instanceof EnchantmentStorageMeta storage
                ? storage.getStoredEnchants()
                : meta.getEnchants();
        if (enchants.isEmpty()) return body;

        String format = string("ENCHANT-LINE", "&7- &f{enchantment} {level}");
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            body.add(style().text(Text.apply(format, Map.of(
                    "enchantment", ItemNames.enchantment(entry.getKey()),
                    "level", Text.roman(entry.getValue())))));
        }
        return body;
    }

    @Override
    public List<ScreenModel.Button> buttons() {
        Listing listing = listing();
        List<ScreenModel.Button> buttons = new ArrayList<>();

        // In a chest menu the contents can simply be shown as themselves, which beats
        // listing them as text.
        if (listing != null) {
            int limit = Math.max(1, plugin.getConfig().getInt("DIALOG.PREVIEW-MAX-CONTENTS", 21));
            int shown = 0;
            for (ItemStack stack : containerContents(listing.getItemCopy())) {
                if (shown++ >= limit) break;
                buttons.add(configButton("CONTENT",
                        Map.of("amount", NumberUtil.count(stack.getAmount()),
                                "item", ItemNames.display(stack)),
                        stack.clone(), responses -> {
                        }));
            }
        }

        // A vanilla client only draws map art for a map it is holding, so the only way to
        // show it is to lend the map for a few seconds.
        if (listing != null && listing.isMap()) {
            buttons.add(configButton("VIEW-MAP", Placeholders.of(plugin, listing),
                    responses -> {
                        click();
                        plugin.mapPreview().show(player, listing.getItemCopy());
                    }));
        }

        buttons.add(backButton("BACK", Map.of(), onBack));
        return buttons;
    }

    @Override
    public org.bukkit.inventory.ItemStack bodyIcon() {
        Listing listing = listing();
        return listing == null ? null : listing.getItemCopy();
    }
}
