package org.herolias.tooltips.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stub — DynamicTooltipsLib TooltipProvider interface.
 * Implement this to inject custom tooltip lines for items.
 */
public interface TooltipProvider {

    /**
     * Unique identifier for this provider, e.g. "my_mod:stats".
     */
    @Nonnull
    String getProviderId();

    /**
     * Priority order — lower values run first.
     * Use constants from {@link TooltipPriority}.
     */
    int getPriority();

    /**
     * Generate tooltip data for the given item.
     *
     * @param itemId   fully-qualified item asset id (e.g. "hytale:Weapon_Daggers_Mithril")
     * @param metadata JSON metadata string attached to this item stack (may be null)
     * @return tooltip data to inject, or null to skip
     */
    @Nullable
    TooltipData getTooltipData(@Nonnull String itemId, @Nullable String metadata);
}
