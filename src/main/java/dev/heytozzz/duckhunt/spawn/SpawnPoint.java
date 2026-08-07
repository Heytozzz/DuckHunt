package dev.heytozzz.duckhunt.spawn;

import dev.heytozzz.duckhunt.effect.EffectSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable representation of a configured duck spawn point.
 *
 * @param amount        how many ducks this point should keep alive at once, or
 *                      {@code null} to fall back to the server-wide default
 *                      ({@code spawn.default-amount} in config.yml).
 * @param path          ordered waypoints (added via "/duckhunt admin spawner <id> path add")
 *                      that every duck spawned here patrols, in addition to
 *                      this point's own location as the starting waypoint.
 * @param pathMode      what a duck does after reaching the last waypoint, or
 *                      {@code null} to fall back to the server-wide default
 *                      ({@code spawn.default-path-mode} in config.yml).
 * @param spawnEffects  sounds/particles played when a duck spawns here, or
 *                      {@code null} to fall back to config.yml's "effects.spawn".
 *                      Hand-edited in spawnpoints.yml; see its template for the format.
 * @param deathEffects  sounds/particles played when a duck spawned here is
 *                      caught, or {@code null} to fall back to config.yml's
 *                      "effects.death". Hand-edited in spawnpoints.yml.
 */
public record SpawnPoint(String id, String worldName, double x, double y, double z, float yaw, Integer amount,
                          List<Waypoint> path, @Nullable PathMode pathMode, @Nullable EffectSet spawnEffects,
                          @Nullable EffectSet deathEffects) {

    public SpawnPoint {
        path = (path == null) ? List.of() : List.copyOf(path);
    }

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

    /**
     * @param defaultMode the server-wide default to fall back to.
     * @return this spawn point's own path-mode override if set, otherwise
     * {@code defaultMode}.
     */
    public PathMode effectivePathMode(PathMode defaultMode) {
        return pathMode != null ? pathMode : defaultMode;
    }

    /**
     * @param defaultEffects the server-wide default ("effects.spawn" in
     *                       config.yml) to fall back to.
     * @return this spawn point's own spawn-effects override if set,
     * otherwise {@code defaultEffects}.
     */
    public EffectSet effectiveSpawnEffects(EffectSet defaultEffects) {
        return spawnEffects != null ? spawnEffects : defaultEffects;
    }

    /**
     * @param defaultEffects the server-wide default ("effects.death" in
     *                       config.yml) to fall back to.
     * @return this spawn point's own death-effects override if set,
     * otherwise {@code defaultEffects}.
     */
    public EffectSet effectiveDeathEffects(EffectSet defaultEffects) {
        return deathEffects != null ? deathEffects : defaultEffects;
    }

    /**
     * The full patrol route: this spawn point's own location followed by
     * every waypoint added to it (in the order they were added), resolved
     * against the live world.
     *
     * @return the route (always at least size 1 if the world is loaded),
     * or an empty list if the configured world isn't loaded.
     */
    public List<Location> route() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return List.of();
        }
        List<Location> route = new ArrayList<>(path.size() + 1);
        route.add(new Location(world, x, y, z));
        for (Waypoint waypoint : path) {
            route.add(new Location(world, waypoint.x(), waypoint.y(), waypoint.z()));
        }
        return route;
    }
}
