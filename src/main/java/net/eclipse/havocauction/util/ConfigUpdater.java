package net.eclipse.havocauction.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Adds keys that a plugin update introduced into the config files already on disk.
 *
 * Bukkit only writes a config when the file is absent, so an updated jar never gives an
 * existing server its new settings - they have to be pasted in by hand, and a missed one
 * means a feature quietly runs on defaults or, for dialog text, renders as "Button".
 *
 * This walks the defaults bundled in the jar, writes any key the live file is missing,
 * and leaves every existing value alone. Comments on new keys come across too, so the
 * file still explains itself. Nothing is ever removed: unknown keys are assumed to be
 * yours, not stale.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public record Result(String fileName, List<String> addedKeys) {
        public boolean changed() {
            return !addedKeys.isEmpty();
        }
    }

    /**
     * Merges jar defaults into the on-disk file.
     *
     * @param resourceName file to sync, e.g. "config.yml"
     * @return which keys were added
     */
    public static Result update(JavaPlugin plugin, String resourceName) {
        List<String> added = new ArrayList<>();
        File file = new File(plugin.getDataFolder(), resourceName);

        if (!file.exists()) {
            // Nothing to merge into: the fresh copy already has everything.
            plugin.saveResource(resourceName, false);
            return new Result(resourceName, added);
        }

        InputStream stream = plugin.getResource(resourceName);
        if (stream == null) return new Result(resourceName, added);

        YamlConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + resourceName, ex);
            return new Result(resourceName, added);
        }

        FileConfiguration current = YamlConfiguration.loadConfiguration(file);

        for (String key : defaults.getKeys(true)) {
            // Only leaf values: parent sections appear implicitly when a child is set.
            if (defaults.isConfigurationSection(key)) continue;
            if (current.contains(key)) continue;

            current.set(key, defaults.get(key));
            current.setComments(key, defaults.getComments(key));
            added.add(key);
        }

        if (added.isEmpty()) return new Result(resourceName, added);

        try {
            // Keep one rollback copy before rewriting a live file.
            Files.copy(file.toPath(), new File(file.getParentFile(), resourceName + ".bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            current.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not write updated " + resourceName + " - your file is unchanged", ex);
            return new Result(resourceName, new ArrayList<>());
        }
        return new Result(resourceName, added);
    }

    /** Logs what an update added, or stays quiet when there was nothing to do. */
    public static void report(JavaPlugin plugin, Result result) {
        if (!result.changed()) return;
        plugin.getLogger().info("Added " + result.addedKeys().size()
                + " new setting(s) to " + result.fileName() + " (previous file kept as "
                + result.fileName() + ".bak):");
        for (String key : result.addedKeys()) {
            plugin.getLogger().info("  + " + key);
        }
    }
}
