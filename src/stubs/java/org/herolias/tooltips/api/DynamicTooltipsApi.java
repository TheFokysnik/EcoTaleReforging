package org.herolias.tooltips.api;

import java.util.UUID;

/**
 * Stub — DynamicTooltipsLib main API interface.
 * Obtained via {@link DynamicTooltipsApiProvider#get()}.
 */
public interface DynamicTooltipsApi {

    /** Register a custom tooltip provider. */
    void registerProvider(TooltipProvider provider);

    /** Unregister a provider by its id. */
    boolean unregisterProvider(String providerId);

    /** Invalidate tooltip cache for a specific player. */
    void invalidatePlayer(UUID playerId);

    /** Invalidate all cached tooltip data. */
    void invalidateAll();

    /** Refresh tooltip display for a specific player. */
    void refreshPlayer(UUID playerId);

    /** Refresh tooltip display for all players. */
    void refreshAllPlayers();
}
