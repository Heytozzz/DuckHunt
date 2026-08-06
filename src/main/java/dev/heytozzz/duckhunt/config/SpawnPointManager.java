package dev.heytozzz.duckhunt.config;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and persists duck spawn points from their own file
 * ("spawnpoints.yml"), kept separate from the general config.yml.
 */
public class SpawnPointManager {

    private final DuckHuntPlugin plugin;
    private final File file;
    private final Map<String, SpawnPoint> spawnPoints = new LinkedHashMap<>();
    private YamlConfiguration storage;

    public SpawnPointManager(DuckHuntPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawnpoints.yml");
    }

    /**
     * (Re)loads spawnpoints.yml from disk into memory, extracting the
     * bundled template first if the file doesn't exist yet.
     */
    public void load() {
        if (!file.exists()) {
            plugin.saveResource("spawnpoints.yml", false);
        }
        storage = YamlConfiguration.loadConfiguration(file);

        spawnPoints.clear();
        ConfigurationSection section = storage.getConfigurationSection("points");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection point = section.getConfigurationSection(id);
                if (point == null) {
                    continue;
                }
                Integer amount = point.contains("amount") ? point.getInt("amount") : null;
                spawnPoints.put(id, new SpawnPoint(
                        id,
                        point.getString("world", "world"),
                        point.getDouble("x"),
                        point.getDouble("y"),
                        point.getDouble("z"),
                        (float) point.getDouble("yaw", 0.0),
                        amount
                ));
            }
        }
    }

    /**
     * Adds or overwrites a spawn point and writes it to spawnpoints.yml
     * immediately.
     */
    public void save(SpawnPoint point) {
        spawnPoints.put(point.id(), point);

        String path = "points." + point.id();
        storage.set(path + ".world", point.worldName());
        storage.set(path + ".x", point.x());
        storage.set(path + ".y", point.y());
        storage.set(path + ".z", point.z());
        storage.set(path + ".yaw", point.yaw());
        // A null amount clears the key, falling back to the server-wide default.
        storage.set(path + ".amount", point.amount());
        persist();
    }

    /**
     * Removes a spawn point, both from memory and from spawnpoints.yml.
     *
     * @return true if a spawn point with that id existed.
     */
    public boolean remove(String id) {
        if (!spawnPoints.containsKey(id)) {
            return false;
        }
        spawnPoints.remove(id);
        storage.set("points." + id, null);
        persist();
        return true;
    }

    private void persist() {
        try {
            storage.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save spawnpoints.yml: " + exception.getMessage());
        }
    }

    public Map<String, SpawnPoint> getSpawnPoints() {
        return Collections.unmodifiableMap(spawnPoints);
    }

    public SpawnPoint get(String id) {
        return spawnPoints.get(id);
    }
}
