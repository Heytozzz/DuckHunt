package dev.heytozzz.duckhunt.effect;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Plays an {@link EffectSet} (every one of its sounds and particles) at
 * a location.
 */
public final class EffectPlayer {

    private EffectPlayer() {
    }

    public static void play(Location location, EffectSet effects) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (SoundEffect sound : effects.sounds()) {
            // The String overload (rather than the Sound enum one) is what
            // lets this play both vanilla shorthand keys and custom
            // resource-pack sound keys the same way.
            world.playSound(location, sound.sound(), sound.volume(), sound.pitch());
        }
        for (ParticleEffect particle : effects.particles()) {
            world.spawnParticle(particle.particle(), location, particle.count(),
                    particle.offsetX(), particle.offsetY(), particle.offsetZ(), particle.speed());
        }
    }
}
