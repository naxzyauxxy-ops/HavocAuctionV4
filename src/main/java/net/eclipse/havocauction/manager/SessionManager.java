package net.eclipse.havocauction.manager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager implements Listener {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session get(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), Session::new);
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public int size() {
        return sessions.size();
    }
}
