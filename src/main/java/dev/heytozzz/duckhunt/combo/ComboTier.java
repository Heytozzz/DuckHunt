package dev.heytozzz.duckhunt.combo;

import dev.heytozzz.duckhunt.effect.ParticleEffect;

/**
 * A single combo milestone: once a player's streak reaches
 * {@code threshold} kills, their arrows start trailing {@code particle}
 * and their leaderboard points get multiplied by {@code pointsMultiplier}
 * — until the streak either climbs into the next tier or expires.
 */
public record ComboTier(int threshold, double pointsMultiplier, ParticleEffect particle) {
}
