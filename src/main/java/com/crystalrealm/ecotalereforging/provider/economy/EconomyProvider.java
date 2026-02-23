package com.crystalrealm.ecotalereforging.provider.economy;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Universal interface for any economy plugin.
 *
 * <p>Reforging uses: deposit, withdraw, getBalance, hasBalance, format.</p>
 */
public interface EconomyProvider {

    /** Human-readable provider name. */
    @Nonnull
    String getName();

    /** Whether the backing economy plugin is loaded and callable. */
    boolean isAvailable();

    /**
     * Deposits currency to a player's account.
     *
     * @return true if the deposit succeeded
     */
    boolean deposit(@Nonnull UUID playerUuid, double amount, @Nonnull String reason);

    /**
     * Withdraws currency from a player's account.
     *
     * @return true if the withdrawal succeeded
     */
    boolean withdraw(@Nonnull UUID playerUuid, double amount, @Nonnull String reason);

    /**
     * Gets the player's current balance.
     */
    double getBalance(@Nonnull UUID playerUuid);

    /**
     * Checks if the player has at least the given amount.
     * <p>Default implementation uses {@link #getBalance}.</p>
     */
    default boolean hasBalance(@Nonnull UUID playerUuid, double amount) {
        return getBalance(playerUuid) >= amount;
    }

    /**
     * Formats a currency amount for display.
     * <p>Default implementation returns a plain number string.</p>
     */
    @Nonnull
    default String format(double amount) {
        return String.format("%.0f", amount);
    }
}
