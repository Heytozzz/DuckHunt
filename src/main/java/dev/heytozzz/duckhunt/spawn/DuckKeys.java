package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import org.bukkit.NamespacedKey;

/**
 * Central place for the scoreboard tags and persistent-data keys used to
 * mark and identify the three entities that make up a "duck"
 * (zombie -> armor stand -> minecart).
 */
public final class DuckKeys {

    public static final String TAG_DUCK = "duckhunt_duck";
    public static final String TAG_STAND = "duckhunt_stand";
    public static final String TAG_CART = "duckhunt_cart";

    private static NamespacedKey groupKey;
    private static NamespacedKey spawnKey;

    private DuckKeys() {
    }

    public static void init(DuckHuntPlugin plugin) {
        groupKey = new NamespacedKey(plugin, "group");
        spawnKey = new NamespacedKey(plugin, "spawn");
    }

    /**
     * Key holding a random UUID shared by the zombie, armor stand and
     * minecart of a single duck. Used as a fallback to clean up leftover
     * parts if the passenger chain is ever broken before death.
     */
    public static NamespacedKey group() {
        return groupKey;
    }

    /**
     * Key (only set on the zombie) holding the id of the spawn point it
     * came from, so its slot can be freed up when it dies.
     */
    public static NamespacedKey spawn() {
        return spawnKey;
    }
}
