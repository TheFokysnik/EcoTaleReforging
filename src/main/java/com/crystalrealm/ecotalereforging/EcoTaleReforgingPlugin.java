package com.crystalrealm.ecotalereforging;

import com.crystalrealm.ecotalereforging.commands.ReforgeAdminCommandCollection;
import com.crystalrealm.ecotalereforging.commands.ReforgeCommandCollection;
import com.crystalrealm.ecotalereforging.config.ConfigManager;
import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.lang.LangManager;
import com.crystalrealm.ecotalereforging.npc.ReforgeStationManager;
import com.crystalrealm.ecotalereforging.service.ItemValidationService;
import com.crystalrealm.ecotalereforging.service.ReforgeDataStore;
import com.crystalrealm.ecotalereforging.service.ReforgeService;
import com.crystalrealm.ecotalereforging.service.WeaponStatsService;
import com.crystalrealm.ecotalereforging.system.ReforgeActionBarSystem;
import com.crystalrealm.ecotalereforging.system.ReforgeDamageSystem;
import com.crystalrealm.ecotalereforging.tooltip.ReforgeTooltipProvider;
import com.crystalrealm.ecotalereforging.util.AssetExtractor;
import com.crystalrealm.ecotalereforging.util.MessageUtil;
import com.crystalrealm.ecotalereforging.util.PermissionHelper;
import com.crystalrealm.ecotalereforging.util.PluginLogger;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * EcoTaleReforging — Reforging system for Hytale servers.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Progressive weapon/armor reforging with configurable success chances</li>
 *   <li>Material + coin costs per reforge level</li>
 *   <li>Reverse crafting on failure — item destroyed, materials returned</li>
 *   <li>Reforge Station block — F-key opens GUI</li>
 *   <li>Admin panel for runtime configuration</li>
 *   <li>Full RU/EN localization</li>
 *   <li>LuckPerms permission integration</li>
 * </ul>
 *
 * @version 1.0.0
 */
public class EcoTaleReforgingPlugin extends JavaPlugin {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String VERSION = "1.0.2";

    // ── Services ────────────────────────────────────────────
    private ConfigManager        configManager;
    private LangManager          langManager;
    private ReforgeDataStore     dataStore;
    private ItemValidationService validator;
    private ReforgeService       reforgeService;
    private WeaponStatsService   weaponStatsService;
    private ReforgeStationManager      stationManager;
    private ReforgeActionBarSystem   actionBarSystem;

    public EcoTaleReforgingPlugin(JavaPluginInit init) {
        super(init);
    }

    // ═════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════

    @Override
    protected void setup() {
        LOGGER.info("═══════════════════════════════════════");
        LOGGER.info("  EcoTaleReforging v{} — setup", VERSION);
        LOGGER.info("═══════════════════════════════════════");

        // 0. Extract bundled assets (Server/, Common/, manifest.json) from JAR
        //    Hytale does NOT extract asset-pack files from plugin JARs automatically.
        AssetExtractor.extractAssets(getDataDirectory());

        // 1. Config
        configManager = new ConfigManager(getDataDirectory());
        configManager.loadOrCreate();
        ReforgeConfig config = configManager.getConfig();

        // 2. Permission resolver
        PermissionHelper.getInstance().init(getDataDirectory());

        // 3. Language
        langManager = new LangManager(getDataDirectory());
        langManager.load(config.getGeneral().getLanguage());

        // 4. Reforge data store (server-side reforge level persistence)
        dataStore = new ReforgeDataStore(getDataDirectory());

        // 5. Item validation service
        validator = new ItemValidationService(config, dataStore);

        // 6. Reforge service
        reforgeService = new ReforgeService(config, validator, dataStore);

        // 6.5. Weapon stats (via WeaponStatsViewer plugin)
        weaponStatsService = new WeaponStatsService();
        weaponStatsService.init();

        // 6.6. Damage modification system — applies reforge bonus to weapon damage
        try {
            ReforgeDamageSystem damageSystem = new ReforgeDamageSystem(dataStore, config);
            getEntityStoreRegistry().registerSystem(damageSystem);
            LOGGER.info("ReforgeDamageSystem registered — reforge bonuses will apply to weapon damage.");
        } catch (Exception e) {
            LOGGER.error("Failed to register ReforgeDamageSystem: {}", e.getMessage());
        }

        // 7. Reforge Station manager
        stationManager = new ReforgeStationManager(configManager, langManager, reforgeService, validator, weaponStatsService);
        stationManager.registerEvents(getEventRegistry(), getEntityStoreRegistry());
        LOGGER.info("Reforge Station manager initialized.");

        // 8. Action bar stat display system
        actionBarSystem = new ReforgeActionBarSystem(config, dataStore, weaponStatsService);
        actionBarSystem.setNpcManager(stationManager);
        LOGGER.info("ReforgeActionBarSystem initialized.");

        // 8.5. DynamicTooltipsLib integration — show reforge info on item tooltips
        registerTooltipProvider(config);

        // 9. Commands
        ReforgeCommandCollection reforgeCmds = new ReforgeCommandCollection(
                configManager, langManager, reforgeService, validator, weaponStatsService, VERSION
        );
        getCommandRegistry().registerCommand(reforgeCmds);
        LOGGER.info("Registered /reforge command.");

        getCommandRegistry().registerCommand(new ReforgeAdminCommandCollection(
                configManager, langManager, VERSION
        ));
        LOGGER.info("Registered /reforgeadmin command.");
    }

    @Override
    protected void start() {
        LOGGER.info("═══════════════════════════════════════");
        LOGGER.info("  EcoTaleReforging v{} — STARTED", VERSION);
        LOGGER.info("  Max Level:  {}", configManager.getConfig().getGeneral().getMaxReforgeLevel());
        LOGGER.info("  Levels:     {} configured", configManager.getConfig().getLevels().size());
        LOGGER.info("  Language:   {}", configManager.getConfig().getGeneral().getLanguage());
        LOGGER.info("═══════════════════════════════════════");

        // AssetStore is now populated — warm weapon stats cache
        weaponStatsService.lateInit();

        // Start action bar system
        if (actionBarSystem != null) {
            actionBarSystem.start();
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.info("EcoTaleReforging shutting down...");

        // Save reforge data
        if (dataStore != null) dataStore.save();

        // Cleanup
        if (actionBarSystem != null) actionBarSystem.shutdown();
        if (stationManager != null) stationManager.shutdown();
        MessageUtil.clearCache();
        if (langManager != null) langManager.clearPlayerData();

        LOGGER.info("EcoTaleReforging v{} — shutdown complete.", VERSION);
    }

    // ═════════════════════════════════════════════════════════
    //  DYNAMIC TOOLTIPS INTEGRATION
    // ═════════════════════════════════════════════════════════

    /**
     * Register {@link ReforgeTooltipProvider} with DynamicTooltipsLib if available.
     *
     * <p>Uses direct API calls (not reflection) to avoid IllegalAccessException
     * caused by Java module access checks on the internal implementation class.
     * A {@code Class.forName} guard protects against the optional dependency
     * being absent at runtime.</p>
     */
    private void registerTooltipProvider(ReforgeConfig config) {
        try {
            // Guard: check if the library is present at runtime
            Class.forName("org.herolias.tooltips.api.DynamicTooltipsApiProvider");

            // Direct API call — no reflection needed
            org.herolias.tooltips.api.DynamicTooltipsApi api =
                    org.herolias.tooltips.api.DynamicTooltipsApiProvider.get();

            if (api != null) {
                ReforgeTooltipProvider provider = new ReforgeTooltipProvider(config);
                api.registerProvider(provider);
                LOGGER.info("ReforgeTooltipProvider registered with DynamicTooltipsLib (id: {})",
                        provider.getProviderId());
            } else {
                LOGGER.warn("[Tooltip] DynamicTooltipsApiProvider.get() returned null — scheduling retry...");
                scheduleTooltipRetry(config, 0);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.info("DynamicTooltipsLib not found — tooltip provider not registered (optional dependency)");
        } catch (Exception e) {
            LOGGER.warn("Failed to register ReforgeTooltipProvider: {}", e.getMessage(), e);
        }
    }

    /** Retry tooltip registration a few times if the API wasn't ready yet. */
    private void scheduleTooltipRetry(ReforgeConfig config, int attempt) {
        if (attempt >= 5) {
            LOGGER.warn("[Tooltip] Gave up registering after {} retries.", attempt);
            return;
        }

        new Thread(() -> {
            try {
                Thread.sleep(2000L * (attempt + 1));
                org.herolias.tooltips.api.DynamicTooltipsApi api =
                        org.herolias.tooltips.api.DynamicTooltipsApiProvider.get();

                if (api == null) {
                    LOGGER.warn("[Tooltip] Retry {} — API still null, will try again...", attempt + 1);
                    scheduleTooltipRetry(config, attempt + 1);
                    return;
                }

                ReforgeTooltipProvider provider = new ReforgeTooltipProvider(config);
                api.registerProvider(provider);
                LOGGER.info("ReforgeTooltipProvider registered on retry {} (id: {})",
                        attempt + 1, provider.getProviderId());
            } catch (Exception e) {
                LOGGER.warn("[Tooltip] Retry {} failed: {}", attempt + 1, e.getMessage());
            }
        }, "EcoTaleReforging-TooltipRetry-" + attempt).start();
    }

    // ═════════════════════════════════════════════════════════
    //  GETTERS
    // ═════════════════════════════════════════════════════════

    @Nonnull public ConfigManager        getConfigManager()  { return configManager; }
    @Nonnull public LangManager          getLangManager()     { return langManager; }
    @Nonnull public ItemValidationService getValidator()      { return validator; }
    @Nonnull public ReforgeService       getReforgeService()  { return reforgeService; }
    @Nonnull public WeaponStatsService   getWeaponStatsService() { return weaponStatsService; }
    @Nonnull public ReforgeStationManager getStationManager() { return stationManager; }
    @Nonnull public String               getVersion()         { return VERSION; }
}
