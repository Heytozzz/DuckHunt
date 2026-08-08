package dev.heytozzz.duckhunt.listener;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * Feeds arrow shots/landings into {@link dev.heytozzz.duckhunt.combo.ComboManager},
 * which is what actually decides whether a given arrow gets a particle
 * trail (based on the shooter's current combo tier) and stops tracking
 * it once it lands.
 */
public class ComboArrowListener implements Listener {

    private final DuckHuntPlugin plugin;

    public ComboArrowListener(DuckHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArrowShot(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        plugin.getComboManager().onArrowShot(arrow, shooter);
    }

    @EventHandler
    public void onArrowLanded(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        plugin.getComboManager().onArrowLanded(arrow.getUniqueId());
    }
}
