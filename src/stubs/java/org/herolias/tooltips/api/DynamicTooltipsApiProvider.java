package org.herolias.tooltips.api;

/**
 * Stub — DynamicTooltipsLib API provider (singleton holder).
 * Call {@link #get()} to obtain the {@link DynamicTooltipsApi} instance.
 */
public final class DynamicTooltipsApiProvider {

    private static volatile DynamicTooltipsApi instance;

    private DynamicTooltipsApiProvider() {}

    /** Returns the API instance, or null if DynamicTooltipsLib hasn't initialised yet. */
    public static DynamicTooltipsApi get() {
        return instance;
    }

    /** Called by DynamicTooltipsLib itself — not for external use. */
    public static void register(DynamicTooltipsApi api) {
        instance = api;
    }
}
