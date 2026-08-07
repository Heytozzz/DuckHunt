package dev.heytozzz.duckhunt.lang;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.event.EventScope;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads translation files from the plugin's "lang" folder and renders
 * messages for each recipient using their client's own locale, falling
 * back to English when no matching translation exists.
 */
public class LangManager {

    private static final String DEFAULT_LOCALE = "en";
    private static final String[] BUNDLED_LOCALES = {"en", "es"};

    private final DuckHuntPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, YamlConfiguration> messages = new LinkedHashMap<>();

    public LangManager(DuckHuntPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Copies the bundled translation files to disk (if missing) and loads
     * every ".yml" file found in the "lang" folder into memory.
     */
    public void load() {
        messages.clear();

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        for (String locale : BUNDLED_LOCALES) {
            File file = new File(langFolder, locale + ".yml");
            if (!file.exists()) {
                copyBundled(locale, file);
            }
        }

        File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String locale = file.getName().substring(0, file.getName().length() - 4);
                messages.put(locale, YamlConfiguration.loadConfiguration(file));
            }
        }

        if (!messages.containsKey(DEFAULT_LOCALE)) {
            plugin.getLogger().warning("Missing 'lang/en.yml', falling back to raw message keys.");
        }
    }

    private void copyBundled(String locale, File target) {
        try (InputStream in = plugin.getResource("lang/" + locale + ".yml")) {
            if (in == null) {
                return;
            }
            Files.copy(in, target.toPath());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not extract default lang file for locale '"
                    + locale + "': " + exception.getMessage());
        }
    }

    private YamlConfiguration configFor(CommandSender sender) {
        String localeKey = DEFAULT_LOCALE;
        if (sender instanceof Player player) {
            localeKey = player.locale().getLanguage();
        }
        YamlConfiguration found = messages.get(localeKey);
        return found != null ? found : messages.get(DEFAULT_LOCALE);
    }

    private String prefixRaw(CommandSender sender) {
        YamlConfiguration config = configFor(sender);
        return config != null ? config.getString("prefix", "") : "";
    }

    /**
     * Builds the translated, prefixed message for the given recipient.
     */
    public Component render(CommandSender sender, String key, TagResolver... placeholders) {
        YamlConfiguration config = configFor(sender);
        String raw = config != null ? config.getString(key) : null;
        if (raw == null) {
            // Missing translation: fall back to the raw key so it's obvious
            // in-game rather than silently sending an empty message.
            raw = key;
        }

        Component prefix = miniMessage.deserialize(prefixRaw(sender));
        Component message = miniMessage.deserialize(raw, placeholders);
        return prefix.append(message);
    }

    /**
     * Renders and sends a translated message to a single recipient.
     */
    public void send(CommandSender sender, String key, TagResolver... placeholders) {
        sender.sendMessage(render(sender, key, placeholders));
    }

    /**
     * Renders and sends a translated message to every online player
     * (each in their own locale) plus the console.
     */
    public void broadcast(String key, TagResolver... placeholders) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(render(player, key, placeholders));
        }
        CommandSender console = plugin.getServer().getConsoleSender();
        console.sendMessage(render(console, key, placeholders));
    }

    /**
     * Renders a translated message for the given recipient without the
     * "[DuckHunt]" prefix — used for action bars and titles, where the
     * prefix would just be visual noise.
     */
    public Component renderRaw(CommandSender sender, String key, TagResolver... placeholders) {
        YamlConfiguration config = configFor(sender);
        String raw = config != null ? config.getString(key) : null;
        if (raw == null) {
            raw = key;
        }
        return miniMessage.deserialize(raw, placeholders);
    }

    /**
     * Renders and sends a translated message to every online player within
     * {@code radius} blocks of {@code origin} (same world only), plus the
     * console. Used for the "radius" kill-broadcast mode.
     */
    public void broadcastNear(Location origin, double radius, String key, TagResolver... placeholders) {
        double radiusSquared = radius * radius;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(origin) > radiusSquared) {
                continue;
            }
            player.sendMessage(render(player, key, placeholders));
        }
        CommandSender console = plugin.getServer().getConsoleSender();
        console.sendMessage(render(console, key, placeholders));
    }

    /**
     * Renders and sends a translated message to every online player
     * currently in one of {@code worldNames}, plus the console.
     */
    public void broadcastToWorlds(List<String> worldNames, String key, TagResolver... placeholders) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!worldNames.contains(player.getWorld().getName())) {
                continue;
            }
            player.sendMessage(render(player, key, placeholders));
        }
        CommandSender console = plugin.getServer().getConsoleSender();
        console.sendMessage(render(console, key, placeholders));
    }

    /**
     * Renders and sends a translated chat message according to an
     * {@link EventScope}: to everyone (global), to players near
     * {@code origin} (radius), or to players in specific worlds (world).
     * Falls back to global if {@code origin} is null and the scope is
     * "radius" (e.g. the event's spawn point world got unloaded).
     */
    public void broadcastScoped(EventScope scope, Location origin, String key, TagResolver... placeholders) {
        switch (scope.mode()) {
            case RADIUS -> {
                if (origin != null) {
                    broadcastNear(origin, scope.radius(), key, placeholders);
                } else {
                    broadcast(key, placeholders);
                }
            }
            case WORLD -> broadcastToWorlds(scope.worlds(), key, placeholders);
            default -> broadcast(key, placeholders);
        }
    }

    /**
     * Sends an un-prefixed action bar to a single player.
     */
    public void sendActionBar(Player player, String key, TagResolver... placeholders) {
        player.sendActionBar(renderRaw(player, key, placeholders));
    }

    /**
     * Sends an action bar to every player in scope of an
     * {@link EventScope} (see {@link #broadcastScoped}). Action bars
     * can't be shown to the console, so it's skipped here.
     */
    public void actionBarScoped(EventScope scope, Location origin, String key, TagResolver... placeholders) {
        for (Player player : playersInScope(scope, origin)) {
            player.sendActionBar(renderRaw(player, key, placeholders));
        }
    }

    /**
     * Shows a title (with a short default fade-in/stay/fade-out) to
     * every player in scope of an {@link EventScope}.
     */
    public void titleScoped(EventScope scope, Location origin, String mainKey, String subKey,
                             TagResolver... placeholders) {
        Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500));
        for (Player player : playersInScope(scope, origin)) {
            Component main = renderRaw(player, mainKey, placeholders);
            Component sub = renderRaw(player, subKey, placeholders);
            player.showTitle(Title.title(main, sub, times));
        }
    }

    private List<Player> playersInScope(EventScope scope, Location origin) {
        List<Player> result = new ArrayList<>();
        double radiusSquared = scope.radius() * scope.radius();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean inScope = switch (scope.mode()) {
                case RADIUS -> origin != null && player.getWorld().equals(origin.getWorld())
                        && player.getLocation().distanceSquared(origin) <= radiusSquared;
                case WORLD -> scope.worlds().contains(player.getWorld().getName());
                default -> true;
            };
            if (inScope) {
                result.add(player);
            }
        }
        return result;
    }
}
