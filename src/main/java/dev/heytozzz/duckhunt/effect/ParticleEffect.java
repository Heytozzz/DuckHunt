package dev.heytozzz.duckhunt.effect;

import org.bukkit.Particle;

/**
 * A single particle effect to spawn: the particle type, how many
 * particles, the random per-axis spread around the location, and their
 * extra speed/data value (meaning depends on the particle, but for the
 * simple vanilla particles used here it's roughly "how fast they fly
 * outward").
 */
public record ParticleEffect(Particle particle, int count, double offsetX, double offsetY, double offsetZ,
                              double speed) {
}
