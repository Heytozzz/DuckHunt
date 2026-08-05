package dev.heytozzz.duckhunt.config;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and persists everything under config.yml: spawn points and duck stats.
 */
public class ConfigManager {

    private final DuckHuntPlugin plugin;
    private final Map<String, SpawnPoint> spawnPoints = new LinkedHashMap<>();

    private double duckHealth;
    private double duckMovementSpeed;
    private boolean duckAiEnabled;
    private boolean duckSilent;
    private boolean duckGlowing;

    private boolean autoSpawnEnabled;
    private int autoSpawnIntervalSeconds;

    private boolean broadcastKill;

    public ConfigManager(DuckHuntPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * (Re)loads config.yml from disk into memory.
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        spawnPoints.clear();
        ConfigurationSection section = config.getConfigurationSection("spawn-points");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection point = section.getConfigurationSection(id);
                if (point == null) {
                    continue;
                }
                spawnPoints.put(id, new SpawnPoint(
                        id,
                        point.getString("world", "world"),
                        point.getDouble("x"),
                        point.getDouble("y"),
                        point.getDouble("z"),
                        (float) point.getDouble("yaw", 0.0)
                ));
            }
        }

        duckHealth = config.getDouble("duck.health", 2.0);
        duckMovementSpeed = config.getDouble("duck.movement-speed", 0.23);
        duckAiEnabled = config.getBoolean("duck.ai-enabled", false);
        duckSilent = config.getBoolean("duck.silent", true);
        duckGlowing = config.getBoolean("duck.glowing", false);

        autoSpawnEnabled = config.getBoolean("auto-spawn.enabled", false);
        autoSpawnIntervalSeconds = config.getInt("auto-spawn.interval-seconds", 20);

        broadcastKill = config.getBoolean("broadcast-kill", true);
    }

    /**
     * Adds or overwrites a spawn point and writes it to config.yml immediately.
     */
    public void saveSpawnPoint(SpawnPoint point) {
        spawnPoints.put(point.id(), point);

        String path = "spawn-points." + point.id();
        FileConfiguration config = plugin.getConfig();
        config.set(path + ".world", point.worldName());
        config.set(path + ".x", point.x());
        config.set(path + ".y", point.y());
        config.set(path + ".z", point.z());
        config.set(path + ".yaw", point.yaw());
        plugin.saveConfig();
    }

    /**
     * Removes a spawn point, both from memory and from config.yml.
     *
     * @return true if a spawn point with that id existed.
     */
    public boolean removeSpawnPoint(String id) {
        if (!spawnPoints.containsKey(id)) {
            return false;
        }
        spawnPoints.remove(id);
        plugin.getConfig().set("spawn-points." + id, null);
        plugin.saveConfig();
        return true;
    }

    public Map<String, SpawnPoint> getSpawnPoints() {
        return Collections.unmodifiableMap(spawnPoints);
    }

    public double getDuckHealth() {
        return duckHealth;
    }

    public double getDuckMovementSpeed() {
        return duckMovementSpeed;
    }

    public boolean isDuckAiEnabled() {
        return duckAiEnabled;
    }

    public boolean isDuckSilent() {
        return duckSilent;
    }

    public boolean isDuckGlowing() {
        return duckGlowing;
    }

    public boolean isAutoSpawnEnabled() {
        return autoSpawnEnabled;
    }

    public int getAutoSpawnIntervalSeconds() {
        return autoSpawnIntervalSeconds;
    }

    public boolean isBroadcastKill() {
        return broadcastKill;
    }
}
