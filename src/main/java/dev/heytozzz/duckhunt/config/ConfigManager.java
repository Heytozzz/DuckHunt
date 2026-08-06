package dev.heytozzz.duckhunt.config;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Loads settings from config.yml: duck stats, spawn defaults,
 * auto-spawn behaviour and misc options. Spawn points themselves live in
 * {@link SpawnPointManager} / spawnpoints.yml.
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

        duckHealth = config.getDouble("duck.health", 2.0);
        duckMovementSpeed = config.getDouble("duck.movement-speed", 0.23);
        duckAiEnabled = config.getBoolean("duck.ai-enabled", false);
        duckSilent = config.getBoolean("duck.silent", true);
        duckGlowing = config.getBoolean("duck.glowing", false);

        defaultDuckAmount = Math.max(1, config.getInt("spawn.default-amount", 1));
        instantRespawn = config.getBoolean("spawn.instant-respawn", false);

        autoSpawnEnabled = config.getBoolean("auto-spawn.enabled", false);
        autoSpawnIntervalSeconds = config.getInt("auto-spawn.interval-seconds", 20);

        broadcastKill = config.getBoolean("broadcast-kill", true);
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
