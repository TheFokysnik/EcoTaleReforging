package com.crystalrealm.ecotalereforging.tooltip;

import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.util.PluginLogger;
import org.herolias.tooltips.api.TooltipData;
import org.herolias.tooltips.api.TooltipPriority;
import org.herolias.tooltips.api.TooltipProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DynamicTooltipsLib provider that shows reforge level, stat bonuses
 * <b>and</b> enchantment info on items.
 *
 * <p>Reads the {@code reforge_level} field from item metadata (JSON string).
 * Calculates cumulative DMG/DEF bonus from config and renders colored lines.</p>
 *
 * <h3>Simple Enchantments compatibility</h3>
 * <p>Simple Enchantments uses {@code descriptionTranslationKey} combined with
 * its own packet-level translation injection ({@code EnchantmentTranslationManager}).
 * When {@code descriptionTranslationKey} is set, the virtual item's description
 * field points to SE's translation key instead of the standard virtual
 * description key.  DynamicTooltipsLib stores {@code buildDescription()} output
 * under the virtual key, but the client resolves SE's key from SE's
 * translations &mdash; so any text from other providers is silently ignored.</p>
 *
 * <p>There is no public API to clear {@code descriptionTranslationKey} once
 * set by an earlier provider.  To work around this limitation the plugin
 * <b>unregisters</b> SE's tooltip provider at startup and this provider takes
 * full responsibility for rendering both enchantment and reforge info via
 * additive lines only (no {@code descriptionOverride}, no
 * {@code descriptionTranslationKey}).</p>
 *
 * <p>SE's gameplay systems (damage modifiers, enchanting recipes, etc.)
 * remain fully functional &mdash; only the tooltip provider is replaced.</p>
 */
public class ReforgeTooltipProvider implements TooltipProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String PROVIDER_ID = "ecotale_reforging:stats";

    private static final String[] ROMAN = {
        "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
        "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"
    };

    /** Enchantment key used by Simple Enchantments in item metadata (capital E). */
    private static final String ENCHANTMENTS_KEY = "Enchantments";

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
        return TooltipPriority.LATE + 50; // 950
    }

    @Nullable
    @Override
    public TooltipData getTooltipData(@Nonnull String itemId, @Nullable String metadata) {
        String bareId = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        boolean isWeapon = config.getAllowedItems().isWeapon(bareId);
        boolean isArmor  = config.getAllowedItems().isArmor(bareId);

        // Parse reforge level (only for weapons/armor)
        int reforgeLevel = 0;
        if (isWeapon || isArmor) {
            reforgeLevel = parseReforgeLevel(metadata);
        }

        // Parse enchantments (any item may have them)
        Map<String, Integer> enchantments = parseEnchantments(metadata);
        boolean hasEnchantments = !enchantments.isEmpty();

        // Nothing to display -> null (DynamicTooltipsLib skips this provider)
        if (reforgeLevel <= 0 && !hasEnchantments) return null;

        // -- Calculate reforge bonuses ------------------------------------
        double totalDmg = 0;
        double totalDef = 0;
        if (reforgeLevel > 0) {
            for (int lvl = 1; lvl <= reforgeLevel; lvl++) {
                ReforgeConfig.LevelConfig lc = config.getLevelConfig(lvl);
                if (lc != null) {
                    totalDmg += lc.getWeaponDamageBonus();
                    totalDef += lc.getArmorDefenseBonus();
                }
            }
        }

        // -- Build stable hash --------------------------------------------
        String hash = "combined"
                + (reforgeLevel > 0 ? ":r" + reforgeLevel + ":" + bareId + ":d" + totalDmg + ":a" + totalDef : "")
                + (hasEnchantments  ? ":enc" + enchantments.hashCode() : "");

        // -- Additive lines only ------------------------------------------
        // Using ONLY additive lines ensures no descriptionTranslationKey is
        // set, so the virtual item uses the standard virtual-description-key
        // and DynamicTooltipsLib's translation mechanism works correctly.
        TooltipData.Builder builder = TooltipData.builder().hashInput(hash);

        // Enchantment lines (displayed first, like SE does)
        if (hasEnchantments) {
            builder.addLine("<color is=\"#C8A2C8\">Enchantments:</color>");
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                String name  = formatEnchantmentName(entry.getKey());
                String level = toRoman(entry.getValue());
                builder.addLine(String.format(
                        "<color is=\"#D4A4FF\">\u2022 %s %s</color>", name, level));
            }
        }

        // Reforge lines (displayed after enchantments)
        if (reforgeLevel > 0) {
            builder.addLine(String.format(
                    "<color is=\"#FFD700\">Reforged +%d</color>", reforgeLevel));

            if (isWeapon && totalDmg > 0) {
                builder.addLine(String.format(
                        "<color is=\"#FF6B6B\">DMG +%.1f</color>", totalDmg));
            } else if (isArmor && totalDef > 0) {
                builder.addLine(String.format(
                        "<color is=\"#6BB5FF\">DEF +%.1f</color>", totalDef));
            }
        }

        return builder.build();
    }

    // =================================================================
    //  ENCHANTMENT PARSING
    // =================================================================

    /**
     * Parse enchantment data from the item's metadata JSON string.
     *
     * <p>Simple Enchantments stores enchantments as a BSON document in item
     * metadata.  The serialised JSON form is typically:</p>
     * <pre>
     * { "Enchantments": { "sharpness": 3, "durability": 3 } }
     * // or in extended JSON:
     * { "Enchantments": { "sharpness": {"$numberInt":"3"}, ... } }
     * </pre>
     *
     * @return ordered map of enchantment-id to level; empty if none found
     */
    @Nonnull
    private Map<String, Integer> parseEnchantments(@Nullable String metadata) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) return result;

        // Find the "Enchantments" key
        int keyIdx = metadata.indexOf("\"" + ENCHANTMENTS_KEY + "\"");
        if (keyIdx < 0) return result;

        // Find the opening brace of the enchantments object
        int colonIdx = metadata.indexOf(':', keyIdx + ENCHANTMENTS_KEY.length() + 2);
        if (colonIdx < 0) return result;

        int braceStart = metadata.indexOf('{', colonIdx);
        if (braceStart < 0) return result;

        // Find matching closing brace (handle nested objects for extended JSON)
        int braceEnd = findMatchingBrace(metadata, braceStart);
        if (braceEnd < 0) return result;

        String enchBlock = metadata.substring(braceStart + 1, braceEnd);

        // Parse key-value pairs from the enchantments block.
        // Handles both: "sharpness": 3  and  "sharpness": {"$numberInt": "3"}
        int pos = 0;
        while (pos < enchBlock.length()) {
            int qStart = enchBlock.indexOf('"', pos);
            if (qStart < 0) break;
            int qEnd = enchBlock.indexOf('"', qStart + 1);
            if (qEnd < 0) break;

            String enchId = enchBlock.substring(qStart + 1, qEnd);

            // Skip internal BSON keys like $numberInt
            if (enchId.startsWith("$")) {
                pos = qEnd + 1;
                continue;
            }

            int eColon = enchBlock.indexOf(':', qEnd);
            if (eColon < 0) break;

            int level = parseIntValue(enchBlock, eColon + 1);
            if (level > 0) {
                result.put(enchId, level);
            }

            pos = eColon + 1;
            while (pos < enchBlock.length()) {
                char c = enchBlock.charAt(pos);
                if (c == ',') { pos++; break; }
                if (c == '}') break;
                pos++;
            }
        }

        return result;
    }

    /** Find the index of the brace that closes the one at {@code openIdx}. */
    private int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Parse an integer value starting at {@code offset}.
     * Handles plain numbers ({@code 3}) and extended JSON
     * ({@code {"$numberInt": "3"}}).
     */
    private int parseIntValue(String s, int offset) {
        while (offset < s.length() && Character.isWhitespace(s.charAt(offset))) offset++;
        if (offset >= s.length()) return 0;

        char c = s.charAt(offset);

        // Extended JSON: {"$numberInt": "3"}
        if (c == '{') {
            int numIntIdx = s.indexOf("$numberInt", offset);
            if (numIntIdx < 0) return 0;
            int vColon = s.indexOf(':', numIntIdx);
            if (vColon < 0) return 0;
            int vqStart = s.indexOf('"', vColon + 1);
            if (vqStart < 0) return 0;
            int vqEnd = s.indexOf('"', vqStart + 1);
            if (vqEnd < 0) return 0;
            try {
                return Integer.parseInt(s.substring(vqStart + 1, vqEnd));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Plain number
        StringBuilder numStr = new StringBuilder();
        for (int i = offset; i < s.length(); i++) {
            char nc = s.charAt(i);
            if (Character.isDigit(nc) || nc == '-') {
                numStr.append(nc);
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

    // =================================================================
    //  FORMATTING HELPERS
    // =================================================================

    /**
     * Convert an enchantment id like {@code "life_leech"} to a
     * display-friendly name like {@code "Life Leech"}.
     */
    @Nonnull
    private String formatEnchantmentName(@Nonnull String id) {
        if (id.isEmpty()) return id;
        String[] parts = id.split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    /** Convert a level number to Roman numeral (1-20), or plain digits for &gt; 20. */
    @Nonnull
    private String toRoman(int level) {
        if (level >= 1 && level < ROMAN.length) return ROMAN[level];
        return String.valueOf(level);
    }

    // =================================================================
    //  REFORGE LEVEL PARSING
    // =================================================================

    /**
     * Parse the reforge_level integer from a metadata JSON string.
     * Expected format: {"reforge_level": 3} or {"reforge_level": {"$numberInt": "3"}}
     */
    private int parseReforgeLevel(@Nullable String metadata) {
        if (metadata == null || metadata.isEmpty()) return 0;

        int keyIdx = metadata.indexOf("\"reforge_level\"");
        if (keyIdx < 0) {
            keyIdx = metadata.indexOf("reforge_level");
            if (keyIdx < 0) return 0;
        }

        int colonIdx = metadata.indexOf(':', keyIdx);
        if (colonIdx < 0) return 0;

        return parseIntValue(metadata, colonIdx + 1);
    }
}
