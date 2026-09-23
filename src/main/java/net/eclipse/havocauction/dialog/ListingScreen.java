package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Buy confirmation. Skipped entirely for players with fast buy enabled. */
public class ListingScreen extends Screen {

    private final UUID listingId;

    public ListingScreen(HavocAuction plugin, Player player, UUID listingId) {
        super(plugin, player);
        this.listingId = listingId;
    }

    @Override
    protected String configPath() {
        return "CONFIRM-PURCHASE";
    }

    private Listing listing() {
        return plugin.auction().byId(listingId);
    }

    private Map<String, String> placeholders(Listing listing) {
        Map<String, String> map = Placeholders.of(plugin, listing);
        map.put("balance", NumberUtil.money(plugin.economy().balance(player)));
        map.put("after", NumberUtil.money(plugin.economy().balance(player) - listing.getPrice()));
        return map;
    }

    /** Buys straight away when the player has turned confirmations off. */
    public void showOrBuy() {
        if (plugin.profiles().fastBuy(player.getUniqueId())) {
            purchase();
        } else {
            show();
        }
    }

    @Override
    public String title() {
        Listing listing = listing();
        return titleFrom(listing == null ? Map.of() : placeholders(listing));
    }

    @Override
    public List<String> bodyLines() {
        Listing listing = listing();
        if (listing == null) {
            return List.of(plugin.message("LISTING-UNAVAILABLE"));
        }
        List<String> body = new ArrayList<>();
        body.addAll(resolve(lines("BODY"), placeholders(listing)));
        return body;
    }

    @Override
    public ScreenModel.Button exitButton() {
        return backButton("BACK", Map.of(), () -> new AuctionScreen(plugin, player).show());
    }

    @Override
    public List<ScreenModel.Button> buttons() {
        Listing listing = listing();
        List<ScreenModel.Button> buttons = new ArrayList<>();
        if (listing == null) {
            buttons.add(backButton("BACK", Map.of(), () -> new AuctionScreen(plugin, player).show()));
            return buttons;
        }

        Map<String, String> placeholders = placeholders(listing);
        buttons.add(configButton("CONFIRM", placeholders, responses -> purchase()));

        if (listing.isPreviewable()) {
            buttons.add(configButton("PREVIEW", placeholders, responses -> {
                click();
                new ContainerPreviewScreen(plugin, player, listingId,
                        () -> new ListingScreen(plugin, player, listingId).show()).show();
            }));
        }
        return buttons;
    }

    private void purchase() {
        AuctionManager.Result result = plugin.auction().buy(player, listingId);
        tell(result.message());
        if (result.success()) success();
        else deny();
        new AuctionScreen(plugin, player).show();
    }

    @Override
    public org.bukkit.inventory.ItemStack bodyIcon() {
        Listing listing = listing();
        return listing == null ? null : listing.getItemCopy();
    }
}
