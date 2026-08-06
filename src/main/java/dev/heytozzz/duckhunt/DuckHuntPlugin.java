package dev.heytozzz.duckhunt;

import dev.heytozzz.duckhunt.command.DuckHuntCommand;
import dev.heytozzz.duckhunt.config.ConfigManager;
import dev.heytozzz.duckhunt.config.SpawnPointManager;
import dev.heytozzz.duckhunt.lang.LangManager;
import dev.heytozzz.duckhunt.leaderboard.LeaderboardManager;
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

    @Override
    public void onEnable() {
        DuckKeys.init(this);

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.spawnPointManager = new SpawnPointManager(this);
        this.spawnPointManager.load();

        this.langManager = new LangManager(this);
        this.langManager.load();

        this.leaderboardManager = new LeaderboardManager(this);
        this.leaderboardManager.load();

        this.duckSpawner = new DuckSpawner(this);
        this.duckSpawner.reconcileFromWorld();
        this.autoSpawnManager = new AutoSpawnManager(this);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new DuckDeathListener(this), this);

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

        getLogger().info("DuckHunt enabled.");
    }

    @Override
    public void onDisable() {
        if (autoSpawnManager != null) {
            autoSpawnManager.stop();
        }
        if (duckSpawner != null) {
            duckSpawner.stopPathFollowing();
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
}
