package com.crystalrealm.ecotalereforging.gui;

import com.crystalrealm.ecotalereforging.config.ConfigManager;
import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.lang.LangManager;
import com.crystalrealm.ecotalereforging.util.MessageUtil;
import com.crystalrealm.ecotalereforging.util.PluginLogger;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin settings GUI for configuring reforge parameters at runtime.
 *
 * <p>Sections:</p>
 * <ul>
 *   <li>General: max level, language, debug</li>
 *   <li>Level Editor: per-level chance, bonuses, costs</li>
 *   <li>Actions: reload, save config</li>
 * </ul>
 */
public final class AdminReforgeGui extends InteractiveCustomUIPage<AdminReforgeGui.AdminEventData> {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String PAGE_PATH = "Pages/CrystalRealm_EcoTaleReforging_AdminPanel.ui";

    private static final String KEY_ACTION = "Action";
    private static final String KEY_SLOT   = "Slot";

    static final BuilderCodec<AdminEventData> CODEC = ReflectiveCodecBuilder
            .<AdminEventData>create(AdminEventData.class, AdminEventData::new)
            .addStringField(KEY_ACTION, (d, v) -> d.action = v, d -> d.action)
            .addStringField(KEY_SLOT,   (d, v) -> d.slot = v,   d -> d.slot)
            .build();

    // ── Dependencies ────────────────────────────────────────
    private final ConfigManager configManager;
    private final LangManager   lang;
    private final UUID          playerUuid;
    private final String        pluginVersion;

    @Nullable private String statusMessage;

    private Ref<EntityStore>   savedRef;
    private Store<EntityStore> savedStore;

    /** Currently viewing level in the editor. */
    private int editingLevel = 1;

    /** Currently viewing recipe index in the recipe editor. */
    private int editingRecipeIdx = 0;

    /** Currently viewing weapon pattern index. */
    private int editingWeaponIdx = 0;

    /** Currently viewing armor pattern index. */
    private int editingArmorIdx = 0;

    /** Cursor position within the currently editing weapon pattern. */
    private int weaponCursorPos = 0;

    /** Cursor position within the currently editing armor pattern. */
    private int armorCursorPos = 0;

    /** Available material types for cycling in the editor. */
    private static final String[] MATERIAL_TYPES = {
        "Ingredient_Bar_Iron",
        "Ingredient_Bar_Cobalt",
        "Ingredient_Bar_Mithril",
        "Ingredient_Bar_Adamantite",
        "Ingredient_Bar_Onyxium"
    };

    /** Charset for character-by-character pattern editing. */
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_*";

    /** Predefined recipe item templates for adding new recipes. */
    private static final String[] RECIPE_ITEM_TEMPLATES = {
        "Weapon_Sword_Iron", "Weapon_Sword_Cobalt", "Weapon_Sword_Mithril", "Weapon_Sword_Adamantite",
        "Weapon_Axe_Iron", "Weapon_Axe_Cobalt", "Weapon_Axe_Mithril", "Weapon_Axe_Adamantite",
        "Weapon_Daggers_Iron", "Weapon_Daggers_Cobalt", "Weapon_Daggers_Mithril", "Weapon_Daggers_Adamantite",
        "Weapon_Mace_Iron", "Weapon_Mace_Cobalt", "Weapon_Mace_Mithril", "Weapon_Mace_Adamantite",
        "Weapon_Spear_Iron", "Weapon_Spear_Cobalt", "Weapon_Spear_Mithril", "Weapon_Spear_Adamantite",
        "Weapon_Battleaxe_Iron", "Weapon_Battleaxe_Cobalt", "Weapon_Battleaxe_Mithril", "Weapon_Battleaxe_Adamantite",
        "Armor_Iron_Head", "Armor_Iron_Chest", "Armor_Iron_Legs", "Armor_Iron_Hands", "Armor_Iron_Feet",
        "Armor_Cobalt_Head", "Armor_Cobalt_Chest", "Armor_Cobalt_Legs", "Armor_Cobalt_Hands", "Armor_Cobalt_Feet",
        "Armor_Mithril_Head", "Armor_Mithril_Chest", "Armor_Mithril_Legs", "Armor_Mithril_Hands", "Armor_Mithril_Feet",
        "Armor_Adamantite_Head", "Armor_Adamantite_Chest", "Armor_Adamantite_Legs", "Armor_Adamantite_Hands", "Armor_Adamantite_Feet"
    };

    // ════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════

    public AdminReforgeGui(@Nonnull ConfigManager configManager,
                           @Nonnull LangManager lang,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull UUID playerUuid,
                           @Nonnull String pluginVersion,
                           @Nullable String statusMessage) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
        this.configManager = configManager;
        this.lang          = lang;
        this.playerUuid    = playerUuid;
        this.pluginVersion = pluginVersion;
        this.statusMessage = statusMessage;
    }

    // ════════════════════════════════════════════════════════
    //  BUILD
    // ════════════════════════════════════════════════════════

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        try {
            buildInternal(ref, cmd, events, store);
        } catch (Throwable t) {
            LOGGER.error("AdminReforgeGui.build() threw: {} — {}", t.getClass().getName(), t.getMessage());
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            LOGGER.error("AdminReforgeGui.build() stack trace:\n{}", sw.toString());
            throw t;
        }
    }

    private void buildInternal(@Nonnull Ref<EntityStore> ref,
                               @Nonnull UICommandBuilder cmd,
                               @Nonnull UIEventBuilder events,
                               @Nonnull Store<EntityStore> store) {
        this.savedRef   = ref;
        this.savedStore = store;

        cmd.append(PAGE_PATH);

        cmd.set("#TitleLabel.Text", L("gui.admin.title"));

        // ── Bind events ─────────────────────────────────────
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadBtn",
                new EventData().append(KEY_ACTION, "reload"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBtn",
                new EventData().append(KEY_ACTION, "save"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DebugToggleBtn",
                new EventData().append(KEY_ACTION, "toggle_debug"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LangToggleBtn",
                new EventData().append(KEY_ACTION, "toggle_lang"));

        // Level editor navigation
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevLevelBtn",
                new EventData().append(KEY_ACTION, "level_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextLevelBtn",
                new EventData().append(KEY_ACTION, "level_next"));

        // Level adjust buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ChanceUpBtn",
                new EventData().append(KEY_ACTION, "chance_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ChanceDownBtn",
                new EventData().append(KEY_ACTION, "chance_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CostUpBtn",
                new EventData().append(KEY_ACTION, "cost_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CostDownBtn",
                new EventData().append(KEY_ACTION, "cost_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DmgUpBtn",
                new EventData().append(KEY_ACTION, "dmg_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DmgDownBtn",
                new EventData().append(KEY_ACTION, "dmg_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DefUpBtn",
                new EventData().append(KEY_ACTION, "def_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DefDownBtn",
                new EventData().append(KEY_ACTION, "def_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MaxLevelUpBtn",
                new EventData().append(KEY_ACTION, "maxlevel_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MaxLevelDownBtn",
                new EventData().append(KEY_ACTION, "maxlevel_down"));

        // Material editor buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MatPrevBtn",
                new EventData().append(KEY_ACTION, "mat_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MatNextBtn",
                new EventData().append(KEY_ACTION, "mat_next"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MatCountUpBtn",
                new EventData().append(KEY_ACTION, "mat_count_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MatCountDownBtn",
                new EventData().append(KEY_ACTION, "mat_count_down"));

        // Failure return rate buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReturnRateUpBtn",
                new EventData().append(KEY_ACTION, "return_rate_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReturnRateDownBtn",
                new EventData().append(KEY_ACTION, "return_rate_down"));

        // Protection feature buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ProtectionToggleBtn",
                new EventData().append(KEY_ACTION, "toggle_protection"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ProtCostUpBtn",
                new EventData().append(KEY_ACTION, "prot_cost_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ProtCostDownBtn",
                new EventData().append(KEY_ACTION, "prot_cost_down"));

        // Reverse recipe editor buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRecipeBtn",
                new EventData().append(KEY_ACTION, "recipe_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextRecipeBtn",
                new EventData().append(KEY_ACTION, "recipe_next"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeMatPrevBtn",
                new EventData().append(KEY_ACTION, "recipe_mat_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeMatNextBtn",
                new EventData().append(KEY_ACTION, "recipe_mat_next"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeCountUpBtn",
                new EventData().append(KEY_ACTION, "recipe_count_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeCountDownBtn",
                new EventData().append(KEY_ACTION, "recipe_count_down"));

        // Allowed items editor buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnPrevBtn",
                new EventData().append(KEY_ACTION, "wpn_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnNextBtn",
                new EventData().append(KEY_ACTION, "wpn_next"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnRemoveBtn",
                new EventData().append(KEY_ACTION, "wpn_remove"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnAddBtn",
                new EventData().append(KEY_ACTION, "wpn_add"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmPrevBtn",
                new EventData().append(KEY_ACTION, "arm_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmNextBtn",
                new EventData().append(KEY_ACTION, "arm_next"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmRemoveBtn",
                new EventData().append(KEY_ACTION, "arm_remove"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmAddBtn",
                new EventData().append(KEY_ACTION, "arm_add"));

        // Weapon character editing buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnCurLeftBtn",
                new EventData().append(KEY_ACTION, "wpn_cur_left"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnCurRightBtn",
                new EventData().append(KEY_ACTION, "wpn_cur_right"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnCharUpBtn",
                new EventData().append(KEY_ACTION, "wpn_char_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnCharDownBtn",
                new EventData().append(KEY_ACTION, "wpn_char_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnCharInsBtn",
                new EventData().append(KEY_ACTION, "wpn_char_ins"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WpnCharDelBtn",
                new EventData().append(KEY_ACTION, "wpn_char_del"));

        // Armor character editing buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmCurLeftBtn",
                new EventData().append(KEY_ACTION, "arm_cur_left"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmCurRightBtn",
                new EventData().append(KEY_ACTION, "arm_cur_right"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmCharUpBtn",
                new EventData().append(KEY_ACTION, "arm_char_up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmCharDownBtn",
                new EventData().append(KEY_ACTION, "arm_char_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmCharInsBtn",
                new EventData().append(KEY_ACTION, "arm_char_ins"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ArmCharDelBtn",
                new EventData().append(KEY_ACTION, "arm_char_del"));

        // Recipe add/remove buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeAddBtn",
                new EventData().append(KEY_ACTION, "recipe_add"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeRemoveBtn",
                new EventData().append(KEY_ACTION, "recipe_remove"));

        // ── Status banner ───────────────────────────────────
        if (statusMessage != null && !statusMessage.isEmpty()) {
            cmd.set("#StatusBanner.Visible", true);
            cmd.set("#StatusText.Text", stripForUI(statusMessage));
        }

        // ── Fill data ───────────────────────────────────────
        updateGeneralSection(cmd);
        updateLevelEditor(cmd);
        updateAllowedItemsEditor(cmd);
        updateRecipeEditor(cmd);
        updateStats(cmd);

        LOGGER.info("Admin GUI built for {}", playerUuid);
    }
    // ════════════════════════════════════════════════════════

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull AdminEventData data) {
        ReforgeConfig config = configManager.getConfig();

        switch (data.action) {
            case "reload" -> {
                boolean ok = configManager.reload();
                lang.reload(configManager.getConfig().getGeneral().getLanguage());
                refreshPage(ok ? L("gui.admin.reload_ok") : L("gui.admin.reload_fail"));
            }
            case "save" -> {
                configManager.saveConfig();
                refreshPage(L("gui.admin.saved"));
            }
            case "toggle_debug" -> {
                config.getGeneral().setDebugMode(!config.getGeneral().isDebugMode());
                configManager.saveConfig();
                refreshPage(L("gui.admin.debug_toggled"));
            }
            case "toggle_lang" -> {
                String[] langs = {"en", "ru", "de", "fr", "es", "pt_br"};
                String current = config.getGeneral().getLanguage();
                int idx = 0;
                for (int i = 0; i < langs.length; i++) {
                    if (langs[i].equals(current)) { idx = i; break; }
                }
                String next = langs[(idx + 1) % langs.length];
                config.getGeneral().setLanguage(next);
                lang.reload(next);
                configManager.saveConfig();
                refreshPage(L("gui.admin.lang_changed", "lang", next));
            }
            case "level_prev" -> {
                if (editingLevel > 1) editingLevel--;
                refreshPage(null);
            }
            case "level_next" -> {
                if (editingLevel < config.getGeneral().getMaxReforgeLevel()) editingLevel++;
                refreshPage(null);
            }
            case "chance_up" -> {
                adjustLevelParam("chance", 0.05);
            }
            case "chance_down" -> {
                adjustLevelParam("chance", -0.05);
            }
            case "cost_up" -> {
                adjustLevelParam("cost", 50.0);
            }
            case "cost_down" -> {
                adjustLevelParam("cost", -50.0);
            }
            case "dmg_up" -> {
                adjustLevelParam("dmg", 1.0);
            }
            case "dmg_down" -> {
                adjustLevelParam("dmg", -1.0);
            }
            case "def_up" -> {
                adjustLevelParam("def", 1.0);
            }
            case "def_down" -> {
                adjustLevelParam("def", -1.0);
            }

            case "maxlevel_up" -> {
                int ml = config.getGeneral().getMaxReforgeLevel();
                if (ml < 20) {
                    config.getGeneral().setMaxReforgeLevel(ml + 1);
                    configManager.saveConfig();
                }
                refreshPage(null);
            }
            case "maxlevel_down" -> {
                int ml = config.getGeneral().getMaxReforgeLevel();
                if (ml > 1) {
                    config.getGeneral().setMaxReforgeLevel(ml - 1);
                    if (editingLevel > ml - 1) editingLevel = ml - 1;
                    configManager.saveConfig();
                }
                refreshPage(null);
            }
            case "mat_prev" -> {
                cycleMaterial(-1);
            }
            case "mat_next" -> {
                cycleMaterial(1);
            }
            case "mat_count_up" -> {
                adjustMatCount(1);
            }
            case "mat_count_down" -> {
                adjustMatCount(-1);
            }
            case "return_rate_up" -> {
                double rate = config.getGeneral().getFailureReturnRate();
                rate = Math.min(1.0, rate + 0.05);
                config.getGeneral().setFailureReturnRate(rate);
                configManager.saveConfig();
                refreshPage(null);
            }
            case "return_rate_down" -> {
                double rate = config.getGeneral().getFailureReturnRate();
                rate = Math.max(0.0, rate - 0.05);
                config.getGeneral().setFailureReturnRate(rate);
                configManager.saveConfig();
                refreshPage(null);
            }
            case "toggle_protection" -> {
                config.getGeneral().setProtectionEnabled(!config.getGeneral().isProtectionEnabled());
                configManager.saveConfig();
                refreshPage(L("gui.admin.protection_toggled"));
            }
            case "prot_cost_up" -> {
                double mult = config.getGeneral().getProtectionCostMultiplier();
                mult = Math.min(10.0, mult + 0.5);
                config.getGeneral().setProtectionCostMultiplier(mult);
                configManager.saveConfig();
                refreshPage(null);
            }
            case "prot_cost_down" -> {
                double mult = config.getGeneral().getProtectionCostMultiplier();
                mult = Math.max(0.5, mult - 0.5);
                config.getGeneral().setProtectionCostMultiplier(mult);
                configManager.saveConfig();
                refreshPage(null);
            }
            case "recipe_prev" -> {
                navigateRecipe(-1);
            }
            case "recipe_next" -> {
                navigateRecipe(1);
            }
            case "recipe_mat_prev" -> {
                cycleRecipeMaterial(-1);
            }
            case "recipe_mat_next" -> {
                cycleRecipeMaterial(1);
            }
            case "recipe_count_up" -> {
                adjustRecipeCount(1);
            }
            case "recipe_count_down" -> {
                adjustRecipeCount(-1);
            }
            case "wpn_prev" -> {
                navigateAllowedPattern("weapons", -1);
            }
            case "wpn_next" -> {
                navigateAllowedPattern("weapons", 1);
            }
            case "wpn_remove" -> {
                removeAllowedPattern("weapons");
            }
            case "wpn_add" -> {
                addAllowedPattern("weapons");
            }
            case "arm_prev" -> {
                navigateAllowedPattern("armor", -1);
            }
            case "arm_next" -> {
                navigateAllowedPattern("armor", 1);
            }
            case "arm_remove" -> {
                removeAllowedPattern("armor");
            }
            case "arm_add" -> {
                addAllowedPattern("armor");
            }
            // Weapon character editing
            case "wpn_cur_left" -> { moveCursor("weapons", -1); }
            case "wpn_cur_right" -> { moveCursor("weapons", 1); }
            case "wpn_char_up" -> { cycleCharAt("weapons", 1); }
            case "wpn_char_down" -> { cycleCharAt("weapons", -1); }
            case "wpn_char_ins" -> { insertCharAt("weapons"); }
            case "wpn_char_del" -> { deleteCharAt("weapons"); }
            // Armor character editing
            case "arm_cur_left" -> { moveCursor("armor", -1); }
            case "arm_cur_right" -> { moveCursor("armor", 1); }
            case "arm_char_up" -> { cycleCharAt("armor", 1); }
            case "arm_char_down" -> { cycleCharAt("armor", -1); }
            case "arm_char_ins" -> { insertCharAt("armor"); }
            case "arm_char_del" -> { deleteCharAt("armor"); }
            case "recipe_add" -> {
                addNewRecipe();
            }
            case "recipe_remove" -> {
                removeCurrentRecipe();
            }
        }
    }

    private void adjustLevelParam(String param, double delta) {
        ReforgeConfig config = configManager.getConfig();
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(editingLevel);
        if (lc == null) {
            lc = new ReforgeConfig.LevelConfig();
            config.getLevels().put(String.valueOf(editingLevel), lc);
        }

        switch (param) {
            case "chance" -> {
                double v = Math.max(0.01, Math.min(1.0, lc.getSuccessChance() + delta));
                lc.setSuccessChance(v);
            }
            case "cost" -> {
                double v = Math.max(0, lc.getCoinCost() + delta);
                lc.setCoinCost(v);
            }
            case "dmg" -> {
                double dmg = Math.max(0, lc.getWeaponDamageBonus() + delta);
                lc.setWeaponDamageBonus(dmg);
            }
            case "def" -> {
                double def = Math.max(0, lc.getArmorDefenseBonus() + delta);
                lc.setArmorDefenseBonus(def);
            }
        }

        configManager.saveConfig();
        refreshPage(null);
    }

    private void cycleMaterial(int direction) {
        ReforgeConfig config = configManager.getConfig();
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(editingLevel);
        if (lc == null) return;

        List<ReforgeConfig.MaterialEntry> mats = lc.getMaterials();
        String currentId = (!mats.isEmpty()) ? mats.get(0).getItemId() : MATERIAL_TYPES[0];
        int currentCount = (!mats.isEmpty()) ? mats.get(0).getCount() : 2;

        // Find current index in MATERIAL_TYPES
        int idx = 0;
        for (int i = 0; i < MATERIAL_TYPES.length; i++) {
            if (MATERIAL_TYPES[i].equals(currentId) || stripNs(MATERIAL_TYPES[i]).equals(stripNs(currentId))) {
                idx = i;
                break;
            }
        }

        idx = (idx + direction + MATERIAL_TYPES.length) % MATERIAL_TYPES.length;

        mats.clear();
        mats.add(new ReforgeConfig.MaterialEntry(MATERIAL_TYPES[idx], currentCount));
        configManager.saveConfig();
        refreshPage(null);
    }

    private void adjustMatCount(int delta) {
        ReforgeConfig config = configManager.getConfig();
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(editingLevel);
        if (lc == null) return;

        List<ReforgeConfig.MaterialEntry> mats = lc.getMaterials();
        if (mats.isEmpty()) {
            mats.add(new ReforgeConfig.MaterialEntry(MATERIAL_TYPES[0], 2));
        }

        ReforgeConfig.MaterialEntry mat = mats.get(0);
        int newCount = Math.max(1, mat.getCount() + delta);
        mat.setCount(newCount);
        configManager.saveConfig();
        refreshPage(null);
    }

    private static String stripNs(String id) {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    // ── Reverse recipe helpers ─────────────────────────────

    private List<String> getRecipeKeys() {
        Map<String, List<ReforgeConfig.MaterialEntry>> recipes = configManager.getConfig().getReverseRecipes();
        return new ArrayList<>(recipes.keySet());
    }

    private void navigateRecipe(int direction) {
        List<String> keys = getRecipeKeys();
        if (keys.isEmpty()) return;
        editingRecipeIdx = (editingRecipeIdx + direction + keys.size()) % keys.size();
        refreshPage(null);
    }

    private void cycleRecipeMaterial(int direction) {
        List<String> keys = getRecipeKeys();
        if (keys.isEmpty() || editingRecipeIdx >= keys.size()) return;

        String recipeKey = keys.get(editingRecipeIdx);
        Map<String, List<ReforgeConfig.MaterialEntry>> recipes = configManager.getConfig().getReverseRecipes();
        List<ReforgeConfig.MaterialEntry> mats = recipes.get(recipeKey);
        if (mats == null || mats.isEmpty()) return;

        ReforgeConfig.MaterialEntry mat = mats.get(0);
        String currentId = mat.getItemId();
        int idx = 0;
        for (int i = 0; i < MATERIAL_TYPES.length; i++) {
            if (MATERIAL_TYPES[i].equals(currentId) || stripNs(MATERIAL_TYPES[i]).equals(stripNs(currentId))) {
                idx = i;
                break;
            }
        }
        idx = (idx + direction + MATERIAL_TYPES.length) % MATERIAL_TYPES.length;
        mat.setItemId(MATERIAL_TYPES[idx]);
        configManager.saveConfig();
        refreshPage(null);
    }

    private void adjustRecipeCount(int delta) {
        List<String> keys = getRecipeKeys();
        if (keys.isEmpty() || editingRecipeIdx >= keys.size()) return;

        String recipeKey = keys.get(editingRecipeIdx);
        Map<String, List<ReforgeConfig.MaterialEntry>> recipes = configManager.getConfig().getReverseRecipes();
        List<ReforgeConfig.MaterialEntry> mats = recipes.get(recipeKey);
        if (mats == null || mats.isEmpty()) return;

        ReforgeConfig.MaterialEntry mat = mats.get(0);
        mat.setCount(Math.max(1, mat.getCount() + delta));
        configManager.saveConfig();
        refreshPage(null);
    }

    // ── Allowed items helpers ──────────────────────────────

    private void navigateAllowedPattern(String type, int direction) {
        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();
        if ("weapons".equals(type)) {
            List<String> list = allowed.getWeapons();
            if (!list.isEmpty()) {
                editingWeaponIdx = (editingWeaponIdx + direction + list.size()) % list.size();
            }
            weaponCursorPos = 0;
        } else {
            List<String> list = allowed.getArmor();
            if (!list.isEmpty()) {
                editingArmorIdx = (editingArmorIdx + direction + list.size()) % list.size();
            }
            armorCursorPos = 0;
        }
        refreshPage(null);
    }

    private void removeAllowedPattern(String type) {
        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();
        if ("weapons".equals(type)) {
            List<String> list = allowed.getWeapons();
            if (!list.isEmpty() && editingWeaponIdx < list.size()) {
                list.remove(editingWeaponIdx);
                if (editingWeaponIdx >= list.size() && editingWeaponIdx > 0) editingWeaponIdx--;
            }
        } else {
            List<String> list = allowed.getArmor();
            if (!list.isEmpty() && editingArmorIdx < list.size()) {
                list.remove(editingArmorIdx);
                if (editingArmorIdx >= list.size() && editingArmorIdx > 0) editingArmorIdx--;
            }
        }
        configManager.saveConfig();
        refreshPage(null);
    }

    private void addAllowedPattern(String type) {
        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();
        List<String> list = "weapons".equals(type) ? allowed.getWeapons() : allowed.getArmor();

        list.add("New_*");
        if ("weapons".equals(type)) {
            editingWeaponIdx = list.size() - 1;
            weaponCursorPos = 0;
        } else {
            editingArmorIdx = list.size() - 1;
            armorCursorPos = 0;
        }
        configManager.saveConfig();
        refreshPage(null);
    }

    // ── Character-by-character pattern editing ─────────────

    private void moveCursor(String type, int direction) {
        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();
        List<String> list = "weapons".equals(type) ? allowed.getWeapons() : allowed.getArmor();
        int idx = "weapons".equals(type) ? editingWeaponIdx : editingArmorIdx;
        if (list.isEmpty() || idx >= list.size()) return;

        String pattern = list.get(idx);
        if ("weapons".equals(type)) {
            weaponCursorPos = Math.max(0, Math.min(pattern.length() - 1, weaponCursorPos + direction));
        } else {
            armorCursorPos = Math.max(0, Math.min(pattern.length() - 1, armorCursorPos + direction));
        }
        refreshPage(null);
    }

    private void cycleCharAt(String type, int direction) {
        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();
        List<String> list = "weapons".equals(type) ? allowed.getWeapons() : allowed.getArmor();
        int idx = "weapons".equals(type) ? editingWeaponIdx : editingArmorIdx;
        int cursorPos = "weapons".equals(type) ? weaponCursorPos : armorCursorPos;
        if (list.isEmpty() || idx >= list.size()) return;

        String pattern = list.get(idx);
        if (cursorPos >= pattern.length()) return;

        char current = pattern.charAt(cursorPos);
        int charIdx = CHARSET.indexOf(current);
        if (charIdx < 0) charIdx = 0;
        charIdx = (charIdx + direction + CHARSET.length()) % CHARSET.length();

        StringBuilder sb = new StringBuilder(pattern);
        sb.setCharAt(cursorPos, CHARSET.charAt(charIdx));
        list.set(idx, sb.toString());
        configManager.saveConfig();
        refreshPage(null);
    }

    private void insertCharAt(String type) {
        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();
        List<String> list = "weapons".equals(type) ? allowed.getWeapons() : allowed.getArmor();
        int idx = "weapons".equals(type) ? editingWeaponIdx : editingArmorIdx;
        int cursorPos = "weapons".equals(type) ? weaponCursorPos : armorCursorPos;
        if (list.isEmpty() || idx >= list.size()) return;

        String pattern = list.get(idx);
        StringBuilder sb = new StringBuilder(pattern);
        sb.insert(cursorPos, 'A');
        list.set(idx, sb.toString());
        configManager.saveConfig();
        refreshPage(null);
    }

    private void deleteCharAt(String type) {
        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();
        List<String> list = "weapons".equals(type) ? allowed.getWeapons() : allowed.getArmor();
        int idx = "weapons".equals(type) ? editingWeaponIdx : editingArmorIdx;
        int cursorPos = "weapons".equals(type) ? weaponCursorPos : armorCursorPos;
        if (list.isEmpty() || idx >= list.size()) return;

        String pattern = list.get(idx);
        if (pattern.length() <= 1) return; // Keep at least 1 char
        if (cursorPos >= pattern.length()) return;

        StringBuilder sb = new StringBuilder(pattern);
        sb.deleteCharAt(cursorPos);
        list.set(idx, sb.toString());

        // Adjust cursor if past end
        if ("weapons".equals(type)) {
            if (weaponCursorPos >= sb.length()) weaponCursorPos = sb.length() - 1;
        } else {
            if (armorCursorPos >= sb.length()) armorCursorPos = sb.length() - 1;
        }
        configManager.saveConfig();
        refreshPage(null);
    }

    private void addNewRecipe() {
        Map<String, List<ReforgeConfig.MaterialEntry>> recipes = configManager.getConfig().getReverseRecipes();

        String newKey = null;
        for (String tpl : RECIPE_ITEM_TEMPLATES) {
            if (!recipes.containsKey(tpl)) {
                newKey = tpl;
                break;
            }
        }
        if (newKey == null) newKey = "Custom_Item_" + (recipes.size() + 1);

        List<ReforgeConfig.MaterialEntry> mats = new ArrayList<>();
        mats.add(new ReforgeConfig.MaterialEntry(MATERIAL_TYPES[0], 10));
        recipes.put(newKey, mats);

        List<String> keys = getRecipeKeys();
        editingRecipeIdx = keys.indexOf(newKey);
        if (editingRecipeIdx < 0) editingRecipeIdx = keys.size() - 1;

        configManager.saveConfig();
        refreshPage(null);
    }

    private void removeCurrentRecipe() {
        List<String> keys = getRecipeKeys();
        if (keys.isEmpty() || editingRecipeIdx >= keys.size()) return;

        String recipeKey = keys.get(editingRecipeIdx);
        configManager.getConfig().getReverseRecipes().remove(recipeKey);

        if (editingRecipeIdx >= keys.size() - 1 && editingRecipeIdx > 0) editingRecipeIdx--;
        configManager.saveConfig();
        refreshPage(null);
    }

    // ════════════════════════════════════════════════════════
    //  DATA BUILDERS
    // ════════════════════════════════════════════════════════

    private void updateGeneralSection(@Nonnull UICommandBuilder cmd) {
        ReforgeConfig config = configManager.getConfig();

        cmd.set("#SecGeneral.Text", L("gui.admin.sec_general"));
        cmd.set("#DebugLabel.Text", L("gui.admin.debug_mode"));
        cmd.set("#DebugValue.Text", config.getGeneral().isDebugMode() ? "ON" : "OFF");
        cmd.set("#LangLabel.Text", L("gui.admin.language"));
        cmd.set("#LangValue.Text", config.getGeneral().getLanguage().toUpperCase());
        cmd.set("#MaxLevelLabel.Text", L("gui.admin.max_level"));
        cmd.set("#MaxLevelValue.Text", String.valueOf(config.getGeneral().getMaxReforgeLevel()));

        // Failure return rate
        cmd.set("#ReturnRateLabel.Text", L("gui.admin.return_rate"));
        cmd.set("#ReturnRateValue.Text", MessageUtil.formatPercent(config.getGeneral().getFailureReturnRate()));

        // Protection feature
        cmd.set("#ProtectionLabel.Text", L("gui.admin.protection"));
        cmd.set("#ProtectionValue.Text", config.getGeneral().isProtectionEnabled() ? "ON" : "OFF");
        cmd.set("#ProtCostLabel.Text", L("gui.admin.prot_cost_mult"));
        cmd.set("#ProtCostValue.Text", String.format("x%.1f", config.getGeneral().getProtectionCostMultiplier()));
    }

    private void updateLevelEditor(@Nonnull UICommandBuilder cmd) {
        ReforgeConfig config = configManager.getConfig();
        ReforgeConfig.LevelConfig lc = config.getLevelConfig(editingLevel);

        cmd.set("#SecLevelEditor.Text", L("gui.admin.sec_level_editor"));
        cmd.set("#CurrentLevelLabel.Text", L("gui.admin.editing_level", "level", String.valueOf(editingLevel)));

        if (lc != null) {
            cmd.set("#ChanceLabel.Text", L("gui.admin.chance"));
            cmd.set("#ChanceValue.Text", MessageUtil.formatPercent(lc.getSuccessChance()));

            cmd.set("#CostLabel.Text", L("gui.admin.coin_cost"));
            cmd.set("#CostValue.Text", MessageUtil.formatCoins(lc.getCoinCost()) + "$");

            cmd.set("#DmgBonusLabel.Text", L("gui.admin.dmg_bonus"));
            cmd.set("#DmgValue.Text", MessageUtil.formatBonus(lc.getWeaponDamageBonus()));

            cmd.set("#DefBonusLabel.Text", L("gui.admin.def_bonus"));
            cmd.set("#DefValue.Text", MessageUtil.formatBonus(lc.getArmorDefenseBonus()));

            // Materials with icon
            cmd.set("#MaterialsLabel.Text", L("gui.admin.materials"));
            if (!lc.getMaterials().isEmpty()) {
                ReforgeConfig.MaterialEntry mat = lc.getMaterials().get(0);
                String matId = mat.getItemId();
                String iconId = stripNs(matId);
                // Pretty name: "Ingredient_Bar_Iron" → "Iron Bar"
                String prettyName = iconId.replace("Ingredient_Bar_", "").replace("_", " ") + " Bar";
                cmd.set("#MatEditIcon.ItemId", iconId);
                cmd.set("#MaterialsValue.Text", prettyName);
                cmd.set("#MatCountValue.Text", String.valueOf(mat.getCount()));
            } else {
                cmd.set("#MatEditIcon.ItemId", "");
                cmd.set("#MaterialsValue.Text", "—");
                cmd.set("#MatCountValue.Text", "0");
            }
        }
    }

    private void updateAllowedItemsEditor(@Nonnull UICommandBuilder cmd) {
        cmd.set("#SecAllowed.Text", L("gui.admin.sec_allowed"));

        ReforgeConfig.AllowedItems allowed = configManager.getConfig().getAllowedItems();

        // Weapons
        cmd.set("#WpnLabel.Text", L("gui.admin.weapons"));
        List<String> weapons = allowed.getWeapons();
        if (!weapons.isEmpty()) {
            if (editingWeaponIdx >= weapons.size()) editingWeaponIdx = 0;
            String pattern = weapons.get(editingWeaponIdx);
            if (weaponCursorPos >= pattern.length()) weaponCursorPos = Math.max(0, pattern.length() - 1);

            // Show pattern with cursor brackets: "Cus[t]om_*"
            StringBuilder display = new StringBuilder();
            for (int i = 0; i < pattern.length(); i++) {
                if (i == weaponCursorPos) {
                    display.append('[').append(pattern.charAt(i)).append(']');
                } else {
                    display.append(pattern.charAt(i));
                }
            }
            cmd.set("#WpnPatternLabel.Text", display.toString());
            cmd.set("#WpnCountLabel.Text", (editingWeaponIdx + 1) + "/" + weapons.size());

            // Edit row
            cmd.set("#WpnPosLabel.Text", (weaponCursorPos + 1) + "/" + pattern.length());
            cmd.set("#WpnCharLabel.Text", String.valueOf(pattern.charAt(weaponCursorPos)));
        } else {
            cmd.set("#WpnPatternLabel.Text", "\u2014");
            cmd.set("#WpnCountLabel.Text", "0/0");
            cmd.set("#WpnPosLabel.Text", "—");
            cmd.set("#WpnCharLabel.Text", "—");
        }

        // Armor
        cmd.set("#ArmLabel.Text", L("gui.admin.armor_patterns"));
        List<String> armor = allowed.getArmor();
        if (!armor.isEmpty()) {
            if (editingArmorIdx >= armor.size()) editingArmorIdx = 0;
            String pattern = armor.get(editingArmorIdx);
            if (armorCursorPos >= pattern.length()) armorCursorPos = Math.max(0, pattern.length() - 1);

            // Show pattern with cursor brackets
            StringBuilder display = new StringBuilder();
            for (int i = 0; i < pattern.length(); i++) {
                if (i == armorCursorPos) {
                    display.append('[').append(pattern.charAt(i)).append(']');
                } else {
                    display.append(pattern.charAt(i));
                }
            }
            cmd.set("#ArmPatternLabel.Text", display.toString());
            cmd.set("#ArmCountLabel.Text", (editingArmorIdx + 1) + "/" + armor.size());

            // Edit row
            cmd.set("#ArmPosLabel.Text", (armorCursorPos + 1) + "/" + pattern.length());
            cmd.set("#ArmCharLabel.Text", String.valueOf(pattern.charAt(armorCursorPos)));
        } else {
            cmd.set("#ArmPatternLabel.Text", "\u2014");
            cmd.set("#ArmCountLabel.Text", "0/0");
            cmd.set("#ArmPosLabel.Text", "—");
            cmd.set("#ArmCharLabel.Text", "—");
        }
    }

    private void updateRecipeEditor(@Nonnull UICommandBuilder cmd) {
        cmd.set("#SecRecipes.Text", L("gui.admin.sec_recipes"));

        List<String> keys = getRecipeKeys();
        if (keys.isEmpty()) {
            cmd.set("#RecipeNameLabel.Text", "No recipes");
            cmd.set("#RecipeMatIcon.ItemId", "");
            cmd.set("#RecipeMatName.Text", "—");
            cmd.set("#RecipeCountValue.Text", "0");
            return;
        }

        if (editingRecipeIdx >= keys.size()) editingRecipeIdx = 0;
        String recipeKey = keys.get(editingRecipeIdx);

        // Show recipe key as display name (e.g., "Weapon_Sword_Iron")
        cmd.set("#RecipeNameLabel.Text", recipeKey + " (" + (editingRecipeIdx + 1) + "/" + keys.size() + ")");

        Map<String, List<ReforgeConfig.MaterialEntry>> recipes = configManager.getConfig().getReverseRecipes();
        List<ReforgeConfig.MaterialEntry> mats = recipes.get(recipeKey);
        if (mats != null && !mats.isEmpty()) {
            ReforgeConfig.MaterialEntry mat = mats.get(0);
            String matId = mat.getItemId();
            String iconId = stripNs(matId);
            String prettyName = iconId.replace("Ingredient_Bar_", "").replace("_", " ") + " Bar";
            cmd.set("#RecipeMatIcon.ItemId", iconId);
            cmd.set("#RecipeMatName.Text", prettyName);
            cmd.set("#RecipeCountValue.Text", String.valueOf(mat.getCount()));
        } else {
            cmd.set("#RecipeMatIcon.ItemId", "");
            cmd.set("#RecipeMatName.Text", "—");
            cmd.set("#RecipeCountValue.Text", "0");
        }
    }

    private void updateStats(@Nonnull UICommandBuilder cmd) {
        ReforgeConfig config = configManager.getConfig();

        cmd.set("#SecStats.Text", L("gui.admin.sec_stats"));
        cmd.set("#VersionLabel.Text", L("gui.admin.version"));
        cmd.set("#VersionValue.Text", pluginVersion);
        cmd.set("#TotalLevelsLabel.Text", L("gui.admin.total_levels"));
        cmd.set("#TotalLevelsValue.Text", String.valueOf(config.getLevels().size()));
    }

    // ════════════════════════════════════════════════════════
    //  REFRESH
    // ════════════════════════════════════════════════════════

    private void refreshPage(@Nullable String status) {
        try {
            UICommandBuilder cmd = new UICommandBuilder();

            cmd.set("#StatusBanner.Visible", status != null && !status.isEmpty());
            if (status != null && !status.isEmpty()) {
                cmd.set("#StatusText.Text", stripForUI(status));
            }

            updateGeneralSection(cmd);
            updateLevelEditor(cmd);
            updateAllowedItemsEditor(cmd);
            updateRecipeEditor(cmd);
            updateStats(cmd);

            sendUpdate(cmd);
        } catch (Exception e) {
            LOGGER.warn("[refreshPage] sendUpdate failed: {}", e.getMessage());
            reopen(status);
        }
    }

    private void reopen(@Nullable String status) {
        close();
        AdminReforgeGui newPage = new AdminReforgeGui(
                configManager, lang, playerRef, playerUuid, pluginVersion, status);
        PageOpenHelper.openPage(savedRef, savedStore, newPage);
    }

    // ════════════════════════════════════════════════════════
    //  STATIC OPEN
    // ════════════════════════════════════════════════════════

    public static void open(@Nonnull ConfigManager configManager,
                            @Nonnull LangManager lang,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid,
                            @Nonnull String pluginVersion) {
        AdminReforgeGui page = new AdminReforgeGui(
                configManager, lang, playerRef, playerUuid, pluginVersion, null);
        PageOpenHelper.openPage(ref, store, page);
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private String L(String key, String... args) {
        return lang.getForPlayer(playerUuid, key, args);
    }

    private static String stripForUI(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").trim();
    }

    // ════════════════════════════════════════════════════════
    //  EVENT DATA CLASS
    // ════════════════════════════════════════════════════════

    public static class AdminEventData {
        public String action = "";
        public String slot = "";
    }
}
