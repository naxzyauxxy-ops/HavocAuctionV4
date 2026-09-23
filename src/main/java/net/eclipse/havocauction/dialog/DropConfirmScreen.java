package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.util.NumberUtil;
import net.eclipse.havocauction.util.Text;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Second step for "drop everything", because loot on the floor is not protected. */
public class DropConfirmScreen extends Screen {

    public DropConfirmScreen(HavocAuction plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "DROP-CONFIRM";
    }

    private Map<String, String> placeholders() {
        return Map.of("amount",
                NumberUtil.count(plugin.auction().collectableCount(player.getUniqueId())));
    }

    @Override
    public String title() {
        return titleFrom(placeholders());
    }

    @Override
    public List<String> bodyLines() {
        return resolve(lines("BODY"), placeholders());
    }

    @Override
    public ScreenModel.Button exitButton() {
        return backButton("BACK", placeholders(), () -> new CollectScreen(plugin, player).show());
    }

    @Override
    public List<ScreenModel.Button> buttons() {
        return List.of(configButton("CONFIRM", placeholders(), responses -> {
            int dropped = plugin.auction().dropListings(player,
                    plugin.auction().collectable(player.getUniqueId()));
            if (dropped > 0) {
                success();
                tell(Text.apply(plugin.message("DROPPED-ALL"),
                        Map.of("amount", NumberUtil.count(dropped))));
            } else {
                deny();
            }
            session.setCollectPage(0);
            new CollectScreen(plugin, player).show();
        }));
    }
}
