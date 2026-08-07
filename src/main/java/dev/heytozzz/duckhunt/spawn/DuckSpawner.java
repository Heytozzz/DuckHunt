package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.ConfigManager;
import dev.heytozzz.duckhunt.effect.EffectPlayer;
import dev.heytozzz.duckhunt.effect.ParticleEffect;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Iterator;
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
 * Also plays each spawn point's spawn sound/particle effects (falling
 * back to config.yml's defaults) the moment a duck appears, and has a
 * small chance ("duck.rare") of making that duck rare: always glowing,
 * faster, worth more points, and trailing its own particle effect.
 */
public class DuckSpawner {

    // Scoreboard team ducks are placed in purely so COLLISION_RULE can
    // disable entity-to-entity pushing between them. Unlike
    // Entity#setCollidable(false), a team collision rule only affects
    // pushing — it doesn't make the duck invisible to projectiles, so
    // arrows still register hits normally.
    private static final String DUCK_TEAM_NAME = "duckhunt_ducks";

    private final DuckHuntPlugin plugin;
    private final ConfigManager config;

    // spawnId -> UUIDs of the ducks currently alive at that point.
    private final Map<String, Set<UUID>> activeBySpawnId = new LinkedHashMap<>();

    // duckId -> where it currently is along its spawn point's route.
    private final Map<UUID, PathState> pathStateByDuck = new HashMap<>();

    // Ducks that rolled "rare" this session, purely to drive their
    // particle trail — their points multiplier is stored on the duck
    // itself (see DuckKeys#pointsMultiplier()), so scoring doesn't
    // depend on this in-memory set.
    private final Set<UUID> rareDucks = new LinkedHashSet<>();

    private BukkitTask pathTask;
    private BukkitTask rareParticleTask;

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

        // Small chance of this duck being "rare": faster, worth more
        // points, and visually distinct (glowing + a particle trail).
        boolean rare = config.isRareDuckEnabled()
                && ThreadLocalRandom.current().nextDouble() < config.getRareDuckChance();
        if (rare) {
            speed *= config.getRareSpeedMultiplier();
        }
        double pointsMultiplier = rare ? config.getRarePointsMultiplier() : 1.0;

        // "speed" above is reassigned when rare, so it isn't effectively
        // final; capture a final copy for the spawn lambda instead.
        double finalSpeed = speed;

        Mob duck = spawnMob(location, mobClass, entity -> {
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.addScoreboardTag(DuckKeys.TAG_DUCK);
            entity.getPersistentDataContainer().set(DuckKeys.spawn(), PersistentDataType.STRING, point.id());
            entity.getPersistentDataContainer().set(DuckKeys.speed(), PersistentDataType.DOUBLE, finalSpeed);
            entity.getPersistentDataContainer()
                    .set(DuckKeys.pointsMultiplier(), PersistentDataType.DOUBLE, pointsMultiplier);
        });

        applyDuckBehavior(duck, speed);
        if (rare) {
            // Always glowing regardless of "duck.glowing", so it stands
            // out from a normal duck even with that setting off.
            duck.setGlowing(true);
            rareDucks.add(duck.getUniqueId());
        }
        EffectPlayer.play(location, point.effectiveSpawnEffects(config.getDefaultSpawnEffects()));

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
     * speed attribute, visibility flags, disabled entity-to-entity
     * collision (so faster ducks can freely overtake slower ones instead
     * of pushing into them, without affecting projectile hits), and (if
     * path-following is enabled) wipes its default AI goals so nothing
     * but our own waypoint task moves or targets it.
     */
    private void applyDuckBehavior(Mob duck, double speed) {
        setAttribute(duck, "max_health", config.getDuckHealth());
        setAttribute(duck, "movement_speed", speed);
        duck.setHealth(config.getDuckHealth());
        duck.setSilent(config.isDuckSilent());
        duck.setGlowing(config.isDuckGlowing());
        duck.setCanPickupItems(false);
        // Adds the duck to a team with COLLISION_RULE.NEVER instead of
        // Entity#setCollidable(false): that method also makes the entity
        // invisible to projectiles, which would stop arrows from hitting it.
        ensureDuckTeam().addEntity(duck);
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
     * Gets (creating if needed) the scoreboard team every duck is placed
     * in to disable entity-to-entity collision between them via
     * {@link Team.Option#COLLISION_RULE}. Teams registered through the
     * API aren't persisted across restarts, so this is safe to call on
     * every spawn/reconcile — it just recreates the team the first time
     * it's needed each server run.
     */
    private Team ensureDuckTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(DUCK_TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(DUCK_TEAM_NAME);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        return team;
    }

    /**
     * Called when a duck dies. Frees up its spawn point slot, removes it
     * from the no-collision team, and (if enabled) instantly spawns a
     * replacement.
     */
    public void handleDeath(Mob duck) {
        UUID duckId = duck.getUniqueId();
        pathStateByDuck.remove(duckId);
        rareDucks.remove(duckId);

        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(DUCK_TEAM_NAME);
        if (team != null) {
            team.removeEntity(duck);
        }

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
     * left over from a previous run (e.g. after a crash). Called on
     * every plugin enable (see the class-level note on why ducks aren't
     * carried over across a restart instead), plus available as
     * "/duckhunt admin clear".
     */
    public void clearAll() {
        activeBySpawnId.clear();
        pathStateByDuck.clear();
        rareDucks.clear();
        for (World world : plugin.getServer().getWorlds()) {
            world.getEntitiesByClass(Mob.class).stream()
                    .filter(entity -> entity.getScoreboardTags().contains(DuckKeys.TAG_DUCK))
                    .forEach(Entity::remove);
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
     * Starts the repeating task that spawns a rare duck's particle trail
     * ("duck.rare.particle"/"duck.rare.particle-interval-ticks"). No-op
     * if already running.
     */
    public void startRareDuckEffects() {
        if (rareParticleTask != null) {
            return;
        }
        long interval = config.getRareParticleIntervalTicks();
        rareParticleTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tickRareParticles, interval, interval);
    }

    /**
     * Stops the rare-duck particle-trail task, if running.
     */
    public void stopRareDuckEffects() {
        if (rareParticleTask != null) {
            rareParticleTask.cancel();
            rareParticleTask = null;
        }
    }

    /**
     * Spawns every currently-alive rare duck's trailing particle at its
     * location. Stray entries (the duck died some other way, or its
     * chunk unloaded and it can no longer be resolved) are pruned here
     * as a safety net, on top of the cleanup already done in
     * {@link #handleDeath}/{@link #clearAll}.
     */
    private void tickRareParticles() {
        if (rareDucks.isEmpty()) {
            return;
        }
        ParticleEffect particle = config.getRareDuckParticle();
        Iterator<UUID> iterator = rareDucks.iterator();
        while (iterator.hasNext()) {
            UUID duckId = iterator.next();
            Entity entity = plugin.getServer().getEntity(duckId);
            if (!(entity instanceof Mob duck) || duck.isDead()) {
                iterator.remove();
                continue;
            }
            World world = duck.getWorld();
            Location center = duck.getLocation().add(0, duck.getHeight() / 2.0, 0);
            world.spawnParticle(particle.particle(), center, particle.count(),
                    particle.offsetX(), particle.offsetY(), particle.offsetZ(), particle.speed());
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
