package dev.heytozzz.duckhunt.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable representation of a configured duck spawn point.
 */
public record SpawnPoint(String id, String worldName, double x, double y, double z, float yaw) {

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
}
