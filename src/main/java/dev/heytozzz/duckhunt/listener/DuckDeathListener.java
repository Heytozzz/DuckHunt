package dev.heytozzz.duckhunt.listener;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.BroadcastMode;
import dev.heytozzz.duckhunt.spawn.DuckKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Listens for duck (zombie) deaths, frees up its spawn point's capacity,
 * records a leaderboard kill (if the killer was far enough away) and
 * (if enabled) broadcasts the kill.
 */
public class DuckDeathListener implements Listener {

    private final DuckHuntPlugin plugin;

    public DuckDeathListener(DuckHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDuckDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) {
            return;
        }
        if (!zombie.getScoreboardTags().contains(DuckKeys.TAG_DUCK)) {
            return;
        }

        // Grab the killer before the chain is torn down; getKiller() only
        // reflects recent player damage and is unaffected by the removal.
        Player killer = zombie.getKiller();
        Location deathLocation = zombie.getLocation();

        // Belt-and-suspenders: on top of clearLootTable() set at spawn
        // time, make absolutely sure nothing drops or grants experience.
        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.getDuckSpawner().handleDeath(zombie);

        if (killer != null) {
            if (qualifiesForLeaderboard(killer, deathLocation)) {
                plugin.getLeaderboardManager().recordKill(killer);
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
