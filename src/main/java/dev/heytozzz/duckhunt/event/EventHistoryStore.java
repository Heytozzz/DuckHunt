package dev.heytozzz.duckhunt.event;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Records every finished event to its own file ("events.yml"), keyed by
 * the date/time it started plus its spawn point id, so past events stay
 * browsable by date. Written once per event, when it ends.
 */
public class EventHistoryStore {

    private static final DateTimeFormatter KEY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern UNSAFE_KEY_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");

    private final DuckHuntPlugin plugin;
    private final File file;
    private YamlConfiguration storage;

    public EventHistoryStore(DuckHuntPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events.yml");
    }

    /**
     * Loads events.yml from disk. The file is created on first save if
     * it doesn't exist yet.
     */
    public void load() {
        storage = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Appends a finished event's final standings to events.yml.
     */
    public void record(EventSession session) {
        String key = "events." + buildKey(session);

        storage.set(key + ".spawner", session.getSpawnerId());
        storage.set(key + ".name", session.getName());
        storage.set(key + ".started", session.getStartedAtMillis());
        storage.set(key + ".ended", System.currentTimeMillis());
        storage.set(key + ".duration-seconds", session.getTotalSeconds());

        List<EventScore> winners = session.getWinners();
        storage.set(key + ".winners", winners.stream().map(EventScore::name).collect(Collectors.toList()));
        storage.set(key + ".winner-points", winners.isEmpty() ? 0 : winners.get(0).points());

        for (EventScore score : session.getScores().values()) {
            String scorePath = key + ".scores." + score.uuid();
            storage.set(scorePath + ".name", score.name());
            storage.set(scorePath + ".points", score.points());
        }

        persist();
    }

    private String buildKey(EventSession session) {
        String timestamp = KEY_TIMESTAMP.format(
                Instant.ofEpochMilli(session.getStartedAtMillis()).atZone(ZoneId.systemDefault()));
        String safeSpawnerId = UNSAFE_KEY_CHARS.matcher(session.getSpawnerId()).replaceAll("_");
        return (timestamp + "-" + safeSpawnerId).toLowerCase(Locale.ROOT);
    }

    private void persist() {
        try {
            storage.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save events.yml: " + exception.getMessage());
        }
    }
}
