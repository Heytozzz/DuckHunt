package dev.heytozzz.duckhunt.event;

/**
 * One player's tally within a single event.
 */
public record EventScore(java.util.UUID uuid, String name, int points) {
}
