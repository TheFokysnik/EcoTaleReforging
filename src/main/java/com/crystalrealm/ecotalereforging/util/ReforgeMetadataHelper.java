package com.crystalrealm.ecotalereforging.util;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Reflection-based helper for reading/writing reforge level in ItemStack metadata.
 *
 * <p>Hytale's runtime ItemStack has these methods (not exposed in public stubs):
 * <ul>
 *   <li>{@code BsonDocument getMetadata()}</li>
 *   <li>{@code ItemStack withMetadata(String key, BsonValue value)}</li>
 * </ul>
 *
 * <p>We use reflection so that compilation succeeds without BsonDocument/BsonValue stubs,
 * and gracefully falls back to 0 (no reforge) if metadata API is unavailable.</p>
 */
public final class ReforgeMetadataHelper {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    public static final String META_KEY = "reforge_level";

    // ── Cached reflection handles ──────────────────────────

    private static volatile boolean initialized = false;
    private static Method getMetadataMethod;       // ItemStack.getMetadata() → BsonDocument
    private static Method withMetadataMethod;      // ItemStack.withMetadata(String, BsonValue) → ItemStack
    private static Method bsonDocContainsKey;      // BsonDocument.containsKey(Object) → boolean
    private static Method bsonDocGet;              // BsonDocument.get(Object) → BsonValue
    private static Method bsonValueAsInt32;        // BsonValue.asInt32() → BsonInt32
    private static Method bsonInt32GetValue;       // BsonInt32.getValue() → int
    private static Constructor<?> bsonInt32Ctor;   // BsonInt32(int)
    private static boolean available = false;

    private ReforgeMetadataHelper() {}

    private static void init() {
        if (initialized) return;
        synchronized (ReforgeMetadataHelper.class) {
            if (initialized) return;
            try {
                // ItemStack methods
                Class<?> itemStackClass = ItemStack.class;
                getMetadataMethod = itemStackClass.getMethod("getMetadata");

                Class<?> bsonValueClass = Class.forName("org.bson.BsonValue");
                withMetadataMethod = itemStackClass.getMethod("withMetadata", String.class, bsonValueClass);

                // BsonDocument (extends LinkedHashMap — has Map.containsKey/get)
                Class<?> bsonDocClass = Class.forName("org.bson.BsonDocument");
                bsonDocContainsKey = bsonDocClass.getMethod("containsKey", Object.class);
                bsonDocGet = bsonDocClass.getMethod("get", Object.class);

                // BsonValue.asInt32()
                bsonValueAsInt32 = bsonValueClass.getMethod("asInt32");

                // BsonInt32
                Class<?> bsonInt32Class = Class.forName("org.bson.BsonInt32");
                bsonInt32GetValue = bsonInt32Class.getMethod("getValue");
                bsonInt32Ctor = bsonInt32Class.getConstructor(int.class);

                available = true;
                LOGGER.info("[ReforgeMetadata] Metadata API initialized successfully.");
            } catch (Exception e) {
                available = false;
                LOGGER.warn("[ReforgeMetadata] Metadata API NOT available — falling back to DataStore. Reason: {}", e.getMessage());
            } finally {
                initialized = true;
            }
        }
    }

    /**
     * Check if the metadata API is available at runtime.
     */
    public static boolean isAvailable() {
        init();
        return available;
    }

    /**
     * Read the reforge level stored in an item's metadata.
     *
     * @param item the item stack
     * @return reforge level (0 if none or metadata unavailable)
     */
    public static int getReforgeLevel(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return 0;
        init();
        if (!available) return 0;

        try {
            Object metadata = getMetadataMethod.invoke(item);
            if (metadata == null) return 0;

            Boolean hasKey = (Boolean) bsonDocContainsKey.invoke(metadata, META_KEY);
            if (!hasKey) return 0;

            Object bsonValue = bsonDocGet.invoke(metadata, META_KEY);
            if (bsonValue == null) return 0;

            Object bsonInt32 = bsonValueAsInt32.invoke(bsonValue);
            return (int) bsonInt32GetValue.invoke(bsonInt32);
        } catch (Exception e) {
            LOGGER.debug("[ReforgeMetadata] Failed to read reforge_level: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Write the reforge level into an item's metadata.
     * Returns a NEW ItemStack with the updated metadata (ItemStack is immutable).
     *
     * @param item  the original item
     * @param level the reforge level to write
     * @return new ItemStack with metadata set, or the original item if metadata API unavailable
     */
    @Nonnull
    public static ItemStack setReforgeLevel(@Nonnull ItemStack item, int level) {
        init();
        if (!available) {
            LOGGER.warn("[ReforgeMetadata] Cannot set metadata — API not available");
            return item;
        }

        try {
            Object bsonInt32 = bsonInt32Ctor.newInstance(level);
            ItemStack result = (ItemStack) withMetadataMethod.invoke(item, META_KEY, bsonInt32);
            LOGGER.debug("[ReforgeMetadata] Set reforge_level={} on item {}", level, item.getItemId());
            return result;
        } catch (Exception e) {
            LOGGER.warn("[ReforgeMetadata] Failed to set reforge_level: {}", e.getMessage());
            return item;
        }
    }
}
