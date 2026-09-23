package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One of your own listings: preview it, or pull it off the board. */
public class ManageListingScreen extends Screen {

    private final UUID listingId;

    public ManageListingScreen(HavocAuction plugin, Player player, UUID listingId) {
        super(plugin, player);
        this.listingId = listingId;
    }

    @Override
    protected String configPath() {
        return "MANAGE-LISTING";
    }

    private Listing listing() {
        return plugin.auction().byId(listingId);
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
        List<String> body = new ArrayList<>();
        body.addAll(resolve(lines("BODY"), Placeholders.of(plugin, listing)));
        return body;
    }

    @Override
    public ScreenModel.Button exitButton() {
        return backButton("BACK", Map.of(), () -> new MyListingsScreen(plugin, player).show());
    }

    @Override
    public List<ScreenModel.Button> buttons() {
        Listing listing = listing();
        List<ScreenModel.Button> buttons = new ArrayList<>();
        if (listing == null) {
            buttons.add(backButton("BACK", Map.of(), () -> new MyListingsScreen(plugin, player).show()));
            return buttons;
        }

        Map<String, String> placeholders = Placeholders.of(plugin, listing);
        buttons.add(configButton("CANCEL-LISTING", placeholders, responses -> {
            AuctionManager.Result result = plugin.auction().cancel(player, listingId);
            tell(result.message());
            if (result.success()) success();
            else deny();
            new MyListingsScreen(plugin, player).show();
        }));

        if (listing.isPreviewable()) {
            buttons.add(configButton("PREVIEW", placeholders, responses -> {
                click();
                new ContainerPreviewScreen(plugin, player, listingId,
                        () -> new ManageListingScreen(plugin, player, listingId).show()).show();
            }));
        }
        return buttons;
    }

    @Override
    public org.bukkit.inventory.ItemStack bodyIcon() {
        Listing listing = listing();
        return listing == null ? null : listing.getItemCopy();
    }
}
