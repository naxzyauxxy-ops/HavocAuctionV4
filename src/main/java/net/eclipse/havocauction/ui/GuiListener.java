package net.eclipse.havocauction.ui;

import net.eclipse.havocauction.HavocAuction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Routes menu clicks back to the screen's button actions. */
public class GuiListener implements Listener {

    private final HavocAuction plugin;

    public GuiListener(HavocAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Gui.View view)) return;

        // Cancel everything, including shift-clicks from the player's own inventory,
        // so nothing can be pulled into or out of a menu.
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(view.getInventory())) {
            return;
        }

        ScreenModel.Button button = view.buttonAt(event.getRawSlot());
        if (button == null) return;

        if (button.action() == null) {
            view.screen().viewer().closeInventory();
            return;
        }
        ScreenModel.Responses responses = event.getWhoClicked() instanceof Player player
                ? plugin.prompts().responsesFor(player)
                : ScreenModel.Responses.EMPTY;
        button.action().run(responses);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Gui.View) {
            event.setCancelled(true);
        }
    }
}
