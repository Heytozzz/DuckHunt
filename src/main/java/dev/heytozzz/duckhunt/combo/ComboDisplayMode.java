package dev.heytozzz.duckhunt.combo;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How the per-kill "Combo: xN" indicator is shown to a player.
 */
public enum ComboDisplayMode {

    /** A normal chat message. */
    CHAT,
    /** The action bar (the same spot vanilla's saturation/status hints use). */
    ACTIONBAR,
    /** A {@code Title} with an invisible main line and the combo text as its subtitle. */
    TITLE,
    /** A short-lived floating text that drifts up and shrinks away right in front of the player. */
    FLOATING_TEXT;

    /**
     * Parses a config value like "actionbar" or "floating-text"
     * (hyphens/spaces are treated the same as underscores).
     *
     * @return the matching mode, or {@code null} if {@code raw} is null
     * or doesn't match any mode.
     */
    @Nullable
    public static ComboDisplayMode parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return ComboDisplayMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
