package dev.heytozzz.duckhunt.command;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.spawn.PathMode;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
import dev.heytozzz.duckhunt.spawn.Waypoint;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles "/duckhunt" and its subcommands. The whole command is gated by
 * the "duckhunt.admin" permission declared in plugin.yml.
 */
public class DuckHuntCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "setspawn", "setamount", "removespawn", "list", "spawn", "clear",
            "start", "stop", "reload", "add", "path"
    );
    private static final List<String> PATH_ACTIONS = List.of("list", "remove", "clear", "mode");
    private static final List<String> PATH_MODES = List.of("loop", "pingpong", "stop");

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
        switch (sub) {
            case "setspawn" -> handleSetSpawn(sender, args);
            case "setamount" -> handleSetAmount(sender, args);
            case "removespawn" -> handleRemoveSpawn(sender, args);
            case "list" -> handleList(sender);
            case "spawn" -> handleSpawn(sender, args);
            case "clear" -> handleClear(sender);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "reload" -> handleReload(sender);
            case "add" -> handleAdd(sender, args);
            case "path" -> handlePath(sender, args);
            default -> plugin.getLangManager().send(sender, "error.unknown-subcommand");
        }
        return true;
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLangManager().send(sender, "error.players-only");
            return;
        }
        if (args.length < 2) {
            plugin.getLangManager().send(sender, "usage.setspawn");
            return;
        }

        String id = args[1];

        // Optional 3rd argument: per-spawn-point duck amount override.
        Integer amount = null;
        if (args.length >= 3) {
            Integer parsed = parseAmount(args[2]);
            if (parsed == null) {
                plugin.getLangManager().send(sender, "error.invalid-amount");
                return;
            }
            amount = parsed;
        }

        // Re-running setspawn on an existing id only updates its location
        // and amount override: its waypoint path and path-mode are kept.
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

    private void handleSetAmount(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.setamount");
            return;
        }

        String id = args[1];
        SpawnPoint existing = plugin.getSpawnPointManager().get(id);
        if (existing == null) {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
            return;
        }

        Integer amount = parseAmount(args[2]);
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

    private void handleRemoveSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getLangManager().send(sender, "usage.removespawn");
            return;
        }

        String id = args[1];
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
        // Restart the path-following task so a changed
        // "spawn.path-check-interval-ticks" takes effect immediately.
        plugin.getDuckSpawner().stopPathFollowing();
        plugin.getDuckSpawner().startPathFollowing();
        plugin.getLangManager().send(sender, "reload.success");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("path")) {
            plugin.getLangManager().send(sender, "usage.addpath");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.getLangManager().send(sender, "error.players-only");
            return;
        }
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.addpath");
            return;
        }

        String id = args[2];
        SpawnPoint point = plugin.getSpawnPointManager().get(id);
        if (point == null) {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
            return;
        }

        plugin.getSpawnPointManager().addWaypoint(id, player.getLocation());
        int count = plugin.getSpawnPointManager().get(id).path().size();
        plugin.getLangManager().send(sender, "path.added",
                Placeholder.unparsed("id", id),
                Placeholder.unparsed("count", String.valueOf(count)));
    }

    private void handlePath(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getLangManager().send(sender, "usage.path");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String id = args[2];
        SpawnPoint point = plugin.getSpawnPointManager().get(id);
        if (point == null) {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
            return;
        }

        switch (action) {
            case "list" -> handlePathList(sender, point);
            case "clear" -> handlePathClear(sender, id);
            case "remove" -> handlePathRemove(sender, args, id);
            case "mode" -> handlePathMode(sender, args, id);
            default -> plugin.getLangManager().send(sender, "usage.path");
        }
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

    private void handlePathRemove(CommandSender sender, String[] args, String id) {
        if (args.length < 4) {
            plugin.getLangManager().send(sender, "usage.path");
            return;
        }

        Integer index = parseAmount(args[3]);
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

    private void handlePathMode(CommandSender sender, String[] args, String id) {
        if (args.length < 4) {
            plugin.getLangManager().send(sender, "usage.path");
            return;
        }

        PathMode mode = PathMode.parse(args[3]);
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

        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);

            if (sub.equals("add")) {
                return Stream.of("path")
                        .filter(name -> name.startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (sub.equals("path")) {
                return PATH_ACTIONS.stream()
                        .filter(name -> name.startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (sub.equals("removespawn") || sub.equals("setamount") || sub.equals("spawn")) {
                List<String> ids = new ArrayList<>(plugin.getSpawnPointManager().getSpawnPoints().keySet());
                if (sub.equals("spawn")) {
                    ids.add("all");
                }
                return ids.stream()
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            boolean addPath = sub.equals("add") && args[1].equalsIgnoreCase("path");
            boolean pathAction = sub.equals("path")
                    && Set.copyOf(PATH_ACTIONS).contains(args[1].toLowerCase(Locale.ROOT));
            if (addPath || pathAction) {
                return plugin.getSpawnPointManager().getSpawnPoints().keySet().stream()
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
            return List.of();
        }

        if (args.length == 4 && sub.equals("path") && args[1].equalsIgnoreCase("mode")) {
            String partial = args[3].toLowerCase(Locale.ROOT);
            return PATH_MODES.stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
