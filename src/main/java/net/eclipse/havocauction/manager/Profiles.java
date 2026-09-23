package net.eclipse.havocauction.manager;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.storage.SqlStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player preferences: sale alerts, and "fast buy" which skips the confirmation
 * dialog. Both existed in the legacy database, so imports keep players' choices.
 */
public class Profiles {

    private record Prefs(boolean alerts, boolean fastBuy) {
    }

    private final HavocAuction plugin;
    private final SqlStorage storage;

    private final Map<UUID, Prefs> prefs = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public Profiles(HavocAuction plugin, SqlStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadAll() {
        prefs.clear();
        for (Map.Entry<UUID, boolean[]> entry : storage.loadProfiles().entrySet()) {
            prefs.put(entry.getKey(), new Prefs(entry.getValue()[0], entry.getValue()[1]));
        }
        plugin.getLogger().info("Loaded " + prefs.size() + " player preferences.");
    }

    private Prefs get(UUID playerId) {
        return prefs.getOrDefault(playerId, new Prefs(true, false));
    }

    public boolean alertsEnabled(UUID playerId) {
        return get(playerId).alerts();
    }

    public boolean fastBuy(UUID playerId) {
        return get(playerId).fastBuy();
    }

    public void set(UUID playerId, boolean alerts, boolean fastBuy) {
        prefs.put(playerId, new Prefs(alerts, fastBuy));
        dirty.add(playerId);
    }

    public boolean toggleAlerts(UUID playerId) {
        Prefs current = get(playerId);
        set(playerId, !current.alerts(), current.fastBuy());
        return !current.alerts();
    }

    public boolean toggleFastBuy(UUID playerId) {
        Prefs current = get(playerId);
        set(playerId, current.alerts(), !current.fastBuy());
        return !current.fastBuy();
    }

    /** Batched with the listing flush; safe from any thread. */
    public void flush() {
        if (dirty.isEmpty()) return;
        List<UUID> ids = new ArrayList<>(dirty);
        dirty.removeAll(ids);
        Map<UUID, boolean[]> batch = new HashMap<>();
        for (UUID id : ids) {
            Prefs p = get(id);
            batch.put(id, new boolean[]{p.alerts(), p.fastBuy()});
        }
        storage.saveProfiles(batch);
    }
}
