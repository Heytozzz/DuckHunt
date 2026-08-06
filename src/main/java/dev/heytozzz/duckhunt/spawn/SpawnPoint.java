package dev.heytozzz.duckhunt.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable representation of a configured duck spawn point.
 *
 * @param amount how many ducks this point should keep alive at once, or
 *               {@code null} to fall back to the server-wide default
 *               ({@code spawn.default-amount} in config.yml).
 */
public record SpawnPoint(String id, String worldName, double x, double y, double z, float yaw, Integer amount) {

    /**
     * Resolves this spawn point to a live {@link Location}.
     *
     * @return the location, or {@code null} if the configured world isn't loaded.
     */
    @Nullable
    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, 0f);
    }

    /**
     * @param defaultAmount the server-wide default to fall back to.
     * @return this spawn point's own amount override if set and positive,
     * otherwise {@code defaultAmount}.
     */
    public int effectiveAmount(int defaultAmount) {
        return (amount != null && amount > 0) ? amount : defaultAmount;
    }
}

