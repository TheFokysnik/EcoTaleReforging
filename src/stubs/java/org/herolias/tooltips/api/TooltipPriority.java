package org.herolias.tooltips.api;

/**
 * Stub — DynamicTooltipsLib TooltipPriority constants.
 * Lower values execute first.
 */
public final class TooltipPriority {

    /** Runs before most providers. */
    public static final int EARLY  = 100;

    /** Default priority. */
    public static final int NORMAL = 500;

    /** Runs after most providers. */
    public static final int LATE   = 900;

    private TooltipPriority() {}
}
