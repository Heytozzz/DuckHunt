package dev.heytozzz.duckhunt.leaderboard;

import java.util.UUID;

/**
 * A single player's spot on the duck-kill leaderboard: their last known
 * name (for display, in case they're offline), how many qualifying kills
 * they've landed, and their total points (faster ducks are worth more —
 * see {@link dev.heytozzz.duckhunt.config.ConfigManager#getPointsForSpeed(double)}).
 */
public record LeaderboardEntry(UUID uuid, String name, int kills, int points) {
}
