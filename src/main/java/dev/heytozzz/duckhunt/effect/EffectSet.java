package dev.heytozzz.duckhunt.effect;

import java.util.List;

/**
 * A bundle of sounds and particles played together for one event (a
 * duck spawning, or a duck being caught). Both lists can hold any
 * number of entries — e.g. two sounds and a particle can all play at
 * once for the same event.
 */
public record EffectSet(List<SoundEffect> sounds, List<ParticleEffect> particles) {

    public static final EffectSet EMPTY = new EffectSet(List.of(), List.of());

    public EffectSet {
        sounds = (sounds == null) ? List.of() : List.copyOf(sounds);
        particles = (particles == null) ? List.of() : List.copyOf(particles);
    }
}
