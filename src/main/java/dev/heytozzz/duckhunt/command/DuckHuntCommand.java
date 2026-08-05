package dev.heytozzz.duckhunt.command;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.spawn.SpawnPoint;
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
import java.util.stream.Collectors;

/**
 * Handles "/duckhunt" and its subcommands. The whole command is gated by
 * the "duckhunt.admin" permission declared in plugin.yml.
 */
public class DuckHuntCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "setspawn", "removespawn", "list", "spawn", "clear", "start", "stop", "reload"
    );

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
            case "removespawn" -> handleRemoveSpawn(sender, args);
            case "list" -> handleList(sender);
            case "spawn" -> handleSpawn(sender, args);
            case "clear" -> handleClear(sender);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "reload" -> handleReload(sender);
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
        SpawnPoint point = new SpawnPoint(
                id,
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw()
        );
        plugin.getConfigManager().saveSpawnPoint(point);
        plugin.getLangManager().send(sender, "spawnpoint.set", Placeholder.unparsed("id", id));
    }

    private void handleRemoveSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getLangManager().send(sender, "usage.removespawn");
            return;
        }

        String id = args[1];
        if (plugin.getConfigManager().removeSpawnPoint(id)) {
            plugin.getLangManager().send(sender, "spawnpoint.removed", Placeholder.unparsed("id", id));
        } else {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
        }
    }

    private void handleList(CommandSender sender) {
        var spawnPoints = plugin.getConfigManager().getSpawnPoints();
        if (spawnPoints.isEmpty()) {
            plugin.getLangManager().send(sender, "spawnpoint.list-empty");
            return;
        }

        plugin.getLangManager().send(sender, "spawnpoint.list-header");
        for (SpawnPoint point : spawnPoints.values()) {
            plugin.getLangManager().send(sender, "spawnpoint.list-entry",
                    Placeholder.unparsed("id", point.id()),
                    Placeholder.unparsed("world", point.worldName()),
                    Placeholder.unparsed("x", format(point.x())),
                    Placeholder.unparsed("y", format(point.y())),
                    Placeholder.unparsed("z", format(point.z())));
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getLangManager().send(sender, "usage.spawn");
            return;
        }

        String id = args[1];
        if (id.equalsIgnoreCase("all")) {
            int spawned = plugin.getDuckSpawner().spawnAll();
            plugin.getLangManager().send(sender, "spawn.all-success",
                    Placeholder.unparsed("count", String.valueOf(spawned)));
            return;
        }

        SpawnPoint point = plugin.getConfigManager().getSpawnPoints().get(id);
        if (point == null) {
            plugin.getLangManager().send(sender, "spawnpoint.not-found", Placeholder.unparsed("id", id));
            return;
        }

        if (plugin.getDuckSpawner().isOccupied(id)) {
            plugin.getLangManager().send(sender, "spawn.occupied", Placeholder.unparsed("id", id));
            return;
        }

        plugin.getDuckSpawner().spawn(point);
        plugin.getLangManager().send(sender, "spawn.success", Placeholder.unparsed("id", id));
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
        plugin.getLangManager().load();
        plugin.getLangManager().send(sender, "reload.success");
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

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("removespawn") || sub.equals("spawn")) {
                List<String> ids = new ArrayList<>(plugin.getConfigManager().getSpawnPoints().keySet());
                if (sub.equals("spawn")) {
                    ids.add("all");
                }
                String partial = args[1].toLowerCase(Locale.ROOT);
                return ids.stream()
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}
