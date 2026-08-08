package dev.heytozzz.duckhunt;

import dev.heytozzz.duckhunt.combo.ComboManager;
import dev.heytozzz.duckhunt.command.DuckHuntCommand;
import dev.heytozzz.duckhunt.config.ConfigManager;
import dev.heytozzz.duckhunt.config.SpawnPointManager;
import dev.heytozzz.duckhunt.event.EventManager;
import dev.heytozzz.duckhunt.lang.LangManager;
import dev.heytozzz.duckhunt.leaderboard.LeaderboardManager;
import dev.heytozzz.duckhunt.listener.ComboArrowListener;
import dev.heytozzz.duckhunt.listener.DuckDeathListener;
import dev.heytozzz.duckhunt.spawn.AutoSpawnManager;
import dev.heytozzz.duckhunt.spawn.DuckKeys;
import dev.heytozzz.duckhunt.spawn.DuckSpawner;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuckHuntPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private SpawnPointManager spawnPointManager;
    private LangManager langManager;
    private DuckSpawner duckSpawner;
    private AutoSpawnManager autoSpawnManager;
    private LeaderboardManager leaderboardManager;
    private EventManager eventManager;
    private ComboManager comboManager;

    private static final String BANNER =
            "      ,~~.\n" +
            "     (  6 )-_,\n" +
            "(\\___ )=='-'    DuckHunt 1.5.0 enabled\n" +
            " \\ .   ) )\n" +
            "  \\_`-'_/     ";

    @Override
    public void onEnable() {
        for (String line : BANNER.split("\n")) {
            getLogger().info(line);
        }

        DuckKeys.init(this);

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.spawnPointManager = new SpawnPointManager(this);
        this.spawnPointManager.load();

        this.langManager = new LangManager(this);
        this.langManager.load();

        this.leaderboardManager = new LeaderboardManager(this);
        this.leaderboardManager.load();

        this.eventManager = new EventManager(this);
        this.eventManager.load();

        this.comboManager = new ComboManager(this);

        this.duckSpawner = new DuckSpawner(this);
        // Ducks don't survive a restart cleanly: their stripped AI goals
        // and active pathfinder navigation are runtime-only state that
        // Minecraft doesn't persist, so a duck sitting in a chunk that
        // wasn't loaded yet at this exact point of server startup would
        // silently regain full vanilla behaviour (wandering, attacking...)
        // the next time its chunk loads, with nothing left to re-strip it.
        // Clearing on every enable and refilling from scratch below avoids
        // that entirely, instead of trying to reconcile leftover ducks.
        this.duckSpawner.clearAll();
        this.autoSpawnManager = new AutoSpawnManager(this);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new DuckDeathListener(this), this);
        pluginManager.registerEvents(new ComboArrowListener(this), this);

        DuckHuntCommand commandHandler = new DuckHuntCommand(this);
        var command = getCommand("duckhunt");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        }

        if (configManager.isAutoSpawnEnabled()) {
            autoSpawnManager.start(configManager.getAutoSpawnIntervalSeconds());
        }

        duckSpawner.startPathFollowing();
        duckSpawner.startRareDuckEffects();
        eventManager.start();
        comboManager.start();

        // Restock every spawn point right away, instead of leaving them
        // empty until the first auto-spawn tick or a manual command.
        duckSpawner.fillAll();
    }

    @Override
    public void onDisable() {
        if (autoSpawnManager != null) {
            autoSpawnManager.stop();
        }
        if (duckSpawner != null) {
            duckSpawner.stopPathFollowing();
            duckSpawner.stopRareDuckEffects();
        }
        if (eventManager != null) {
            eventManager.stop();
        }
        if (comboManager != null) {
            comboManager.stop();
        }
        getLogger().info("DuckHunt disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SpawnPointManager getSpawnPointManager() {
        return spawnPointManager;
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public DuckSpawner getDuckSpawner() {
        return duckSpawner;
    }

    public AutoSpawnManager getAutoSpawnManager() {
        return autoSpawnManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ComboManager getComboManager() {
        return comboManager;
    }
}
