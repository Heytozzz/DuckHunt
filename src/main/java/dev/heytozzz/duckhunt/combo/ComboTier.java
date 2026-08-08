package dev.heytozzz.duckhunt.combo;

import dev.heytozzz.duckhunt.effect.ParticleEffect;
import org.jetbrains.annotations.Nullable;

/**
 * A single combo milestone: once a player's streak reaches
 * {@code threshold} kills, their leaderboard points get multiplied by
 * {@code pointsMultiplier} until the streak either climbs into the next
 * tier or expires. If {@code particle} is non-null, arrows shot while
 * this tier is active also trail that particle; a {@code null} particle
 * (config: {@code particle.particle: none}) means this tier grants the
 * points bonus without any visual trail — handy for a quiet first tier
 * that only "unlocks" the trail at a later, flashier one.
 */
public record ComboTier(int threshold, double pointsMultiplier, @Nullable ParticleEffect particle) {
}
