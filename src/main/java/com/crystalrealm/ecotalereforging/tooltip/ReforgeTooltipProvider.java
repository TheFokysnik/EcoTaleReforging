package com.crystalrealm.ecotalereforging.tooltip;

import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.util.PluginLogger;
import org.herolias.tooltips.api.TooltipData;
import org.herolias.tooltips.api.TooltipPriority;
import org.herolias.tooltips.api.TooltipProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * DynamicTooltipsLib provider that shows reforge level and stat bonuses
 * on reforged weapons and armor.
 *
 * <p>Reads the {@code reforge_level} field from item metadata (JSON string).
 * Calculates cumulative DMG/DEF bonus from config and renders colored lines.</p>
 *
 * <p>Example output:</p>
 * <pre>
 *   ⚒ Reforged +3
 *   DMG +6.0
 * </pre>
 */
public class ReforgeTooltipProvider implements TooltipProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String PROVIDER_ID = "ecotale_reforging:stats";

    private final ReforgeConfig config;

    public ReforgeTooltipProvider(@Nonnull ReforgeConfig config) {
        this.config = config;
    }

    @Nonnull
    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public int getPriority() {
        return TooltipPriority.EARLY; // Show reforge info before other mods' tooltips
    }

    @Nullable
    @Override
    public TooltipData getTooltipData(@Nonnull String itemId, @Nullable String metadata) {
        // Quick exit: only process items that are in our allowed list
        String bareId = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        boolean isWeapon = config.getAllowedItems().isWeapon(bareId);
        boolean isArmor  = config.getAllowedItems().isArmor(bareId);
        if (!isWeapon && !isArmor) return null;

        // Parse reforge_level from metadata JSON
        int reforgeLevel = parseReforgeLevel(metadata);
        if (reforgeLevel <= 0) return null;

        // Calculate cumulative bonuses
        double totalDmg = 0;
        double totalDef = 0;
        for (int lvl = 1; lvl <= reforgeLevel; lvl++) {
            ReforgeConfig.LevelConfig lc = config.getLevelConfig(lvl);
            if (lc != null) {
                totalDmg += lc.getWeaponDamageBonus();
                totalDef += lc.getArmorDefenseBonus();
            }
        }

        // Build tooltip lines — hash MUST include itemId so weapons and armor get different cache entries
        TooltipData.Builder builder = TooltipData.builder()
                .hashInput("reforge:" + reforgeLevel + ":" + bareId);

        // Header line: Reforged +N (golden color)
        builder.addLine(String.format(
                "<color is=\"#FFD700\">Reforged +%d</color>", reforgeLevel));

        // Stat bonus line — show DMG for weapons, DEF + HP for armor
        if (isWeapon && totalDmg > 0) {
            builder.addLine(String.format(
                    "<color is=\"#FF6B6B\">DMG +%.1f</color>", totalDmg));
        } else if (isArmor && totalDef > 0) {
            if (totalDef > 0) {
                builder.addLine(String.format(
                        "<color is=\"#6BB5FF\">DEF +%.1f</color>", totalDef));
            }
        }

        return builder.build();
    }

    /**
     * Parse the reforge_level integer from a metadata JSON string.
     * Expected format: {"reforge_level": 3, ...} or {"reforge_level": {"$numberInt": "3"}, ...}
     *
     * <p>Uses simple string parsing to avoid a JSON library dependency.</p>
     */
    private int parseReforgeLevel(@Nullable String metadata) {
        if (metadata == null || metadata.isEmpty()) return 0;

        // Look for "reforge_level" key in the JSON
        int keyIdx = metadata.indexOf("\"reforge_level\"");
        if (keyIdx < 0) {
            // Also try without quotes (some BSON serializers)
            keyIdx = metadata.indexOf("reforge_level");
            if (keyIdx < 0) return 0;
        }

        // Find the colon after the key
        int colonIdx = metadata.indexOf(':', keyIdx);
        if (colonIdx < 0) return 0;

        // Extract the value — skip whitespace, find the number
        int start = colonIdx + 1;
        while (start < metadata.length() && (metadata.charAt(start) == ' ' || metadata.charAt(start) == '\t')) {
            start++;
        }
        if (start >= metadata.length()) return 0;

        // Handle extended JSON format: {"$numberInt": "3"}
        if (metadata.charAt(start) == '{') {
            // Look for $numberInt value
            int numIntIdx = metadata.indexOf("$numberInt", start);
            if (numIntIdx < 0) return 0;
            int valStart = metadata.indexOf('"', metadata.indexOf(':', numIntIdx) + 1);
            if (valStart < 0) return 0;
            valStart++;
            int valEnd = metadata.indexOf('"', valStart);
            if (valEnd < 0) return 0;
            try {
                return Integer.parseInt(metadata.substring(valStart, valEnd));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Simple number value
        StringBuilder numStr = new StringBuilder();
        for (int i = start; i < metadata.length(); i++) {
            char c = metadata.charAt(i);
            if (Character.isDigit(c) || c == '-') {
                numStr.append(c);
            } else {
                break;
            }
        }

        if (numStr.length() == 0) return 0;
        try {
            return Integer.parseInt(numStr.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
