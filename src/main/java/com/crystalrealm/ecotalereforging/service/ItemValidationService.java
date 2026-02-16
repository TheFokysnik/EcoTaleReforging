package com.crystalrealm.ecotalereforging.service;

import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.util.PluginLogger;
import com.crystalrealm.ecotalereforging.util.ReforgeMetadataHelper;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Validates whether an item can be reforged.
 * Checks against configurable allow-lists for weapons and armor.
 *
 * <p>Reforge levels are stored in {@link ReforgeDataStore} (server-side),
 * NOT in ItemStack metadata (which doesn't exist in Hytale).</p>
 */
public class ItemValidationService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final ReforgeConfig config;
    private final ReforgeDataStore dataStore;

    public ItemValidationService(@Nonnull ReforgeConfig config,
                                 @Nonnull ReforgeDataStore dataStore) {
        this.config = config;
        this.dataStore = dataStore;
    }

    /**
     * Check if the item stack is eligible for reforging.
     *
     * @param item the item to check
     * @return true if the item is a weapon or armor that matches allowed patterns
     */
    public boolean isReforgeable(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        String itemId = item.getItemId();
        return config.getAllowedItems().isAllowed(itemId);
    }

    /**
     * Check if the item is a weapon.
     */
    public boolean isWeapon(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return config.getAllowedItems().isWeapon(item.getItemId());
    }

    /**
     * Check if the item is armor.
     */
    public boolean isArmor(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        return config.getAllowedItems().isArmor(item.getItemId());
    }

    /**
     * Check if item has reached maximum reforge level.
     */
    public boolean isMaxLevel(@Nullable ItemStack item, @Nonnull UUID playerUuid) {
        if (item == null || item.isEmpty()) return false;
        int level = getReforgeLevel(item, playerUuid);
        return level >= config.getGeneral().getMaxReforgeLevel();
    }

    /**
     * Get current reforge level from server-side data store.
     * Looks up by player UUID + item ID.
     */
    public int getReforgeLevel(@Nullable ItemStack item, @Nonnull UUID playerUuid) {
        if (item == null || item.isEmpty()) return 0;

        // Read reforge level from item metadata (per-instance).
        // Do NOT fall back to DataStore — it stores level per item TYPE,
        // which causes all items of same type to share the same level.
        int metaLevel = ReforgeMetadataHelper.getReforgeLevel(item);
        if (metaLevel > 0) return metaLevel;

        // If metadata API is available, trust that 0 means "not reforged".
        // Only fall back to DataStore if metadata API is entirely unavailable
        // (e.g. Hytale version without withMetadata support).
        if (ReforgeMetadataHelper.isAvailable()) return 0;

        // Legacy fallback — metadata API not available at all
        String itemId = item.getItemId();
        if (itemId == null || itemId.isEmpty()) return 0;
        return dataStore.getLevel(playerUuid, itemId);
    }

    /**
     * Get display name (human-readable) for an item ID.
     * E.g., "Weapon_Sword_Iron" → "Iron Sword"
     */
    @Nonnull
    public String getDisplayName(@Nonnull String itemId) {
        String name = itemId;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(':') + 1);
        }
        // Remove common prefixes
        if (name.startsWith("Weapon_")) name = name.substring("Weapon_".length());
        else if (name.startsWith("Armor_")) name = name.substring("Armor_".length());
        name = name.replace('_', ' ');
        if (name.isEmpty()) name = itemId;
        return name;
    }

    /**
     * Get category display (Weapon / Armor) for an item.
     */
    @Nonnull
    public String getItemCategory(@Nonnull String itemId) {
        if (config.getAllowedItems().isWeapon(itemId)) return "Weapon";
        if (config.getAllowedItems().isArmor(itemId)) return "Armor";
        return "Unknown";
    }
}
