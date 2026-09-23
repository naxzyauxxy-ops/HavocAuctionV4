package net.eclipse.havocauction.storage;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.model.Listing;
import net.eclipse.havocauction.model.ListingStatus;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Flat JDBC storage for SQLite (default) or MySQL.
 * Every method blocks - call them from the batched flush, not the main thread.
 */
public class SqlStorage {

    private static final String TABLE = "havoc_listings";
    private static final String PROFILE_TABLE = "havoc_auction_profiles";

    private final HavocAuction plugin;
    private final boolean mysql;
    private final String url;
    private final String user;
    private final String password;

    private Connection connection;

    public SqlStorage(HavocAuction plugin) {
        this.plugin = plugin;
        ConfigurationSection db = plugin.getConfig().getConfigurationSection("DATABASE");
        String type = db == null ? "SQLITE" : db.getString("TYPE", "SQLITE");
        this.mysql = "MYSQL".equalsIgnoreCase(type);

        if (mysql) {
            ConfigurationSection my = db.getConfigurationSection("MYSQL");
            this.url = "jdbc:mysql://" + my.getString("HOST", "127.0.0.1")
                    + ":" + my.getInt("PORT", 3306) + "/" + my.getString("DATABASE", "havocauction")
                    + "?useSSL=" + my.getBoolean("USE-SSL", false)
                    + "&allowPublicKeyRetrieval=true&characterEncoding=utf8";
            this.user = my.getString("USER", "");
            this.password = my.getString("PASSWORD", "");
        } else {
            File file = new File(plugin.getDataFolder(), "auction.db");
            this.url = "jdbc:sqlite:" + file.getAbsolutePath();
            this.user = null;
            this.password = null;
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            if (mysql) {
                connection = DriverManager.getConnection(url, user, password);
            } else {
                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException ignored) {
                    // Paper/Purpur ship the driver
                }
                connection = DriverManager.getConnection(url);
            }
        }
        return connection;
    }

    public void initialise() throws SQLException {
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id VARCHAR(36) NOT NULL PRIMARY KEY,"
                    + "seller VARCHAR(36) NOT NULL,"
                    + "seller_name VARCHAR(32),"
                    + "item TEXT NOT NULL,"
                    + "price DOUBLE NOT NULL,"
                    + "created BIGINT NOT NULL,"
                    + "expires BIGINT NOT NULL,"
                    + "sold_at BIGINT,"
                    + "status VARCHAR(16) NOT NULL,"
                    + "buyer VARCHAR(36),"
                    + "buyer_name VARCHAR(32)"
                    + ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + PROFILE_TABLE + " ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                    + "alerts BOOLEAN NOT NULL,"
                    + "fast_buy BOOLEAN NOT NULL"
                    + ")");
            for (String index : List.of(
                    "idx_havoc_listings_seller ON " + TABLE + " (seller)",
                    "idx_havoc_listings_buyer ON " + TABLE + " (buyer)",
                    "idx_havoc_listings_status ON " + TABLE + " (status)")) {
                try {
                    statement.executeUpdate((mysql ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ") + index);
                } catch (SQLException ignored) {
                    // already exists; MySQL has no IF NOT EXISTS for indexes
                }
            }
        }
    }

    public List<Listing> loadAll() {
        List<Listing> listings = new ArrayList<>();
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM " + TABLE);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                try {
                    long soldAt = results.getLong("sold_at");
                    String buyer = results.getString("buyer");
                    listings.add(new Listing(
                            UUID.fromString(results.getString("id")),
                            UUID.fromString(results.getString("seller")),
                            results.getString("seller_name"),
                            results.getString("item"),
                            results.getDouble("price"),
                            results.getLong("created"),
                            results.getLong("expires"),
                            results.wasNull() || soldAt <= 0 ? null : soldAt,
                            ListingStatus.valueOf(results.getString("status")),
                            buyer == null ? null : UUID.fromString(buyer),
                            results.getString("buyer_name")
                    ));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping malformed listing row: " + ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load listings", ex);
        }
        return listings;
    }

    public void saveAll(Collection<Listing> listings) {
        if (listings.isEmpty()) return;
        String sql = (mysql ? "REPLACE INTO " : "INSERT OR REPLACE INTO ") + TABLE
                + " (id,seller,seller_name,item,price,created,expires,sold_at,status,buyer,buyer_name)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try {
            Connection conn = getConnection();
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                for (Listing listing : listings) {
                    statement.setString(1, listing.getId().toString());
                    statement.setString(2, listing.getSeller().toString());
                    statement.setString(3, listing.getSellerName());
                    statement.setString(4, listing.getEncodedItem());
                    statement.setDouble(5, listing.getPrice());
                    statement.setLong(6, listing.getCreatedAt());
                    statement.setLong(7, listing.getExpiresAt());
                    if (listing.getSoldAt() == null) {
                        statement.setNull(8, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(8, listing.getSoldAt());
                    }
                    statement.setString(9, listing.getStatus().name());
                    statement.setString(10, listing.getBuyer() == null ? null : listing.getBuyer().toString());
                    statement.setString(11, listing.getBuyer() == null ? null : listing.getBuyerName());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(auto);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save listings", ex);
        }
    }

    public void deleteAll(Collection<UUID> ids) {
        if (ids.isEmpty()) return;
        try {
            Connection conn = getConnection();
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement statement =
                         conn.prepareStatement("DELETE FROM " + TABLE + " WHERE id = ?")) {
                for (UUID id : ids) {
                    statement.setString(1, id.toString());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(auto);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete listings", ex);
        }
    }

    // ------------------------------------------------------------------ profiles

    public Map<UUID, boolean[]> loadProfiles() {
        Map<UUID, boolean[]> profiles = new HashMap<>();
        try (PreparedStatement statement =
                     getConnection().prepareStatement("SELECT uuid, alerts, fast_buy FROM " + PROFILE_TABLE);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                try {
                    profiles.put(UUID.fromString(results.getString("uuid")),
                            new boolean[]{results.getBoolean("alerts"), results.getBoolean("fast_buy")});
                } catch (IllegalArgumentException ignored) {
                    // malformed uuid
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player preferences", ex);
        }
        return profiles;
    }

    public void saveProfiles(Map<UUID, boolean[]> profiles) {
        if (profiles.isEmpty()) return;
        String sql = (mysql ? "REPLACE INTO " : "INSERT OR REPLACE INTO ")
                + PROFILE_TABLE + " (uuid, alerts, fast_buy) VALUES (?,?,?)";
        try {
            Connection conn = getConnection();
            boolean auto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                for (Map.Entry<UUID, boolean[]> entry : profiles.entrySet()) {
                    statement.setString(1, entry.getKey().toString());
                    statement.setBoolean(2, entry.getValue()[0]);
                    statement.setBoolean(3, entry.getValue()[1]);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(auto);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player preferences", ex);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
    }
}
