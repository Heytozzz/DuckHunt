package dev.heytozzz.duckhunt.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    // Captured once at start, so announcements keep working even if the
    // spawn point is later moved/removed while the event is running.
    @Nullable
    private final Location origin;

    private final Map<UUID, EventScore> scores = new LinkedHashMap<>();
    private int remainingSeconds;

    public EventSession(String spawnerId, String name, int totalSeconds, @Nullable Location origin) {
        this.spawnerId = spawnerId;
        this.name = name;
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = totalSeconds;
        this.startedAtMillis = System.currentTimeMillis();
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
     * Every player tied for first place, sorted by name — empty if
     * nobody scored any points.
     */
    public List<EventScore> getWinners() {
        int max = scores.values().stream().mapToInt(EventScore::points).max().orElse(0);
        if (max <= 0) {
            return List.of();
        }
        List<EventScore> winners = new ArrayList<>();
        for (EventScore score : scores.values()) {
            if (score.points() == max) {
                winners.add(score);
            }
        }
        winners.sort(Comparator.comparing(score -> score.name().toLowerCase(java.util.Locale.ROOT)));
        return winners;
    }
}
