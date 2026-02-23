package com.crystalrealm.ecotalereforging.gui;

import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.lang.LangManager;
import com.crystalrealm.ecotalereforging.model.ReforgeAttemptInfo;
import com.crystalrealm.ecotalereforging.model.ReforgeResult;
import com.crystalrealm.ecotalereforging.service.ItemValidationService;
import com.crystalrealm.ecotalereforging.service.ReforgeService;
import com.crystalrealm.ecotalereforging.service.WeaponStatsService;
import com.crystalrealm.ecotalereforging.util.MessageUtil;
import com.crystalrealm.ecotalereforging.util.PluginLogger;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Player-facing reforging GUI.
 *
 * <p>Layout:</p>
 * <ul>
 *   <li>Center: Current weapon/armor info, reforge level, stats</li>
 *   <li>Left: Required materials + coin cost, success chance</li>
 *   <li>Bottom: Forge button</li>
 *   <li>Status banners for success/failure notifications</li>
 * </ul>
 */
public final class ReforgeGui extends InteractiveCustomUIPage<ReforgeGui.ReforgeEventData> {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String PAGE_PATH = "Pages/CrystalRealm_EcoTaleReforging_ReforgePanel.ui";

    private static final String KEY_ACTION = "Action";
    private static final String KEY_SLOT   = "Slot";

    static final BuilderCodec<ReforgeEventData> CODEC = ReflectiveCodecBuilder
            .<ReforgeEventData>create(ReforgeEventData.class, ReforgeEventData::new)
            .addStringField(KEY_ACTION, (d, v) -> d.action = v, d -> d.action)
            .addStringField(KEY_SLOT,   (d, v) -> d.slot = v,   d -> d.slot)
            .build();

    // ── Dependencies ────────────────────────────────────────
    private final ReforgeService        reforgeService;
    private final ItemValidationService validator;
    private final ReforgeConfig         config;
    private final LangManager           lang;
    private final WeaponStatsService    weaponStats;
    private final UUID                  playerUuid;
    private final Player                player;

    @Nullable private String errorMessage;
    @Nullable private String successMessage;

    /** Currently selected inventory slot index, or -1 if none. */
    private int selectedSlot = -1;

    /** Whether the player has enabled protection for the next reforge attempt. */
    private boolean protectionSelected = false;

    private static final int MAX_SLOTS = 8;

    // Saved for re-open
    private Ref<EntityStore>   savedRef;
    private Store<EntityStore> savedStore;

    // ════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════

    public ReforgeGui(@Nonnull ReforgeService reforgeService,
                      @Nonnull ItemValidationService validator,
                      @Nonnull ReforgeConfig config,
                      @Nonnull LangManager lang,
                      @Nonnull WeaponStatsService weaponStats,
                      @Nonnull PlayerRef playerRef,
                      @Nonnull UUID playerUuid,
                      @Nonnull Player player,
                      @Nullable String errorMessage,
                      @Nullable String successMessage) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
        this.reforgeService = reforgeService;
        this.validator      = validator;
        this.config         = config;
        this.lang           = lang;
        this.weaponStats    = weaponStats;
        this.playerUuid     = playerUuid;
        this.player         = player;
        this.errorMessage   = errorMessage;
        this.successMessage = successMessage;
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
            LOGGER.error("ReforgeGui.build() threw: {} — {}", t.getClass().getName(), t.getMessage());
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            LOGGER.error("ReforgeGui.build() stack trace:\n{}", sw.toString());
            throw t; // re-throw so the caller knows it failed
        }
    }

    private void buildInternal(@Nonnull Ref<EntityStore> ref,
                               @Nonnull UICommandBuilder cmd,
                               @Nonnull UIEventBuilder events,
                               @Nonnull Store<EntityStore> store) {
        this.savedRef   = ref;
        this.savedStore = store;

        cmd.append(PAGE_PATH);

        // ── Title ───────────────────────────────────────────
        cmd.set("#TitleLabel.Text", L("gui.reforge.title"));

        // ── Translate static UI labels ──────────────────────
        cmd.set("#InventoryHeader.Text", L("gui.reforge.section.inventory"));
        cmd.set("#ItemInfoHeader.Text", L("gui.reforge.section.item_info"));
        cmd.set("#LblLevel.Text", L("gui.reforge.label.level"));
        cmd.set("#LblStats.Text", L("gui.reforge.label.stats"));
        cmd.set("#LblCoinCost.Text", L("gui.reforge.label.coin_cost"));
        cmd.set("#LblSuccess.Text", L("gui.reforge.label.success"));
        cmd.set("#RefreshBtn.Text", L("gui.reforge.btn.refresh"));

        // ── Bind events ─────────────────────────────────────
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ForgeBtn",
                new EventData().append(KEY_ACTION, "reforge"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshBtn",
                new EventData().append(KEY_ACTION, "refresh"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ProtectionToggleBtn",
                new EventData().append(KEY_ACTION, "toggle_protection"));

        // ── Inventory item slot buttons (up to 8) ───────────
        for (int i = 0; i < MAX_SLOTS; i++) {
            String btnId = "#ItemSlot" + i;
            events.addEventBinding(CustomUIEventBindingType.Activating, btnId,
                    new EventData().append(KEY_ACTION, "select").append(KEY_SLOT, String.valueOf(i)));
        }

        // ── Show banners ────────────────────────────────────
        if (errorMessage != null && !errorMessage.isEmpty()) {
            cmd.set("#ErrorBanner.Visible", true);
            cmd.set("#ErrorText.Text", stripForUI(errorMessage));
        }
        if (successMessage != null && !successMessage.isEmpty()) {
            cmd.set("#SuccessBanner.Visible", true);
            cmd.set("#SuccessText.Text", stripForUI(successMessage));
        }

        // ── Scan inventory for reforgeable items ────────────
        buildItemList(cmd);

        // ── Fill selected item info ─────────────────────────
        updateItemInfo(cmd);
        updateMaterials(cmd);
        updateForgeButton(cmd);

        LOGGER.info("Reforge GUI built for {} (selectedSlot={})", playerUuid, selectedSlot);
    }

    // ════════════════════════════════════════════════════════
    //  HANDLE EVENTS
    // ════════════════════════════════════════════════════════

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ReforgeEventData data) {
        switch (data.action) {
            case "reforge"            -> handleReforge();
            case "refresh"            -> handleRefresh();
            case "select"             -> handleSelect(data.slot);
            case "toggle_protection"  -> handleToggleProtection();
        }
    }

    // ── SELECT ITEM ─────────────────────────────────────────

    private void handleSelect(String slotStr) {
        try {
            int uiIndex = Integer.parseInt(slotStr);
            List<int[]> slots = reforgeService.findReforgeableSlots(player);
            if (uiIndex >= 0 && uiIndex < slots.size()) {
                selectedSlot = slots.get(uiIndex)[0]; // actual inventory slot
                LOGGER.info("Player {} selected item at slot {} (UI index {})", playerUuid, selectedSlot, uiIndex);
                refreshPage(null, null);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid slot selection: {}", slotStr);
        }
    }

    // ── TOGGLE PROTECTION ──────────────────────────────────

    private void handleToggleProtection() {
        protectionSelected = !protectionSelected;
        LOGGER.debug("Player {} toggled protection: {}", playerUuid, protectionSelected);
        refreshPage(null, null);
    }

    // ── REFORGE CLICK ───────────────────────────────────────

    private void handleReforge() {
        if (selectedSlot < 0) {
            refreshPage(L("gui.reforge.error.no_item"), null);
            return;
        }

        // Get item from selected slot
        ItemStack item = reforgeService.getItemAtSlot(player, selectedSlot);
        if (item == null || item.isEmpty()) {
            selectedSlot = -1;
            refreshPage(L("gui.reforge.error.no_item"), null);
            return;
        }

        if (!validator.isReforgeable(item)) {
            selectedSlot = -1;
            refreshPage(L("gui.reforge.error.invalid_item"), null);
            return;
        }

        if (validator.isMaxLevel(item, playerUuid)) {
            refreshPage(L("gui.reforge.error.max_level"), null);
            return;
        }

        int currentLevel = validator.getReforgeLevel(item, playerUuid);

        // Check materials
        if (!reforgeService.hasMaterials(player, currentLevel)) {
            refreshPage(L("gui.reforge.error.no_materials"), null);
            return;
        }

        // Check coins
        if (!reforgeService.hasCoins(playerUuid, currentLevel)) {
            refreshPage(L("gui.reforge.error.no_coins"), null);
            return;
        }

        // Attempt reforge at selected slot
        ReforgeAttemptInfo result = reforgeService.attemptReforgeAtSlot(player, playerUuid, selectedSlot, protectionSelected);
        if (result == null) {
            refreshPage(L("gui.reforge.error.generic"), null);
            return;
        }

        switch (result.getResult()) {
            case SUCCESS -> {
                String bonusText;
                if (validator.isWeapon(item)) {
                    bonusText = MessageUtil.formatBonus(result.getDamageBonus()) + " DMG";
                } else {
                    bonusText = MessageUtil.formatBonus(result.getDefenseBonus()) + " DEF";
                }
                String msg = L("gui.reforge.success",
                        "level", String.valueOf(result.getTargetLevel()),
                        "bonus", bonusText);
                refreshPage(null, msg);
            }
            case FAILURE -> {
                String itemName = validator.getDisplayName(result.getItemId());
                String msg = L("gui.reforge.failure",
                        "item", itemName);
                selectedSlot = -1; // item destroyed, deselect
                refreshPage(msg, null);
            }
            case FAILURE_PROTECTED -> {
                String itemName = validator.getDisplayName(result.getItemId());
                String msg = L("gui.reforge.failure_protected",
                        "item", itemName);
                refreshPage(msg, null);
            }
            case CANNOT_ATTEMPT -> {
                refreshPage(L("gui.reforge.error.cannot_attempt"), null);
            }
        }
    }

    // ── REFRESH CLICK ───────────────────────────────────────

    private void handleRefresh() {
        refreshPage(null, null);
    }

    // ════════════════════════════════════════════════════════
    //  DATA BUILDERS
    // ════════════════════════════════════════════════════════

    /**
     * Populate the item slot list with all reforgeable items from the player's inventory.
     * Sets text + visibility for #ItemSlot0 .. #ItemSlot7.
     */
    private void buildItemList(@Nonnull UICommandBuilder cmd) {
        List<int[]> slots = reforgeService.findReforgeableSlots(player);
        int totalItems = slots.size();

        for (int i = 0; i < MAX_SLOTS; i++) {
            if (i < totalItems) {
                int invSlot = slots.get(i)[0];
                int level   = slots.get(i)[1];
                ItemStack item = reforgeService.getItemAtSlot(player, invSlot);
                String rawId = (item != null) ? item.getItemId() : "";
                String iconId = stripNamespace(rawId);
                String name = getLocalizedItemName(rawId);
                String label = level > 0 ? name + " [+" + level + "]" : name;

                // Set item icon
                cmd.set("#ItemIcon" + i + ".ItemId", iconId);
                // Set label text
                cmd.set("#ItemLabel" + i + ".Text", label);
                cmd.set("#ItemSlot" + i + "Wrap.Visible", true);

                // Highlight selected item with a brighter background
                if (invSlot == selectedSlot) {
                    cmd.set("#ItemSlot" + i + ".Background.Color", "#2a4a3a");
                } else {
                    cmd.set("#ItemSlot" + i + ".Background.Color", "#1a2a3a");
                }
            } else {
                cmd.set("#ItemSlot" + i + "Wrap.Visible", false);
            }
        }

        if (totalItems == 0) {
            cmd.set("#NoItemsWrap.Visible", true);
            cmd.set("#NoItemsLabel.Text", L("gui.reforge.no_reforgeable_items"));
        } else {
            cmd.set("#NoItemsWrap.Visible", false);
        }
    }

    private void updateItemInfo(@Nonnull UICommandBuilder cmd) {
        ItemStack item = selectedSlot >= 0 ? reforgeService.getItemAtSlot(player, selectedSlot) : null;

        if (item == null || item.isEmpty()) {
            cmd.set("#SelectedItemRow.Visible", false);
            cmd.set("#ItemLevel.Text", "");
            cmd.set("#ItemStats.Text", L("gui.reforge.select_item"));
            cmd.set("#WeaponStatsSection.Visible", false);
            return;
        }

        String itemId = item.getItemId();
        String displayName = getLocalizedItemName(itemId);
        int level = validator.getReforgeLevel(item, playerUuid);
        boolean isWeapon = validator.isWeapon(item);

        cmd.set("#SelectedItemRow.Visible", true);
        cmd.set("#SelectedItemIcon.ItemId", stripNamespace(itemId));
        cmd.set("#ItemName.Text", displayName);
        cmd.set("#ItemCategory.Text", isWeapon ? L("gui.reforge.category.weapon") : L("gui.reforge.category.armor"));

        // Show detailed weapon stats from WeaponStatsViewer
        // (Removed — weapon stats are shown on the item tooltip via DynamicTooltipsLib)
        cmd.set("#WeaponStatsSection.Visible", false);

        if (level > 0) {
            cmd.set("#ItemLevel.Text", L("gui.reforge.level", "level", String.valueOf(level)));
        } else {
            cmd.set("#ItemLevel.Text", L("gui.reforge.not_reforged"));
        }

        // Stats display
        if (validator.isReforgeable(item)) {
            int targetLevel = level + 1;
            int maxLevel = config.getGeneral().getMaxReforgeLevel();

            if (level >= maxLevel) {
                cmd.set("#ItemStats.Text", L("gui.reforge.max_level_reached"));
            } else {
                double currentBonus = isWeapon
                        ? reforgeService.getDamageBonus(level)
                        : reforgeService.getDefenseBonus(level);
                double nextBonus = isWeapon
                        ? reforgeService.getDamageBonus(targetLevel)
                        : reforgeService.getDefenseBonus(targetLevel);
                double chance = reforgeService.getSuccessChance(level);

                String statLabel = isWeapon ? "DMG" : "DEF";
                // Current → next bonus with chance
                String statsText;
                if (!isWeapon) {
                    // Armor: show DEF bonus
                    statsText = L("gui.reforge.stats",
                            "current_bonus", MessageUtil.formatBonus(currentBonus),
                            "next_bonus", MessageUtil.formatBonus(nextBonus),
                            "stat", "DEF",
                            "chance", MessageUtil.formatPercent(chance));
                } else {
                    statsText = L("gui.reforge.stats",
                            "current_bonus", MessageUtil.formatBonus(currentBonus),
                            "next_bonus", MessageUtil.formatBonus(nextBonus),
                            "stat", statLabel,
                            "chance", MessageUtil.formatPercent(chance));
                }
                cmd.set("#ItemStats.Text", statsText);
            }
        } else {
            cmd.set("#ItemStats.Text", L("gui.reforge.cannot_reforge"));
        }
    }

    private static final int MAX_MAT_SLOTS = 4;

    private void updateMaterials(@Nonnull UICommandBuilder cmd) {
        ItemStack item = selectedSlot >= 0 ? reforgeService.getItemAtSlot(player, selectedSlot) : null;

        cmd.set("#MaterialsHeader.Text", L("gui.reforge.materials_header"));

        if (item == null || item.isEmpty() || !validator.isReforgeable(item)) {
            hideMaterialSlots(cmd);
            cmd.set("#CoinCost.Text", "");
            return;
        }

        int level = validator.getReforgeLevel(item, playerUuid);
        int maxLevel = config.getGeneral().getMaxReforgeLevel();

        if (level >= maxLevel) {
            hideMaterialSlots(cmd);
            // Show max level message in first slot
            cmd.set("#MatSlot0Wrap.Visible", true);
            cmd.set("#MatIcon0.ItemId", "");
            cmd.set("#MatLabel0.Text", L("gui.reforge.max_level_reached"));
            cmd.set("#CoinCost.Text", "");
            return;
        }

        // Materials list with icons
        List<ReforgeConfig.MaterialEntry> materials = reforgeService.getRequiredMaterials(level);
        for (int i = 0; i < MAX_MAT_SLOTS; i++) {
            if (i < materials.size()) {
                ReforgeConfig.MaterialEntry mat = materials.get(i);
                String matName = getLocalizedItemName(mat.getItemId());
                String iconId = stripNamespace(mat.getItemId());
                int playerHas = reforgeService.countMaterial(player, mat.getItemId());
                boolean hasEnough = playerHas >= mat.getCount();
                cmd.set("#MatSlot" + i + "Wrap.Visible", true);
                cmd.set("#MatIcon" + i + ".ItemId", iconId);
                cmd.set("#MatLabel" + i + ".Text", matName + " x" + mat.getCount()
                        + " (" + playerHas + "/" + mat.getCount() + ")");
                // Green if enough, red if not
                cmd.set("#MatLabel" + i + ".Style.TextColor", hasEnough ? "#55ff88" : "#ff5555");
            } else {
                cmd.set("#MatSlot" + i + "Wrap.Visible", false);
            }
        }

        // Coin cost using EcotaleAPI format
        double coinCost = reforgeService.getCoinCost(level);
        if (coinCost > 0) {
            String formattedCost = reforgeService.formatCurrency(coinCost);
            cmd.set("#CoinCost.Text", formattedCost);
        } else {
            cmd.set("#CoinCost.Text", L("gui.reforge.free"));
        }
    }

    private void hideMaterialSlots(@Nonnull UICommandBuilder cmd) {
        for (int i = 0; i < MAX_MAT_SLOTS; i++) {
            cmd.set("#MatSlot" + i + "Wrap.Visible", false);
        }
    }

    private void updateForgeButton(@Nonnull UICommandBuilder cmd) {
        ItemStack item = selectedSlot >= 0 ? reforgeService.getItemAtSlot(player, selectedSlot) : null;

        if (item == null || item.isEmpty() || !validator.isReforgeable(item)) {
            cmd.set("#ForgeBtnWrap.Visible", false);
            cmd.set("#RefreshBtnWrap.Visible", true);
            cmd.set("#RiskLabel.Text", "");
            cmd.set("#ProtectionWrap.Visible", false);
            return;
        }

        int level = validator.getReforgeLevel(item, playerUuid);
        int maxLevel = config.getGeneral().getMaxReforgeLevel();

        if (level >= maxLevel) {
            cmd.set("#ForgeBtnWrap.Visible", false);
            cmd.set("#RefreshBtnWrap.Visible", false);
            cmd.set("#RiskLabel.Text", "");
            cmd.set("#ProtectionWrap.Visible", false);
            return;
        }

        boolean hasMats = reforgeService.hasMaterials(player, level);
        boolean hasCoins = reforgeService.hasCoins(playerUuid, level);

        cmd.set("#ForgeBtnWrap.Visible", true);
        cmd.set("#RefreshBtnWrap.Visible", true);

        // Always show "FORGE" text; button is visually styled via ForgeStyle
        cmd.set("#ForgeBtn.Text", L("gui.reforge.btn.forge"));

        // ── Protection toggle ──────────────────────────────
        if (reforgeService.isProtectionEnabled()) {
            cmd.set("#ProtectionWrap.Visible", true);
            cmd.set("#ProtectionToggleBtn.Text", protectionSelected
                    ? L("gui.reforge.protection.on")
                    : L("gui.reforge.protection.off"));

            double protCost = reforgeService.getProtectionCost(level);
            String formattedCost = reforgeService.formatCurrency(protCost);
            cmd.set("#ProtectionCostLabel.Text", L("gui.reforge.protection.cost", "cost", formattedCost));
        } else {
            cmd.set("#ProtectionWrap.Visible", false);
            protectionSelected = false; // reset if disabled
        }

        // Show missing resources as subtle warning near the button (NOT as top error banner)
        if (!hasMats || !hasCoins) {
            cmd.set("#RiskLabel.Text", L("gui.reforge.btn.insufficient"));
        } else {
            // Risk/chance display
            double chance = reforgeService.getSuccessChance(level);
            cmd.set("#RiskLabel.Text", L("gui.reforge.risk",
                    "chance", MessageUtil.formatPercent(chance)));
        }
    }

    // ════════════════════════════════════════════════════════
    //  REFRESH / RE-OPEN
    // ════════════════════════════════════════════════════════

    private void refreshPage(@Nullable String error, @Nullable String success) {
        try {
            UICommandBuilder cmd = new UICommandBuilder();

            cmd.set("#ErrorBanner.Visible", error != null && !error.isEmpty());
            if (error != null && !error.isEmpty()) cmd.set("#ErrorText.Text", stripForUI(error));
            cmd.set("#SuccessBanner.Visible", success != null && !success.isEmpty());
            if (success != null && !success.isEmpty()) cmd.set("#SuccessText.Text", stripForUI(success));

            buildItemList(cmd);
            updateItemInfo(cmd);
            updateMaterials(cmd);
            updateForgeButton(cmd);

            sendUpdate(cmd);
        } catch (Exception e) {
            LOGGER.warn("[refreshPage] sendUpdate failed, reopening: {}", e.getMessage());
            reopen(error, success);
        }
    }

    private void reopen(@Nullable String error, @Nullable String success) {
        close();
        ReforgeGui newPage = new ReforgeGui(
                reforgeService, validator, config, lang, weaponStats,
                playerRef, playerUuid, player, error, success);
        PageOpenHelper.openPage(savedRef, savedStore, newPage);
    }

    // ════════════════════════════════════════════════════════
    //  STATIC OPEN
    // ════════════════════════════════════════════════════════

    public static void open(@Nonnull ReforgeService reforgeService,
                            @Nonnull ItemValidationService validator,
                            @Nonnull ReforgeConfig config,
                            @Nonnull LangManager lang,
                            @Nonnull WeaponStatsService weaponStats,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid,
                            @Nonnull Player player) {
        ReforgeGui page = new ReforgeGui(
                reforgeService, validator, config, lang, weaponStats,
                playerRef, playerUuid, player, null, null);
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

    /**
     * Strip namespace prefix from item ID.
     * E.g. "hytale:Weapon_Daggers_Mithril" → "Weapon_Daggers_Mithril"
     */
    private static String stripNamespace(String itemId) {
        if (itemId == null) return "";
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    /**
     * Get a localized item display name by parsing the item ID parts
     * and looking up translations for weapon/armor type and material.
     * Also handles crafting ingredients like "Ingredient_Bar_Iron" → "Железный слиток".
     *
     * Weapons:      Weapon_<Type>_<Material>            → "Type (Material)"
     * Armor:        Armor_<Material>_<Type>             → "Type (Material)"
     * Ingredients:  Ingredient_Bar_<Material>           → "Material слиток"
     * Materials:    <Material>_<ItemType>               → "Material ItemType"
     */
    private String getLocalizedItemName(String rawItemId) {
        String itemId = stripNamespace(rawItemId);

        // 1. Check for a direct full-name translation first
        String fullKey = "item.name." + itemId;
        String fullTranslation = L(fullKey);
        if (!fullTranslation.equals(fullKey)) {
            return fullTranslation;
        }

        // 2. Check customItems map in config
        String customName = config.getCustomItemName(itemId);
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }

        String type = null;
        String material = null;

        if (itemId.startsWith("Weapon_")) {
            String rest = itemId.substring("Weapon_".length());
            String[] parts = rest.split("_", 2);
            type = parts[0];
            material = parts.length > 1 ? parts[1] : null;
        } else if (itemId.startsWith("Armor_")) {
            String rest = itemId.substring("Armor_".length());
            String[] parts = rest.split("_", 2);
            material = parts[0];
            type = parts.length > 1 ? parts[1] : null;
        } else if (itemId.startsWith("Ingredient_Bar_")) {
            // Ingredient bars: Ingredient_Bar_Iron → "Железный слиток"
            String matName = itemId.substring("Ingredient_Bar_".length());
            String matKey = "item.material." + matName;
            String localMat = L(matKey);
            if (localMat.equals(matKey)) localMat = matName;
            String ingotWord = L("item.craft.Ingot");
            if (ingotWord.equals("item.craft.Ingot")) ingotWord = "Ingot";
            return localMat + " " + ingotWord;
        } else {
            // Try splitting: "Material_Type" → material + type translations
            String[] parts = itemId.split("_", 2);
            if (parts.length == 2) {
                String matKey = "item.material." + parts[0];
                String typeKey = "item.craft." + parts[1];
                String localMat = L(matKey);
                String localType = L(typeKey);
                if (!localMat.equals(matKey) || !localType.equals(typeKey)) {
                    if (localMat.equals(matKey)) localMat = parts[0];
                    if (localType.equals(typeKey)) localType = parts[1];
                    return localMat + " " + localType;
                }
            }
            // Fallback — Title Case: "Skull_Skeleton_epic" → "Skull Skeleton Epic"
            return toTitleCase(itemId.replace('_', ' '));
        }

        String typeKey = "item.type." + type;
        String localType = L(typeKey);
        // If lang returns the key unchanged, translation is missing — use raw
        if (localType.equals(typeKey)) localType = type;

        if (material != null) {
            String matKey = "item.material." + material;
            String localMat = L(matKey);
            if (localMat.equals(matKey)) localMat = material;
            return localType + " (" + localMat + ")";
        }

        return localType;
    }

    /**
     * Format an attack type key into a human-readable label.
     * E.g. "lightAttack" → "Light Attack", "heavyAttack" → "Heavy Attack"
     */
    private String formatAttackType(String key) {
        if (key == null || key.isEmpty()) return key;
        // Try lang lookup first
        String langKey = "gui.reforge.ws.atk." + key;
        String translated = L(langKey);
        if (!translated.equals(langKey)) return translated;
        // Fallback: camelCase → Title Case
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                sb.append(' ').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Convert a space-separated string to Title Case.
     * E.g. "skull skeleton epic" → "Skull Skeleton Epic"
     */
    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            String w = words[i];
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
            }
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    //  EVENT DATA CLASS
    // ════════════════════════════════════════════════════════

    public static class ReforgeEventData {
        public String action = "";
        public String slot = "";
    }
}
