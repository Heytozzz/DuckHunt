package dev.heytozzz.duckhunt.effect;

/**
 * A single sound to play, given as a raw sound key rather than a
 * {@link org.bukkit.Sound} enum constant, so it accepts both vanilla
 * shorthand keys (e.g. "entity.chicken.egg", resolved as
 * "minecraft:entity.chicken.egg") and custom resource-pack keys (e.g.
 * "htz:duck_quack"). The client simply stays silent if it doesn't have
 * the referenced sound.
 */
public record SoundEffect(String sound, float volume, float pitch) {
}
