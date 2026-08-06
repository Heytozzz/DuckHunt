package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import org.bukkit.NamespacedKey;

/**
 * Central place for the scoreboard tag and persistent-data key used to
 * mark and identify a "duck" (a single zombie patrolling its spawn
 * point's waypoint path).
 */
public final class DuckKeys {

    public static final String TAG_DUCK = "duckhunt_duck";

    private static NamespacedKey spawnKey;

    private DuckKeys() {
    }

    public static void init(DuckHuntPlugin plugin) {
        spawnKey = new NamespacedKey(plugin, "spawn");
    }

    /**
     * Key (set on every duck) holding the id of the spawn point it came
     * from, so its slot can be freed up when it dies and so the path it
     * should patrol can be looked up.
     */
    public static NamespacedKey spawn() {
        return spawnKey;
    }
}
