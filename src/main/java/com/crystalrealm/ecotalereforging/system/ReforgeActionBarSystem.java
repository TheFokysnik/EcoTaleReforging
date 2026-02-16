package com.crystalrealm.ecotalereforging.system;

import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.npc.ReforgeStationManager;
import com.crystalrealm.ecotalereforging.service.ReforgeDataStore;
import com.crystalrealm.ecotalereforging.service.WeaponStatsService;
import com.crystalrealm.ecotalereforging.util.PluginLogger;
import com.crystalrealm.ecotalereforging.util.ReforgeMetadataHelper;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks each online player's held/equipped items and shows
 * reforge info + base stats via chat notification and action bar.
 *
 * <p>Uses {@code sendMessage()} for one-time notifications when a player
 * first holds a reforged item, plus {@code sendActionBar()} at high frequency
 * to compete with MultipleHUD for action bar space.</p>
 *
 * <p>When a player is holding a reforged weapon, displays:</p>
 * <pre>  ⚒ Reforged +3 | ⚔ Legendary Lv.60 | DMG +6.0 | Base: 15.0 → Total: 21.0</pre>
 */
public class ReforgeActionBarSystem {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    /** Tick interval — 200ms to compete with MultipleHUD for action bar. */
    private static final long TICK_INTERVAL_MS = 200;

    private final ReforgeConfig      config;
    private final ReforgeDataStore   dataStore;
    private final WeaponStatsService weaponStats;
    private ReforgeStationManager     npcManager;

    /** Last action bar text sent per player — avoids spamming identical messages. */
    private final Map<UUID, String> lastSent = new ConcurrentHashMap<>();

    /** Tracks which item+level combo was already notified via chat per player. */
    private final Map<UUID, String> notifiedItems = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    public ReforgeActionBarSystem(@Nonnull ReforgeConfig config,
                                  @Nonnull ReforgeDataStore dataStore,
                                  @Nonnull WeaponStatsService weaponStats) {
        this.config      = config;
        this.dataStore   = dataStore;
        this.weaponStats = weaponStats;
    }

    /** Set the station manager to pull tracked players from. */
    public void setNpcManager(@Nonnull ReforgeStationManager npcManager) {
        this.npcManager = npcManager;
    }

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EcoTaleReforging-ActionBar");
            t.setDaemon(true);
            return t;
        });
        task = scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("ReforgeActionBarSystem started (interval={}ms)", TICK_INTERVAL_MS);
    }

    public void shutdown() {
        if (task != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        lastSent.clear();
        notifiedItems.clear();
        LOGGER.info("ReforgeActionBarSystem stopped.");
    }

    // ════════════════════════════════════════════════════════
    //  TICK
    // ════════════════════════════════════════════════════════

    private void tick() {
        if (npcManager == null) return;
        Map<UUID, Player> players = npcManager.getOnlinePlayers();
        if (players.isEmpty()) return;

        for (var entry : players.entrySet()) {
            try {
                processPlayer(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.debug("[ActionBar] Error processing {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void processPlayer(@Nonnull UUID uuid, @Nonnull Player player) {
        // 1. Check held item first
        ItemStack held = null;
        try {
            held = player.getInventory().getItemInHand();
        } catch (Exception ignored) {}

        if (held != null && !held.isEmpty()) {
            String itemId = held.getItemId();
            if (itemId != null && config.getAllowedItems().isAllowed(itemId)) {
                int level = ReforgeMetadataHelper.getReforgeLevel(held);
                if (level > 0) {
                    String text = buildHeldItemText(itemId, level);
                    // Always send action bar (compete with MultipleHUD)
                    try { player.sendActionBar(text); } catch (Exception ignored) {}

                    // One-time chat notification when player first holds this reforged item
                    String notifyKey = itemId + ":" + level;
                    if (!notifyKey.equals(notifiedItems.get(uuid))) {
                        notifiedItems.put(uuid, notifyKey);
                        try { player.sendMessage("§6" + text); } catch (Exception ignored) {}
                    }
                    return;
                }
            }
        }

        // 2. If not holding a reforged item, check equipped armor
        double totalArmorBonus = 0;
        int armorPieces = 0;
        try {
            ItemContainer container = player.getInventory().getCombinedHotbarFirst();
            if (container != null) {
                short capacity = container.getCapacity();
                for (short s = 0; s < capacity; s++) {
                    try {
                        ItemStack stack = container.getItemStack(s);
                        if (stack == null || stack.isEmpty()) continue;
                        String id = stack.getItemId();
                        if (id == null || !config.getAllowedItems().isArmor(id)) continue;

                        int armorLevel = ReforgeMetadataHelper.getReforgeLevel(stack);
                        if (armorLevel <= 0) continue;

                        armorPieces++;
                        for (int lvl = 1; lvl <= armorLevel; lvl++) {
                            ReforgeConfig.LevelConfig lc = config.getLevelConfig(lvl);
                            if (lc != null) {
                                totalArmorBonus += lc.getArmorDefenseBonus();
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        if (armorPieces > 0 && totalArmorBonus > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("⚒ Reforged Armor (%d pcs)", armorPieces));
            if (totalArmorBonus > 0) sb.append(String.format(" | DEF +%.1f", totalArmorBonus));
            try { player.sendActionBar(sb.toString()); } catch (Exception ignored) {}
            return;
        }

        // 3. Nothing reforged — clear tracking
        lastSent.remove(uuid);
    }

    // ════════════════════════════════════════════════════════
    //  TEXT BUILDERS
    // ════════════════════════════════════════════════════════

    @Nonnull
    private String buildHeldItemText(@Nonnull String itemId, int reforgeLevel) {
        boolean isWeapon = config.getAllowedItems().isWeapon(itemId);

        // Calculate reforge bonus
        double totalDmg = 0;
        double totalDef = 0;
        for (int lvl = 1; lvl <= reforgeLevel; lvl++) {
            ReforgeConfig.LevelConfig lc = config.getLevelConfig(lvl);
            if (lc != null) {
                totalDmg += lc.getWeaponDamageBonus();
                totalDef += lc.getArmorDefenseBonus();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⚒ Reforged +").append(reforgeLevel);

        if (isWeapon) {
            WeaponStatsService.WeaponStats ws = weaponStats.getWeaponStats(itemId);
            if (ws != null && ws.quality != null) {
                sb.append(" | ⚔ ").append(ws.qualityLabel());
                if (ws.itemLevel > 0) sb.append(" Lv.").append(ws.itemLevel);
            }
            sb.append(" | DMG +").append(String.format("%.1f", totalDmg));

            // Show base damage if RootInteraction extraction succeeded
            if (ws != null && ws.hasDamageData()) {
                sb.append(" | Base: ").append(ws.formatDamage());
                sb.append(" → ").append(String.format("%.1f", ws.avgDamage + totalDmg));
            }
            if (ws != null && ws.signatureEnergy > 0) {
                sb.append(" | SE +").append(String.format("%.0f", ws.signatureEnergy));
            }
        } else {
            sb.append(" | DEF +").append(String.format("%.1f", totalDef));

            WeaponStatsService.ArmorStats as = weaponStats.getArmorStats(itemId);
            if (as != null) {
                StringBuilder statsStr = new StringBuilder();
                if (as.health > 0) statsStr.append("HP +").append(String.format("%.0f", as.health));
                if (as.defense > 0) {
                    if (statsStr.length() > 0) statsStr.append(", ");
                    statsStr.append("DEF +").append(String.format("%.0f", as.defense));
                }
                if (statsStr.length() > 0) {
                    sb.append(" | Base: ").append(statsStr);
                }
            }
        }

        return sb.toString();
    }

    private void sendIfChanged(@Nonnull Player player, @Nonnull UUID uuid, @Nonnull String text) {
        String prev = lastSent.get(uuid);
        if (text.equals(prev)) return; // don't spam identical messages
        try {
            player.sendActionBar(text);
            lastSent.put(uuid, text);
        } catch (Exception e) {
            LOGGER.debug("[ActionBar] sendActionBar failed for {}: {}", uuid, e.getMessage());
        }
    }
}
