package com.crystalrealm.ecotalereforging.provider.economy;

import com.crystalrealm.ecotalereforging.util.PluginLogger;
import com.ecotale.api.EcotaleAPI;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Economy provider for native <b>EcotaleAPI</b>.
 */
public class EcotaleEconomyProvider implements EconomyProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    @Nonnull
    @Override
    public String getName() {
        return "EcotaleAPI";
    }

    @Override
    public boolean isAvailable() {
        try {
            return EcotaleAPI.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deposit(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        try {
            EcotaleAPI.deposit(playerUuid, amount, reason);
            return true;
        } catch (Exception e) {
            LOGGER.warn("EcotaleAPI deposit failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean withdraw(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        try {
            return EcotaleAPI.withdraw(playerUuid, amount, reason);
        } catch (Exception e) {
            LOGGER.warn("EcotaleAPI withdraw failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public double getBalance(@Nonnull UUID playerUuid) {
        try {
            return EcotaleAPI.getBalance(playerUuid);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean hasBalance(@Nonnull UUID playerUuid, double amount) {
        try {
            return EcotaleAPI.hasBalance(playerUuid, amount);
        } catch (Exception e) {
            return false;
        }
    }

    @Nonnull
    @Override
    public String format(double amount) {
        try {
            return EcotaleAPI.format(amount);
        } catch (Exception e) {
            return String.format("%.0f", amount);
        }
    }
}
