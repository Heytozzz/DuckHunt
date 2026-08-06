package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles spawning and despawning of "duck" mob groups: a {@link Zombie}
 * riding an invisible {@link ArmorStand} riding a {@link RideableMinecart}.
 * Each spawn point can keep several ducks alive at once, up to its
 * configured capacity.
 */
public class DuckSpawner {

    private final DuckHuntPlugin plugin;
    private final ConfigManager config;

    // spawnId -> group UUIDs of the ducks currently alive at that point.
    private final Map<String, Set<UUID>> activeBySpawnId = new LinkedHashMap<>();

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

        World world = location.getWorld();
        UUID groupId = UUID.randomUUID();

        RideableMinecart cart = world.spawn(location, RideableMinecart.class, entity -> {
            entity.setInvulnerable(true);
            entity.setPersistent(true);
            entity.addScoreboardTag(DuckKeys.TAG_CART);
            tag(entity, groupId, null);
        });

        ArmorStand stand = world.spawn(location, ArmorStand.class, entity -> {
            entity.setInvisible(true);
            entity.setInvulnerable(true);
            entity.setSmall(true);
            entity.setBasePlate(false);
            entity.setMarker(false);
            entity.setSilent(true);
            entity.setPersistent(true);
            entity.addScoreboardTag(DuckKeys.TAG_STAND);
            tag(entity, groupId, null);
        });

        Zombie zombie = world.spawn(location, Zombie.class, entity -> {
            applyDuckStats(entity);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.addScoreboardTag(DuckKeys.TAG_DUCK);
            tag(entity, groupId, point.id());
        });

        // Mount the chain: zombie -> stand -> cart.
        cart.addPassenger(stand);
        stand.addPassenger(zombie);

        activeBySpawnId.computeIfAbsent(point.id(), id -> new LinkedHashSet<>()).add(groupId);
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

    private void applyDuckStats(Zombie zombie) {
        setAttribute(zombie, "max_health", config.getDuckHealth());
        setAttribute(zombie, "movement_speed", config.getDuckMovementSpeed());
        zombie.setHealth(config.getDuckHealth());
        zombie.setAI(config.isDuckAiEnabled());
        zombie.setSilent(config.isDuckSilent());
        zombie.setGlowing(config.isDuckGlowing());
        zombie.setCanPickupItems(false);
        zombie.setShouldBurnInDay(false);
        // No loot table at all: the duck must never drop anything.
        zombie.clearLootTable();
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

    private void tag(Entity entity, UUID groupId, String spawnId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(DuckKeys.group(), PersistentDataType.STRING, groupId.toString());
        if (spawnId != null) {
            pdc.set(DuckKeys.spawn(), PersistentDataType.STRING, spawnId);
        }
    }

    /**
     * Called when a duck (zombie) dies. Removes its armor stand and
     * minecart, frees up its spawn point slot, and (if enabled) instantly
     * spawns a replacement.
     */
    public void handleDeath(Zombie zombie) {
        boolean removedViaVehicle = false;

        Entity stand = zombie.getVehicle();
        if (stand != null) {
            Entity cart = stand.getVehicle();
            stand.remove();
            if (cart != null) {
                cart.remove();
            }
            removedViaVehicle = true;
        }

        PersistentDataContainer pdc = zombie.getPersistentDataContainer();
        String groupIdRaw = pdc.get(DuckKeys.group(), PersistentDataType.STRING);

        if (!removedViaVehicle && groupIdRaw != null) {
            // Fallback: the passenger chain was already broken for some
            // reason. Clean up any leftover part sharing this duck's
            // group id nearby.
            zombie.getNearbyEntities(4, 4, 4).stream()
                    .filter(nearby -> groupIdRaw.equals(nearby.getPersistentDataContainer()
                            .get(DuckKeys.group(), PersistentDataType.STRING)))
                    .forEach(Entity::remove);
        }

        String spawnId = pdc.get(DuckKeys.spawn(), PersistentDataType.STRING);
        if (spawnId == null) {
            return;
        }

        Set<UUID> active = activeBySpawnId.get(spawnId);
        if (active != null && groupIdRaw != null) {
            active.remove(UUID.fromString(groupIdRaw));
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
     * Removes every currently tracked duck plus any stray tagged entities
     * left over from a previous run (e.g. after a crash or a reload).
     */
    public void clearAll() {
        activeBySpawnId.clear();
        for (World world : plugin.getServer().getWorlds()) {
            world.getEntitiesByClass(Zombie.class).stream()
                    .filter(entity -> entity.getScoreboardTags().contains(DuckKeys.TAG_DUCK))
                    .forEach(Entity::remove);
            world.getEntitiesByClass(ArmorStand.class).stream()
                    .filter(entity -> entity.getScoreboardTags().contains(DuckKeys.TAG_STAND))
                    .forEach(Entity::remove);
            world.getEntitiesByClass(RideableMinecart.class).stream()
                    .filter(entity -> entity.getScoreboardTags().contains(DuckKeys.TAG_CART))
                    .forEach(Entity::remove);
        }
    }

    /**
     * Rebuilds the "active ducks per spawn point" tracking from ducks
     * already present in the world. Needed on startup, since ducks
     * spawned in a previous server session (they're persistent) would
     * otherwise be invisible to this session's in-memory tracking,
     * causing the plugin to spawn past the configured capacity.
     */
    public void reconcileFromWorld() {
        activeBySpawnId.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                if (!zombie.getScoreboardTags().contains(DuckKeys.TAG_DUCK)) {
                    continue;
                }
                PersistentDataContainer pdc = zombie.getPersistentDataContainer();
                String spawnId = pdc.get(DuckKeys.spawn(), PersistentDataType.STRING);
                String groupId = pdc.get(DuckKeys.group(), PersistentDataType.STRING);
                if (spawnId != null && groupId != null) {
                    activeBySpawnId.computeIfAbsent(spawnId, id -> new LinkedHashSet<>())
                            .add(UUID.fromString(groupId));
                }
            }
        }
    }
}
