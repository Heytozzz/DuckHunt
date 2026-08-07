package dev.heytozzz.duckhunt.command;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.leaderboard.LeaderboardEntry;
import dev.heytozzz.duckhunt.spawn.PathMode;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
import dev.heytozzz.duckhunt.spawn.Waypoint;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles "/duckhunt" and its subcommands. "/duckhunt top" only requires
 * "duckhunt.user" (default: true); everything under "/duckhunt admin"
 * requires "duckhunt.admin", both declared in plugin.yml.
 */
public class DuckHuntCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("top", "admin");
    private static final List<String> ADMIN_ACTIONS = List.of(
            "spawner", "spawn", "clear", "start", "stop", "reload", "top", "settings"
    );
    private static final List<String> SPAWNER_ACTIONS = List.of("list", "create", "remove");
    private static final List<String> SPAWNER_ID_ACTIONS = List.of("max", "path");
    private static final List<String> PATH_ACTIONS = List.of("add", "list", "remove", "clear", "mode");
    private static final List<String> PATH_MODES = List.of("loop", "pingpong", "stop");
    private static final List<String> TOP_ACTIONS = List.of("reset");
    private static final List<String> SETTINGS_ACTIONS = List.of("broadcast");
    private static final List<String> BROADCAST_MODES = List.of("global", "radius");

    private final DuckHuntPlugin plugin;

    public DuckHuntCommand(DuckHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            plugin.getLangManager().send(sender, "usage.main");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("top")) {
            if (!sender.hasPermission("duckhunt.user")) {
                plugin.getLangManager().send(sender, "permission.denied");
                return true;
            }
            handleTop(sender);
            return true;
        }

        if (sub.equals("admin")) {
            if (!sender.hasPermission("duckhunt.admin")) {
                plugin.getLangManager().send(sender, "permission.denied");
                return true;
            }
            handleAdmin(sender, args);
            return true;
        }

        plugin.getLangManager().send(sender, "error.unknown-subcommand");
        return true;
    }

    /**
     * Dispatches "/duckhunt admin ...". Rebases the array (dropping the
     * leading "admin") so every handler below sees the exact same indices
     * it would have if it were still directly under "/duckhunt" — nothing
     * else in this class needed to change to make this move.
     */
    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getLangManager().send(sender, "usage.admin");
            return;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        String action = rest[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "spawner" -> handleSpawner(sender, rest);
            case "spawn" -> handleSpawn(sender, rest);
            case "clear" -> handleClear(sender);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "reload" -> handleReload(sender);
            case "top" -> handleAdminTop(sender, rest);
            case "settings" -> handleAdminSettings(sender, rest);
            default -> plugin.getLangManager().send(sender, "usage.admin");
        }
    }

    private void handleAdminTop(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("reset")) {
            plugin.getLangManager().send(sender, "usage.admin-top");
            return;
        }
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.admin-top");
            return;
        }

        String target = args[2];
        if (target.equalsIgnoreCase("all")) {
            plugin.getLeaderboardManager().resetAll();
            plugin.getLangManager().send(sender, "top.reset-all");
            return;
        }

        Player online = plugin.getServer().getPlayerExact(target);
        OfflinePlayer offlinePlayer = online != null ? online : plugin.getServer().getOfflinePlayer(target);
        boolean removed = plugin.getLeaderboardManager().reset(offlinePlayer.getUniqueId());
        if (removed) {
            plugin.getLangManager().send(sender, "top.reset", Placeholder.unparsed("player", target));
        } else {
            plugin.getLangManager().send(sender, "top.reset-not-found", Placeholder.unparsed("player", target));
        }
    }

    private void handleAdminSettings(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("broadcast")) {
            plugin.getLangManager().send(sender, "usage.admin-settings");
            return;
        }
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.admin-settings");
            return;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "global" -> {
                plugin.getConfigManager().setBroadcastGlobal();
                plugin.getLangManager().send(sender, "settings.broadcast-global");
            }
            case "radius" -> {
                if (args.length < 4) {
                    plugin.getLangManager().send(sender, "usage.admin-settings");
                    return;
                }
                Integer radius = parseAmount(args[3]);
                if (radius == null) {
                    plugin.getLangManager().send(sender, "error.invalid-amount");
                    return;
                }
                plugin.getConfigManager().setBroadcastRadius(radius);
                plugin.getLangManager().send(sender, "settings.broadcast-radius",
                        Placeholder.unparsed("radius", String.valueOf(radius)));
            }
            default -> plugin.getLangManager().send(sender, "usage.admin-settings");
        }
    }

    private void handleTop(CommandSender sender) {
        int limit = plugin.getConfigManager().getLeaderboardTopSize();
        List<LeaderboardEntry> top = plugin.getLeaderboardManager().getTop(limit);
        if (top.isEmpty()) {
            plugin.getLangManager().send(sender, "top.empty");
            return;
        }

        plugin.getLangManager().send(sender, "top.header");
        for (int i = 0; i < top.size(); i++) {
            LeaderboardEntry entry = top.get(i);
            plugin.getLangManager().send(sender, "top.entry",
                    Placeholder.unparsed("rank", String.valueOf(i + 1)),
                    Placeholder.unparsed("player", entry.name()),
                    Placeholder.unparsed("points", String.valueOf(entry.points())),
                    Placeholder.unparsed("kills", String.valueOf(entry.kills())));
        }
    }

    /**
     * Dispatches "/duckhunt admin spawner ...". Second argument is either a
     * literal action ("list", "create", "remove") or the id of an existing
     * spawn point, in which case a third argument ("max"/"path") follows.
     */
    private void handleSpawner(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getLangManager().send(sender, "usage.spawner");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> handleList(sender);
            case "create" -> handleSpawnerCreate(sender, args);
            case "remove" -> handleSpawnerRemove(sender, args);
            default -> handleSpawnerId(sender, args);
        }
    }

    private void handleSpawnerId(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.spawner");
            return;
        }

        String id = args[1];
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "max" -> handleMax(sender, id, args);
            case "path" -> handlePath(sender, id, args);
            default -> plugin.getLangManager().send(sender, "usage.spawner");
        }
    }

    private void handleSpawnerCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLangManager().send(sender, "error.players-only");
            return;
        }
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.spawner-create");
            return;
        }

        String id = args[2];

        // Optional 4th argument: per-spawn-point duck amount override.
        Integer amount = null;
        if (args.length >= 4) {
            Integer parsed = parseAmount(args[3]);
            if (parsed == null) {
                plugin.getLangManager().send(sender, "error.invalid-amount");
                return;
            }
            amount = parsed;
        }

        // Re-running "spawner create" on an existing id only updates its
        // location and amount override: its waypoint path and path-mode
        // are kept.
        SpawnPoint existing = plugin.getSpawnPointManager().get(id);
        List<Waypoint> path = existing != null ? existing.path() : List.of();
        PathMode pathMode = existing != null ? existing.pathMode() : null;

        SpawnPoint point = new SpawnPoint(
                id,
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                amount,
                path,
                pathMode
        );
        plugin.getSpawnPointManager().save(point);
        plugin.getLangManager().send(sender, "spawnpoint.set", Placeholder.unparsed("id", id));
    }

    private void handleMax(CommandSender sender, String id, String[] args) {
        if (args.length < 4) {
            plugin.getLangManager().send(sender, "usage.max");
            return;
        }

        SpawnPoint existing = plugin.getSpawnPointManager().get(id);
        if (existing == null) {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
            return;
        }

        Integer amount = parseAmount(args[3]);
        if (amount == null) {
            plugin.getLangManager().send(sender, "error.invalid-amount");
            return;
        }

        SpawnPoint updated = new SpawnPoint(
                existing.id(), existing.worldName(), existing.x(), existing.y(), existing.z(),
                existing.yaw(), amount, existing.path(), existing.pathMode()
        );
        plugin.getSpawnPointManager().save(updated);
        plugin.getLangManager().send(sender, "spawnpoint.amount-set",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("amount", String.valueOf(amount)));
    }

    private void handleSpawnerRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.spawner-remove");
            return;
        }

        String id = args[2];
        if (plugin.getSpawnPointManager().remove(id)) {
            plugin.getLangManager().send(sender, "spawnpoint.removed", Placeholder.unparsed("id", id));
        } else {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
        }
    }

    private void handleList(CommandSender sender) {
        var spawnPoints = plugin.getSpawnPointManager().getSpawnPoints();
        if (spawnPoints.isEmpty()) {
            plugin.getLangManager().send(sender, "spawnpoint.list-empty");
            return;
        }

        plugin.getLangManager().send(sender, "spawnpoint.list-header");
        int defaultAmount = plugin.getConfigManager().getDefaultDuckAmount();
        for (SpawnPoint point : spawnPoints.values()) {
            int active = plugin.getDuckSpawner().getActiveCount(point.id());
            int capacity = point.effectiveAmount(defaultAmount);
            plugin.getLangManager().send(sender, "spawnpoint.list-entry",
                    Placeholder.unparsed("id", point.id()),
                    Placeholder.unparsed("world", point.worldName()),
                    Placeholder.unparsed("x", format(point.x())),
                    Placeholder.unparsed("y", format(point.y())),
                    Placeholder.unparsed("z", format(point.z())),
                    Placeholder.unparsed("active", String.valueOf(active)),
                    Placeholder.unparsed("amount", String.valueOf(capacity)),
                    Placeholder.unparsed("waypoints", String.valueOf(point.path().size())));
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getLangManager().send(sender, "usage.spawn");
            return;
        }

        String id = args[1];
        if (id.equalsIgnoreCase("all")) {
            int spawned = plugin.getDuckSpawner().fillAll();
            plugin.getLangManager().send(sender, "spawn.all-success",
                    Placeholder.unparsed("count", String.valueOf(spawned)));
            return;
        }

        SpawnPoint point = plugin.getSpawnPointManager().get(id);
        if (point == null) {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
            return;
        }

        int spawned = plugin.getDuckSpawner().fill(point);
        if (spawned == 0) {
            plugin.getLangManager().send(sender, "spawn.already-full", Placeholder.unparsed("id", id));
            return;
        }

        plugin.getLangManager().send(sender, "spawn.success",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("count", String.valueOf(spawned)));
    }

    private void handleClear(CommandSender sender) {
        plugin.getDuckSpawner().clearAll();
        plugin.getLangManager().send(sender, "cleared");
    }

    private void handleStart(CommandSender sender) {
        if (plugin.getAutoSpawnManager().isRunning()) {
            plugin.getLangManager().send(sender, "auto.already-running");
            return;
        }
        plugin.getAutoSpawnManager().start(plugin.getConfigManager().getAutoSpawnIntervalSeconds());
        plugin.getLangManager().send(sender, "auto.started");
    }

    private void handleStop(CommandSender sender) {
        if (!plugin.getAutoSpawnManager().isRunning()) {
            plugin.getLangManager().send(sender, "auto.already-stopped");
            return;
        }
        plugin.getAutoSpawnManager().stop();
        plugin.getLangManager().send(sender, "auto.stopped");
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().load();
        plugin.getSpawnPointManager().load();
        plugin.getLangManager().load();
        plugin.getLeaderboardManager().load();
        // Restart the path-following task so a changed
        // "spawn.path-check-interval-ticks" takes effect immediately.
        plugin.getDuckSpawner().stopPathFollowing();
        plugin.getDuckSpawner().startPathFollowing();
        plugin.getLangManager().send(sender, "reload.success");
    }

    private void handlePath(CommandSender sender, String id, String[] args) {
        if (args.length < 4) {
            plugin.getLangManager().send(sender, "usage.path");
            return;
        }

        String action = args[3].toLowerCase(Locale.ROOT);
        SpawnPoint point = plugin.getSpawnPointManager().get(id);
        if (point == null) {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
            return;
        }

        switch (action) {
            case "add" -> handlePathAdd(sender, id);
            case "list" -> handlePathList(sender, point);
            case "clear" -> handlePathClear(sender, id);
            case "remove" -> handlePathRemove(sender, id, args);
            case "mode" -> handlePathMode(sender, id, args);
            default -> plugin.getLangManager().send(sender, "usage.path");
        }
    }

    private void handlePathAdd(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            plugin.getLangManager().send(sender, "error.players-only");
            return;
        }

        plugin.getSpawnPointManager().addWaypoint(id, player.getLocation());
        int count = plugin.getSpawnPointManager().get(id).path().size();
        plugin.getLangManager().send(sender, "path.added",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("count", String.valueOf(count)));
    }

    private void handlePathList(CommandSender sender, SpawnPoint point) {
        List<Waypoint> path = point.path();
        if (path.isEmpty()) {
            plugin.getLangManager().send(sender, "path.list-empty", Placeholder.unparsed("id", point.id()));
            return;
        }

        plugin.getLangManager().send(sender, "path.list-header", Placeholder.unparsed("id", point.id()));
        for (int i = 0; i < path.size(); i++) {
            Waypoint waypoint = path.get(i);
            plugin.getLangManager().send(sender, "path.list-entry",
                    Placeholder.unparsed("index", String.valueOf(i + 1)),
                    Placeholder.unparsed("x", format(waypoint.x())),
                    Placeholder.unparsed("y", format(waypoint.y())),
                    Placeholder.unparsed("z", format(waypoint.z())));
        }
    }

    private void handlePathClear(CommandSender sender, String id) {
        plugin.getSpawnPointManager().clearPath(id);
        plugin.getLangManager().send(sender, "path.cleared", Placeholder.unparsed("id", id));
    }

    private void handlePathRemove(CommandSender sender, String id, String[] args) {
        if (args.length < 5) {
            plugin.getLangManager().send(sender, "usage.path");
            return;
        }

        Integer index = parseAmount(args[4]);
        if (index == null) {
            plugin.getLangManager().send(sender, "error.invalid-amount");
            return;
        }

        boolean removed = plugin.getSpawnPointManager().removeWaypoint(id, index - 1);
        if (removed) {
            plugin.getLangManager().send(sender, "path.removed",
                    Placeholder.unparsed("id", id),
                    Placeholder.unparsed("index", String.valueOf(index)));
        } else {
            plugin.getLangManager().send(sender, "path.invalid-index",
                    Placeholder.unparsed("id", id),
                    Placeholder.unparsed("index", String.valueOf(index)));
        }
    }

    private void handlePathMode(CommandSender sender, String id, String[] args) {
        if (args.length < 5) {
            plugin.getLangManager().send(sender, "usage.path");
            return;
        }

        PathMode mode = PathMode.parse(args[4]);
        if (mode == null) {
            plugin.getLangManager().send(sender, "error.invalid-path-mode");
            return;
        }

        plugin.getSpawnPointManager().setPathMode(id, mode);
        plugin.getLangManager().send(sender, "path.mode-set",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("mode", mode.name().toLowerCase(Locale.ROOT)));
    }

    @Nullable
    private Integer parseAmount(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value >= 1 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("admin")) {
            return List.of(); // "top" takes no further arguments.
        }

        return completeAdmin(Arrays.copyOfRange(args, 1, args.length));
    }

    /**
     * Tab-completion for everything under "/duckhunt admin ...". Operates
     * on a rebased array where index 0 is the action right after "admin",
     * mirroring the indices the handlers above expect.
     */
    private List<String> completeAdmin(String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return ADMIN_ACTIONS.stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("spawn")) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                List<String> ids = new ArrayList<>(plugin.getSpawnPointManager().getSpawnPoints().keySet());
                ids.add("all");
                return ids.stream()
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        if (action.equals("top")) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return TOP_ACTIONS.stream()
                        .filter(name -> name.startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("reset")) {
                String partial = args[2].toLowerCase(Locale.ROOT);
                List<String> names = new ArrayList<>();
                names.add("all");
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return names.stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        if (action.equals("settings")) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return SETTINGS_ACTIONS.stream()
                        .filter(name -> name.startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("broadcast")) {
                String partial = args[2].toLowerCase(Locale.ROOT);
                return BROADCAST_MODES.stream()
                        .filter(name -> name.startsWith(partial))
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        if (!action.equals("spawner")) {
            return List.of(); // clear/start/stop/reload take no further arguments.
        }

        // Everything below handles "/duckhunt admin spawner ...".
        Set<String> spawnerIds = plugin.getSpawnPointManager().getSpawnPoints().keySet();

        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return Stream.concat(SPAWNER_ACTIONS.stream(), spawnerIds.stream())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
        }

        String spawnerAction = args[1].toLowerCase(Locale.ROOT);

        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            if (spawnerAction.equals("remove")) {
                return spawnerIds.stream()
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (spawnerAction.equals("create")) {
                return List.of();
            }
            // args[1] is treated as an existing spawn point id.
            return SPAWNER_ID_ACTIONS.stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        boolean isIdContext = !SPAWNER_ACTIONS.contains(spawnerAction);

        if (args.length == 4 && isIdContext && args[2].equalsIgnoreCase("path")) {
            String partial = args[3].toLowerCase(Locale.ROOT);
            return PATH_ACTIONS.stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 5 && isIdContext && args[2].equalsIgnoreCase("path")
                && args[3].equalsIgnoreCase("mode")) {
            String partial = args[4].toLowerCase(Locale.ROOT);
            return PATH_MODES.stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
