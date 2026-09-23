package net.eclipse.havocauction.storage;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.model.ListingStatus;
import net.eclipse.havocauction.util.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Imports an auction.db written by the original DonutAuction plugin.
 *
 * Legacy schema:
 *   auction_listings(id BINARY(16), owner BINARY(16), item BLOB, price, created_at,
 *                    expires_at, sold_at, status, buyer BINARY(16))
 *   auction_profiles(player_id BINARY(16), fast_auction, alerts)
 *
 * UUIDs are raw 16-byte blobs, not strings, and items are BukkitObjectOutputStream dumps.
 *
 * The importer moves no money and hands out no items. Sold rows come across as history so
 * the transaction log and lifetime totals survive; active listings go back on the board;
 * cancelled and expired rows keep their items waiting for collection, exactly as before.
 * Ids are preserved, so re-running skips anything already imported.
 */
public class LegacyImporter {

    public record Report(int active, int history, int awaitingCollection,
                         int skipped, int failed, int profiles) {
    }

    private final HavocAuction plugin;

    public LegacyImporter(HavocAuction plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection settings() {
        return plugin.getConfig().getConfigurationSection("IMPORT");
    }

    public File defaultFile() {
        ConfigurationSection section = settings();
        String name = section == null ? "import.db" : section.getString("FILE", "import.db");
        File file = new File(name);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), name);
    }

    /** BINARY(16) -> UUID. */
    private static UUID toUuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public Report importFrom(File file) throws SQLException {
        ConfigurationSection section = settings();
        boolean importHistory = section == null || section.getBoolean("IMPORT-HISTORY", true);
        boolean importProfiles = section == null || section.getBoolean("IMPORT-PROFILES", true);
        String expiryMode = section == null ? "EXTEND" : section.getString("EXPIRY.MODE", "EXTEND");
        int extendDays = section == null ? 7 : section.getInt("EXPIRY.EXTEND-DAYS", 7);

        int active = 0;
        int history = 0;
        int awaiting = 0;
        int skipped = 0;
        int failed = 0;
        int profiles = 0;

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {

            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT * FROM auction_listings")) {

                while (results.next()) {
                    UUID id = toUuid(results.getBytes("id"));
                    try {
                        if (id == null) {
                            failed++;
                            continue;
                        }
                        if (plugin.auction().byId(id) != null) {
                            skipped++;
                            continue;
                        }

                        UUID seller = toUuid(results.getBytes("owner"));
                        UUID buyer = toUuid(results.getBytes("buyer"));
                        if (seller == null) {
                            failed++;
                            continue;
                        }

                        ItemStack item = readItem(results.getBytes("item"));
                        if (item == null) {
                            plugin.getLogger().warning("Import: unreadable item for listing " + id + ", skipping.");
                            failed++;
                            continue;
                        }

                        ListingStatus status = statusOf(results.getString("status"));
                        if (status.isHistory() && !importHistory) {
                            skipped++;
                            continue;
                        }

                        long created = results.getLong("created_at");
                        long expires = results.getLong("expires_at");
                        long soldAtRaw = results.getLong("sold_at");
                        Long soldAt = results.wasNull() || soldAtRaw <= 0 ? null : soldAtRaw;

                        if (status == ListingStatus.ACTIVE && expires <= System.currentTimeMillis()) {
                            if ("EXTEND".equalsIgnoreCase(expiryMode) && extendDays > 0) {
                                expires = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(extendDays);
                            } else {
                                // Item still belongs to the seller, waiting for collection.
                                status = ListingStatus.EXPIRED;
                            }
                        }

                        Listing listing = new Listing(id, seller, nameOf(seller),
                                ItemSerializer.encodeFull(item),
                                results.getDouble("price"),
                                created, expires, soldAt, status,
                                buyer, buyer == null ? null : nameOf(buyer));

                        if (!plugin.auction().importListing(listing)) {
                            skipped++;
                            continue;
                        }
                        if (status == ListingStatus.ACTIVE) active++;
                        else if (status.awaitsCollection()) awaiting++;
                        else history++;
                    } catch (Exception ex) {
                        failed++;
                        plugin.getLogger().log(Level.WARNING, "Import: could not convert listing " + id, ex);
                    }
                }
            }

            if (importProfiles) profiles = importProfiles(connection);
        }

        plugin.auction().flush();
        plugin.profiles().flush();
        return new Report(active, history, awaiting, skipped, failed, profiles);
    }

    private ListingStatus statusOf(String raw) {
        if (raw == null) return ListingStatus.CANCELLED;
        try {
            return ListingStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            // Unknown legacy state: treat it as the seller's property rather than guess.
            return ListingStatus.CANCELLED;
        }
    }

    /** Best-effort name lookup; falls back to the uuid rather than blocking on Mojang. */
    private String nameOf(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    private ItemStack readItem(byte[] blob) {
        if (blob == null || blob.length == 0) return null;
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(blob);
             BukkitObjectInputStream input = new BukkitObjectInputStream(bytes)) {
            Object read = input.readObject();
            if (read instanceof ItemStack stack && stack.getType() != Material.AIR) return stack;
        } catch (Exception ex) {
            plugin.getLogger().warning("Import: item blob unreadable ("
                    + ex.getClass().getSimpleName() + ").");
        }
        return null;
    }

    public void markDone(File file) {
        File done = new File(file.getParentFile(), file.getName() + ".imported");
        if (!file.renameTo(done)) {
            plugin.getLogger().warning("Import: could not rename " + file.getName()
                    + ". Move or delete it, or it will be re-read on the next start.");
        }
    }

    private int importProfiles(Connection connection) {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT player_id, alerts, fast_auction FROM auction_profiles")) {
            while (results.next()) {
                UUID uuid = toUuid(results.getBytes("player_id"));
                if (uuid == null) continue;
                plugin.profiles().set(uuid, results.getBoolean("alerts"), results.getBoolean("fast_auction"));
                count++;
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Import: no auction_profiles table, skipping", ex);
        }
        return count;
    }

    public List<String> summary(Report report) {
        List<String> lines = new ArrayList<>();
        lines.add("Imported " + report.active() + " live listings, "
                + report.awaitingCollection() + " awaiting collection, "
                + report.history() + " history rows.");
        lines.add(report.skipped() + " already present, " + report.failed() + " failed.");
        lines.add("Imported " + report.profiles() + " player preferences.");
        return lines;
    }
}
