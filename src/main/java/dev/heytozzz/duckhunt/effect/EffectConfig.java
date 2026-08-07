package dev.heytozzz.duckhunt.effect;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parses {@link EffectSet}s (sounds + particles) out of a YAML section,
 * shared between config.yml's server-wide defaults
 * ({@link dev.heytozzz.duckhunt.config.ConfigManager}) and each spawn
 * point's own override in spawnpoints.yml
 * ({@link dev.heytozzz.duckhunt.config.SpawnPointManager}).
 *
 * <p>Expected shape, both lists optional and independent of each other:
 * <pre>
 * sounds:
 *   - sound: entity.chicken.egg   # vanilla shorthand or "namespace:key" custom sound
 *     volume: 1.0
 *     pitch: 1.2
 * particles:
 *   - particle: poof              # org.bukkit.Particle name, case-insensitive
 *     count: 8
 *     offset-x: 0.3
 *     offset-y: 0.3
 *     offset-z: 0.3
 *     speed: 0.0
 * </pre>
 */
public final class EffectConfig {

    private EffectConfig() {
    }

    /**
     * @param section the section to read "sounds"/"particles" from.
     * @return the parsed effect set, or {@code null} if {@code section}
     * is {@code null} (meaning "not configured here at all", as opposed
     * to configured-but-empty).
     */
    @Nullable
    public static EffectSet parse(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) {
            return null;
        }
        return new EffectSet(parseSounds(section, logger), parseParticles(section, logger));
    }

    private static List<SoundEffect> parseSounds(ConfigurationSection section, Logger logger) {
        List<SoundEffect> sounds = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("sounds")) {
            Object soundValue = entry.get("sound");
            if (!(soundValue instanceof String soundKey) || soundKey.isBlank()) {
                logger.warning("Skipping a sound effect with a missing/blank 'sound' key.");
                continue;
            }
            float volume = toFloat(entry.get("volume"), 1.0f);
            float pitch = toFloat(entry.get("pitch"), 1.0f);
            sounds.add(new SoundEffect(soundKey, volume, pitch));
        }
        return sounds;
    }

    private static List<ParticleEffect> parseParticles(ConfigurationSection section, Logger logger) {
        List<ParticleEffect> particles = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("particles")) {
            Object typeValue = entry.get("particle");
            if (!(typeValue instanceof String typeName) || typeName.isBlank()) {
                logger.warning("Skipping a particle effect with a missing/blank 'particle' key.");
                continue;
            }
            Particle particle;
            try {
                particle = Particle.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping particle effect: '" + typeName + "' isn't a valid particle type.");
                continue;
            }
            int count = toInt(entry.get("count"), 1);
            double offsetX = toDouble(entry.get("offset-x"), 0.3);
            double offsetY = toDouble(entry.get("offset-y"), 0.3);
            double offsetZ = toDouble(entry.get("offset-z"), 0.3);
            double speed = toDouble(entry.get("speed"), 0.0);
            particles.add(new ParticleEffect(particle, count, offsetX, offsetY, offsetZ, speed));
        }
        return particles;
    }

    private static float toFloat(@Nullable Object value, float defaultValue) {
        return value instanceof Number number ? number.floatValue() : defaultValue;
    }

    private static int toInt(@Nullable Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static double toDouble(@Nullable Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }
}
