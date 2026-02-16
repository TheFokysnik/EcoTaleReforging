package com.crystalrealm.ecotalereforging.util;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for formatting and sending messages to players.
 */
public final class MessageUtil {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final DecimalFormat COIN_FORMAT;
    private static final Map<UUID, Object> PLAYER_REF_CACHE = new ConcurrentHashMap<>();

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        COIN_FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    private MessageUtil() {}

    // ── PlayerRef Cache ─────────────────────────────────────────

    public static void cachePlayerRef(UUID uuid, Object playerRef) {
        if (uuid != null && playerRef != null) {
            PLAYER_REF_CACHE.put(uuid, playerRef);
        }
    }

    public static void removePlayerRef(UUID uuid) {
        PLAYER_REF_CACHE.remove(uuid);
    }

    public static void clearCache() {
        PLAYER_REF_CACHE.clear();
    }

    public static java.util.Set<UUID> getCachedPlayerUuids() {
        return PLAYER_REF_CACHE.keySet();
    }

    // ── Message Sending ─────────────────────────────────────────

    /**
     * Sends a MiniMessage string to a player via cached PlayerRef.
     */
    public static void sendMessage(UUID playerUuid, String miniMessage) {
        try {
            Object playerRef = PLAYER_REF_CACHE.get(playerUuid);
            if (playerRef != null) {
                trySendViaPlayerRef(playerRef, miniMessage);
            }
        } catch (Throwable e) {
            LOGGER.debug("sendMessage failed for {}: {}", playerUuid, e.getMessage());
        }
    }

    private static void trySendViaPlayerRef(Object playerRef, String text) {
        try {
            String jsonText = MiniMessageParser.toJson(text);
            Class<?> msgClass = Class.forName("com.hypixel.hytale.server.core.Message");
            Method parseMethod = msgClass.getMethod("parse", String.class);
            Object parsedMsg = parseMethod.invoke(null, jsonText);
            Method sendMethod = playerRef.getClass().getMethod("sendMessage", msgClass);
            sendMethod.invoke(playerRef, parsedMsg);
        } catch (Throwable e) {
            LOGGER.warn("[sendMsg] failed: {}", e.getMessage());
        }
    }

    // ── Formatting ──────────────────────────────────────────────

    public static String formatCoins(double amount) {
        return COIN_FORMAT.format(amount);
    }

    public static String formatPercent(double value) {
        return String.format("%.0f%%", value * 100);
    }

    public static String formatBonus(double value) {
        return String.format("+%.1f", value);
    }
}
