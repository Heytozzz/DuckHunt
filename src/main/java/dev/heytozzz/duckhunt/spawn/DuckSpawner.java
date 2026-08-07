package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Handles spawning and despawning of "ducks" — a random mob picked from
 * "duck.types" each time, tagged and stat-adjusted, each with its own
 * randomly-rolled movement speed (faster ducks are worth more leaderboard
 * points, see {@link ConfigManager#getPointsForSpeed(double)}) — and, when
 * enabled, walks each one along its spawn point's waypoint path using
 * real pathfinding AI. Each spawn point can keep several ducks alive at
 * once, up to its configured capacity, all patrolling the same path.
 */
public class DuckSpawner {

    private final DuckHuntPlugin plugin;
    private final ConfigManager config;

    // spawnId -> UUIDs of the ducks currently alive at that point.
    private final Map<String, Set<UUID>> activeBySpawnId = new LinkedHashMap<>();

    // duckId -> where it currently is along its spawn point's route.
    private final Map<UUID, PathState> pathStateByDuck = new HashMap<>();

    private BukkitTask pathTask;

    public DuckSpawner(DuckHuntPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public int getActiveCount(String spawnId) {
        Set<UUID> active = activeBySpawnId.get(spawnId);
        return active == null ? 0 : active.size();
    }

    public boolean isFull(SpawnPoint point) {
        return getActiveCount(point.id()) >= point.effectiveAmount(config.getDefaultDuckAmount());
    }

    /**
     * Spawns a single duck at the given spawn point, unless it's already
     * at capacity or its world isn't loaded. The duck's mob type is
     * picked at random from "duck.types" and its movement speed is
     * randomly rolled within "duck.speed.min"/"duck.speed.max".
     *
     * @return true if a duck was actually spawned.
     */
    public boolean spawnOne(SpawnPoint point) {
        if (isFull(point)) {
            return false;
        }

        Location location = point.toLocation();
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Cannot spawn duck '" + point.id()
                    + "': world '" + point.worldName() + "' isn't loaded.");
            return false;
        }

        Class<? extends Mob> mobClass = mobClassOf(pickRandomDuckType());
        double speed = rollSpeed();

        Mob duck = spawnMob(location, mobClass, entity -> {
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.addScoreboardTag(DuckKeys.TAG_DUCK);
            entity.getPersistentDataContainer().set(DuckKeys.spawn(), PersistentDataType.STRING, point.id());
            entity.getPersistentDataContainer().set(DuckKeys.speed(), PersistentDataType.DOUBLE, speed);
        });

        applyDuckBehavior(duck, speed);

        activeBySpawnId.computeIfAbsent(point.id(), id -> new LinkedHashSet<>()).add(duck.getUniqueId());
        pathStateByDuck.put(duck.getUniqueId(), new PathState());
        return true;
    }

    /**
     * Tops a single spawn point up to its configured capacity.
     *
     * @return how many ducks were actually spawned.
     */
    public int fill(SpawnPoint point) {
        int spawned = 0;
        while (!isFull(point)) {
            if (!spawnOne(point)) {
                break; // e.g. world not loaded, no point retrying in a loop
            }
            spawned++;
        }
        return spawned;
    }

    /**
     * Tops up every configured spawn point to its capacity.
     *
     * @return how many ducks were actually spawned in total.
     */
    public int fillAll() {
        int total = 0;
        for (SpawnPoint point : plugin.getSpawnPointManager().getSpawnPoints().values()) {
            total += fill(point);
        }
        return total;
    }

    /**
     * Picks a random entry from "duck.types" (already validated to only
     * contain usable {@link Mob} types by {@link ConfigManager}).
     */
    private EntityType pickRandomDuckType() {
        List<EntityType> types = config.getDuckTypes();
        return types.get(ThreadLocalRandom.current().nextInt(types.size()));
    }

    /**
     * Rolls a random speed within "duck.speed.min"/"duck.speed.max"
     * (inclusive of the minimum; the two collapse to a fixed speed if
     * equal).
     */
    private double rollSpeed() {
        double min = config.getMinDuckSpeed();
        double max = config.getMaxDuckSpeed();
        if (max <= min) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Mob> mobClassOf(EntityType type) {
        return (Class<? extends Mob>) type.getEntityClass();
    }

    /**
     * Generic helper around {@link World#spawn(Location, Class, Consumer)}
     * that lets call sites work with a plain {@link Mob} reference instead
     * of needing to know the concrete captured type of a
     * {@code Class<? extends Mob>} obtained at runtime.
     */
    private <T extends Mob> T spawnMob(Location location, Class<T> mobClass, Consumer<Mob> initializer) {
        return location.getWorld().spawn(location, mobClass, initializer::accept);
    }

    /**
     * Applies stats and behaviour to a duck: health, its rolled movement
     * speed attribute, visibility flags, disabled collisions (so faster
     * ducks can freely overtake slower ones instead of pushing into
     * them), and (if path-following is enabled) wipes its default AI
     * goals so nothing but our own waypoint task moves or targets it.
     */
    private void applyDuckBehavior(Mob duck, double speed) {
        setAttribute(duck, "max_health", config.getDuckHealth());
        setAttribute(duck, "movement_speed", speed);
        duck.setHealth(config.getDuckHealth());
        duck.setSilent(config.isDuckSilent());
        duck.setGlowing(config.isDuckGlowing());
        duck.setCanPickupItems(false);
        // Disables collision resolution with other entities so ducks
        // never bump/push each other — faster ones simply pass through.
        duck.setCollidable(false);
        // No loot table at all: the duck must never drop anything.
        duck.clearLootTable();

        // Zombie-family mobs (zombie, husk, drowned, ...) would otherwise
        // catch fire in daylight; not applicable to other mob families.
        if (duck instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }

        boolean followPath = config.isDuckAiEnabled();
        duck.setAI(followPath);
        if (followPath) {
            // Wipes vanilla behaviour (attacking, wandering, looking
            // around...) so the only thing driving this duck is the
            // moveTo() calls issued from tickPaths().
            Bukkit.getMobGoals().removeAllGoals(duck);
        }
    }

    private void setAttribute(Attributable entity, String key, double value) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Called when a duck dies. Frees up its spawn point slot and (if
     * enabled) instantly spawns a replacement.
     */
    public void handleDeath(Mob duck) {
        UUID duckId = duck.getUniqueId();
        pathStateByDuck.remove(duckId);

        String spawnId = duck.getPersistentDataContainer().get(DuckKeys.spawn(), PersistentDataType.STRING);
        if (spawnId == null) {
            return;
        }

        Set<UUID> active = activeBySpawnId.get(spawnId);
        if (active != null) {
            active.remove(duckId);
            if (active.isEmpty()) {
                activeBySpawnId.remove(spawnId);
            }
        }

        if (config.isInstantRespawn()) {
            SpawnPoint point = plugin.getSpawnPointManager().get(spawnId);
            if (point != null) {
                spawnOne(point);
            }
        }
    }

    /**
     * Removes every currently tracked duck plus any stray tagged mobs
     * left over from a previous run (e.g. after a crash or a reload).
     */
    public void clearAll() {
        activeBySpawnId.clear();
        pathStateByDuck.clear();
        for (World world : plugin.getServer().getWorlds()) {
            world.getEntitiesByClass(Mob.class).stream()
                    .filter(entity -> entity.getScoreboardTags().contains(DuckKeys.TAG_DUCK))
                    .forEach(Entity::remove);
        }
    }

    /**
     * Rebuilds the "active ducks per spawn point" tracking from ducks
     * already present in the world. Needed on startup, since ducks
     * spawned in a previous server session (they're persistent) would
     * otherwise be invisible to this session's in-memory tracking,
     * causing the plugin to spawn past the configured capacity. Also
     * re-strips their AI goals, since goal removal doesn't survive a
     * server restart. Reuses each duck's already-rolled speed (stored on
     * it) instead of rolling a new one.
     */
    public void reconcileFromWorld() {
        activeBySpawnId.clear();
        pathStateByDuck.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Mob duck : world.getEntitiesByClass(Mob.class)) {
                if (!duck.getScoreboardTags().contains(DuckKeys.TAG_DUCK)) {
                    continue;
                }
                String spawnId = duck.getPersistentDataContainer().get(DuckKeys.spawn(), PersistentDataType.STRING);
                if (spawnId == null) {
                    continue;
                }
                double speed = duck.getPersistentDataContainer()
                        .getOrDefault(DuckKeys.speed(), PersistentDataType.DOUBLE, config.getMinDuckSpeed());
                activeBySpawnId.computeIfAbsent(spawnId, id -> new LinkedHashSet<>()).add(duck.getUniqueId());
                pathStateByDuck.put(duck.getUniqueId(), new PathState());
                applyDuckBehavior(duck, speed);
            }
        }
    }

    /**
     * Starts the repeating task that walks every active duck along its
     * spawn point's waypoint path. No-op if already running.
     */
    public void startPathFollowing() {
        if (pathTask != null) {
            return;
        }
        long interval = config.getPathCheckIntervalTicks();
        pathTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPaths, interval, interval);
    }

    /**
     * Stops the path-following task, if running.
     */
    public void stopPathFollowing() {
        if (pathTask != null) {
            pathTask.cancel();
            pathTask = null;
        }
    }

    /**
     * Checks every active duck: if it isn't currently walking towards a
     * waypoint (either just spawned or just arrived at the previous
     * one), sends it to the next one according to its spawn point's path
     * mode, at its own rolled speed.
     */
    private void tickPaths() {
        if (!config.isDuckAiEnabled()) {
            return;
        }

        for (Map.Entry<String, Set<UUID>> entry : activeBySpawnId.entrySet()) {
            SpawnPoint point = plugin.getSpawnPointManager().get(entry.getKey());
            if (point == null) {
                continue;
            }

            List<Location> route = point.route();
            if (route.size() <= 1) {
                continue; // no extra waypoints: the duck just stands at its spawn point
            }

            PathMode mode = point.effectivePathMode(config.getDefaultPathMode());

            for (UUID duckId : entry.getValue()) {
                Entity entity = plugin.getServer().getEntity(duckId);
                if (!(entity instanceof Mob duck) || duck.isDead()) {
                    continue;
                }

                PathState state = pathStateByDuck.computeIfAbsent(duckId, id -> new PathState());
                if (state.index >= route.size()) {
                    state.index = 0;
                }

                if (!duck.getPathfinder().hasPath()) {
                    advance(state, route.size(), mode);
                    double speed = duck.getPersistentDataContainer()
                            .getOrDefault(DuckKeys.speed(), PersistentDataType.DOUBLE, config.getMinDuckSpeed());
                    duck.getPathfinder().moveTo(route.get(state.index), speed);
                }
            }
        }
    }

    /**
     * Advances a duck's route index to its next target, according to the
     * spawn point's path mode.
     */
    private void advance(PathState state, int routeSize, PathMode mode) {
        switch (mode) {
            case PINGPONG -> {
                int next = state.index + state.direction;
                if (next >= routeSize) {
                    state.direction = -1;
                    next = Math.max(0, routeSize - 2);
                } else if (next < 0) {
                    state.direction = 1;
                    next = Math.min(routeSize - 1, 1);
                }
                state.index = next;
            }
            case STOP -> state.index = Math.min(state.index + 1, routeSize - 1);
            case LOOP -> state.index = (state.index + 1) % routeSize;
        }
    }

    /** Tracks a single duck's progress along its spawn point's route. */
    private static final class PathState {
        int index;
        int direction = 1; // only used by PathMode.PINGPONG
    }
}
