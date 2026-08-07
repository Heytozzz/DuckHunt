package dev.heytozzz.duckhunt.event;

import dev.heytozzz.duckhunt.config.BroadcastMode;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Logger;

/**
 * Parses an {@link EventScope} out of a YAML section shaped like:
 * <pre>
 * mode: radius     # global | radius | world
 * radius: 50.0
 * worlds: [world, world_nether]
 * </pre>
 * Used for each of "event.start", "event.countdown" and "event.winner"
 * in config.yml.
 */
public final class EventScopeConfig {

    private EventScopeConfig() {
    }

    public static EventScope parse(@Nullable ConfigurationSection section, String label,
                                    EventScope fallback, Logger logger) {
        if (section == null) {
            return fallback;
        }
        BroadcastMode mode = BroadcastMode.parse(section.getString("mode"));
        if (mode == null) {
            logger.warning("Invalid or missing 'mode' under '" + label + "', falling back to global.");
            mode = BroadcastMode.GLOBAL;
        }
        double radius = Math.max(0.0, section.getDouble("radius", 50.0));
        List<String> worlds = section.getStringList("worlds");
        return new EventScope(mode, radius, worlds);
    }
}
