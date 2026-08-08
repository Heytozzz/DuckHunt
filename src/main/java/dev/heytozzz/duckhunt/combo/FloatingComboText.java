package dev.heytozzz.duckhunt.combo;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns a short-lived, player-only floating text right in front of a
 * player — the "floating-text" {@code combo.display.mode}. It's a
 * {@link TextDisplay} that drifts upward and shrinks away over
 * "combo.display.floating-text.duration-ticks", then removes itself; a
 * fresh one appearing lands with a random left/right/up/down offset each
 * time (within "combo.display.floating-text.spread") so a fast streak's
 * texts don't perfectly overlap.
 *
 * <p>Only the player it's shown for can see it (matching the other
 * display modes, which are all personal too) via
 * {@code setVisibleByDefault(false)} + {@link Player#showEntity}.
 */
public final class FloatingComboText {

    private FloatingComboText() {
    }

    public static void show(DuckHuntPlugin plugin, Player player, Component text) {
        ConfigManager config = plugin.getConfigManager();
        double distance = config.getComboFloatingTextDistance();
        double spread = config.getComboFloatingTextSpread();
        double rise = config.getComboFloatingTextRise();
        int durationTicks = Math.max(1, config.getComboFloatingTextDurationTicks());

        Location spawnLocation = randomizedSpawnLocation(player, distance, spread);

        TextDisplay display = player.getWorld().spawn(spawnLocation, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setShadowed(true);
            entity.setSeeThrough(true);
            entity.setPersistent(false);
            entity.setInterpolationDuration(0);
            // Personal-only: hidden from everyone by default, then
            // explicitly revealed to just the player below.
            entity.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);

        // Drives its own up-drift/shrink animation directly (rather than
        // relying on Display's client-side interpolation, which doesn't
        // cover text opacity anyway) and removes it once done.
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > durationTicks || !display.isValid()) {
                    display.remove();
                    cancel();
                    return;
                }
                float progress = (float) ticks / durationTicks;
                float scale = Math.max(0.0f, 1.0f - progress);
                Vector3f translation = new Vector3f(0f, (float) (progress * rise), 0f);
                Vector3f scaleVector = new Vector3f(scale, scale, scale);
                display.setTransformation(new Transformation(
                        translation, new AxisAngle4f(0f, 0f, 0f, 1f), scaleVector, new AxisAngle4f(0f, 0f, 0f, 1f)));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * A point {@code distance} blocks in front of the player's eyes,
     * offset by a random amount (up to {@code spread} blocks each way)
     * left/right along their view and up/down vertically.
     */
    private static Location randomizedSpawnLocation(Player player, double distance, double spread) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) {
            right = new Vector(1, 0, 0); // looking straight up/down: any "right" works
        } else {
            right.normalize();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double lateral = (random.nextDouble() - 0.5) * 2 * spread;
        double vertical = (random.nextDouble() - 0.5) * 2 * spread;

        return eye.clone()
                .add(direction.multiply(distance))
                .add(right.multiply(lateral))
                .add(0, vertical, 0);
    }
}
