package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Your live listings, plus the way into selling, collecting and history. */
public class MyListingsScreen extends Screen {

    /** Computed once per draw: title, body, buttons and footer all need it. */
    private List<Listing> cached;

    public MyListingsScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void show() {
        cached = null;
        super.show();
    }

    @Override
    protected String configPath() {
        return "MY-LISTINGS";
    }

    private int perPage() {
        // Your own listings are a short list, not a board: a handful at a time reads
        // better than a wall of mostly-empty slots.
        return Math.max(1, plugin.getConfig().getInt("DIALOG.MY-LISTINGS-PER-PAGE", 4));
    }

    private List<Listing> results() {
        if (cached == null) cached = plugin.auction().activeOf(player.getUniqueId());
        return cached;
    }

    private Map<String, String> screen(List<Listing> results) {
        int pages = totalPages(results.size(), perPage());
        Map<String, String> map = new HashMap<>();
        map.put("count", NumberUtil.count(results.size()));
        map.put("collectable", NumberUtil.count(plugin.auction().collectable(player.getUniqueId()).size()));
        map.put("collectable_items", NumberUtil.count(plugin.auction().collectableCount(player.getUniqueId())));
        map.put("page", String.valueOf(Math.min(session.getMyListingsPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        map.put("value", NumberUtil.money(results.stream().mapToDouble(Listing::getPrice).sum()));
        map.put("alerts", plugin.profiles().alertsEnabled(player.getUniqueId()) ? "ON" : "OFF");
        map.put("fast_buy", plugin.profiles().fastBuy(player.getUniqueId()) ? "ON" : "OFF");
        return map;
    }

    @Override
    public String title() {
        return titleFrom(screen(results()));
    }

    @Override
    public List<String> bodyLines() {
        List<Listing> results = results();
        List<String> body = resolve(lines("BODY"), screen(results));
        if (results.isEmpty()) {
            body.add(string("EMPTY", "&7You have nothing listed."));
        }
        return body;
    }

    @Override
    public ScreenModel.Button exitButton() {
        return backButton("BACK", screen(results()), () -> new AuctionScreen(plugin, player).show());
    }

    @Override
    public List<ScreenModel.Button> buttons() {
        List<Listing> results = results();
        Map<String, String> screen = screen(results);
        int pages = totalPages(results.size(), perPage());
        List<ScreenModel.Button> buttons = new ArrayList<>();

        for (Listing listing : slice(results, session.getMyListingsPage(), perPage())) {
            buttons.add(entryButton("LISTING", Placeholders.of(plugin, listing),
                    listing.getItemCopy(), responses -> {
                click();
                new ManageListingScreen(plugin, player, listing.getId()).show();
            }));
        }

        if (session.getMyListingsPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, responses -> {
                session.setMyListingsPage(session.getMyListingsPage() - 1);
                click();
                show();
            }));
        }
        if (session.getMyListingsPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, responses -> {
                session.setMyListingsPage(session.getMyListingsPage() + 1);
                click();
                show();
            }));
        }

        buttons.add(configButton("SELL", screen, responses -> {
            click();
            new SellScreen(plugin, player).show();
        }));
        buttons.add(configButton("COLLECT", screen, responses -> {
            click();
            new CollectScreen(plugin, player).show();
        }));
        buttons.add(configButton("TRANSACTIONS", screen, responses -> {
            click();
            new TransactionsScreen(plugin, player).show();
        }));
        return buttons;
    }
}
