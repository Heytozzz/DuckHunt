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
import java.util.Map;
import java.util.UUID;

/**
 * Handles spawning and despawning of "duck" mob groups: a {@link Zombie}
 * riding an invisible {@link ArmorStand} riding a {@link RideableMinecart}.
 */
public class DuckSpawner {

    private final DuckHuntPlugin plugin;
    private final ConfigManager config;

    // spawnId -> groupId of the duck currently occupying that spawn point.
    private final Map<String, UUID> activeBySpawnId = new LinkedHashMap<>();

    public DuckSpawner(DuckHuntPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public boolean isOccupied(String spawnId) {
        return activeBySpawnId.containsKey(spawnId);
    }

    /**
     * Rebuilds the "occupied spawn points" tracking from ducks already
     * present in the world. Needed on startup, since ducks spawned in a
     * previous server session (they're persistent) would otherwise be
     * invisible to this session's in-memory tracking, causing duplicate
     * spawns at the same point.
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
                    activeBySpawnId.put(spawnId, UUID.fromString(groupId));
                }
            }
        }
    }

    /**
     * Spawns a duck at the given spawn point, unless it's already occupied
     * or its world isn't loaded.
     *
     * @return true if a duck was actually spawned.
     */
    public boolean spawn(SpawnPoint point) {
        if (isOccupied(point.id())) {
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

        activeBySpawnId.put(point.id(), groupId);
        return true;
    }

    /**
     * Spawns a duck on every configured, currently-empty spawn point.
     *
     * @return how many ducks were actually spawned.
     */
    public int spawnAll() {
        int spawned = 0;
        for (SpawnPoint point : config.getSpawnPoints().values()) {
            if (spawn(point)) {
                spawned++;
            }
        }
        return spawned;
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
     * minecart, then frees its spawn point so it can respawn later.
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

        String groupId = zombie.getPersistentDataContainer()
                .get(DuckKeys.group(), PersistentDataType.STRING);

        if (!removedViaVehicle && groupId != null) {
            // Fallback: the passenger chain was already broken for some
            // reason (another plugin, a weird edge case, etc). Clean up
            // any leftover part sharing this duck's group id nearby.
            zombie.getNearbyEntities(4, 4, 4).stream()
                    .filter(nearby -> groupId.equals(nearby.getPersistentDataContainer()
                            .get(DuckKeys.group(), PersistentDataType.STRING)))
                    .forEach(Entity::remove);
        }

        String spawnId = zombie.getPersistentDataContainer()
                .get(DuckKeys.spawn(), PersistentDataType.STRING);
        if (spawnId != null) {
            activeBySpawnId.remove(spawnId);
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
}
