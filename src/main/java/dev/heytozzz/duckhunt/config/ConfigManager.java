package dev.heytozzz.duckhunt.config;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.spawn.PathMode;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Loads settings from config.yml: duck stats, spawn defaults,
 * auto-spawn behaviour and misc options. Spawn points (and their
 * waypoint paths) live in {@link SpawnPointManager} / spawnpoints.yml.
 */
public class ConfigManager {

    private final DuckHuntPlugin plugin;

    private double duckHealth;
    private double duckMovementSpeed;
    private boolean duckAiEnabled;
    private boolean duckSilent;
    private boolean duckGlowing;

    private int defaultDuckAmount;
    private boolean instantRespawn;
    private PathMode defaultPathMode;
    private int pathCheckIntervalTicks;

    private boolean autoSpawnEnabled;
    private int autoSpawnIntervalSeconds;

    private double minKillDistance;
    private int leaderboardTopSize;

    private boolean broadcastEnabled;
    private BroadcastMode broadcastMode;
    private double broadcastRadius;

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

        duckHealth = config.getDouble("duck.health", 2.0);
        duckMovementSpeed = config.getDouble("duck.movement-speed", 0.23);
        duckAiEnabled = config.getBoolean("duck.ai-enabled", false);
        duckSilent = config.getBoolean("duck.silent", true);
        duckGlowing = config.getBoolean("duck.glowing", false);

        defaultDuckAmount = Math.max(1, config.getInt("spawn.default-amount", 1));
        instantRespawn = config.getBoolean("spawn.instant-respawn", false);

        defaultPathMode = PathMode.parse(config.getString("spawn.default-path-mode", "loop"));
        if (defaultPathMode == null) {
            plugin.getLogger().warning("Invalid 'spawn.default-path-mode' in config.yml, falling back to 'loop'.");
            defaultPathMode = PathMode.LOOP;
        }
        pathCheckIntervalTicks = Math.max(1, config.getInt("spawn.path-check-interval-ticks", 10));

        autoSpawnEnabled = config.getBoolean("auto-spawn.enabled", false);
        autoSpawnIntervalSeconds = config.getInt("auto-spawn.interval-seconds", 20);

        minKillDistance = Math.max(0.0, config.getDouble("leaderboard.min-kill-distance", 10.0));
        leaderboardTopSize = Math.max(1, config.getInt("leaderboard.top-size", 10));

        broadcastEnabled = config.getBoolean("kill-broadcast.enabled", true);
        broadcastMode = BroadcastMode.parse(config.getString("kill-broadcast.mode", "global"));
        if (broadcastMode == null) {
            plugin.getLogger().warning("Invalid 'kill-broadcast.mode' in config.yml, falling back to 'global'.");
            broadcastMode = BroadcastMode.GLOBAL;
        }
        broadcastRadius = Math.max(0.0, config.getDouble("kill-broadcast.radius", 100.0));
    }

    public double getDuckHealth() {
        return duckHealth;
    }

    public double getDuckMovementSpeed() {
        return duckMovementSpeed;
    }

    /**
     * Whether ducks actively patrol their spawn point's waypoint path
     * using real pathfinding AI. When false, ducks stay put at their
     * spawn point (their default vanilla goals are also left untouched
     * in that case, though {@code duck.ai-enabled: false} already keeps
     * them passive via {@code Zombie#setAI(false)}).
     */
    public boolean isDuckAiEnabled() {
        return duckAiEnabled;
    }

    public boolean isDuckSilent() {
        return duckSilent;
    }

    public boolean isDuckGlowing() {
        return duckGlowing;
    }

    /**
     * Default number of ducks kept alive per spawn point, used whenever a
     * spawn point doesn't define its own "amount" override.
     */
    public int getDefaultDuckAmount() {
        return defaultDuckAmount;
    }

    /**
     * Whether a spawn point should instantly spawn a replacement duck the
     * moment one of its ducks dies, instead of waiting for the next
     * auto-spawn cycle or a manual "/duckhunt spawn".
     */
    public boolean isInstantRespawn() {
        return instantRespawn;
    }

    /**
     * Server-wide default for what a duck does after reaching the last
     * waypoint of its path, used whenever a spawn point doesn't define
     * its own "path-mode" override.
     */
    public PathMode getDefaultPathMode() {
        return defaultPathMode;
    }

    /**
     * How often (in ticks) the path-following task checks whether each
     * duck needs to be sent to its next waypoint.
     */
    public int getPathCheckIntervalTicks() {
        return pathCheckIntervalTicks;
    }

    public boolean isAutoSpawnEnabled() {
        return autoSpawnEnabled;
    }

    public int getAutoSpawnIntervalSeconds() {
        return autoSpawnIntervalSeconds;
    }

    /**
     * Minimum distance (in blocks) between the killer and the duck at the
     * moment of death for the kill to count towards the leaderboard.
     */
    public double getMinKillDistance() {
        return minKillDistance;
    }

    /**
     * How many entries "/duckhunt top" shows.
     */
    public int getLeaderboardTopSize() {
        return leaderboardTopSize;
    }

    public boolean isBroadcastEnabled() {
        return broadcastEnabled;
    }

    /**
     * Whether the kill broadcast is sent to every online player
     * ({@link BroadcastMode#GLOBAL}) or only to those within
     * {@link #getBroadcastRadius()} blocks of the kill
     * ({@link BroadcastMode#RADIUS}).
     */
    public BroadcastMode getBroadcastMode() {
        return broadcastMode;
    }

    /**
     * Radius (in blocks) used when {@link #getBroadcastMode()} is
     * {@link BroadcastMode#RADIUS}.
     */
    public double getBroadcastRadius() {
        return broadcastRadius;
    }
}
