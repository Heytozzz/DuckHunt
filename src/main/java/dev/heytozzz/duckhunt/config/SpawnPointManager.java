package dev.heytozzz.duckhunt.config;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.spawn.PathMode;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
import dev.heytozzz.duckhunt.spawn.Waypoint;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and persists duck spawn points (including their waypoint paths)
 * from their own file ("spawnpoints.yml"), kept separate from the
 * general config.yml.
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
                PathMode pathMode = PathMode.parse(point.getString("path-mode"));
                spawnPoints.put(id, new SpawnPoint(
                        id,
                        point.getString("world", "world"),
                        point.getDouble("x"),
                        point.getDouble("y"),
                        point.getDouble("z"),
                        (float) point.getDouble("yaw", 0.0),
                        amount,
                        readPath(point),
                        pathMode
                ));
            }
        }
    }

    private List<Waypoint> readPath(ConfigurationSection point) {
        List<Waypoint> path = new ArrayList<>();
        for (Map<?, ?> entry : point.getMapList("path")) {
            Object x = entry.get("x");
            Object y = entry.get("y");
            Object z = entry.get("z");
            if (x instanceof Number xn && y instanceof Number yn && z instanceof Number zn) {
                path.add(new Waypoint(xn.doubleValue(), yn.doubleValue(), zn.doubleValue()));
            }
        }
        return path;
    }

    /**
     * Adds or overwrites a spawn point (location, amount, path and
     * path-mode included) and writes it to spawnpoints.yml immediately.
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
        storage.set(path + ".path", toMapList(point.path()));
        storage.set(path + ".path-mode", point.pathMode() != null
                ? point.pathMode().name().toLowerCase(Locale.ROOT)
                : null);
        persist();
    }

    private List<Map<String, Object>> toMapList(List<Waypoint> waypoints) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", waypoint.x());
            entry.put("y", waypoint.y());
            entry.put("z", waypoint.z());
            list.add(entry);
        }
        return list;
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

    /**
     * Appends a waypoint to a spawn point's patrol path.
     *
     * @return true if the spawn point existed and the waypoint was added.
     */
    public boolean addWaypoint(String id, Location location) {
        SpawnPoint existing = spawnPoints.get(id);
        if (existing == null) {
            return false;
        }
        List<Waypoint> updatedPath = new ArrayList<>(existing.path());
        updatedPath.add(new Waypoint(location.getX(), location.getY(), location.getZ()));
        save(withPath(existing, updatedPath));
        return true;
    }

    /**
     * Removes a single waypoint from a spawn point's patrol path.
     *
     * @param zeroBasedIndex index of the waypoint to remove, 0-based.
     * @return true if the spawn point existed and the index was valid.
     */
    public boolean removeWaypoint(String id, int zeroBasedIndex) {
        SpawnPoint existing = spawnPoints.get(id);
        if (existing == null || zeroBasedIndex < 0 || zeroBasedIndex >= existing.path().size()) {
            return false;
        }
        List<Waypoint> updatedPath = new ArrayList<>(existing.path());
        updatedPath.remove(zeroBasedIndex);
        save(withPath(existing, updatedPath));
        return true;
    }

    /**
     * Clears every waypoint from a spawn point's patrol path (the point
     * itself, and its amount/path-mode, are kept).
     *
     * @return true if the spawn point existed.
     */
    public boolean clearPath(String id) {
        SpawnPoint existing = spawnPoints.get(id);
        if (existing == null) {
            return false;
        }
        save(withPath(existing, List.of()));
        return true;
    }

    /**
     * Overrides a spawn point's path-mode.
     *
     * @return true if the spawn point existed.
     */
    public boolean setPathMode(String id, PathMode mode) {
        SpawnPoint existing = spawnPoints.get(id);
        if (existing == null) {
            return false;
        }
        save(new SpawnPoint(existing.id(), existing.worldName(), existing.x(), existing.y(), existing.z(),
                existing.yaw(), existing.amount(), existing.path(), mode));
        return true;
    }

    private SpawnPoint withPath(SpawnPoint existing, List<Waypoint> newPath) {
        return new SpawnPoint(existing.id(), existing.worldName(), existing.x(), existing.y(), existing.z(),
                existing.yaw(), existing.amount(), newPath, existing.pathMode());
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
