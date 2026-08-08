package dev.heytozzz.duckhunt.event;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.ConfigManager;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs timed "duck hunt" events: whoever scores the most points at a
 * spawn point before the timer runs out wins. Multiple events can run at
 * once, one per spawn point (a spawn point can only have one active
 * event at a time). Ticks every active event's countdown once a second,
 * announcing milestones and finalizing/recording events that reach zero.
 */
public class EventManager {

    private final DuckHuntPlugin plugin;
    private final EventHistoryStore historyStore;
    // Keyed by spawn point id: a spawn point can only run one event at once.
    private final Map<String, EventSession> activeEvents = new LinkedHashMap<>();
    private BukkitTask tickTask;

    public EventManager(DuckHuntPlugin plugin) {
        this.plugin = plugin;
        this.historyStore = new EventHistoryStore(plugin);
    }

    public void load() {
        historyStore.load();
    }

    /**
     * Starts the once-a-second tick task driving every active event's
     * countdown. No-op if already running.
     */
    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * Stops the tick task. Any events still running are left as-is in
     * memory (not finalized) — this is only meant to be called on plugin
     * disable, right before the server itself shuts down.
     */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public enum StartResult {
        STARTED, SPAWNER_NOT_FOUND, ALREADY_ACTIVE
    }

    /**
     * Starts a new event at a spawn point, using
     * {@link dev.heytozzz.duckhunt.config.ConfigManager#getDefaultEventWinnerCount()}
     * for how many top scorers will be declared winners.
     */
    public StartResult startEvent(String spawnerId, String name, int durationSeconds) {
        return startEvent(spawnerId, name, durationSeconds, plugin.getConfigManager().getDefaultEventWinnerCount());
    }

    /**
     * Starts a new event at a spawn point with an explicit number of
     * winners (the top {@code winnerCount} scorers when it ends).
     */
    public StartResult startEvent(String spawnerId, String name, int durationSeconds, int winnerCount) {
        SpawnPoint point = plugin.getSpawnPointManager().get(spawnerId);
        if (point == null) {
            return StartResult.SPAWNER_NOT_FOUND;
        }
        if (activeEvents.containsKey(spawnerId)) {
            return StartResult.ALREADY_ACTIVE;
        }

        Location origin = point.toLocation();
        EventSession session = new EventSession(spawnerId, name, durationSeconds, winnerCount, origin);
        activeEvents.put(spawnerId, session);

        ConfigManager config = plugin.getConfigManager();
        plugin.getLangManager().broadcastScoped(config.getEventStartScope(), origin, "event.announce-start",
                Placeholder.unparsed("id", spawnerId),
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("time", formatClock(durationSeconds)),
                Placeholder.unparsed("winners", String.valueOf(session.getWinnerCount())));
        return StartResult.STARTED;
    }

    /**
     * Ends an active event immediately: announces the winner with
     * whatever standings it has so far and records it to history, same
     * as if its timer had run out naturally.
     *
     * @return true if a running event was found and ended.
     */
    public boolean stopEvent(String spawnerId) {
        EventSession session = activeEvents.remove(spawnerId);
        if (session == null) {
            return false;
        }
        finalizeEvent(session);
        return true;
    }

    /**
     * Adds points to a player's tally in the event running at a given
     * spawn point, if any. Safe to call unconditionally — it's a no-op
     * if that spawn point has no active event.
     */
    public void addPoints(String spawnerId, Player player, int points) {
        EventSession session = activeEvents.get(spawnerId);
        if (session != null) {
            session.addPoints(player, points);
        }
    }

    public boolean hasActiveEvent(String spawnerId) {
        return activeEvents.containsKey(spawnerId);
    }

    public Collection<EventSession> getActiveEvents() {
        return activeEvents.values();
    }

    private void tick() {
        if (activeEvents.isEmpty()) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        Iterator<EventSession> iterator = activeEvents.values().iterator();
        while (iterator.hasNext()) {
            EventSession session = iterator.next();
            int remaining = session.decrementSecond();

            if (remaining <= 0) {
                finalizeEvent(session);
                iterator.remove();
                continue;
            }

            plugin.getLangManager().actionBarScoped(config.getEventCountdownScope(), session.getOrigin(),
                    "event.actionbar", Placeholder.unparsed("time", formatClock(remaining)));

            if (config.getEventMilestoneSeconds().contains(remaining)) {
                plugin.getLangManager().broadcastScoped(config.getEventCountdownScope(), session.getOrigin(),
                        "event.milestone",
                        Placeholder.unparsed("id", session.getSpawnerId()),
                        Placeholder.unparsed("name", session.getName()),
                        Placeholder.unparsed("seconds", String.valueOf(remaining)));
            }
        }
    }

    private void finalizeEvent(EventSession session) {
        ConfigManager config = plugin.getConfigManager();
        List<EventScore> winners = session.getWinners();
        EventScope winnerScope = config.getEventWinnerScope();

        if (winners.isEmpty()) {
            plugin.getLangManager().broadcastScoped(winnerScope, session.getOrigin(), "event.no-winner",
                    Placeholder.unparsed("id", session.getSpawnerId()),
                    Placeholder.unparsed("name", session.getName()));
        } else {
            String winnerNames = winners.stream().map(EventScore::name).collect(Collectors.joining(", "));
            plugin.getLangManager().broadcastScoped(winnerScope, session.getOrigin(), "event.announce-winner",
                    Placeholder.unparsed("id", session.getSpawnerId()),
                    Placeholder.unparsed("name", session.getName()),
                    Placeholder.unparsed("winner", winnerNames),
                    Placeholder.unparsed("points", String.valueOf(winners.get(0).points())));

            if (config.isEventWinnerTitleEnabled()) {
                plugin.getLangManager().titleScoped(winnerScope, session.getOrigin(),
                        "event.winner-title-main", "event.winner-title-sub",
                        Placeholder.unparsed("id", session.getSpawnerId()),
                        Placeholder.unparsed("name", session.getName()),
                        Placeholder.unparsed("winner", winnerNames),
                        Placeholder.unparsed("points", String.valueOf(winners.get(0).points())));
            }

            rewardWinners(session, winners);
        }

        historyStore.record(session);
    }

    /**
     * Runs each winner's "event.winner-rewards" console commands, picked
     * by their final placement (1st, 2nd, ...), falling back to the
     * "default" entry. A placement with no commands configured (neither
     * its own nor a "default") is skipped entirely — no commands run and
     * no reward-received message is sent for that winner.
     */
    private void rewardWinners(EventSession session, List<EventScore> winners) {
        for (int i = 0; i < winners.size(); i++) {
            EventScore winner = winners.get(i);
            int rank = i + 1;
            List<String> commandTemplates = plugin.getConfigManager().getEventWinnerRewardCommands(rank);
            if (commandTemplates.isEmpty()) {
                continue;
            }

            for (String template : commandTemplates) {
                String command = template
                        .replace("%player%", winner.name())
                        .replace("%points%", String.valueOf(winner.points()))
                        .replace("%rank%", String.valueOf(rank))
                        .replace("%id%", session.getSpawnerId())
                        .replace("%name%", session.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }

            Player onlineWinner = Bukkit.getPlayer(winner.uuid());
            if (onlineWinner != null) {
                plugin.getLangManager().send(onlineWinner, "event.reward-received",
                        Placeholder.unparsed("name", session.getName()),
                        Placeholder.unparsed("rank", String.valueOf(rank)));
            }
        }
    }

    /**
     * Formats a number of seconds as "MM:SS" for the action bar timer.
     */
    public static String formatClock(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        int minutes = clamped / 60;
        int seconds = clamped % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
