package com.crystalrealm.ecotalereforging.npc;

import com.crystalrealm.ecotalereforging.config.ConfigManager;
import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.gui.ReforgeGui;
import com.crystalrealm.ecotalereforging.lang.LangManager;
import com.crystalrealm.ecotalereforging.service.ItemValidationService;
import com.crystalrealm.ecotalereforging.service.ReforgeService;
import com.crystalrealm.ecotalereforging.service.WeaponStatsService;
import com.crystalrealm.ecotalereforging.util.PermissionHelper;
import com.crystalrealm.ecotalereforging.util.PluginLogger;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages Reforge Station block interaction and player tracking.
 *
 * <h3>Architecture</h3>
 * <p>Reforging is done exclusively through the Reforge Station block
 * ({@code EcoTale_Reforge_Station}). When a player presses F on the block,
 * the {@link UseBlockEvent.Pre} ECS system opens the reforge GUI.</p>
 *
 * <p>This manager provides:</p>
 * <ul>
 *   <li>Player tracking (via {@code PlayerReadyEvent}) for action bar notifications
 *       and GUI opening</li>
 *   <li>{@link UseBlockEvent.Pre} ECS system — opens GUI when player presses F on
 *       a Reforge Station block</li>
 * </ul>
 */
public class ReforgeStationManager {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    /**
     * Block type ID fragment for the custom Reforge Station block.
     * When a player presses F on this block, the reforge GUI opens.
     */
    private static final String REFORGE_STATION_BLOCK_ID = "EcoTale_Reforge_Station";

    // ── Dependencies ────────────────────────────────────────
    private final ConfigManager         configManager;
    private final LangManager           langManager;
    private final ReforgeService        reforgeService;
    private final ItemValidationService validator;
    private final WeaponStatsService    weaponStatsService;

    /** Online players tracked by UUID. */
    private final Map<UUID, Player> onlinePlayersByUuid = new ConcurrentHashMap<>();

    /** Per-player cooldown to prevent interaction spam (UUID → timestamp). */
    private final Map<UUID, Long> interactionCooldowns = new ConcurrentHashMap<>();

    /** Cooldown between interactions in milliseconds. */
    private static final long INTERACTION_COOLDOWN_MS = 1500;

    // ════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════

    public ReforgeStationManager(@Nonnull ConfigManager configManager,
                                 @Nonnull LangManager langManager,
                                 @Nonnull ReforgeService reforgeService,
                                 @Nonnull ItemValidationService validator,
                                 @Nonnull WeaponStatsService weaponStatsService) {
        this.configManager      = configManager;
        this.langManager        = langManager;
        this.reforgeService     = reforgeService;
        this.weaponStatsService = weaponStatsService;
        this.validator          = validator;
    }

    // ════════════════════════════════════════════════════════
    //  EVENT REGISTRATION
    // ════════════════════════════════════════════════════════

    /**
     * Register all event handlers and ECS systems.
     *
     * <ol>
     *   <li>Player tracking via {@code PlayerReadyEvent}</li>
     *   <li>{@link UseBlockEvent.Pre} ECS system for Reforge Station block interaction</li>
     * </ol>
     */
    public void registerEvents(@Nonnull EventRegistry eventRegistry,
                               @Nullable ComponentRegistryProxy<EntityStore> entityStoreRegistry) {
        ClassLoader serverLoader = eventRegistry.getClass().getClassLoader();

        // ── 1. Player tracking ─────────────────────────────────
        registerPlayerTracking(eventRegistry, serverLoader);

        // ── 2. Reforge Station block interaction (UseBlockEvent.Pre) ──
        if (entityStoreRegistry != null) {
            try {
                entityStoreRegistry.registerSystem(new ReforgeStationUseSystem());
                LOGGER.info("Registered Reforge Station block interaction system (UseBlockEvent.Pre).");
            } catch (Exception e) {
                LOGGER.warn("Failed to register Reforge Station system: {}", e.getMessage());
            }
        }

        LOGGER.info("ReforgeStationManager initialized.");
        LOGGER.info("  Reforge GUI opens via: Reforge Station block (F-key → UseBlockEvent.Pre → Java).");
    }

    /** Backward-compatible overload. */
    public void registerEvents(@Nonnull EventRegistry eventRegistry) {
        registerEvents(eventRegistry, null);
    }

    /**
     * Register PlayerReadyEvent to track online players.
     */
    @SuppressWarnings("unchecked")
    private void registerPlayerTracking(@Nonnull EventRegistry eventRegistry,
                                        @Nonnull ClassLoader serverLoader) {
        String[] candidates = {
                "com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent",
                "com.hypixel.hytale.event.server.PlayerReadyEvent",
        };

        for (String className : candidates) {
            try {
                Class<?> eventClass = Class.forName(className, true, serverLoader);
                Method registerMethod = eventRegistry.getClass()
                        .getMethod("registerGlobal", Class.class, Consumer.class);

                Consumer<Object> handler = event -> {
                    try {
                        Player player = extractPlayer(event);
                        if (player != null) {
                            trackPlayer(player);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Failed to track player: {}", e.getMessage());
                    }
                };

                registerMethod.invoke(eventRegistry, eventClass, handler);
                LOGGER.info("Registered player tracking via {}", className);
                return;

            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                LOGGER.debug("Could not register {}: {}", className, e.getMessage());
            }
        }

        LOGGER.info("Player tracking event not found.");
    }

    /** Extract Player from an event object via reflection. */
    @Nullable
    private static Player extractPlayer(@Nonnull Object event) {
        for (String m : new String[]{"getPlayer", "getSource"}) {
            try {
                Method getter = event.getClass().getMethod(m);
                Object result = getter.invoke(event);
                if (result instanceof Player p) return p;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) { break; }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  REFORGE STATION BLOCK (UseBlockEvent.Pre)
    // ════════════════════════════════════════════════════════

    /**
     * ECS system that intercepts F-key on "Reforge Station" blocks.
     * <ol>
     *   <li>Detect block type by ID</li>
     *   <li>Cancel the default event</li>
     *   <li>Open the ReforgeGui</li>
     * </ol>
     */
    private class ReforgeStationUseSystem
            extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

        protected ReforgeStationUseSystem() {
            super(UseBlockEvent.Pre.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk,
                           Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                           UseBlockEvent.Pre event) {
            try {
                // Check block type
                var blockType = event.getBlockType();
                if (blockType == null) return;

                String blockId = blockType.getId();
                if (blockId == null || !blockId.contains(REFORGE_STATION_BLOCK_ID)) return;

                // Cancel default handler (prevent engine OpenCustomUI)
                event.setCancelled(true);

                PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
                if (playerRef == null || !playerRef.isValid()) return;

                UUID playerUuid = playerRef.getUuid();
                if (playerUuid == null) return;

                // Permission check — ecotalereforging.use
                if (!PermissionHelper.getInstance().hasPermission(playerUuid, "ecotalereforging.use")) {
                    LOGGER.debug("Player {} denied Reforge Station — missing ecotalereforging.use", playerUuid);
                    Player player = onlinePlayersByUuid.get(playerUuid);
                    if (player != null) {
                        try {
                            String noPermMsg = langManager.getForPlayer(playerUuid, "cmd.no_permission");
                            player.sendMessage(noPermMsg);
                        } catch (Exception ignored) {}
                    }
                    return;
                }

                // Cooldown
                long now = System.currentTimeMillis();
                Long last = interactionCooldowns.get(playerUuid);
                if (last != null && (now - last) < INTERACTION_COOLDOWN_MS) return;
                interactionCooldowns.put(playerUuid, now);

                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (ref == null || !ref.isValid()) return;

                LOGGER.info("Player {} used Reforge Station block.", playerUuid);
                openReforgeGuiFromEcs(playerRef, ref, store, playerUuid);

            } catch (Throwable e) {
                LOGGER.error("Error in ReforgeStationUseSystem: {}", e.getMessage());
            }
        }
    }

    /**
     * Open the reforge GUI from an ECS context.
     */
    public void openReforgeGuiFromEcs(@Nonnull PlayerRef playerRef,
                                      @Nonnull Ref<EntityStore> ref,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull UUID playerUuid) {
        try {
            Player player = onlinePlayersByUuid.get(playerUuid);
            ReforgeConfig cfg = configManager.getConfig();

            ReforgeGui.open(reforgeService, validator, cfg, langManager, weaponStatsService,
                    playerRef, ref, store, playerUuid, player);

            LOGGER.info("Opened reforge GUI for {} (ECS path)", playerUuid);
        } catch (Exception e) {
            LOGGER.error("Failed to open reforge GUI from ECS: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  PLAYER TRACKING
    // ════════════════════════════════════════════════════════

    /** Register a player in the tracking cache. */
    public void trackPlayer(@Nonnull Player player) {
        UUID uuid = player.getUuid();
        if (uuid != null) {
            onlinePlayersByUuid.put(uuid, player);
        }
    }

    /** Get a snapshot of all tracked online players (UUID → Player). */
    @Nonnull
    public Map<UUID, Player> getOnlinePlayers() {
        return onlinePlayersByUuid;
    }

    /** Cleanup: clear tracking. */
    public void shutdown() {
        onlinePlayersByUuid.clear();
        interactionCooldowns.clear();
        LOGGER.info("ReforgeStationManager shut down.");
    }
}
