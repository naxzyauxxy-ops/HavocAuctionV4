package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.manager.AuctionManager;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Your completed sales and purchases, with lifetime totals. */
public class TransactionsScreen extends Screen {

    private List<Listing> cached;

    public TransactionsScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void show() {
        cached = null;
        super.show();
    }

    @Override
    protected String configPath() {
        return "TRANSACTIONS";
    }

    private int perPage() {
        return Math.max(1, plugin.getConfig().getInt("DIALOG.LISTINGS-PER-PAGE", 45));
    }

    private List<Listing> results() {
        if (cached != null) return cached;
        List<Listing> all = plugin.auction().transactionsOf(player.getUniqueId());
        String query = session.getTransactionQuery().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            cached = all;
            return cached;
        }
        List<Listing> matched = new ArrayList<>();
        for (Listing listing : all) {
            if (listing.getSellerName().toLowerCase(Locale.ROOT).contains(query)
                    || listing.getBuyerName().toLowerCase(Locale.ROOT).contains(query)
                    || listing.getTypeName().toLowerCase(Locale.ROOT).contains(query)) {
                matched.add(listing);
            }
        }
        cached = matched;
        return cached;
    }

    private Map<String, String> screen(List<Listing> results) {
        AuctionManager.Stats stats = plugin.auction().statsOf(player.getUniqueId());
        int pages = totalPages(results.size(), perPage());
        Map<String, String> map = new HashMap<>();
        map.put("page", String.valueOf(Math.min(session.getTransactionsPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        map.put("results", NumberUtil.count(results.size()));
        map.put("total_made", NumberUtil.money(stats.made()));
        map.put("total_spent", NumberUtil.money(stats.spent()));
        map.put("sales", NumberUtil.count(stats.sales()));
        map.put("purchases", NumberUtil.count(stats.purchases()));
        map.put("net", NumberUtil.money(stats.made() - stats.spent()));
        map.put("query", session.getTransactionQuery().isEmpty()
                ? "none" : session.getTransactionQuery());
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
            body.add(string("EMPTY", "&7No transactions yet."));
        }
        return body;
    }

    @Override
    public ScreenModel.Button exitButton() {
        return backButton("BACK", screen(results()), () -> new MyListingsScreen(plugin, player).show());
    }

    @Override
    public List<ScreenModel.Button> buttons() {
        List<Listing> results = results();
        Map<String, String> screen = screen(results);
        int pages = totalPages(results.size(), perPage());
        List<ScreenModel.Button> buttons = new ArrayList<>();

        for (Listing listing : slice(results, session.getTransactionsPage(), perPage())) {
            boolean sold = listing.getSeller().equals(player.getUniqueId());
            buttons.add(entryButton(sold ? "SALE" : "PURCHASE", Placeholders.of(plugin, listing),
                    listing.getItemCopy(), responses -> {
                        click();
                        show();
                    }));
        }

        if (session.getTransactionsPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, responses -> {
                session.setTransactionsPage(session.getTransactionsPage() - 1);
                click();
                show();
            }));
        }
        if (session.getTransactionsPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, responses -> {
                session.setTransactionsPage(session.getTransactionsPage() + 1);
                click();
                show();
            }));
        }

        buttons.add(configButton("SEARCH", screen, responses -> {
            click();
            plugin.prompts().request(player, "player name", this, value -> {
                session.setTransactionQuery(value);
                new TransactionsScreen(plugin, player).show();
            });
        }));
        return buttons;
    }
}
