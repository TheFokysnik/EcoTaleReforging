package com.crystalrealm.ecotalereforging.provider.economy;

import com.crystalrealm.ecotalereforging.util.PluginLogger;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Economy provider for <b>EconomyAPI</b> by Sennecoolgames.
 *
 * <p>EconomyAPI is a universal economy bridge that supports multiple backends:
 * EcoTale, TheEconomy, HyEssentialsX, VaultUnlocked, and more.</p>
 *
 * @see <a href="https://www.curseforge.com/hytale/mods/economyapi">CurseForge</a>
 */
public class EconomyApiProvider implements EconomyProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String API_CLASS = "com.sennecoolgames.economyapi.EconomyAPI";

    private boolean available;
    private Object apiInstance;
    private Method depositMethod;
    private Method withdrawMethod;
    private Method getBalanceMethod;
    private Method formatMethod;

    public EconomyApiProvider() {
        resolve();
    }

    private void resolve() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Method getApi = apiClass.getMethod("getAPI");
            apiInstance = getApi.invoke(null);
            if (apiInstance == null) {
                LOGGER.info("EconomyAPI.getAPI() returned null — provider disabled.");
                available = false;
                return;
            }

            Class<?> instClass = apiInstance.getClass();
            depositMethod    = instClass.getMethod("deposit",  UUID.class, double.class, String.class);
            withdrawMethod   = instClass.getMethod("withdraw", UUID.class, double.class, String.class);
            getBalanceMethod = instClass.getMethod("getBalance", UUID.class);
            formatMethod     = instClass.getMethod("format", double.class);

            available = true;
            LOGGER.info("EconomyAPI resolved successfully.");
        } catch (ClassNotFoundException e) {
            LOGGER.info("EconomyAPI not found — provider disabled.");
            available = false;
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve EconomyAPI: {}", e.getMessage());
            available = false;
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "EconomyAPI";
    }

    @Override
    public boolean isAvailable() {
        return available && apiInstance != null;
    }

    @Override
    public boolean deposit(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        if (!isAvailable() || depositMethod == null) return false;
        try {
            depositMethod.invoke(apiInstance, playerUuid, amount, reason);
            return true;
        } catch (Exception e) {
            LOGGER.warn("EconomyAPI deposit failed for {}: {}", playerUuid, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean withdraw(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        if (!isAvailable() || withdrawMethod == null) return false;
        try {
            Object result = withdrawMethod.invoke(apiInstance, playerUuid, amount, reason);
            return result instanceof Boolean b ? b : true;
        } catch (Exception e) {
            LOGGER.warn("EconomyAPI withdraw failed for {}: {}", playerUuid, e.getMessage());
            return false;
        }
    }

    @Override
    public double getBalance(@Nonnull UUID playerUuid) {
        if (!isAvailable() || getBalanceMethod == null) return 0;
        try {
            Object result = getBalanceMethod.invoke(apiInstance, playerUuid);
            if (result instanceof Number n) return n.doubleValue();
        } catch (Exception e) {
            LOGGER.warn("EconomyAPI getBalance failed for {}: {}", playerUuid, e.getMessage());
        }
        return 0;
    }

    @Nonnull
    @Override
    public String format(double amount) {
        if (!isAvailable() || formatMethod == null) return String.format("%.0f", amount);
        try {
            Object result = formatMethod.invoke(apiInstance, amount);
            return result != null ? result.toString() : String.format("%.0f", amount);
        } catch (Exception e) {
            return String.format("%.0f", amount);
        }
    }
}
