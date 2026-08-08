package dev.heytozzz.duckhunt.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The live state of one running "duck hunt" event: how much time is
 * left and every participating player's point tally so far. Scoped to a
 * single spawn point — only kills of ducks spawned there count towards
 * it, which is also what its "radius" announcements (if configured) are
 * centered on.
 */
public class EventSession {

    private final String spawnerId;
    private final String name;
    private final int totalSeconds;
    private final long startedAtMillis;
    // How many top-scoring players count as "winners" once this event
    // ends. Resolved once at start (either given explicitly on
    // "/duckhunt admin event start ..." or taken from
    // "event.default-winner-count" in config.yml) and fixed for the
    // rest of the event's lifetime.
    private final int winnerCount;
    // Captured once at start, so announcements keep working even if the
    // spawn point is later moved/removed while the event is running.
    @Nullable
    private final Location origin;

    private final Map<UUID, EventScore> scores = new LinkedHashMap<>();
    private int remainingSeconds;

    public EventSession(String spawnerId, String name, int totalSeconds, int winnerCount, @Nullable Location origin) {
        this.spawnerId = spawnerId;
        this.name = name;
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = totalSeconds;
        this.startedAtMillis = System.currentTimeMillis();
        this.winnerCount = Math.max(1, winnerCount);
        this.origin = origin;
    }

    public String getSpawnerId() {
        return spawnerId;
    }

    public String getName() {
        return name;
    }

    public int getTotalSeconds() {
        return totalSeconds;
    }

    /**
     * How many top-scoring players are declared winners once this event
     * ends (resolved once at start — see the constructor).
     */
    public int getWinnerCount() {
        return winnerCount;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    /**
     * The event spawn point's location at the moment the event started,
     * used to center "radius"-scoped announcements. Never changes even
     * if the spawn point itself is later edited or removed.
     */
    @Nullable
    public Location getOrigin() {
        return origin;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Ticks the countdown down by one second.
     *
     * @return the new remaining-seconds value.
     */
    public int decrementSecond() {
        return --remainingSeconds;
    }

    /**
     * Adds points to a player's tally for this event (creating their
     * entry if this is their first qualifying kill in it).
     */
    public void addPoints(Player player, int points) {
        EventScore existing = scores.get(player.getUniqueId());
        int total = (existing != null ? existing.points() : 0) + points;
        scores.put(player.getUniqueId(), new EventScore(player.getUniqueId(), player.getName(), total));
    }

    public Map<UUID, EventScore> getScores() {
        return scores;
    }

    /**
     * The top {@link #getWinnerCount()} scoring players, ranked highest
     * points first (ties broken alphabetically by name) — empty if
     * nobody scored any points. A player's position in this list (index
     * 0 = 1st place) is their final placement, used to pick which
     * "event.winner-rewards" entry they get.
     */
    public List<EventScore> getWinners() {
        List<EventScore> ranked = scores.values().stream()
                .filter(score -> score.points() > 0)
                .sorted(Comparator.comparingInt(EventScore::points).reversed()
                        .thenComparing(score -> score.name().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        if (ranked.size() > winnerCount) {
            ranked = ranked.subList(0, winnerCount);
        }
        return ranked;
    }
}
