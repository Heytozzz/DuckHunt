package dev.heytozzz.duckhunt.listener;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.BroadcastMode;
import dev.heytozzz.duckhunt.effect.EffectPlayer;
import dev.heytozzz.duckhunt.effect.EffectSet;
import dev.heytozzz.duckhunt.spawn.DuckKeys;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Listens for duck deaths, frees up its spawn point's capacity, plays its
 * elimination sound/particle effects, records a leaderboard kill (if the
 * killer was far enough away) worth points based on how fast the duck
 * was (multiplied further if it happened to be a rare duck), feeds those
 * same points into that spawn point's active event (if any), and (if
 * enabled) broadcasts the kill.
 */
public class DuckDeathListener implements Listener {

    private final DuckHuntPlugin plugin;

    public DuckDeathListener(DuckHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDuckDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Mob duck)) {
            return;
        }
        if (!duck.getScoreboardTags().contains(DuckKeys.TAG_DUCK)) {
            return;
        }

        // Grab the killer before the chain is torn down; getKiller() only
        // reflects recent player damage and is unaffected by the removal.
        Player killer = duck.getKiller();
        Location deathLocation = duck.getLocation();
        double speed = duck.getPersistentDataContainer()
                .getOrDefault(DuckKeys.speed(), PersistentDataType.DOUBLE, plugin.getConfigManager().getMinDuckSpeed());
        double pointsMultiplier = duck.getPersistentDataContainer()
                .getOrDefault(DuckKeys.pointsMultiplier(), PersistentDataType.DOUBLE, 1.0);
        String spawnId = duck.getPersistentDataContainer().get(DuckKeys.spawn(), PersistentDataType.STRING);

        // Belt-and-suspenders: on top of clearLootTable() set at spawn
        // time, make absolutely sure nothing drops or grants experience.
        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.getDuckSpawner().handleDeath(duck);
        EffectPlayer.play(deathLocation, resolveDeathEffects(spawnId));

        if (killer != null) {
            if (qualifiesForLeaderboard(killer, deathLocation)) {
                int points = (int) Math.round(plugin.getConfigManager().getPointsForSpeed(speed) * pointsMultiplier);
                int total = plugin.getLeaderboardManager().recordKill(killer, points);
                plugin.getLangManager().send(killer, "top.points-earned",
                        Placeholder.unparsed("points", String.valueOf(points)),
                        Placeholder.unparsed("total", String.valueOf(total)));

                if (spawnId != null) {
                    plugin.getEventManager().addPoints(spawnId, killer, points);
                }
            }

            if (plugin.getConfigManager().isBroadcastEnabled()) {
                broadcastKill(killer, deathLocation);
            }
        }
    }

    /**
     * A kill only counts towards the leaderboard if the killer was at
     * least "leaderboard.min-kill-distance" blocks away from the duck at
     * the moment of death (same world required).
     */
    private boolean qualifiesForLeaderboard(Player killer, Location deathLocation) {
        if (!killer.getWorld().equals(deathLocation.getWorld())) {
            return false;
        }
        return killer.getLocation().distance(deathLocation) >= plugin.getConfigManager().getMinKillDistance();
    }

    /**
     * The death sounds/particles to play for a duck: its spawn point's
     * own "effects.death" override if it has one, otherwise config.yml's
     * server-wide default.
     */
    private EffectSet resolveDeathEffects(@Nullable String spawnId) {
        EffectSet defaultEffects = plugin.getConfigManager().getDefaultDeathEffects();
        if (spawnId == null) {
            return defaultEffects;
        }
        SpawnPoint point = plugin.getSpawnPointManager().get(spawnId);
        return point != null ? point.effectiveDeathEffects(defaultEffects) : defaultEffects;
    }

    private void broadcastKill(Player killer, Location deathLocation) {
        BroadcastMode mode = plugin.getConfigManager().getBroadcastMode();
        if (mode == BroadcastMode.RADIUS) {
            double radius = plugin.getConfigManager().getBroadcastRadius();
            plugin.getLangManager().broadcastNear(deathLocation, radius, "kill.broadcast",
                    Placeholder.unparsed("player", killer.getName()));
        } else {
            plugin.getLangManager().broadcast("kill.broadcast",
                    Placeholder.unparsed("player", killer.getName()));
        }
    }
}
