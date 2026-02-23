package com.crystalrealm.ecotalereforging.provider.economy;

import com.crystalrealm.ecotalereforging.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Facade that routes economy operations to the active {@link EconomyProvider}.
 *
 * <p>Supports multiple registered providers with config-driven or
 * auto-detected activation. Falls back to the first available provider
 * if the preferred one is unavailable.</p>
 */
public class EconomyBridge {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final LinkedHashMap<String, EconomyProvider> providers = new LinkedHashMap<>();
    private EconomyProvider active;

    public EconomyBridge() {
        registerProvider("ecotale", new EcotaleEconomyProvider());
        registerProvider("economyapi", new EconomyApiProvider());
    }

    public void registerProvider(@Nonnull String key, @Nonnull EconomyProvider provider) {
        providers.put(key.toLowerCase(), provider);
        LOGGER.info("Economy provider registered: {} ({})", key, provider.getName());
    }

    /**
     * Selects and activates a provider by config key.
     * Falls back to the first available provider if preferred is unavailable.
     */
    public boolean activate(@Nullable String preferredKey) {
        if (preferredKey != null && !preferredKey.isEmpty()) {
            EconomyProvider p = providers.get(preferredKey.toLowerCase());
            if (p != null && p.isAvailable()) {
                active = p;
                LOGGER.info("Economy provider activated: {} ({})", preferredKey, p.getName());
                return true;
            }
        }
        for (Map.Entry<String, EconomyProvider> e : providers.entrySet()) {
            if (e.getValue().isAvailable()) {
                active = e.getValue();
                LOGGER.info("Economy provider fallback: {} ({})", e.getKey(), active.getName());
                return true;
            }
        }
        LOGGER.warn("No economy provider available — economy operations will fail.");
        return false;
    }

    public boolean isAvailable() {
        return active != null && active.isAvailable();
    }

    @Nonnull
    public String getProviderName() {
        return active != null ? active.getName() : "none";
    }

    public boolean deposit(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        if (active == null || !active.isAvailable()) return false;
        return active.deposit(playerUuid, amount, reason);
    }

    public boolean withdraw(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        if (active == null || !active.isAvailable()) return false;
        return active.withdraw(playerUuid, amount, reason);
    }

    public double getBalance(@Nonnull UUID playerUuid) {
        if (active == null || !active.isAvailable()) return 0;
        return active.getBalance(playerUuid);
    }

    public boolean hasBalance(@Nonnull UUID playerUuid, double amount) {
        if (active == null || !active.isAvailable()) return false;
        return active.hasBalance(playerUuid, amount);
    }

    @Nonnull
    public String format(double amount) {
        if (active == null || !active.isAvailable()) return String.format("%.0f", amount);
        return active.format(amount);
    }
}
