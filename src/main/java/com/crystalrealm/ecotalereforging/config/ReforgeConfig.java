package com.crystalrealm.ecotalereforging.config;

import java.util.*;

/**
 * Root configuration object for EcoTaleReforging.
 * Deserialized from JSON by Gson.
 */
public class ReforgeConfig {

    private General general = new General();
    private Map<String, LevelConfig> levels = new LinkedHashMap<>();
    private AllowedItems allowedItems = new AllowedItems();
    private Map<String, List<MaterialEntry>> reverseRecipes = new LinkedHashMap<>();

    public ReforgeConfig() {
        // Defaults: 10 levels with progressive difficulty
        for (int i = 1; i <= 10; i++) {
            LevelConfig lc = new LevelConfig();
            lc.setSuccessChance(Math.max(0.05, 0.95 - (i - 1) * 0.10));
            lc.setWeaponDamageBonus(i * 2.0);
            lc.setArmorDefenseBonus(i * 1.5);
            lc.setCoinCost(100.0 * i);
            List<MaterialEntry> mats = new ArrayList<>();
            mats.add(new MaterialEntry("hytale:Iron_Ingot", Math.min(i * 2, 20)));
            lc.setMaterials(mats);
            levels.put(String.valueOf(i), lc);
        }
    }

    // ── Getters / Setters ────────────────────────────────────

    public General getGeneral() { return general; }
    public void setGeneral(General general) { this.general = general; }

    public Map<String, LevelConfig> getLevels() { return levels; }
    public void setLevels(Map<String, LevelConfig> levels) { this.levels = levels; }

    public AllowedItems getAllowedItems() { return allowedItems; }
    public void setAllowedItems(AllowedItems allowedItems) { this.allowedItems = allowedItems; }

    public Map<String, List<MaterialEntry>> getReverseRecipes() { return reverseRecipes; }
    public void setReverseRecipes(Map<String, List<MaterialEntry>> reverseRecipes) { this.reverseRecipes = reverseRecipes; }

    /**
     * Copy all fields from another config into this instance.
     * Keeps this object reference alive so services that hold it see updates.
     */
    public void updateFrom(ReforgeConfig other) {
        this.general = other.general;
        this.levels = other.levels;
        this.allowedItems = other.allowedItems;
        this.reverseRecipes = other.reverseRecipes;
    }

    /**
     * Get level config for specific reforge level.
     * Falls back to the highest defined level if requested level isn't explicitly configured.
     */
    public LevelConfig getLevelConfig(int level) {
        LevelConfig lc = levels.get(String.valueOf(level));
        if (lc != null) return lc;

        // Fallback to highest defined
        int highest = 0;
        LevelConfig fallback = null;
        for (Map.Entry<String, LevelConfig> entry : levels.entrySet()) {
            try {
                int l = Integer.parseInt(entry.getKey());
                if (l <= level && l > highest) {
                    highest = l;
                    fallback = entry.getValue();
                }
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    // ═══════════════════════════════════════════════════════
    //  INNER CLASSES
    // ═══════════════════════════════════════════════════════

    public static class General {
        private String language = "en";
        private String messagePrefix = "<dark_gray>[<gold>⚒ Reforge<dark_gray>]";
        private int maxReforgeLevel = 10;
        private boolean debugMode = false;
        private double failureReturnRate = 0.30;
        private boolean protectionEnabled = true;
        private double protectionCostMultiplier = 2.0;

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getMessagePrefix() { return messagePrefix; }
        public void setMessagePrefix(String messagePrefix) { this.messagePrefix = messagePrefix; }

        public int getMaxReforgeLevel() { return maxReforgeLevel; }
        public void setMaxReforgeLevel(int maxReforgeLevel) { this.maxReforgeLevel = maxReforgeLevel; }

        public boolean isDebugMode() { return debugMode; }
        public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

        public double getFailureReturnRate() { return failureReturnRate; }
        public void setFailureReturnRate(double failureReturnRate) { this.failureReturnRate = failureReturnRate; }

        public boolean isProtectionEnabled() { return protectionEnabled; }
        public void setProtectionEnabled(boolean protectionEnabled) { this.protectionEnabled = protectionEnabled; }

        public double getProtectionCostMultiplier() { return protectionCostMultiplier; }
        public void setProtectionCostMultiplier(double protectionCostMultiplier) { this.protectionCostMultiplier = protectionCostMultiplier; }
    }

    public static class LevelConfig {
        private double successChance = 0.90;
        private double weaponDamageBonus = 2.0;
        private double armorDefenseBonus = 1.5;
        private double coinCost = 100.0;
        private List<MaterialEntry> materials = new ArrayList<>();

        public double getSuccessChance() { return successChance; }
        public void setSuccessChance(double successChance) { this.successChance = successChance; }

        public double getWeaponDamageBonus() { return weaponDamageBonus; }
        public void setWeaponDamageBonus(double weaponDamageBonus) { this.weaponDamageBonus = weaponDamageBonus; }

        public double getArmorDefenseBonus() { return armorDefenseBonus; }
        public void setArmorDefenseBonus(double armorDefenseBonus) { this.armorDefenseBonus = armorDefenseBonus; }

        public double getCoinCost() { return coinCost; }
        public void setCoinCost(double coinCost) { this.coinCost = coinCost; }

        public List<MaterialEntry> getMaterials() { return materials; }
        public void setMaterials(List<MaterialEntry> materials) { this.materials = materials; }
    }

    public static class MaterialEntry {
        private String itemId;
        private int count;

        public MaterialEntry() {}

        public MaterialEntry(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class AllowedItems {
        private List<String> weapons = new ArrayList<>(List.of(
                "Weapon_*"));
        private List<String> armor = new ArrayList<>(List.of(
                "Armor_*"));

        public List<String> getWeapons() { return weapons; }
        public void setWeapons(List<String> weapons) { this.weapons = weapons; }

        public List<String> getArmor() { return armor; }
        public void setArmor(List<String> armor) { this.armor = armor; }

        /** Check if an item ID matches any allowed weapon or armor pattern. */
        public boolean isAllowed(String itemId) {
            if (itemId == null) return false;
            String name = extractName(itemId);
            for (String pattern : weapons) {
                if (matchesWildcard(name, pattern)) return true;
            }
            for (String pattern : armor) {
                if (matchesWildcard(name, pattern)) return true;
            }
            return false;
        }

        /** Check if an item is specifically a weapon. */
        public boolean isWeapon(String itemId) {
            if (itemId == null) return false;
            String name = extractName(itemId);
            for (String pattern : weapons) {
                if (matchesWildcard(name, pattern)) return true;
            }
            return false;
        }

        /** Check if an item is specifically armor. */
        public boolean isArmor(String itemId) {
            if (itemId == null) return false;
            String name = extractName(itemId);
            for (String pattern : armor) {
                if (matchesWildcard(name, pattern)) return true;
            }
            return false;
        }

        private static String extractName(String fullId) {
            if (fullId.contains(":")) {
                return fullId.substring(fullId.indexOf(':') + 1);
            }
            return fullId;
        }

        private static boolean matchesWildcard(String name, String pattern) {
            if (pattern.equals("*")) return true;
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                return name.startsWith(prefix);
            }
            return name.equals(pattern);
        }
    }
}
