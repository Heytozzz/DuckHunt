package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
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

/**
 * Handles spawning and despawning of "ducks" (plain zombies, tagged and
 * stat-adjusted) and, when enabled, walks each one along its spawn
 * point's waypoint path using real pathfinding AI. Each spawn point can
 * keep several ducks alive at once, up to its configured capacity, all
 * patrolling the same path.
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
     * at capacity or its world isn't loaded.
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

        Zombie zombie = location.getWorld().spawn(location, Zombie.class, entity -> {
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.addScoreboardTag(DuckKeys.TAG_DUCK);
            entity.getPersistentDataContainer().set(DuckKeys.spawn(), PersistentDataType.STRING, point.id());
        });

        applyDuckBehavior(zombie);

        activeBySpawnId.computeIfAbsent(point.id(), id -> new LinkedHashSet<>()).add(zombie.getUniqueId());
        pathStateByDuck.put(zombie.getUniqueId(), new PathState());
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
     * Applies stats and behaviour to a duck: health, speed attribute,
     * visibility flags, and (if path-following is enabled) wipes its
     * default zombie AI goals so nothing but our own waypoint task moves
     * or targets it.
     */
    private void applyDuckBehavior(Zombie zombie) {
        setAttribute(zombie, "max_health", config.getDuckHealth());
        setAttribute(zombie, "movement_speed", config.getDuckMovementSpeed());
        zombie.setHealth(config.getDuckHealth());
        zombie.setSilent(config.isDuckSilent());
        zombie.setGlowing(config.isDuckGlowing());
        zombie.setCanPickupItems(false);
        zombie.setShouldBurnInDay(false);
        // No loot table at all: the duck must never drop anything.
        zombie.clearLootTable();

        boolean followPath = config.isDuckAiEnabled();
        zombie.setAI(followPath);
        if (followPath) {
            // Wipes vanilla zombie behaviour (attacking, wandering,
            // looking around...) so the only thing driving this duck is
            // the moveTo() calls issued from tickPaths().
            Bukkit.getMobGoals().removeAllGoals(zombie);
        }
    }

    private void setAttribute(Zombie zombie, String key, double value) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = zombie.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Called when a duck (zombie) dies. Frees up its spawn point slot and
     * (if enabled) instantly spawns a replacement.
     */
    public void handleDeath(Zombie zombie) {
        UUID duckId = zombie.getUniqueId();
        pathStateByDuck.remove(duckId);

        String spawnId = zombie.getPersistentDataContainer().get(DuckKeys.spawn(), PersistentDataType.STRING);
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
     * Removes every currently tracked duck plus any stray tagged zombies
     * left over from a previous run (e.g. after a crash or a reload).
     */
    public void clearAll() {
        activeBySpawnId.clear();
        pathStateByDuck.clear();
        for (World world : plugin.getServer().getWorlds()) {
            world.getEntitiesByClass(Zombie.class).stream()
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
     * server restart.
     */
    public void reconcileFromWorld() {
        activeBySpawnId.clear();
        pathStateByDuck.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                if (!zombie.getScoreboardTags().contains(DuckKeys.TAG_DUCK)) {
                    continue;
                }
                String spawnId = zombie.getPersistentDataContainer().get(DuckKeys.spawn(), PersistentDataType.STRING);
                if (spawnId == null) {
                    continue;
                }
                activeBySpawnId.computeIfAbsent(spawnId, id -> new LinkedHashSet<>()).add(zombie.getUniqueId());
                pathStateByDuck.put(zombie.getUniqueId(), new PathState());
                applyDuckBehavior(zombie);
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
     * mode.
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
                if (!(entity instanceof Zombie zombie) || zombie.isDead()) {
                    continue;
                }

                PathState state = pathStateByDuck.computeIfAbsent(duckId, id -> new PathState());
                if (state.index >= route.size()) {
                    state.index = 0;
                }

                if (!zombie.getPathfinder().hasPath()) {
                    advance(state, route.size(), mode);
                    zombie.getPathfinder().moveTo(route.get(state.index), config.getDuckMovementSpeed());
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
