package dev.heytozzz.duckhunt.leaderboard;

import java.util.UUID;

/**
 * A single player's spot on the duck-kill leaderboard: their last known
 * name (for display, in case they're offline) and how many qualifying
 * kills they've landed.
 */
public record LeaderboardEntry(UUID uuid, String name, int kills) {
}
