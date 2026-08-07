package dev.heytozzz.duckhunt.config;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How a message (a duck-kill, or an event announcement) is broadcast to
 * players.
 */
public enum BroadcastMode {

    /** Sent to every online player, regardless of distance. */
    GLOBAL,
    /** Only sent to players within a configurable radius of a point. */
    RADIUS,
    /** Only sent to players currently in one of a configurable list of worlds. */
    WORLD;

    /**
     * Parses a config/command value like "global" or "Radius".
     *
     * @return the matching mode, or {@code null} if {@code raw} is null or
     * doesn't match any mode.
     */
    @Nullable
    public static BroadcastMode parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return BroadcastMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
