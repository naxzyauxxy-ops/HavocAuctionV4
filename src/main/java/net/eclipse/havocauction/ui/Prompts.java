package net.eclipse.havocauction.ui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.dialog.Screen;
import net.eclipse.havocauction.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat-based text entry for chest mode.
 *
 * A chest menu has nowhere to type, so values are collected in chat and remembered until
 * the action that needs them runs. Dialog mode does not use this at all - it has proper
 * input fields, which is the one place the classic look is genuinely worse.
 */
public class Prompts implements Listener {

    private record Pending(String key, Screen screen) {
    }

    private final HavocAuction plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> values = new ConcurrentHashMap<>();

    public Prompts(HavocAuction plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, ScreenModel.Input input, Screen screen) {
        waiting.put(player.getUniqueId(), new Pending(input.key(), screen));
        player.closeInventory();
        player.sendMessage(Text.component(Text.apply(plugin.message("PROMPT-CHAT"),
                Map.of("label", Text.strip(input.label())))));
    }

    /** Values this player has typed, for actions to read. */
    public ScreenModel.Responses responsesFor(Player player) {
        Map<String, String> stored = values.get(player.getUniqueId());
        return stored == null ? ScreenModel.Responses.EMPTY : stored::get;
    }

    public void clear(Player player) {
        values.remove(player.getUniqueId());
        waiting.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending pending = waiting.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!message.equalsIgnoreCase("cancel")) {
                values.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
                        .put(pending.key(), message);
            }
            pending.screen().show();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }
}
