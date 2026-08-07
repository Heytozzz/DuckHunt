package dev.heytozzz.duckhunt.event;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses simple duration strings like "10m", "90s" or "1h30m" (any
 * sequence of "<number><unit>" tokens, units h/m/s, case-insensitive)
 * into a total number of seconds. Used by "/duckhunt admin event start".
 */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)([hHmMsS])");

    private DurationParser() {
    }

    /**
     * @return the total duration in seconds, or {@code null} if
     * {@code raw} is null/blank, contains anything other than valid
     * "<number><unit>" tokens back-to-back, or resolves to zero.
     */
    @Nullable
    public static Long parseSeconds(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        Matcher matcher = TOKEN.matcher(trimmed);

        long totalSeconds = 0;
        int consumedUpTo = 0;
        boolean matchedAnything = false;

        while (matcher.find()) {
            if (matcher.start() != consumedUpTo) {
                return null; // stray characters between/before tokens
            }
            matchedAnything = true;
            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return null;
            }
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            totalSeconds += switch (unit) {
                case 'h' -> value * 3600;
                case 'm' -> value * 60;
                default -> value; // 's'
            };
            consumedUpTo = matcher.end();
        }

        if (!matchedAnything || consumedUpTo != trimmed.length() || totalSeconds <= 0) {
            return null;
        }
        return totalSeconds;
    }
}
