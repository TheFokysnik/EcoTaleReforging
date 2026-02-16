package com.crystalrealm.ecotalereforging.service;

import com.crystalrealm.ecotalereforging.util.PluginLogger;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side storage for reforge levels.
 *
 * <p>Since Hytale {@code ItemStack} has no metadata API (no getMetadata/withMetadata),
 * reforge levels are stored externally, keyed by playerUUID + itemId.</p>
 *
 * <p>Limitation: if a player has multiple items of the same type (e.g., two Iron Swords),
 * they share the same reforge level. This is an acceptable trade-off given the API constraints.</p>
 *
 * <p>Data is persisted to {@code reforge_data.json} in the plugin data directory.</p>
 */
public class ReforgeDataStore {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String DATA_FILE_NAME = "reforge_data.json";

    /** playerUUID → (itemId → reforgeLevel) */
    private final Map<UUID, Map<String, Integer>> data = new ConcurrentHashMap<>();

    private final Path dataFile;

    public ReforgeDataStore(@Nonnull Path dataDirectory) {
        this.dataFile = dataDirectory.resolve(DATA_FILE_NAME);
        load();
    }

    // ════════════════════════════════════════════════════════
    //  GET / SET
    // ════════════════════════════════════════════════════════

    /**
     * Get the reforge level for a specific player's item.
     *
     * @param playerUuid the player's UUID
     * @param itemId     the item ID (e.g., "Weapon_Sword_Iron")
     * @return reforge level (0 if never reforged)
     */
    public int getLevel(@Nonnull UUID playerUuid, @Nonnull String itemId) {
        String normalizedId = normalizeItemId(itemId);
        Map<String, Integer> playerData = data.get(playerUuid);
        if (playerData == null) return 0;
        return playerData.getOrDefault(normalizedId, 0);
    }

    /**
     * Set the reforge level for a specific player's item.
     * Automatically saves to disk.
     *
     * @param playerUuid the player's UUID
     * @param itemId     the item ID
     * @param level      the new reforge level
     */
    public void setLevel(@Nonnull UUID playerUuid, @Nonnull String itemId, int level) {
        String normalizedId = normalizeItemId(itemId);
        data.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(normalizedId, level);
        save();
        LOGGER.info("[ReforgeDataStore] Set {} / {} = level {}", playerUuid, normalizedId, level);
    }

    /**
     * Remove reforge data for a specific player's item (e.g., on item destruction).
     */
    public void removeLevel(@Nonnull UUID playerUuid, @Nonnull String itemId) {
        String normalizedId = normalizeItemId(itemId);
        Map<String, Integer> playerData = data.get(playerUuid);
        if (playerData != null) {
            playerData.remove(normalizedId);
            if (playerData.isEmpty()) {
                data.remove(playerUuid);
            }
            save();
            LOGGER.info("[ReforgeDataStore] Removed {} / {}", playerUuid, normalizedId);
        }
    }

    /**
     * Get all reforge data for a player.
     */
    @Nonnull
    public Map<String, Integer> getPlayerData(@Nonnull UUID playerUuid) {
        Map<String, Integer> playerData = data.get(playerUuid);
        return playerData != null ? Map.copyOf(playerData) : Map.of();
    }

    // ════════════════════════════════════════════════════════
    //  PERSISTENCE
    // ════════════════════════════════════════════════════════

    /**
     * Save all data to disk as JSON.
     * Format: { "uuid": { "itemId": level, ... }, ... }
     */
    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            boolean firstPlayer = true;
            for (Map.Entry<UUID, Map<String, Integer>> playerEntry : data.entrySet()) {
                if (!firstPlayer) sb.append(",\n");
                firstPlayer = false;
                sb.append("  \"").append(playerEntry.getKey()).append("\": {");
                boolean firstItem = true;
                for (Map.Entry<String, Integer> itemEntry : playerEntry.getValue().entrySet()) {
                    if (!firstItem) sb.append(", ");
                    firstItem = false;
                    sb.append("\"").append(escapeJson(itemEntry.getKey())).append("\": ")
                            .append(itemEntry.getValue());
                }
                sb.append("}");
            }
            sb.append("\n}\n");

            Files.writeString(dataFile, sb.toString());
            LOGGER.debug("[ReforgeDataStore] Saved {} player(s) to {}", data.size(), dataFile);
        } catch (IOException e) {
            LOGGER.error("[ReforgeDataStore] Failed to save: {}", e.getMessage());
        }
    }

    /**
     * Load data from disk.
     */
    public void load() {
        if (!Files.exists(dataFile)) {
            LOGGER.info("[ReforgeDataStore] No data file found at {}, starting fresh.", dataFile);
            return;
        }

        try {
            String json = Files.readString(dataFile);
            parseJson(json);
            LOGGER.info("[ReforgeDataStore] Loaded {} player(s) from {}", data.size(), dataFile);
        } catch (IOException e) {
            LOGGER.error("[ReforgeDataStore] Failed to load: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  MINIMAL JSON PARSER (no external dependencies)
    // ════════════════════════════════════════════════════════

    private void parseJson(@Nonnull String json) {
        data.clear();
        // Simple state-machine parser for our specific format:
        // { "uuid-string": { "itemId": number, ... }, ... }
        try {
            json = json.trim();
            if (!json.startsWith("{") || !json.endsWith("}")) return;
            json = json.substring(1, json.length() - 1).trim();

            // Split by top-level entries (UUID → object)
            int pos = 0;
            while (pos < json.length()) {
                // Find next UUID key
                int keyStart = json.indexOf('"', pos);
                if (keyStart < 0) break;
                int keyEnd = json.indexOf('"', keyStart + 1);
                if (keyEnd < 0) break;
                String uuidStr = json.substring(keyStart + 1, keyEnd);

                // Find the opening brace of the value object
                int objStart = json.indexOf('{', keyEnd);
                if (objStart < 0) break;

                // Find matching closing brace
                int objEnd = findMatchingBrace(json, objStart);
                if (objEnd < 0) break;

                String objContent = json.substring(objStart + 1, objEnd).trim();

                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Integer> items = parseItemMap(objContent);
                    if (!items.isEmpty()) {
                        data.put(uuid, new ConcurrentHashMap<>(items));
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[ReforgeDataStore] Invalid UUID: {}", uuidStr);
                }

                pos = objEnd + 1;
            }
        } catch (Exception e) {
            LOGGER.error("[ReforgeDataStore] JSON parse error: {}", e.getMessage());
        }
    }

    private Map<String, Integer> parseItemMap(@Nonnull String content) {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        int pos = 0;
        while (pos < content.length()) {
            int keyStart = content.indexOf('"', pos);
            if (keyStart < 0) break;
            int keyEnd = content.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = content.substring(keyStart + 1, keyEnd);

            int colonPos = content.indexOf(':', keyEnd);
            if (colonPos < 0) break;

            // Parse the integer value
            int valStart = colonPos + 1;
            while (valStart < content.length() && content.charAt(valStart) == ' ') valStart++;
            int valEnd = valStart;
            while (valEnd < content.length() && (Character.isDigit(content.charAt(valEnd)) || content.charAt(valEnd) == '-')) valEnd++;

            if (valEnd > valStart) {
                try {
                    int value = Integer.parseInt(content.substring(valStart, valEnd).trim());
                    result.put(key, value);
                } catch (NumberFormatException ignored) {}
            }

            pos = valEnd;
        }
        return result;
    }

    private static int findMatchingBrace(@Nonnull String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static String normalizeItemId(@Nonnull String itemId) {
        // Strip namespace if present
        if (itemId.contains(":")) {
            return itemId.substring(itemId.indexOf(':') + 1);
        }
        return itemId;
    }

    private static String escapeJson(@Nonnull String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
