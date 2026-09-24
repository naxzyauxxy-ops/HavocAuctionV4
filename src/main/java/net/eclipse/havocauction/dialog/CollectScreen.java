package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Items from cancelled or expired listings, waiting to come home. */
public class CollectScreen extends Screen {

    private List<Listing> cached;

    public CollectScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void show() {
        cached = null;
        super.show();
    }

    @Override
    protected String configPath() {
        return "COLLECT";
    }

    private int perPage() {
        return Math.max(1, plugin.getConfig().getInt("DIALOG.COLLECT-PER-PAGE", 45));
    }

    private int pageBatch() {
        return Math.max(2, plugin.getConfig().getInt("AUCTION.DROP.PAGE-BATCH", 5));
    }

    private List<Listing> results() {
        if (cached == null) cached = plugin.auction().collectable(player.getUniqueId());
        return cached;
    }

    private Map<String, String> screen(List<Listing> results) {
        int pages = totalPages(results.size(), perPage());
        Map<String, String> map = new HashMap<>();
        map.put("page", String.valueOf(Math.min(session.getCollectPage() + 1, pages)));
        map.put("pages", String.valueOf(pages));
        map.put("per_page", String.valueOf(perPage()));
        map.put("pages_batch", String.valueOf(pageBatch()));
        map.put("count", NumberUtil.count(results.size()));
        map.put("total_items", NumberUtil.count(plugin.auction().collectableCount(player.getUniqueId())));
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
            body.add(string("EMPTY", "&7Nothing waiting."));
        }
        return body;
    }

    private List<Listing> inPages(List<Listing> results, int pageCount) {
        Set<Listing> unique = new LinkedHashSet<>();
        int start = session.getCollectPage();
        for (int offset = 0; offset < pageCount; offset++) {
            unique.addAll(slice(results, start + offset, perPage()));
        }
        return new ArrayList<>(unique);
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

        for (Listing listing : slice(results, session.getCollectPage(), perPage())) {
            buttons.add(configButton("LOOT", Placeholders.of(plugin, listing),
                    listing.getItemCopy(), responses -> {
                if (plugin.auction().collect(player, listing.getId())) success();
                else deny();
                show();
            }));
        }

        if (session.getCollectPage() > 0) {
            buttons.add(configButton("PREVIOUS", screen, responses -> {
                session.setCollectPage(session.getCollectPage() - 1);
                click();
                show();
            }));
        }
        if (session.getCollectPage() < pages - 1) {
            buttons.add(configButton("NEXT", screen, responses -> {
                session.setCollectPage(session.getCollectPage() + 1);
                click();
                show();
            }));
        }

        buttons.add(configButton("COLLECT-ALL", screen, responses -> {
            int collected = plugin.auction().collectAll(player);
            if (collected > 0) {
                success();
                tell(Text.apply(plugin.message("COLLECTED-ALL"),
                        Map.of("amount", NumberUtil.count(collected))));
            } else {
                deny();
                tell(plugin.message("INVENTORY-FULL"));
            }
            show();
        }));

        buttons.add(configButton("DROP-PAGE", screen, responses -> drop(inPages(results, 1))));
        if (pages > 1) {
            buttons.add(configButton("DROP-PAGES", screen, responses ->
                    drop(inPages(results, pageBatch()))));
        }
        if (!results.isEmpty()) {
            buttons.add(configButton("DROP-ALL", screen, responses -> {
                click();
                new DropConfirmScreen(plugin, player).show();
            }));
        }
        return buttons;
    }

    private void drop(List<Listing> subset) {
        int dropped = plugin.auction().dropListings(player, subset);
        if (dropped > 0) {
            success();
            tell(Text.apply(plugin.message("DROPPED-ALL"), Map.of("amount", NumberUtil.count(dropped))));
        } else {
            deny();
        }
        session.setCollectPage(0);
        show();
    }
}
