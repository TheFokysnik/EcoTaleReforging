package com.crystalrealm.ecotalereforging.service;

import com.crystalrealm.ecotalereforging.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads weapon/armor stats directly from Hytale's internal Item asset store
 * and RootInteraction AssetStore via reflection.
 *
 * <p>Weapon damage in Hytale is stored inside {@code interactionVars} as
 * contained RootInteraction assets. This service discovers the RootInteraction
 * class from the Item's {@code data.containedRawAssets} map, then queries
 * the RootInteraction AssetStore for actual damage values.</p>
 */
public class WeaponStatsService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    // ── Item reflection handles ─────────────────────────────
    private Method itemGetAssetStore;
    private Method assetStoreGetMap;
    private Method assetMapGetAsset;
    private Method itemGetWeapon;
    private Field  weaponRawStatMods;
    private Field  modifierAmount;
    private Field  itemQualityId;
    private Field  itemLevelField;
    private Field  interactionVarsField;
    private Field  maxDurabilityField;
    private Method assetMapGetAssetMap;

    // ── RootInteraction reflection handles ───────────────────
    private Class<?> rootInteractionClass;
    private Method   riGetAssetStore;
    private Method   riAssetStoreGetMap;
    private Method   riAssetMapGetAsset;
    private Method   riAssetMapGetAssetMap;
    private boolean  riAvailable = false;

    // ── Armor reflection handles ────────────────────────────
    private Method itemGetArmor;
    private Field  armorRawStatMods;
    private boolean armorAvailable = false;

    private boolean initialized = false;
    private boolean available = false;
    private int dumpCount = 0;

    private final Map<String, WeaponStats> cache = new ConcurrentHashMap<>();
    private final Map<String, ArmorStats> armorCache = new ConcurrentHashMap<>();

    // ════════════════════════════════════════════════════════
    //  INIT
    // ════════════════════════════════════════════════════════

    public void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> itemClass = Class.forName(
                    "com.hypixel.hytale.server.core.asset.type.item.config.Item");
            itemGetAssetStore = itemClass.getMethod("getAssetStore");

            Class<?> assetStoreClass = itemGetAssetStore.getReturnType();
            assetStoreGetMap = assetStoreClass.getMethod("getAssetMap");

            Class<?> assetMapClass = assetStoreGetMap.getReturnType();
            assetMapGetAsset = assetMapClass.getMethod("getAsset", Object.class);
            assetMapGetAssetMap = assetMapClass.getMethod("getAssetMap");

            itemGetWeapon = itemClass.getMethod("getWeapon");

            itemQualityId        = getDeclaredFieldSafe(itemClass, "qualityId");
            itemLevelField       = getDeclaredFieldSafe(itemClass, "itemLevel");
            interactionVarsField = getDeclaredFieldSafe(itemClass, "interactionVars");
            maxDurabilityField   = getDeclaredFieldSafe(itemClass, "maxDurability");

            Class<?> weaponClass = Class.forName(
                    "com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon");
            weaponRawStatMods = weaponClass.getDeclaredField("rawStatModifiers");
            weaponRawStatMods.setAccessible(true);

            Class<?> modClass = Class.forName(
                    "com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier");
            modifierAmount = modClass.getDeclaredField("amount");
            modifierAmount.setAccessible(true);

            available = true;
            LOGGER.info("WeaponStatsService initialised — fields: level={}, vars={}, quality={}, durability={}",
                    itemLevelField != null, interactionVarsField != null,
                    itemQualityId != null, maxDurabilityField != null);

            initArmorReflection(itemClass);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Item asset class not found — weapon stats disabled: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("Failed to initialise WeaponStatsService: {}", e.getMessage());
        }
    }

    private void initArmorReflection(Class<?> itemClass) {
        try {
            itemGetArmor = itemClass.getMethod("getArmor");
            Class<?> armorClass = itemGetArmor.getReturnType();
            for (String fname : new String[]{"rawStatModifiers", "statModifiers"}) {
                try {
                    armorRawStatMods = armorClass.getDeclaredField(fname);
                    armorRawStatMods.setAccessible(true);
                    armorAvailable = true;
                    LOGGER.info("Armor stats reflection initialised (field: {})", fname);
                    return;
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.info("Armor reflection unavailable: {}", e.getMessage());
        }
    }

    /**
     * Discover the RootInteraction class by scanning an Item's data.containedRawAssets map.
     * The keys of that map are Class objects — one of them is RootInteraction.
     */
    private void discoverRootInteraction() {
        if (riAvailable) return;
        try {
            Object store = itemGetAssetStore.invoke(null);
            Object map   = assetStoreGetMap.invoke(store);
            @SuppressWarnings("unchecked")
            Map<String, ?> allItems = (Map<String, ?>) assetMapGetAssetMap.invoke(map);

            // Find any weapon item to scan its contained assets
            for (var entry : allItems.entrySet()) {
                Object item = entry.getValue();
                Object weapon = itemGetWeapon.invoke(item);
                if (weapon == null) continue;

                // Navigate to Item.data field
                Field dataField = getDeclaredFieldSafe(item.getClass(), "data");
                if (dataField == null) continue;
                Object data = dataField.get(item);
                if (data == null) continue;

                // Navigate to data.containedRawAssets → Map<Class<?>, Map<String, ?>>
                Field containedRawField = getDeclaredFieldSafe(data.getClass(), "containedRawAssets");
                if (containedRawField == null) {
                    // Try alternate name
                    containedRawField = getDeclaredFieldSafe(data.getClass(), "containedRaw");
                }
                if (containedRawField == null) {
                    LOGGER.warn("[WS] data.containedRawAssets field not found. Data class: {}", data.getClass().getName());
                    // Log all fields of data for diagnostics
                    for (Field f : data.getClass().getDeclaredFields()) {
                        LOGGER.info("[WS]   data field: {}({})", f.getName(), f.getType().getSimpleName());
                    }
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<Object, ?> containedRaw = (Map<Object, ?>) containedRawField.get(data);
                if (containedRaw == null || containedRaw.isEmpty()) continue;

                // The keys are Class objects — find the one that's RootInteraction
                for (Object key : containedRaw.keySet()) {
                    if (key instanceof Class<?>) {
                        Class<?> clazz = (Class<?>) key;
                        if (clazz.getSimpleName().contains("RootInteraction")
                                || clazz.getSimpleName().contains("Interaction")) {
                            rootInteractionClass = clazz;
                            LOGGER.info("[WS] Discovered RootInteraction class: {}", clazz.getName());
                            break;
                        }
                    }
                }
                if (rootInteractionClass != null) break;
            }

            if (rootInteractionClass == null) {
                LOGGER.warn("[WS] Could not discover RootInteraction class from any Item");
                return;
            }

            // Get AssetStore methods on RootInteraction
            riGetAssetStore = rootInteractionClass.getMethod("getAssetStore");
            Class<?> riStoreClass = riGetAssetStore.getReturnType();
            riAssetStoreGetMap = riStoreClass.getMethod("getAssetMap");
            Class<?> riMapClass = riAssetStoreGetMap.getReturnType();
            riAssetMapGetAsset = riMapClass.getMethod("getAsset", Object.class);
            riAssetMapGetAssetMap = riMapClass.getMethod("getAssetMap");

            // Count total interactions
            Object riStore = riGetAssetStore.invoke(null);
            Object riMap   = riAssetStoreGetMap.invoke(riStore);
            @SuppressWarnings("unchecked")
            Map<String, ?> allRI = (Map<String, ?>) riAssetMapGetAssetMap.invoke(riMap);
            LOGGER.info("[WS] RootInteraction AssetStore: {} total entries", allRI.size());

            riAvailable = true;
        } catch (Exception e) {
            LOGGER.warn("[WS] Failed to discover RootInteraction: {}", e.getMessage());
        }
    }

    @Nullable
    private static Field getDeclaredFieldSafe(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
            current = current.getSuperclass();
        }
        return null;
    }

    private void logWeaponCount() {
        try {
            Object store = itemGetAssetStore.invoke(null);
            Object map   = assetStoreGetMap.invoke(store);
            @SuppressWarnings("unchecked")
            Map<String, ?> all = (Map<String, ?>) assetMapGetAssetMap.invoke(map);
            int weapons = 0;
            for (var entry : all.entrySet()) {
                Object weapon = itemGetWeapon.invoke(entry.getValue());
                if (weapon != null) weapons++;
            }
            LOGGER.info("[WS] Item AssetStore: {} total items, {} weapons", all.size(), weapons);
        } catch (Exception e) {
            LOGGER.warn("[WS] Failed to count weapons: {}", e.getMessage());
        }
    }

    public boolean isAvailable() { return available; }

    public void lateInit() {
        if (!available) return;
        logWeaponCount();
        discoverRootInteraction();
    }

    // ════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════

    @Nullable
    public WeaponStats getWeaponStats(@Nonnull String itemId) {
        if (!available) return null;
        String name = stripNamespace(itemId);
        WeaponStats cached = cache.get(name);
        if (cached != null) return cached;

        WeaponStats stats = loadWeaponStats(name);
        if (stats != null) cache.put(name, stats);
        return stats;
    }

    public void clearCache() {
        cache.clear();
        armorCache.clear();
        LOGGER.info("[WS] Cache cleared");
    }

    @Nullable
    public ArmorStats getArmorStats(@Nonnull String itemId) {
        if (!available || !armorAvailable) return null;
        String name = stripNamespace(itemId);
        ArmorStats cached = armorCache.get(name);
        if (cached != null) return cached;

        ArmorStats stats = loadArmorStats(name);
        if (stats != null) armorCache.put(name, stats);
        return stats;
    }

    private static String stripNamespace(String itemId) {
        return itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
    }

    // ════════════════════════════════════════════════════════
    //  WEAPON LOADING
    // ════════════════════════════════════════════════════════

    @Nullable
    private WeaponStats loadWeaponStats(String name) {
        try {
            Object store = itemGetAssetStore.invoke(null);
            Object map   = assetStoreGetMap.invoke(store);
            Object item  = assetMapGetAsset.invoke(map, name);
            if (item == null) item = assetMapGetAsset.invoke(map, "hytale:" + name);
            if (item == null) return null;

            Object weapon = itemGetWeapon.invoke(item);
            if (weapon == null) return null;

            // ── Item-level properties ──
            String quality       = readStringField(item, itemQualityId);
            int    itemLevel     = readIntField(item, itemLevelField, 0);
            double maxDurability = readDoubleField(item, maxDurabilityField, 0);

            // ── Read interactionVars + extract damage via RootInteraction ──
            Map<String, Double> attackDamages = new LinkedHashMap<>();
            double maxDamage = 0, sumDamage = 0;
            int attackCount = 0;

            if (interactionVarsField != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, ?> vars = (Map<String, ?>) interactionVarsField.get(item);
                    if (vars != null) {
                        for (var vEntry : vars.entrySet()) {
                            String varKey = vEntry.getKey();
                            if (!varKey.toLowerCase().contains("damage")) continue;
                            attackCount++;

                            // Try to look up RootInteraction and extract damage
                            if (riAvailable && vEntry.getValue() instanceof String) {
                                String ref = (String) vEntry.getValue();
                                double dmg = lookupInteractionDamage(ref, varKey);
                                if (dmg > 0) {
                                    attackDamages.put(varKey, dmg);
                                    if (dmg > maxDamage) maxDamage = dmg;
                                    sumDamage += dmg;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("[WS] Failed to read interactionVars for '{}': {}", name, e.getMessage());
                }
            }

            double avgDamage = attackDamages.isEmpty() ? 0 : sumDamage / attackDamages.size();

            // ── Passive stats from rawStatModifiers (SignatureEnergy etc.) ──
            double signatureEnergy = 0;
            Map<String, Double> passiveStats = new LinkedHashMap<>();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object[]> rawMods =
                        (Map<String, Object[]>) weaponRawStatMods.get(weapon);
                if (rawMods != null) {
                    for (var entry : rawMods.entrySet()) {
                        String statName = entry.getKey();
                        Object[] mods   = entry.getValue();
                        if (mods == null || mods.length == 0) continue;

                        double total = 0;
                        for (Object mod : mods) total += modifierAmount.getFloat(mod);

                        if (statName.toLowerCase().contains("signature")) {
                            signatureEnergy = total;
                        }
                        passiveStats.put(statName, total);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[WS] rawStatModifiers read failed for '{}': {}", name, e.getMessage());
            }

            LOGGER.info("[WS] Loaded '{}': level={}, quality={}, dur={}, attacks={}/{}, " +
                            "avg={}, max={}, SE={}, damages={}",
                    name, itemLevel, quality,
                    String.format("%.0f", maxDurability),
                    attackDamages.size(), attackCount,
                    String.format("%.1f", avgDamage),
                    String.format("%.1f", maxDamage),
                    String.format("%.1f", signatureEnergy),
                    attackDamages);

            return new WeaponStats(quality, itemLevel, maxDurability,
                    signatureEnergy, maxDamage, avgDamage,
                    attackCount, attackDamages, passiveStats);
        } catch (Exception e) {
            LOGGER.warn("[WS] Failed to load weapon stats for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  ROOT INTERACTION DAMAGE EXTRACTION
    // ════════════════════════════════════════════════════════

    /**
     * Look up a RootInteraction by ref key and extract damage.
     * The ref may start with '*' (contained asset prefix).
     *
     * <p>RootInteraction data is NOT stored in regular Java fields — it uses
     * a CODEC-based pattern where data lives in the parent class's generic
     * container or is accessible only via getter methods.</p>
     */
    private double lookupInteractionDamage(String ref, String varKey) {
        try {
            Object riStore = riGetAssetStore.invoke(null);
            Object riMap   = riAssetStoreGetMap.invoke(riStore);

            // Try with original ref (which starts with *)
            Object ri = riAssetMapGetAsset.invoke(riMap, ref);
            if (ri == null) {
                // Try without * prefix
                String noStar = ref.startsWith("*") ? ref.substring(1) : ref;
                ri = riAssetMapGetAsset.invoke(riMap, noStar);
            }

            if (ri == null) {
                LOGGER.debug("[WS-RI] RootInteraction not found for ref: {}", ref);
                return 0;
            }

            // Diagnostic dump for first two interactions found (methods + fields)
            if (dumpCount < 2) {
                dumpCount++;
                dumpFullDiagnostics("[WS-RI] RI '" + varKey + "'", ri);
            }

            // Try to extract damage from the RootInteraction hierarchy
            return extractDamageValue(ri);
        } catch (Exception e) {
            LOGGER.debug("[WS-RI] Failed to lookup '{}': {}", varKey, e.getMessage());
            return 0;
        }
    }

    /**
     * Extract damage from a RootInteraction. Tries multiple approaches:
     * 1) Getter METHODS on the instance (getInteractions, etc.)
     * 2) CODEC getter Functions
     * 3) Generic data containers in parent classes (Map, Object[], List)
     * 4) Direct field access (original approach, as fallback)
     */
    private double extractDamageValue(Object ri) {
        try {
            // ── 1) Try getter methods for child interactions ──
            double dmg = tryExtractViaGetterMethods(ri);
            if (dmg > 0) return dmg;

            // ── 2) Try CODEC-based getter extraction ──
            dmg = tryExtractViaCodec(ri);
            if (dmg > 0) return dmg;

            // ── 3) Scan ALL no-arg methods returning collections/arrays ──
            dmg = tryExtractFromAllMethods(ri);
            if (dmg > 0) return dmg;

            // ── 4) Scan generic data containers in parent classes ──
            dmg = tryExtractFromGenericContainers(ri);
            if (dmg > 0) return dmg;

            // ── 5) Fallback: direct field-based approach ──
            return tryExtractFromFields(ri);
        } catch (Exception e) {
            LOGGER.debug("[WS-RI] extractDamageValue error: {}", e.getMessage());
            return 0;
        }
    }

    /** Approach 1: Try known getter method names. */
    private double tryExtractViaGetterMethods(Object ri) {
        String[] methodNames = {
                "getInteractions", "interactions", "getInteraction",
                "getEffects", "effects", "getActions", "actions"
        };
        for (String methodName : methodNames) {
            try {
                Method m = ri.getClass().getMethod(methodName);
                m.setAccessible(true);
                Object result = m.invoke(ri);
                if (result != null) {
                    double dmg = extractDamageFromContainer(result);
                    if (dmg > 0) return dmg;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                LOGGER.debug("[WS-RI] Method {}() failed: {}", methodName, e.getMessage());
            }
        }
        return 0;
    }

    /** Approach 2: Try extracting data via CODEC getter Functions. */
    private double tryExtractViaCodec(Object ri) {
        try {
            // Find the CODEC static field
            Field codecField = getDeclaredFieldSafe(ri.getClass(), "CODEC");
            if (codecField == null) return 0;

            Object codec = codecField.get(null); // static field
            if (codec == null) return 0;

            // The CODEC has an "entries" field (Map<String, ?>) or similar
            // Try to find entries/fields/getters
            Field entriesField = findFieldByName(codec.getClass(), "entries", "fields", "codecs", "members");
            if (entriesField == null) return 0;

            Object entries = entriesField.get(codec);
            if (!(entries instanceof Map)) return 0;

            Map<?, ?> entryMap = (Map<?, ?>) entries;
            // Look for "Interactions" entry
            Object interactionsEntry = entryMap.get("Interactions");
            if (interactionsEntry == null) return 0;

            // The entry might have a "getter" field (Function<T, V>)
            Field getterField = findFieldByName(interactionsEntry.getClass(),
                    "getter", "accessor", "get", "extractor", "reader");
            if (getterField != null) {
                Object getter = getterField.get(interactionsEntry);
                if (getter instanceof java.util.function.Function) {
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<Object, Object> fn =
                            (java.util.function.Function<Object, Object>) getter;
                    Object interactions = fn.apply(ri);
                    if (interactions != null) {
                        double dmg = extractDamageFromContainer(interactions);
                        if (dmg > 0) return dmg;
                    }
                }
            }

            // Alternative: the entry itself might have an encode/decode method
            for (Method m : interactionsEntry.getClass().getMethods()) {
                String mn = m.getName().toLowerCase();
                if ((mn.contains("encode") || mn.contains("get") || mn.contains("extract"))
                        && m.getParameterCount() == 1) {
                    try {
                        Object result = m.invoke(interactionsEntry, ri);
                        if (result != null) {
                            double dmg = extractDamageFromContainer(result);
                            if (dmg > 0) return dmg;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[WS-RI] CODEC extraction failed: {}", e.getMessage());
        }
        return 0;
    }

    /** Approach 3: Scan ALL no-arg methods returning collections/arrays/objects. */
    private double tryExtractFromAllMethods(Object ri) {
        try {
            for (Method m : ri.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (m.getDeclaringClass() == Object.class) continue;
                if (m.getReturnType() == void.class) continue;
                if (m.getReturnType() == Class.class) continue;

                String mn = m.getName().toLowerCase();
                // Skip static utility methods
                if (mn.equals("getassetstore") || mn.equals("getassetmap")
                        || mn.equals("hashcode") || mn.equals("tostring")) continue;

                try {
                    m.setAccessible(true);
                    Object result = m.invoke(ri);
                    if (result == null) continue;

                    // If returns collection/array — check for interactions/effects inside
                    if (result.getClass().isArray() || result instanceof Collection) {
                        double dmg = extractDamageFromContainer(result);
                        if (dmg > 0) return dmg;
                    }

                    // If returns a numeric type with a damage-sounding name
                    if (mn.contains("damage") || mn.contains("amount") || mn.contains("value")) {
                        if (result instanceof Number) {
                            double d = ((Number) result).doubleValue();
                            if (d > 0) return d;
                        }
                    }

                    // If returns a complex object, drill deeper
                    if (!isPrimitive(result) && !result.getClass().isArray()
                            && !(result instanceof Collection) && !(result instanceof Map)) {
                        double dmg = tryExtractFromFields(result);
                        if (dmg > 0) return dmg;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.debug("[WS-RI] Method scan failed: {}", e.getMessage());
        }
        return 0;
    }

    /** Approach 4: Look for generic data containers in all parent classes. */
    private double tryExtractFromGenericContainers(Object ri) {
        Class<?> current = ri.getClass();
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object value = f.get(ri);
                    if (value == null) continue;

                    // If it's a Map — look for "Interactions" or damage-related keys
                    if (value instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) value;
                        for (var entry : map.entrySet()) {
                            String key = String.valueOf(entry.getKey()).toLowerCase();
                            if (key.contains("interaction") || key.contains("effect")
                                    || key.contains("damage")) {
                                Object val = entry.getValue();
                                if (val != null) {
                                    double dmg = extractDamageFromContainer(val);
                                    if (dmg > 0) return dmg;
                                    dmg = tryReadNumericValue(val);
                                    if (dmg > 0) return dmg;
                                }
                            }
                        }
                    }

                    // If it's an Object[] — might be CODEC-indexed data slots
                    if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
                        int len = Array.getLength(value);
                        for (int i = 0; i < len; i++) {
                            Object elem = Array.get(value, i);
                            if (elem == null) continue;
                            // Try to find damage in each slot
                            double dmg = tryExtractFromFields(elem);
                            if (dmg > 0) return dmg;
                            dmg = extractDamageFromContainer(elem);
                            if (dmg > 0) return dmg;
                        }
                    }

                    // If it's a List — similar treatment
                    if (value instanceof java.util.List) {
                        for (Object elem : (java.util.List<?>) value) {
                            if (elem == null) continue;
                            double dmg = tryExtractFromFields(elem);
                            if (dmg > 0) return dmg;
                        }
                    }
                } catch (Exception ignored) {}
            }
            current = current.getSuperclass();
        }
        return 0;
    }

    /** Approach 5 (fallback): Direct field access on the object. */
    private double tryExtractFromFields(Object obj) {
        if (obj == null) return 0;

        // Try direct damage fields
        double dmg = tryReadNumericValue(obj);
        if (dmg > 0) return dmg;

        // Try "interactions" field
        String[] containerFields = {"interactions", "Interactions", "effects", "Effects",
                "actions", "Actions", "children", "Children"};
        for (String fn : containerFields) {
            Field f = getDeclaredFieldSafe(obj.getClass(), fn);
            if (f == null) continue;
            try {
                Object val = f.get(obj);
                if (val != null) {
                    dmg = extractDamageFromContainer(val);
                    if (dmg > 0) return dmg;
                }
            } catch (Exception ignored) {}
        }

        return 0;
    }

    /** Extract damage from a collection/array of interactions/effects. */
    private double extractDamageFromContainer(Object container) {
        if (container == null) return 0;

        java.util.List<Object> items = toObjectList(container);
        for (Object item : items) {
            if (item == null) continue;

            // Try direct numeric value
            double dmg = tryReadNumericValue(item);
            if (dmg > 0) return dmg;

            // Try "effects" sub-container
            String[] subFields = {"effects", "Effects", "actions", "Actions"};
            for (String fn : subFields) {
                Field f = getDeclaredFieldSafe(item.getClass(), fn);
                if (f == null) continue;
                try {
                    Object sub = f.get(item);
                    if (sub != null) {
                        java.util.List<Object> subItems = toObjectList(sub);
                        for (Object sItem : subItems) {
                            double d = tryReadNumericValue(sItem);
                            if (d > 0) return d;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Try getter methods for effects
            for (String methodName : new String[]{"getEffects", "effects", "getActions"}) {
                try {
                    Method m = item.getClass().getMethod(methodName);
                    Object sub = m.invoke(item);
                    if (sub != null) {
                        java.util.List<Object> subItems = toObjectList(sub);
                        for (Object sItem : subItems) {
                            double d = tryReadNumericValue(sItem);
                            if (d > 0) return d;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    /** Try reading damage/amount/value numeric fields or methods. */
    private double tryReadNumericValue(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();

        // Try known field names
        String[] fieldNames = {"damage", "Damage", "amount", "Amount", "value", "Value",
                "baseDamage", "BaseDamage", "baseAmount", "BaseAmount",
                "flatDamage", "FlatDamage", "damageAmount", "DamageAmount"};
        for (String fn : fieldNames) {
            Field f = getDeclaredFieldSafe(obj.getClass(), fn);
            if (f == null) continue;
            try {
                Class<?> type = f.getType();
                if (type == float.class)  return f.getFloat(obj);
                if (type == double.class) return f.getDouble(obj);
                if (type == int.class)    return f.getInt(obj);
                Object val = f.get(obj);
                if (val instanceof Number) return ((Number) val).doubleValue();
            } catch (Exception ignored) {}
        }

        // Try known method names
        String[] methodNames = {"getDamage", "getAmount", "getValue",
                "damage", "amount", "value", "getBaseDamage"};
        for (String mn : methodNames) {
            try {
                Method m = obj.getClass().getMethod(mn);
                Object val = m.invoke(obj);
                if (val instanceof Number) return ((Number) val).doubleValue();
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {}
        }
        return 0;
    }

    /** Convert array/Collection/single to List. */
    private java.util.List<Object> toObjectList(Object obj) {
        if (obj == null) return Collections.emptyList();
        java.util.List<Object> result = new java.util.ArrayList<>();
        if (obj.getClass().isArray()) {
            int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) result.add(Array.get(obj, i));
        } else if (obj instanceof Collection) {
            result.addAll((Collection<?>) obj);
        } else {
            result.add(obj);
        }
        return result;
    }

    /** Find a field by trying multiple candidate names. */
    @Nullable
    private static Field findFieldByName(Class<?> clazz, String... names) {
        for (String name : names) {
            Field f = getDeclaredFieldSafe(clazz, name);
            if (f != null) return f;
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  ARMOR LOADING
    // ════════════════════════════════════════════════════════

    @Nullable
    private ArmorStats loadArmorStats(String name) {
        try {
            Object store = itemGetAssetStore.invoke(null);
            Object map   = assetStoreGetMap.invoke(store);
            Object item  = assetMapGetAsset.invoke(map, name);
            if (item == null) item = assetMapGetAsset.invoke(map, "hytale:" + name);
            if (item == null) return null;

            Object armor = itemGetArmor.invoke(item);
            if (armor == null) return null;

            String quality = readStringField(item, itemQualityId);

            @SuppressWarnings("unchecked")
            Map<String, Object[]> rawMods =
                    (Map<String, Object[]>) armorRawStatMods.get(armor);

            Map<String, Double> stats = new LinkedHashMap<>();
            double health = 0, defense = 0;

            if (rawMods != null) {
                for (var entry : rawMods.entrySet()) {
                    String statName = entry.getKey();
                    Object[] mods   = entry.getValue();
                    if (mods == null || mods.length == 0) continue;

                    double total = 0;
                    for (Object mod : mods) total += modifierAmount.getFloat(mod);

                    stats.put(statName, total);

                    String lower = statName.toLowerCase();
                    if (lower.contains("health") || lower.contains("hp")
                            || lower.contains("maxhealth")) {
                        health += total;
                    } else if (lower.contains("defense") || lower.contains("def")
                            || lower.contains("armor") || lower.contains("protection")) {
                        defense += total;
                    }
                }
            }

            LOGGER.info("[WS] Loaded armor '{}': HP={}, DEF={}, quality={}, stats={}",
                    name, String.format("%.1f", health), String.format("%.1f", defense),
                    quality, stats.keySet());

            return new ArmorStats(health, defense, quality, stats);
        } catch (Exception e) {
            LOGGER.warn("[WS] Failed to load armor stats for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    @Nullable
    private static String readStringField(Object obj, @Nullable Field field) {
        if (field == null) return null;
        try { return (String) field.get(obj); } catch (Exception e) { return null; }
    }

    private static int readIntField(Object obj, @Nullable Field field, int fallback) {
        if (field == null) return fallback;
        try { return field.getInt(obj); } catch (Exception e) { return fallback; }
    }

    private static double readDoubleField(Object obj, @Nullable Field field, double fallback) {
        if (field == null) return fallback;
        try { return field.getDouble(obj); } catch (Exception e) { return fallback; }
    }

    // ════════════════════════════════════════════════════════
    //  DIAGNOSTIC DUMP (enhanced — methods + fields + static flag)
    // ════════════════════════════════════════════════════════

    /**
     * Comprehensive diagnostic dump: all fields (static/instance) AND
     * all no-arg getter methods + their return values.
     */
    private void dumpFullDiagnostics(String prefix, Object obj) {
        if (obj == null) { LOGGER.info("{} = null", prefix); return; }
        Class<?> clazz = obj.getClass();
        LOGGER.info("{} [class={}]", prefix, clazz.getName());

        // ── Superclass chain ──
        StringBuilder chain = new StringBuilder();
        for (Class<?> c = clazz.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (chain.length() > 0) chain.append(" → ");
            chain.append(c.getSimpleName());
        }
        if (chain.length() > 0) LOGGER.info("{}   extends: {}", prefix, chain);

        // ── INSTANCE fields (skip static) ──
        LOGGER.info("{}   ── INSTANCE FIELDS ──", prefix);
        Class<?> current = clazz;
        int instanceFieldCount = 0;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                instanceFieldCount++;
                try {
                    f.setAccessible(true);
                    Object value = f.get(obj);
                    String typeName = f.getType().getSimpleName();
                    String declaring = current == clazz ? "" : " [from " + current.getSimpleName() + "]";

                    if (value == null) {
                        LOGGER.info("{}     {}({}) = null{}", prefix, f.getName(), typeName, declaring);
                    } else if (value instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) value;
                        LOGGER.info("{}     {}({}) = Map[{}] keys={}{}", prefix, f.getName(), typeName, m.size(), m.keySet(), declaring);
                        // Dump map values for small maps
                        if (m.size() <= 12) {
                            for (var e : m.entrySet()) {
                                Object v = e.getValue();
                                if (v == null) {
                                    LOGGER.info("{}       [{}] = null", prefix, e.getKey());
                                } else if (v.getClass().isArray()) {
                                    LOGGER.info("{}       [{}] = {}[{}]", prefix, e.getKey(), v.getClass().getComponentType().getSimpleName(), Array.getLength(v));
                                } else {
                                    LOGGER.info("{}       [{}] = {} ({})", prefix, e.getKey(), truncate(v), v.getClass().getSimpleName());
                                }
                            }
                        }
                    } else if (value.getClass().isArray()) {
                        int len = Array.getLength(value);
                        LOGGER.info("{}     {}({}[]) = [{}]{}", prefix, f.getName(),
                                value.getClass().getComponentType().getSimpleName(), len, declaring);
                        // Dump first 3 elements
                        for (int i = 0; i < Math.min(3, len); i++) {
                            Object elem = Array.get(value, i);
                            LOGGER.info("{}       [{}] = {} ({})", prefix, i,
                                    truncate(elem), elem != null ? elem.getClass().getSimpleName() : "null");
                        }
                    } else if (value instanceof Collection) {
                        Collection<?> c2 = (Collection<?>) value;
                        LOGGER.info("{}     {}({}) = Collection[{}]{}", prefix, f.getName(), typeName, c2.size(), declaring);
                    } else if (isPrimitive(value)) {
                        LOGGER.info("{}     {}({}) = {}{}", prefix, f.getName(), typeName, value, declaring);
                    } else {
                        LOGGER.info("{}     {}({}) = {} [{}]{}", prefix, f.getName(), typeName, truncate(value), value.getClass().getSimpleName(), declaring);
                    }
                } catch (Exception e) {
                    LOGGER.info("{}     {}(?) = <err: {}>{}", prefix, f.getName(), e.getMessage(),
                            current == clazz ? "" : " [from " + current.getSimpleName() + "]");
                }
            }
            current = current.getSuperclass();
        }
        if (instanceFieldCount == 0) {
            LOGGER.info("{}     (none — data likely in parent generic container)", prefix);
        }

        // ── STATIC fields (brief) ──
        LOGGER.info("{}   ── STATIC FIELDS ──", prefix);
        current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    LOGGER.info("{}     {}({}) = {}", prefix, f.getName(), f.getType().getSimpleName(),
                            value != null ? value.getClass().getSimpleName() : "null");
                } catch (Exception e) {
                    LOGGER.info("{}     {}(?) = <err>", prefix, f.getName());
                }
            }
            current = current.getSuperclass();
        }

        // ── NO-ARG METHODS ──
        LOGGER.info("{}   ── METHODS (no-arg, non-void) ──", prefix);
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() == void.class) continue;
            if (m.getDeclaringClass() == Object.class) continue;

            try {
                m.setAccessible(true);
                Object result = m.invoke(obj);
                String ret = m.getReturnType().getSimpleName();
                if (result == null) {
                    LOGGER.info("{}     {}() → null [{}]", prefix, m.getName(), ret);
                } else if (result.getClass().isArray()) {
                    LOGGER.info("{}     {}() → {}[{}]", prefix, m.getName(), ret, Array.getLength(result));
                } else if (result instanceof Collection) {
                    LOGGER.info("{}     {}() → Collection[{}] [{}]", prefix, m.getName(),
                            ((Collection<?>) result).size(), ret);
                } else if (result instanceof Map) {
                    LOGGER.info("{}     {}() → Map[{}] [{}]", prefix, m.getName(),
                            ((Map<?, ?>) result).size(), ret);
                } else {
                    LOGGER.info("{}     {}() → {} [{}]", prefix, m.getName(), truncate(result), ret);
                }
            } catch (Exception e) {
                LOGGER.info("{}     {}() → <err: {}>", prefix, m.getName(), e.getMessage());
            }
        }
    }

    private static String truncate(Object obj) {
        if (obj == null) return "null";
        String s = String.valueOf(obj);
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private static boolean isPrimitive(Object v) {
        return v instanceof Number || v instanceof String
                || v instanceof Boolean || v instanceof Character || v instanceof Enum;
    }

    // ════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ════════════════════════════════════════════════════════

    public static class WeaponStats {
        public final String quality;
        public final int    itemLevel;
        public final double maxDurability;
        public final double signatureEnergy;
        public final double maxDamage;
        public final double avgDamage;
        public final int    attackCount;
        public final Map<String, Double> attackDamages;
        public final Map<String, Double> passiveStats;

        public WeaponStats(String quality, int itemLevel, double maxDurability,
                           double signatureEnergy, double maxDamage, double avgDamage,
                           int attackCount, Map<String, Double> attackDamages,
                           Map<String, Double> passiveStats) {
            this.quality         = quality;
            this.itemLevel       = itemLevel;
            this.maxDurability   = maxDurability;
            this.signatureEnergy = signatureEnergy;
            this.maxDamage       = maxDamage;
            this.avgDamage       = avgDamage;
            this.attackCount     = attackCount;
            this.attackDamages   = attackDamages != null
                    ? Collections.unmodifiableMap(attackDamages)
                    : Collections.emptyMap();
            this.passiveStats    = passiveStats != null
                    ? Collections.unmodifiableMap(passiveStats)
                    : Collections.emptyMap();
        }

        public String qualityLabel() { return quality != null ? quality : ""; }
        public String formatDamage()    { return String.format("%.1f", avgDamage); }
        public String formatMaxDamage() { return String.format("%.1f", maxDamage); }
        public boolean hasDamageData()  { return !attackDamages.isEmpty(); }
    }

    public static class ArmorStats {
        public final double health;
        public final double defense;
        public final String quality;
        public final Map<String, Double> statModifiers;

        public ArmorStats(double health, double defense, String quality,
                          Map<String, Double> statModifiers) {
            this.health        = health;
            this.defense       = defense;
            this.quality       = quality;
            this.statModifiers = statModifiers != null
                    ? Collections.unmodifiableMap(statModifiers)
                    : Collections.emptyMap();
        }
    }
}
