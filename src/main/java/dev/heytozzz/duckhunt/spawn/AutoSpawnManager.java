package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically calls {@link DuckSpawner#fillAll()} to keep every
 * configured spawn point filled with a duck.
 */
public class AutoSpawnManager {

    private final DuckHuntPlugin plugin;
    private BukkitTask task;

    public AutoSpawnManager(DuckHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    public void start(long intervalSeconds) {
        if (isRunning()) {
            return;
        }
        long ticks = Math.max(20L, intervalSeconds * 20L);
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> plugin.getDuckSpawner().fillAll(),
                ticks,
                ticks
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
