package dev.heytozzz.duckhunt.listener;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.spawn.DuckKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Listens for duck (zombie) deaths and cleans up the armor stand and
 * minecart it was riding on.
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

        // Belt-and-suspenders: on top of clearLootTable() set at spawn
        // time, make absolutely sure nothing drops or grants experience.
        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.getDuckSpawner().handleDeath(zombie);

        if (killer != null && plugin.getConfigManager().isBroadcastKill()) {
            plugin.getLangManager().broadcast("kill.broadcast",
                    Placeholder.unparsed("player", killer.getName()));
        }
    }
}
