package dev.heytozzz.duckhunt.config;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.effect.EffectConfig;
import dev.heytozzz.duckhunt.effect.EffectSet;
import dev.heytozzz.duckhunt.event.EventScope;
import dev.heytozzz.duckhunt.event.EventScopeConfig;
import dev.heytozzz.duckhunt.spawn.PathMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads settings from config.yml: duck stats, spawn defaults,
 * auto-spawn behaviour and misc options. Spawn points (and their
 * waypoint paths) live in {@link SpawnPointManager} / spawnpoints.yml.
 */
public class ConfigManager {

    private final DuckHuntPlugin plugin;

    private double duckHealth;
    private List<EntityType> duckTypes;
    private double minDuckSpeed;
    private double maxDuckSpeed;
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
    private int minDuckPoints;
    private int maxDuckPoints;

    private boolean broadcastEnabled;
    private BroadcastMode broadcastMode;
    private double broadcastRadius;

    private EffectSet defaultSpawnEffects;
    private EffectSet defaultDeathEffects;

    private Set<Integer> eventMilestoneSeconds;
    private boolean eventWinnerTitleEnabled;
    private List<String> eventWinnerRewardCommands;
    private EventScope eventStartScope;
    private EventScope eventCountdownScope;
    private EventScope eventWinnerScope;

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
        duckTypes = parseDuckTypes(config.getStringList("duck.types"));
        minDuckSpeed = Math.max(0.0, config.getDouble("duck.speed.min", 0.15));
        maxDuckSpeed = Math.max(minDuckSpeed, config.getDouble("duck.speed.max", 0.35));
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
        minDuckPoints = Math.max(0, config.getInt("leaderboard.min-points", 1));
        maxDuckPoints = Math.max(minDuckPoints, config.getInt("leaderboard.max-points", 10));

        broadcastEnabled = config.getBoolean("kill-broadcast.enabled", true);
        broadcastMode = BroadcastMode.parse(config.getString("kill-broadcast.mode", "global"));
        if (broadcastMode == null) {
            plugin.getLogger().warning("Invalid 'kill-broadcast.mode' in config.yml, falling back to 'global'.");
            broadcastMode = BroadcastMode.GLOBAL;
        }
        broadcastRadius = Math.max(0.0, config.getDouble("kill-broadcast.radius", 100.0));

        defaultSpawnEffects = EffectConfig.parse(config.getConfigurationSection("effects.spawn"), plugin.getLogger());
        if (defaultSpawnEffects == null) {
            defaultSpawnEffects = EffectSet.EMPTY;
        }
        defaultDeathEffects = EffectConfig.parse(config.getConfigurationSection("effects.death"), plugin.getLogger());
        if (defaultDeathEffects == null) {
            defaultDeathEffects = EffectSet.EMPTY;
        }

        eventMilestoneSeconds = new LinkedHashSet<>(config.getIntegerList("event.milestone-seconds"));
        if (eventMilestoneSeconds.isEmpty()) {
            eventMilestoneSeconds = Set.of(60, 30, 5, 4, 3, 2, 1);
        }
        eventWinnerTitleEnabled = config.getBoolean("event.winner-title-enabled", true);
        eventWinnerRewardCommands = config.getStringList("event.winner-rewards");

        EventScope globalFallback = new EventScope(BroadcastMode.GLOBAL, 50.0, List.of());
        eventStartScope = EventScopeConfig.parse(
                config.getConfigurationSection("event.start"), "event.start", globalFallback, plugin.getLogger());
        eventCountdownScope = EventScopeConfig.parse(
                config.getConfigurationSection("event.countdown"), "event.countdown", globalFallback, plugin.getLogger());
        eventWinnerScope = EventScopeConfig.parse(
                config.getConfigurationSection("event.winner"), "event.winner", globalFallback, plugin.getLogger());
    }

    /**
     * Parses and validates "duck.types": each entry must be a real
     * {@link EntityType} that maps to a {@link Mob} (so it supports AI,
     * pathfinding, attributes, etc.). Unknown or non-mob entries are
     * skipped with a warning; if nothing valid is left, falls back to a
     * single-entry list of {@code ZOMBIE}.
     */
    private List<EntityType> parseDuckTypes(List<String> raw) {
        List<EntityType> types = new ArrayList<>();
        for (String name : raw) {
            EntityType type = parseEntityType(name);
            if (type == null) {
                plugin.getLogger().warning("Unknown entity type '" + name + "' in 'duck.types', skipping.");
                continue;
            }
            Class<?> entityClass = type.getEntityClass();
            if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
                plugin.getLogger().warning("Entity type '" + name + "' in 'duck.types' isn't a usable mob, skipping.");
                continue;
            }
            types.add(type);
        }
        if (types.isEmpty()) {
            plugin.getLogger().warning("No valid entries in 'duck.types', falling back to a single ZOMBIE.");
            types.add(EntityType.ZOMBIE);
        }
        return types;
    }

    @Nullable
    private EntityType parseEntityType(String raw) {
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public double getDuckHealth() {
        return duckHealth;
    }

    /**
     * The pool of mob types a newly-spawned duck is randomly picked from
     * (already validated: every entry is guaranteed to be a usable
     * {@link Mob} type). Always has at least one entry.
     */
    public List<EntityType> getDuckTypes() {
        return duckTypes;
    }

    /**
     * Lower bound (inclusive) of a duck's randomly-rolled movement speed.
     */
    public double getMinDuckSpeed() {
        return minDuckSpeed;
    }

    /**
     * Upper bound (inclusive) of a duck's randomly-rolled movement speed.
     */
    public double getMaxDuckSpeed() {
        return maxDuckSpeed;
    }

    /**
     * Whether ducks actively patrol their spawn point's waypoint path
     * using real pathfinding AI. When false, ducks stay put at their
     * spawn point (their default vanilla goals are also left untouched
     * in that case, though {@code duck.ai-enabled: false} already keeps
     * them passive via {@code Mob#setAI(false)}).
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
     * auto-spawn cycle or a manual "/duckhunt admin spawn".
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

    /**
     * Converts a duck's rolled movement speed into leaderboard points:
     * linearly interpolated between "leaderboard.min-points" (at
     * "duck.speed.min") and "leaderboard.max-points" (at
     * "duck.speed.max") — faster ducks are worth more. Speeds outside
     * that range are clamped.
     */
    public int getPointsForSpeed(double speed) {
        if (maxDuckSpeed <= minDuckSpeed) {
            return minDuckPoints;
        }
        double fraction = (speed - minDuckSpeed) / (maxDuckSpeed - minDuckSpeed);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return (int) Math.round(minDuckPoints + fraction * (maxDuckPoints - minDuckPoints));
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

    /**
     * Switches the kill broadcast to "global" mode and persists it to
     * config.yml. Used by "/duckhunt admin settings broadcast global".
     */
    public void setBroadcastGlobal() {
        broadcastMode = BroadcastMode.GLOBAL;
        plugin.getConfig().set("kill-broadcast.mode", "global");
        plugin.saveConfig();
    }

    /**
     * Switches the kill broadcast to "radius" mode with the given radius
     * (in blocks) and persists both to config.yml. Used by
     * "/duckhunt admin settings broadcast radius <blocks>".
     */
    public void setBroadcastRadius(double radius) {
        broadcastMode = BroadcastMode.RADIUS;
        broadcastRadius = radius;
        plugin.getConfig().set("kill-broadcast.mode", "radius");
        plugin.getConfig().set("kill-broadcast.radius", radius);
        plugin.saveConfig();
    }

    /**
     * Server-wide default sounds/particles played when a duck spawns,
     * used whenever a spawn point doesn't define its own "effects.spawn"
     * override in spawnpoints.yml.
     */
    public EffectSet getDefaultSpawnEffects() {
        return defaultSpawnEffects;
    }

    /**
     * Server-wide default sounds/particles played when a duck is caught,
     * used whenever a spawn point doesn't define its own "effects.death"
     * override in spawnpoints.yml.
     */
    public EffectSet getDefaultDeathEffects() {
        return defaultDeathEffects;
    }

    /**
     * Seconds remaining at which the countdown announces a milestone in
     * chat (e.g. {1, 2, 3, 4, 5, 30, 60}).
     */
    public Set<Integer> getEventMilestoneSeconds() {
        return eventMilestoneSeconds;
    }

    /**
     * Whether an event's winner announcement is also shown as a title,
     * on top of the chat message.
     */
    public boolean isEventWinnerTitleEnabled() {
        return eventWinnerTitleEnabled;
    }

    /**
     * Console command templates run once per winner when an event ends
     * (skipped entirely if nobody scored any points). Each template can
     * use %player% (the winner's name), %points% (their winning score),
     * %id% (the spawn point id) and %name% (the event's name).
     */
    public List<String> getEventWinnerRewardCommands() {
        return eventWinnerRewardCommands;
    }

    /**
     * Who receives an event's start announcement.
     */
    public EventScope getEventStartScope() {
        return eventStartScope;
    }

    /**
     * Who receives an event's countdown (the action bar timer and the
     * milestone chat announcements).
     */
    public EventScope getEventCountdownScope() {
        return eventCountdownScope;
    }

    /**
     * Who receives an event's winner announcement (chat, and title if
     * {@link #isEventWinnerTitleEnabled()}).
     */
    public EventScope getEventWinnerScope() {
        return eventWinnerScope;
    }
}
