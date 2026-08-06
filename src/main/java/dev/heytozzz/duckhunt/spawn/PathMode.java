package dev.heytozzz.duckhunt.spawn;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What a duck does after reaching the last waypoint of its patrol path.
 */
public enum PathMode {

    /** Goes back to the first waypoint (its spawn point) and starts over. */
    LOOP,
    /** Walks the path backwards until it reaches the start, then forwards again. */
    PINGPONG,
    /** Stays put once it reaches the last waypoint. */
    STOP;

    /**
     * Parses a config/command value like "loop" or "PingPong".
     *
     * @return the matching mode, or {@code null} if {@code raw} is null or
     * doesn't match any mode.
     */
    @Nullable
    public static PathMode parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PathMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
