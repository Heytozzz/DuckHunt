package dev.heytozzz.duckhunt.config;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.combo.ComboTier;
import dev.heytozzz.duckhunt.effect.EffectConfig;
import dev.heytozzz.duckhunt.effect.EffectSet;
import dev.heytozzz.duckhunt.effect.ParticleEffect;
import dev.heytozzz.duckhunt.event.EventScope;
import dev.heytozzz.duckhunt.event.EventScopeConfig;
import dev.heytozzz.duckhunt.spawn.PathMode;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private boolean rareDuckEnabled;
    private double rareDuckChance;
    private double rareSpeedMultiplier;
    private double rarePointsMultiplier;
    private ParticleEffect rareDuckParticle;
    private int rareParticleIntervalTicks;

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

    private boolean comboEnabled;
    private double comboWindowSeconds;
    private int comboTickIntervalTicks;
    private List<ComboTier> comboTiers;

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

        rareDuckEnabled = config.getBoolean("duck.rare.enabled", true);
        rareDuckChance = clamp01(config.getDouble("duck.rare.chance", 0.05));
        rareSpeedMultiplier = Math.max(1.0, config.getDouble("duck.rare.speed-multiplier", 1.6));
        rarePointsMultiplier = Math.max(1.0, config.getDouble("duck.rare.points-multiplier", 4.0));
        rareParticleIntervalTicks = Math.max(1, config.getInt("duck.rare.particle-interval-ticks", 4));
        rareDuckParticle = parseParticleSection(
                config.getConfigurationSection("duck.rare.particle"), DEFAULT_RARE_PARTICLE);

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

        comboEnabled = config.getBoolean("combo.enabled", true);
        comboWindowSeconds = Math.max(0.1, config.getDouble("combo.window-seconds", 5.0));
        comboTickIntervalTicks = Math.max(1, config.getInt("combo.tick-interval-ticks", 4));
        comboTiers = parseComboTiers(config);

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

    private static final ParticleEffect DEFAULT_RARE_PARTICLE =
            new ParticleEffect(Particle.END_ROD, 2, 0.2, 0.3, 0.2, 0.0);
    private static final ParticleEffect DEFAULT_COMBO_PARTICLE =
            new ParticleEffect(Particle.CRIT, 2, 0.05, 0.05, 0.05, 0.0);

    /**
     * Parses "combo.tiers": a list of milestones, each with a kill-streak
     * threshold, a points multiplier, and an optional particle override
     * (falling back to "combo.default-particle" if omitted). Invalid
     * entries (missing/non-positive "combo") are skipped with a warning.
     * The result is always sorted ascending by threshold, since
     * {@link dev.heytozzz.duckhunt.combo.ComboManager} relies on that
     * order to find a streak's highest qualifying tier.
     */
    private List<ComboTier> parseComboTiers(FileConfiguration config) {
        ParticleEffect defaultParticle =
                parseParticleSection(config.getConfigurationSection("combo.default-particle"), DEFAULT_COMBO_PARTICLE);

        List<ComboTier> tiers = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList("combo.tiers")) {
            Object thresholdValue = entry.get("combo");
            if (!(thresholdValue instanceof Number number) || number.intValue() <= 0) {
                plugin.getLogger().warning("Skipping a 'combo.tiers' entry with a missing/invalid 'combo' threshold.");
                continue;
            }
            double multiplier = Math.max(1.0, toDouble(entry.get("points-multiplier"), 1.0));
            ParticleEffect particle = entry.get("particle") instanceof Map<?, ?> particleMap
                    ? parseParticleMap(particleMap, defaultParticle)
                    : defaultParticle;
            tiers.add(new ComboTier(number.intValue(), multiplier, particle));
        }
        tiers.sort(Comparator.comparingInt(ComboTier::threshold));
        return tiers;
    }

    /**
     * Parses a single particle description out of a {@link ConfigurationSection}
     * (used for both "duck.rare.particle" and "combo.default-particle" —
     * same shape as one entry of an {@link EffectSet}'s particle list,
     * just not wrapped in a list since there's always exactly one).
     */
    private ParticleEffect parseParticleSection(@Nullable ConfigurationSection section, ParticleEffect fallback) {
        if (section == null) {
            return fallback;
        }
        String rawType = section.getString("particle");
        Particle particle = parseParticleType(rawType, fallback.particle());
        int count = Math.max(0, section.getInt("count", fallback.count()));
        double offsetX = section.getDouble("offset-x", fallback.offsetX());
        double offsetY = section.getDouble("offset-y", fallback.offsetY());
        double offsetZ = section.getDouble("offset-z", fallback.offsetZ());
        double speed = section.getDouble("speed", fallback.speed());
        return new ParticleEffect(particle, count, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Same as {@link #parseParticleSection}, but reading from a raw
     * {@link Map} instead — needed for "combo.tiers" entries, since each
     * comes from {@code getMapList} rather than a proper
     * {@link ConfigurationSection}.
     */
    private ParticleEffect parseParticleMap(Map<?, ?> map, ParticleEffect fallback) {
        Particle particle = parseParticleType(
                map.get("particle") instanceof String raw ? raw : null, fallback.particle());
        int count = Math.max(0, (int) toDouble(map.get("count"), fallback.count()));
        double offsetX = toDouble(map.get("offset-x"), fallback.offsetX());
        double offsetY = toDouble(map.get("offset-y"), fallback.offsetY());
        double offsetZ = toDouble(map.get("offset-z"), fallback.offsetZ());
        double speed = toDouble(map.get("speed"), fallback.speed());
        return new ParticleEffect(particle, count, offsetX, offsetY, offsetZ, speed);
    }

    private Particle parseParticleType(@Nullable String rawType, Particle fallback) {
        if (rawType == null || rawType.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid particle type '" + rawType + "', using the default instead.");
            return fallback;
        }
    }

    private double toDouble(@Nullable Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
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
     * Whether a duck can roll as "rare" at spawn time (glowing,
     * faster, worth more points, with a particle trail).
     */
    public boolean isRareDuckEnabled() {
        return rareDuckEnabled;
    }

    /**
     * Chance (0.0-1.0) that a newly-spawned duck is rare.
     */
    public double getRareDuckChance() {
        return rareDuckChance;
    }

    /**
     * Multiplies a rare duck's normally-rolled speed.
     */
    public double getRareSpeedMultiplier() {
        return rareSpeedMultiplier;
    }

    /**
     * Multiplies the points a rare duck is worth on top of
     * {@link #getPointsForSpeed(double)} — e.g. 4.0 means 4x the points
     * a normal duck with that same (already-multiplied) speed would be worth.
     */
    public double getRarePointsMultiplier() {
        return rarePointsMultiplier;
    }

    /**
     * The particle trail a rare duck leaves behind while alive.
     */
    public ParticleEffect getRareDuckParticle() {
        return rareDuckParticle;
    }

    /**
     * How often (in ticks) a rare duck's particle trail is spawned.
     */
    public int getRareParticleIntervalTicks() {
        return rareParticleIntervalTicks;
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
     * Whether the duck-kill combo/streak system is active at all — no
     * streak tracking, no arrow trails, no points multiplier.
     */
    public boolean isComboEnabled() {
        return comboEnabled;
    }

    /**
     * Max seconds allowed between two consecutive duck kills for a
     * player's streak to keep climbing instead of resetting to zero.
     */
    public double getComboWindowSeconds() {
        return comboWindowSeconds;
    }

    /**
     * How often (in ticks) combo streaks are checked for expiry and
     * every in-flight "combo arrow"'s trail is advanced.
     */
    public int getComboTickIntervalTicks() {
        return comboTickIntervalTicks;
    }

    /**
     * Every configured combo milestone, sorted ascending by threshold.
     */
    public List<ComboTier> getComboTiers() {
        return comboTiers;
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
