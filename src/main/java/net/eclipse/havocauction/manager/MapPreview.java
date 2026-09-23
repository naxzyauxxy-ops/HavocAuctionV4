package net.eclipse.havocauction.manager;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a player actually see map art before buying.
 *
 * A vanilla client only renders map art when it holds the map, so there is no way to draw
 * it inside a dialog or an item tooltip - the client-side mods that add map tooltips exist
 * precisely because the game does not do it. Instead the map is placed in the player's off
 * hand for a few seconds and then taken back.
 *
 * The loaned item is a liability, so it is fenced in: tagged so it can always be
 * recognised, impossible to drop, move, swap or die with, returned on logout, and purged
 * on login in case the server stopped mid-preview.
 */
public class MapPreview implements Listener {

    private record Held(ItemStack original, int taskId) {
    }

    private final HavocAuction plugin;
    private final NamespacedKey key;
    private final Map<UUID, Held> active = new ConcurrentHashMap<>();

    public MapPreview(HavocAuction plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "map_preview");
    }

    private boolean isPreviewItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private ItemStack tag(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    /** Puts the map in the player's off hand for the configured number of seconds. */
    public void show(Player player, ItemStack map) {
        stop(player);

        int seconds = Math.max(1, plugin.getConfig().getInt("DIALOG.MAP-PREVIEW-SECONDS", 10));
        ItemStack original = player.getInventory().getItemInOffHand();
        ItemStack preview = tag(map);

        player.getInventory().setItemInOffHand(preview);

        // Make sure the client has the pixels, not just the item.
        if (preview.getItemMeta() instanceof MapMeta meta && meta.hasMapView()) {
            MapView view = meta.getMapView();
            if (view != null) player.sendMap(view);
        }

        player.closeDialog();
        player.sendMessage(Text.component(Text.apply(plugin.message("MAP-PREVIEW-START"),
                Map.of("seconds", String.valueOf(seconds)))));

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                stop(player);
                player.sendMessage(Text.component(plugin.message("MAP-PREVIEW-END")));
            }
        }.runTaskLater(plugin, seconds * 20L).getTaskId();

        active.put(player.getUniqueId(), new Held(original == null ? null : original.clone(), taskId));
    }

    /** Takes the loaned map back and restores whatever was in the off hand. */
    public void stop(Player player) {
        Held held = active.remove(player.getUniqueId());
        if (held == null) return;

        plugin.getServer().getScheduler().cancelTask(held.taskId());
        purge(player);

        ItemStack current = player.getInventory().getItemInOffHand();
        if (current == null || current.getType() == Material.AIR) {
            player.getInventory().setItemInOffHand(held.original());
        } else if (held.original() != null && held.original().getType() != Material.AIR) {
            // Something else ended up in the slot; do not overwrite it.
            for (ItemStack leftover : player.getInventory().addItem(held.original()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    /** Strips every tagged item from a player, wherever it ended up. */
    private void purge(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isPreviewItem(contents[slot])) contents[slot] = null;
        }
        player.getInventory().setStorageContents(contents);

        if (isPreviewItem(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
        if (isPreviewItem(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(null);
        }
    }

    // ------------------------------------------------------------------ fencing

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
        purge(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Covers a server that stopped while someone was mid-preview.
        purge(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isPreviewItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isPreviewItem(event.getOffHandItem()) || isPreviewItem(event.getMainHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (isPreviewItem(event.getCurrentItem()) || isPreviewItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isPreviewItem);
        stop(event.getEntity());
    }
}
