package dev.heytozzz.duckhunt.leaderboard;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads and persists the duck-kill leaderboard from its own file
 * ("leaderboard.yml"). Only kills that qualify under
 * "leaderboard.min-kill-distance" (see {@link dev.heytozzz.duckhunt.config.ConfigManager})
 * are ever recorded here.
 */
public class LeaderboardManager {

    private final DuckHuntPlugin plugin;
    private final File file;
    private final Map<UUID, LeaderboardEntry> entries = new HashMap<>();
    private YamlConfiguration storage;

    public LeaderboardManager(DuckHuntPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboard.yml");
    }

    /**
     * (Re)loads leaderboard.yml from disk into memory. The file is created
     * on first save if it doesn't exist yet.
     */
    public void load() {
        storage = YamlConfiguration.loadConfiguration(file);

        entries.clear();
        ConfigurationSection section = storage.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection playerSection = section.getConfigurationSection(key);
                if (playerSection == null) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                String name = playerSection.getString("name", uuid.toString());
                int kills = playerSection.getInt("kills", 0);
                int points = playerSection.getInt("points", 0);
                entries.put(uuid, new LeaderboardEntry(uuid, name, kills, points));
            }
        }
    }

    /**
     * Adds one qualifying kill (worth {@code points}, based on how fast
     * the duck was — see
     * {@link dev.heytozzz.duckhunt.config.ConfigManager#getPointsForSpeed(double)})
     * to a player's tally (creating their entry if this is their first)
     * and persists it immediately. The stored display name is refreshed
     * on every kill in case it changed.
     *
     * @return the player's new point total.
     */
    public int recordKill(Player player, int points) {
        LeaderboardEntry existing = entries.get(player.getUniqueId());
        int kills = (existing != null ? existing.kills() : 0) + 1;
        int totalPoints = (existing != null ? existing.points() : 0) + points;
        entries.put(player.getUniqueId(),
                new LeaderboardEntry(player.getUniqueId(), player.getName(), kills, totalPoints));

        String path = "players." + player.getUniqueId();
        storage.set(path + ".name", player.getName());
        storage.set(path + ".kills", kills);
        storage.set(path + ".points", totalPoints);
        persist();
        return totalPoints;
    }

    /**
     * The top entries, ordered by points descending (ties broken by kill
     * count, then alphabetically by name).
     */
    public List<LeaderboardEntry> getTop(int limit) {
        return entries.values().stream()
                .sorted(Comparator.comparingInt(LeaderboardEntry::points).reversed()
                        .thenComparing(Comparator.comparingInt(LeaderboardEntry::kills).reversed())
                        .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Resets a single player's tally.
     *
     * @return true if the player had an entry to reset.
     */
    public boolean reset(UUID uuid) {
        if (!entries.containsKey(uuid)) {
            return false;
        }
        entries.remove(uuid);
        storage.set("players." + uuid, null);
        persist();
        return true;
    }

    /**
     * Resets every player's tally.
     */
    public void resetAll() {
        entries.clear();
        storage.set("players", null);
        persist();
    }

    private void persist() {
        try {
            storage.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save leaderboard.yml: " + exception.getMessage());
        }
    }
}
