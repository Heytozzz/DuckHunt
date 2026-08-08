package dev.heytozzz.duckhunt.combo;

import dev.heytozzz.duckhunt.DuckHuntPlugin;
import dev.heytozzz.duckhunt.config.ConfigManager;
import dev.heytozzz.duckhunt.effect.ParticleEffect;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's duck-kill streak ("combo"): consecutive kills
 * landed within "combo.window-seconds" of each other keep it climbing;
 * going quiet for longer than that resets it to zero. Reaching one of
 * "combo.tiers" gives the player's arrows a trailing particle effect
 * (until the streak drops below that tier or a new bow shot happens to
 * carry the current one) and multiplies the points their qualifying
 * kills are worth.
 *
 * <p>A single repeating task (see {@link #tick()}) both expires stale
 * streaks and advances every in-flight "combo arrow"'s particle trail,
 * mirroring the pattern {@code DuckSpawner} already uses for rare-duck
 * trails.
 */
public class ComboManager {

    private final DuckHuntPlugin plugin;
    private final ConfigManager config;

    // player UUID -> current streak count.
    private final Map<UUID, Integer> comboByPlayer = new HashMap<>();
    // player UUID -> epoch millis of their last kill, used to expire streaks.
    private final Map<UUID, Long> lastKillMillisByPlayer = new HashMap<>();
    // player UUID -> their name as of their last kill, so an expiring
    // streak can still be announced even if they log off in the meantime.
    private final Map<UUID, String> lastKillerNameByPlayer = new HashMap<>();

    // in-flight arrow UUID -> the particle trailing behind it.
    private final Map<UUID, ParticleEffect> trackedArrows = new LinkedHashMap<>();

    private BukkitTask tickTask;

    public ComboManager(DuckHuntPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    /**
     * Starts the repeating tick task. Always started regardless of
     * "combo.enabled", so that setting can be flipped by
     * "/duckhunt admin reload" and take effect immediately, instead of
     * needing a full server restart. No-op if already running.
     */
    public void start() {
        if (tickTask != null) {
            return;
        }
        long interval = config.getComboTickIntervalTicks();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        comboByPlayer.clear();
        lastKillMillisByPlayer.clear();
        lastKillerNameByPlayer.clear();
        trackedArrows.clear();
    }

    /**
     * Registers a duck kill towards a player's streak: extends it if it
     * landed within "combo.window-seconds" of their last one, otherwise
     * starts a fresh streak at 1. Announces a chat message the moment a
     * new tier is reached, and always refreshes the player's action bar
     * with their current streak.
     *
     * @return the resulting combo count (0 if "combo.enabled" is false).
     */
    public int registerKill(Player player) {
        if (!config.isComboEnabled()) {
            return 0;
        }

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastKill = lastKillMillisByPlayer.get(id);
        long windowMillis = (long) (config.getComboWindowSeconds() * 1000);

        int previousCombo = (lastKill != null && (now - lastKill) <= windowMillis)
                ? comboByPlayer.getOrDefault(id, 0) : 0;
        int combo = previousCombo + 1;

        comboByPlayer.put(id, combo);
        lastKillMillisByPlayer.put(id, now);
        lastKillerNameByPlayer.put(id, player.getName());

        ComboTier previousTier = tierFor(previousCombo);
        ComboTier currentTier = tierFor(combo);
        if (currentTier != null && currentTier != previousTier) {
            plugin.getLangManager().send(player, "combo.tier-up", Placeholder.unparsed("combo", String.valueOf(combo)));
        }
        plugin.getLangManager().sendActionBar(player, "combo.actionbar", Placeholder.unparsed("combo", String.valueOf(combo)));

        return combo;
    }

    /**
     * The points multiplier a player's qualifying kills currently earn,
     * based on their active combo tier (1.0 if none, or if
     * "combo.enabled" is false).
     */
    public double getPointsMultiplier(Player player) {
        if (!config.isComboEnabled()) {
            return 1.0;
        }
        ComboTier tier = tierFor(getCombo(player));
        return tier != null ? tier.pointsMultiplier() : 1.0;
    }

    public int getCombo(Player player) {
        return comboByPlayer.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Called when a player shoots an arrow: if they currently have an
     * active combo tier, that arrow starts trailing its particle until
     * it lands (see {@link #onArrowLanded}) or its chunk unloads.
     */
    public void onArrowShot(Arrow arrow, Player shooter) {
        if (!config.isComboEnabled()) {
            return;
        }
        ComboTier tier = tierFor(getCombo(shooter));
        if (tier == null) {
            return;
        }
        trackedArrows.put(arrow.getUniqueId(), tier.particle());
    }

    /**
     * Called when a tracked arrow hits something (or a block) and should
     * stop trailing. Safe to call for any arrow, tracked or not.
     */
    public void onArrowLanded(UUID arrowId) {
        trackedArrows.remove(arrowId);
    }

    /**
     * Highest tier a given combo count qualifies for.
     *
     * @return the tier, or {@code null} if {@code combo} is below every
     * configured tier's threshold.
     */
    @Nullable
    private ComboTier tierFor(int combo) {
        ComboTier active = null;
        for (ComboTier tier : config.getComboTiers()) {
            if (combo < tier.threshold()) {
                break; // config.getComboTiers() is sorted ascending
            }
            active = tier;
        }
        return active;
    }

    private void tick() {
        tickArrowTrails();
        expireStaleCombos();
    }

    private void tickArrowTrails() {
        if (trackedArrows.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, ParticleEffect>> iterator = trackedArrows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ParticleEffect> entry = iterator.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof Arrow arrow) || arrow.isDead() || !arrow.isValid()) {
                iterator.remove();
                continue;
            }
            ParticleEffect particle = entry.getValue();
            World world = arrow.getWorld();
            world.spawnParticle(particle.particle(), arrow.getLocation(), particle.count(),
                    particle.offsetX(), particle.offsetY(), particle.offsetZ(), particle.speed());
        }
    }

    /**
     * Drops any streak that's gone quiet for longer than
     * "combo.window-seconds", announcing it to the player if they're
     * still online.
     */
    private void expireStaleCombos() {
        if (lastKillMillisByPlayer.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMillis = (long) (config.getComboWindowSeconds() * 1000);

        Iterator<Map.Entry<UUID, Long>> iterator = lastKillMillisByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now - entry.getValue() <= windowMillis) {
                continue;
            }
            UUID id = entry.getKey();
            iterator.remove();
            Integer combo = comboByPlayer.remove(id);
            String name = lastKillerNameByPlayer.remove(id);

            if (combo == null || combo <= 0) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                plugin.getLangManager().send(player, "combo.broken", Placeholder.unparsed("combo", String.valueOf(combo)));
            } else if (name != null) {
                plugin.getLogger().fine(name + "'s " + combo + "-kill duck combo expired while offline.");
            }
        }
    }
}
