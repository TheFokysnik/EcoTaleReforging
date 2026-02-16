package com.crystalrealm.ecotalereforging.service;

import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.model.ReforgeAttemptInfo;
import com.crystalrealm.ecotalereforging.model.ReforgeResult;
import com.crystalrealm.ecotalereforging.util.PluginLogger;
import com.crystalrealm.ecotalereforging.util.ReforgeMetadataHelper;
import com.ecotale.api.EcotaleAPI;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core reforging logic.
 *
 * <ul>
 *   <li>Validates item + materials + coins</li>
 *   <li>Rolls success/failure based on configured chance</li>
 *   <li>On success: persists +1 ReforgeLevel in {@link ReforgeDataStore}</li>
 *   <li>On failure: destroys item, returns materials via reverse crafting</li>
 * </ul>
 *
 * Thread-safety: uses a per-player lock set to prevent double-click exploits.
 */
public class ReforgeService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final ReforgeConfig config;
    private final ItemValidationService validator;
    private final ReforgeDataStore dataStore;

    /** Active reforges — prevents double-click. */
    private final Set<UUID> activeReforges = ConcurrentHashMap.newKeySet();

    public ReforgeService(@Nonnull ReforgeConfig config,
                          @Nonnull ItemValidationService validator,
                          @Nonnull ReforgeDataStore dataStore) {
        this.config = config;
        this.validator = validator;
        this.dataStore = dataStore;
    }

    // ═══════════════════════════════════════════════════════
    //  ATTEMPT REFORGE
    // ═══════════════════════════════════════════════════════

    /**
     * Attempt to reforge the player's held item.
     *
     * @param player     the player
     * @param playerUuid the player's UUID
     * @return attempt info with result, or null if pre-checks fail
     */
    @Nullable
    public ReforgeAttemptInfo attemptReforge(@Nonnull Player player,
                                             @Nonnull UUID playerUuid) {
        return attemptReforgeAtSlot(player, playerUuid, -1);
    }

    /**
     * Attempt to reforge the item at a specific inventory slot.
     *
     * @param player     the player
     * @param playerUuid the player's UUID
     * @param slotIndex  inventory slot index, or -1 for held item
     * @return attempt info with result, or null if pre-checks fail
     */
    @Nullable
    public ReforgeAttemptInfo attemptReforgeAtSlot(@Nonnull Player player,
                                                   @Nonnull UUID playerUuid,
                                                   int slotIndex) {
        return attemptReforgeAtSlot(player, playerUuid, slotIndex, false);
    }

    /**
     * Attempt to reforge the item at a specific inventory slot, optionally with protection.
     *
     * @param player        the player
     * @param playerUuid    the player's UUID
     * @param slotIndex     inventory slot index, or -1 for held item
     * @param useProtection if true, on failure the item resets to +0 instead of being destroyed
     * @return attempt info with result, or null if pre-checks fail
     */
    @Nullable
    public ReforgeAttemptInfo attemptReforgeAtSlot(@Nonnull Player player,
                                                   @Nonnull UUID playerUuid,
                                                   int slotIndex,
                                                   boolean useProtection) {
        // Prevent concurrent reforges
        if (!activeReforges.add(playerUuid)) {
            LOGGER.debug("Reforge blocked — already in progress for {}", playerUuid);
            return null;
        }

        try {
            return doReforge(player, playerUuid, slotIndex, useProtection);
        } finally {
            activeReforges.remove(playerUuid);
        }
    }

    /**
     * Get the item at a specific inventory slot.
     */
    @Nullable
    public ItemStack getItemAtSlot(@Nonnull Player player, int slotIndex) {
        if (slotIndex < 0) return player.getInventory().getItemInHand();
        try {
            ItemContainer container = player.getInventory().getCombinedHotbarFirst();
            if (slotIndex >= container.getCapacity()) return null;
            return container.getItemStack((short) slotIndex);
        } catch (Exception e) {
            LOGGER.debug("getItemAtSlot({}) failed: {}", slotIndex, e.getMessage());
            return null;
        }
    }

    /**
     * Scan inventory and return a list of reforgeable items with their slot indices.
     */
    @Nonnull
    public List<int[]> findReforgeableSlots(@Nonnull Player player) {
        List<int[]> result = new ArrayList<>();
        try {
            var inv = player.getInventory();

            ItemContainer container = inv.getCombinedHotbarFirst();
            if (container == null) {
                LOGGER.warn("[findReforgeableSlots] getCombinedHotbarFirst() returned null!");
                return result;
            }

            short slotCount = container.getCapacity();
            LOGGER.debug("[findReforgeableSlots] Capacity: {}", slotCount);

            UUID playerUuid = player.getUuid();

            for (short i = 0; i < slotCount; i++) {
                try {
                    ItemStack stack = container.getItemStack(i);
                    if (stack == null) continue;
                    boolean empty = stack.isEmpty();
                    String itemId = null;
                    try { itemId = stack.getItemId(); } catch (Exception ignore) {}

                    LOGGER.debug("[findReforgeableSlots] Slot {}: empty={}, itemId={}", i, empty, itemId);
                    if (!empty && itemId != null && validator.isReforgeable(stack)) {
                        int level = validator.getReforgeLevel(stack, playerUuid);
                        result.add(new int[]{i, level});
                        LOGGER.debug("[findReforgeableSlots] -> Reforgeable! level={}", level);
                    }
                } catch (Exception slotEx) {
                    LOGGER.warn("[findReforgeableSlots] Error reading slot {}: {}", i, slotEx.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[findReforgeableSlots] FAILED: {} — {}", e.getClass().getName(), e.getMessage());
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            LOGGER.warn("[findReforgeableSlots] Stack trace:\n{}", sw.toString());
        }
        LOGGER.debug("[findReforgeableSlots] Found {} reforgeable item(s)", result.size());
        return result;
    }

    private ReforgeAttemptInfo doReforge(@Nonnull Player player,
                                         @Nonnull UUID playerUuid,
                                         int slotIndex,
                                         boolean useProtection) {
        // 1. Get item from slot or hand
        ItemStack heldItem;
        if (slotIndex >= 0) {
            ItemContainer container = player.getInventory().getCombinedHotbarFirst();
            if (slotIndex >= container.getCapacity()) return null;
            heldItem = container.getItemStack((short) slotIndex);
        } else {
            heldItem = player.getInventory().getItemInHand();
        }
        if (heldItem == null || heldItem.isEmpty()) {
            LOGGER.debug("[reforge] Player {} has no item at slot {}", playerUuid, slotIndex);
            return null;
        }

        // 2. Validate item
        if (!validator.isReforgeable(heldItem)) {
            LOGGER.debug("[reforge] Item {} is not reforgeable", heldItem.getItemId());
            return null;
        }

        // 3. Check max level
        int currentLevel = validator.getReforgeLevel(heldItem, playerUuid);
        int maxLevel = config.getGeneral().getMaxReforgeLevel();
        if (currentLevel >= maxLevel) {
            LOGGER.debug("[reforge] Item already at max level {}", currentLevel);
            return null;
        }

        int targetLevel = currentLevel + 1;

        // 4. Get level config
        ReforgeConfig.LevelConfig levelCfg = config.getLevelConfig(targetLevel);
        if (levelCfg == null) {
            LOGGER.warn("[reforge] No level config for level {}", targetLevel);
            return null;
        }

        // 5. Check economy (EcotaleAPI)
        double coinCost = levelCfg.getCoinCost();
        double protectionCost = 0;
        if (useProtection && config.getGeneral().isProtectionEnabled()) {
            protectionCost = coinCost * config.getGeneral().getProtectionCostMultiplier();
        }
        double totalCost = coinCost + protectionCost;
        if (totalCost > 0 && !withdrawCoins(playerUuid, totalCost)) {
            return new ReforgeAttemptInfo(
                    heldItem.getItemId(), currentLevel, targetLevel,
                    levelCfg.getSuccessChance(), totalCost,
                    levelCfg.getWeaponDamageBonus(), levelCfg.getArmorDefenseBonus(),
                    ReforgeResult.CANNOT_ATTEMPT);
        }

        // 6. Check and consume materials
        if (!consumeMaterials(player, levelCfg.getMaterials())) {
            // Refund coins if materials insufficient
            if (totalCost > 0) refundCoins(playerUuid, totalCost);
            return new ReforgeAttemptInfo(
                    heldItem.getItemId(), currentLevel, targetLevel,
                    levelCfg.getSuccessChance(), totalCost,
                    levelCfg.getWeaponDamageBonus(), levelCfg.getArmorDefenseBonus(),
                    ReforgeResult.CANNOT_ATTEMPT);
        }

        // 7. Roll the dice
        double roll = ThreadLocalRandom.current().nextDouble();
        boolean success = roll < levelCfg.getSuccessChance();

        LOGGER.info("[reforge] {} item={} lv={}->{} chance={} roll={} result={} protection={}",
                playerUuid, heldItem.getItemId(), currentLevel, targetLevel,
                String.format("%.2f", levelCfg.getSuccessChance()),
                String.format("%.4f", roll),
                success ? "SUCCESS" : "FAIL",
                useProtection);

        if (success) {
            // ── SUCCESS ──
            handleSuccess(player, heldItem, currentLevel, targetLevel, levelCfg, slotIndex);

            return new ReforgeAttemptInfo(
                    heldItem.getItemId(), currentLevel, targetLevel,
                    levelCfg.getSuccessChance(), totalCost,
                    levelCfg.getWeaponDamageBonus(), levelCfg.getArmorDefenseBonus(),
                    ReforgeResult.SUCCESS);
        } else if (useProtection && config.getGeneral().isProtectionEnabled()) {
            // ── FAILURE with PROTECTION ── item stays, level resets to 0
            handleFailureProtected(player, heldItem, playerUuid, slotIndex);

            return new ReforgeAttemptInfo(
                    heldItem.getItemId(), currentLevel, targetLevel,
                    levelCfg.getSuccessChance(), totalCost,
                    levelCfg.getWeaponDamageBonus(), levelCfg.getArmorDefenseBonus(),
                    ReforgeResult.FAILURE_PROTECTED);
        } else {
            // ── FAILURE ──
            handleFailure(player, heldItem, playerUuid, slotIndex);

            return new ReforgeAttemptInfo(
                    heldItem.getItemId(), currentLevel, targetLevel,
                    levelCfg.getSuccessChance(), totalCost,
                    levelCfg.getWeaponDamageBonus(), levelCfg.getArmorDefenseBonus(),
                    ReforgeResult.FAILURE);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  SUCCESS HANDLER
    // ═══════════════════════════════════════════════════════

    private void handleSuccess(@Nonnull Player player,
                                @Nonnull ItemStack item,
                                int oldLevel,
                                int newLevel,
                                @Nonnull ReforgeConfig.LevelConfig levelCfg,
                                int slotIndex) {
        // Save new level to server-side data store (backup/legacy)
        UUID playerUuid = player.getUuid();
        String itemId = item.getItemId();
        dataStore.setLevel(playerUuid, itemId, newLevel);

        // ── Write reforge level into item metadata (per-instance) ──
        ItemStack upgraded = ReforgeMetadataHelper.setReforgeLevel(item, newLevel);
        try {
            if (slotIndex >= 0) {
                ItemContainer container = player.getInventory().getCombinedHotbarFirst();
                container.setItemStackForSlot((short) slotIndex, upgraded);
            } else {
                player.getInventory().setItemInHand(upgraded);
            }
            LOGGER.info("[reforge] Item metadata updated: slot={}, level={}", slotIndex, newLevel);
        } catch (Exception e) {
            LOGGER.warn("[reforge] Failed to write upgraded item back to slot {}: {}", slotIndex, e.getMessage());
        }

        // Calculate total cumulative bonus for display
        double totalDmgBonus = 0;
        double totalDefBonus = 0;
        for (int lvl = 1; lvl <= newLevel; lvl++) {
            ReforgeConfig.LevelConfig lc = config.getLevelConfig(lvl);
            if (lc != null) {
                totalDmgBonus += lc.getWeaponDamageBonus();
                totalDefBonus += lc.getArmorDefenseBonus();
            }
        }

        // Notify player about the active bonus (via reflection — sendActionBar doesn't exist)
        boolean isWeapon = config.getAllowedItems().isWeapon(itemId);
        String bonusText = null;
        if (isWeapon && totalDmgBonus > 0) {
            bonusText = String.format("⚒ +%d | DMG +%.1f", newLevel, totalDmgBonus);
        } else if (totalDefBonus > 0) {
            bonusText = String.format("⚒ +%d | DEF +%.1f", newLevel, totalDefBonus);
        }
        if (bonusText != null) {
            safeSendMessage(player, bonusText);
        }

        LOGGER.info("[reforge] SUCCESS: item {} upgraded to +{} (slot={}, player={}, dmg+={}, def+={})",
                itemId, newLevel, slotIndex, playerUuid,
                String.format("%.1f", totalDmgBonus), String.format("%.1f", totalDefBonus));

    }

    // ═══════════════════════════════════════════════════════
    //  FAILURE HANDLER — REVERSE CRAFTING
    // ═══════════════════════════════════════════════════════

    private void handleFailure(@Nonnull Player player,
                                @Nonnull ItemStack item,
                                @Nonnull UUID playerUuid,
                                int slotIndex) {
        String itemId = item.getItemId();

        // 1. Remove reforge data for destroyed item
        dataStore.removeLevel(playerUuid, itemId);

        // 2. Destroy the item at the appropriate slot
        if (slotIndex >= 0) {
            ItemContainer container = player.getInventory().getCombinedHotbarFirst();
            container.removeItemStackFromSlot((short) slotIndex);
        } else {
            player.getInventory().setItemInHand(ItemStack.EMPTY);
        }

        // 3. Return configured % of the craft materials (rounded down, at least 1 if recipe exists)
        double returnRate = config.getGeneral().getFailureReturnRate();
        List<ReforgeConfig.MaterialEntry> recipe = getReverseCraftingRecipe(itemId);
        if (recipe != null && !recipe.isEmpty()) {
            ItemContainer container = player.getInventory().getCombinedHotbarFirst();
            for (ReforgeConfig.MaterialEntry material : recipe) {
                int returnCount = Math.max(1, (int) (material.getCount() * returnRate));
                // Strip namespace prefix (e.g. "hytale:") — ItemStack constructor requires bare ID
                String bareMatId = material.getItemId();
                if (bareMatId.contains(":")) {
                    bareMatId = bareMatId.substring(bareMatId.indexOf(':') + 1);
                }
                try {
                    ItemStack matStack = new ItemStack(bareMatId, returnCount);
                    container.addItemStack(matStack);
                    LOGGER.info("[reforge] Returned {} x{} ({}% of {}) to {}",
                            bareMatId, returnCount,
                            String.format("%.0f", returnRate * 100),
                            material.getCount(), playerUuid);
                } catch (Exception e) {
                    LOGGER.warn("[reforge] Failed to return material {}: {}",
                            bareMatId, e.getMessage());
                }
            }
        }

        LOGGER.info("[reforge] FAIL: item {} destroyed for {}", itemId, playerUuid);
    }

    // ═══════════════════════════════════════════════════════
    //  FAILURE HANDLER — PROTECTION (item kept, level reset to 0)
    // ═══════════════════════════════════════════════════════

    private void handleFailureProtected(@Nonnull Player player,
                                         @Nonnull ItemStack item,
                                         @Nonnull UUID playerUuid,
                                         int slotIndex) {
        String itemId = item.getItemId();

        // 1. Remove reforge data
        dataStore.removeLevel(playerUuid, itemId);

        // 2. Reset item metadata to level 0 (keep item!)
        ItemStack reset = ReforgeMetadataHelper.setReforgeLevel(item, 0);
        try {
            if (slotIndex >= 0) {
                ItemContainer container = player.getInventory().getCombinedHotbarFirst();
                container.setItemStackForSlot((short) slotIndex, reset);
            } else {
                player.getInventory().setItemInHand(reset);
            }
        } catch (Exception e) {
            LOGGER.warn("[reforge] Failed to write reset item back to slot {}: {}", slotIndex, e.getMessage());
        }

        LOGGER.info("[reforge] FAIL_PROTECTED: item {} reset to +0 for {}", itemId, playerUuid);
    }

    // ═══════════════════════════════════════════════════════
    //  ECONOMY (EcotaleAPI)
    // ═══════════════════════════════════════════════════════

    private boolean withdrawCoins(@Nonnull UUID uuid, double amount) {
        try {
            if (!EcotaleAPI.isAvailable()) {
                LOGGER.debug("EcotaleAPI not available — coins check skipped");
                return true;
            }
            if (!EcotaleAPI.hasBalance(uuid, amount)) return false;
            return EcotaleAPI.withdraw(uuid, amount, "EcoTaleReforging: reforge cost");
        } catch (Exception e) {
            LOGGER.warn("Economy withdraw failed: {}", e.getMessage());
            return false;
        }
    }

    private void refundCoins(@Nonnull UUID uuid, double amount) {
        try {
            if (!EcotaleAPI.isAvailable()) return;
            EcotaleAPI.deposit(uuid, amount, "EcoTaleReforging: reforge refund");
        } catch (Exception e) {
            LOGGER.warn("Economy refund failed: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    //  MATERIAL CONSUMPTION
    // ═══════════════════════════════════════════════════════

    /**
     * Check if player has required materials and consume them.
     * Uses inventory scan pattern.
     */
    private boolean consumeMaterials(@Nonnull Player player,
                                      @Nonnull List<ReforgeConfig.MaterialEntry> required) {
        if (required.isEmpty()) return true;

        Inventory inv = player.getInventory();

        // First pass: verify all materials are present
        for (ReforgeConfig.MaterialEntry mat : required) {
            int needed = mat.getCount();
            int found = countInInventory(inv, mat.getItemId());
            if (found < needed) return false;
        }

        // Second pass: consume
        for (ReforgeConfig.MaterialEntry mat : required) {
            removeFromInventory(inv, mat.getItemId(), mat.getCount());
        }

        return true;
    }

    private int countInInventory(@Nonnull Inventory inv, @Nonnull String itemId) {
        int total = 0;
        try {
            ItemContainer container = inv.getCombinedHotbarFirst();
            short slotCount = container.getCapacity();
            for (short i = 0; i < slotCount; i++) {
                ItemStack stack = container.getItemStack(i);
                if (stack != null && !stack.isEmpty() && itemIdMatches(stack.getItemId(), itemId)) {
                    total += stack.getQuantity();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("countInInventory failed: {}", e.getMessage());
        }
        return total;
    }

    /**
     * Public method to count how many of a specific material the player has.
     * Used by GUI to colour material labels red/green.
     */
    public int countMaterial(@Nonnull Player player, @Nonnull String itemId) {
        return countInInventory(player.getInventory(), itemId);
    }

    private void removeFromInventory(@Nonnull Inventory inv, @Nonnull String itemId, int amount) {
        int remaining = amount;
        try {
            ItemContainer container = inv.getCombinedHotbarFirst();
            short slotCount = container.getCapacity();
            for (short i = 0; i < slotCount && remaining > 0; i++) {
                ItemStack stack = container.getItemStack(i);
                if (stack != null && !stack.isEmpty() && itemIdMatches(stack.getItemId(), itemId)) {
                    int count = stack.getQuantity();
                    if (count <= remaining) {
                        container.removeItemStackFromSlot(i);
                        remaining -= count;
                    } else {
                        container.setItemStackForSlot(i, stack.withQuantity(count - remaining));
                        remaining = 0;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("removeFromInventory failed: {}", e.getMessage());
        }
    }

    private static boolean itemIdMatches(@Nonnull String actual, @Nonnull String expected) {
        if (actual.equals(expected)) return true;
        // Handle with/without namespace
        String actualName = actual.contains(":") ? actual.substring(actual.indexOf(':') + 1) : actual;
        String expectedName = expected.contains(":") ? expected.substring(expected.indexOf(':') + 1) : expected;
        return actualName.equals(expectedName);
    }

    /**
     * Dump all methods of an ItemStack to the log for API discovery.
     * Called once per session on the first non-empty item found.
     */
    /**
     * Send a message to the player via reflection.
     * Tries sendMessage(String) first, silently ignores if not available.
     */
    private void safeSendMessage(@Nonnull Player player, @Nonnull String text) {
        try {
            Method m = player.getClass().getMethod("sendMessage", String.class);
            m.invoke(player, text);
        } catch (NoSuchMethodException nsm) {
            LOGGER.debug("Player.sendMessage(String) not available, skipping notification");
        } catch (Exception e) {
            LOGGER.debug("Failed to send message to player: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    //  REVERSE CRAFTING
    // ═══════════════════════════════════════════════════════

    /**
     * Get reverse crafting recipe for an item.
     * First checks config, then falls back to heuristic estimation.
     */
    @Nullable
    private List<ReforgeConfig.MaterialEntry> getReverseCraftingRecipe(@Nonnull String itemId) {
        // Check config-based recipes
        String name = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;

        Map<String, List<ReforgeConfig.MaterialEntry>> recipes = config.getReverseRecipes();
        if (recipes.containsKey(name)) return recipes.get(name);
        if (recipes.containsKey(itemId)) return recipes.get(itemId);

        // Heuristic: guess based on item name
        return guessReverseRecipe(name);
    }

    /**
     * Heuristic-based recipe guessing from item ID patterns.
     * E.g., "Weapon_Sword_Iron" → 2x Ingredient_Bar_Iron.
     * E.g., "Armor_Adamantite_Head" → 5x Ingredient_Bar_Adamantite.
     */
    @Nullable
    private List<ReforgeConfig.MaterialEntry> guessReverseRecipe(@Nonnull String itemName) {
        // Extract material type from name
        String materialName = null;

        if (itemName.startsWith("Weapon_")) {
            // Weapon_<Type>_<Material> → material is last part
            String[] parts = itemName.split("_");
            if (parts.length >= 3) {
                materialName = parts[parts.length - 1];
            }
        } else if (itemName.startsWith("Armor_")) {
            // Armor_<Material>_<SlotType> → material is second part
            String[] parts = itemName.split("_");
            if (parts.length >= 3) {
                materialName = parts[1];
            }
        }

        if (materialName == null) return null;

        // Real Hytale ID format: Ingredient_Bar_<Material> (no namespace prefix!)
        String ingotId = "Ingredient_Bar_" + materialName;

        // Base count depends on item type — total ingots to craft this item
        // Real Hytale crafting costs (ingot component only)
        int count;
        if (itemName.startsWith("Weapon_Battleaxe")) count = 24;
        else if (itemName.startsWith("Weapon_Longsword")) count = 20;
        else if (itemName.startsWith("Weapon_Sword")) count = 12;
        else if (itemName.startsWith("Weapon_Axe")) count = 16;
        else if (itemName.startsWith("Weapon_Mace")) count = 18;
        else if (itemName.startsWith("Weapon_Spear")) count = 14;
        else if (itemName.startsWith("Weapon_Dagger")) count = 20;
        else if (itemName.contains("_Head")) count = 14;
        else if (itemName.contains("_Chest")) count = 28;
        else if (itemName.contains("_Legs")) count = 20;
        else if (itemName.contains("_Hands")) count = 10;
        else if (itemName.contains("_Feet")) count = 10;
        else count = 12;

        List<ReforgeConfig.MaterialEntry> result = new ArrayList<>();
        result.add(new ReforgeConfig.MaterialEntry(ingotId, count));
        LOGGER.info("[reverseRecipe] Guessed: {} → {} x{}", itemName, ingotId, count);
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  QUERY METHODS (for GUI)
    // ═══════════════════════════════════════════════════════

    /**
     * Get the success chance for upgrading from current level to next.
     */
    public double getSuccessChance(int currentLevel) {
        int target = currentLevel + 1;
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(target);
        return lc != null ? lc.getSuccessChance() : 0.0;
    }

    /**
     * Get coin cost for upgrading from current level to next.
     */
    public double getCoinCost(int currentLevel) {
        int target = currentLevel + 1;
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(target);
        return lc != null ? lc.getCoinCost() : 0.0;
    }

    /**
     * Get materials required for upgrading from current level to next.
     */
    @Nonnull
    public List<ReforgeConfig.MaterialEntry> getRequiredMaterials(int currentLevel) {
        int target = currentLevel + 1;
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(target);
        return lc != null ? lc.getMaterials() : Collections.emptyList();
    }

    /**
     * Get the damage bonus for a specific target level.
     */
    public double getDamageBonus(int targetLevel) {
        double total = 0;
        for (int i = 1; i <= targetLevel; i++) {
            ReforgeConfig.LevelConfig lc = config.getLevelConfig(i);
            if (lc != null) total += lc.getWeaponDamageBonus();
        }
        return total;
    }

    /**
     * Get the defense bonus for a specific target level.
     */
    public double getDefenseBonus(int targetLevel) {
        double total = 0;
        for (int i = 1; i <= targetLevel; i++) {
            ReforgeConfig.LevelConfig lc = config.getLevelConfig(i);
            if (lc != null) total += lc.getArmorDefenseBonus();
        }
        return total;
    }

    /**
     * Check if a player has enough materials for the next reforge.
     */
    public boolean hasMaterials(@Nonnull Player player, int currentLevel) {
        int target = currentLevel + 1;
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(target);
        if (lc == null) return false;

        Inventory inv = player.getInventory();
        for (ReforgeConfig.MaterialEntry mat : lc.getMaterials()) {
            if (countInInventory(inv, mat.getItemId()) < mat.getCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the player has enough coins for the next reforge.
     */
    public boolean hasCoins(@Nonnull UUID playerUuid, int currentLevel) {
        int target = currentLevel + 1;
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(target);
        if (lc == null) return false;
        if (lc.getCoinCost() <= 0) return true;

        try {
            if (!EcotaleAPI.isAvailable()) return true;
            return EcotaleAPI.hasBalance(playerUuid, lc.getCoinCost());
        } catch (Exception e) {
            LOGGER.warn("Economy hasCoins check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the player's current balance from EcotaleAPI.
     */
    public double getPlayerBalance(@Nonnull UUID playerUuid) {
        try {
            if (!EcotaleAPI.isAvailable()) return 0;
            return EcotaleAPI.getBalance(playerUuid);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Format a currency amount using EcotaleAPI.
     */
    public String formatCurrency(double amount) {
        try {
            if (!EcotaleAPI.isAvailable()) return String.format("%.0f", amount);
            return EcotaleAPI.format(amount);
        } catch (Exception e) {
            return String.format("%.0f", amount);
        }
    }

    /**
     * Get the protection cost for the next reforge level (extra coins to pay).
     * Returns 0 if protection is disabled.
     */
    public double getProtectionCost(int currentLevel) {
        if (!config.getGeneral().isProtectionEnabled()) return 0;
        int target = currentLevel + 1;
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(target);
        if (lc == null) return 0;
        return lc.getCoinCost() * config.getGeneral().getProtectionCostMultiplier();
    }

    /**
     * Check if the protection feature is enabled in config.
     */
    public boolean isProtectionEnabled() {
        return config.getGeneral().isProtectionEnabled();
    }
}
