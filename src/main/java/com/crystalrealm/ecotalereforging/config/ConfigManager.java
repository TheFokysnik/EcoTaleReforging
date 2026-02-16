package com.crystalrealm.ecotalereforging.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.crystalrealm.ecotalereforging.util.PluginLogger;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages loading, saving and hot-reloading of JSON configuration.
 */
public class ConfigManager {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String CONFIG_FILENAME = "EcoTaleReforging.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path dataDirectory;
    private ReforgeConfig config;

    public ConfigManager(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public void loadOrCreate() {
        Path configPath = getConfigPath();

        try {
            Files.createDirectories(dataDirectory);

            if (Files.exists(configPath)) {
                loadFromFile(configPath);
            } else {
                createDefault(configPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config: {}", e.getMessage());
            config = new ReforgeConfig();
        }
    }

    public boolean reload() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            LOGGER.warn("Config file not found: {}", configPath);
            return false;
        }

        try {
            loadFromFile(configPath);
            LOGGER.info("Configuration reloaded successfully.");
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to reload config: {}", e.getMessage());
            return false;
        }
    }

    public void saveConfig() {
        Path configPath = getConfigPath();
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(configPath), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    @Nonnull
    public ReforgeConfig getConfig() {
        if (config == null) config = new ReforgeConfig();
        return config;
    }

    @Nonnull
    public Path getConfigPath() {
        return dataDirectory.resolve(CONFIG_FILENAME);
    }

    @Nonnull
    public Path getDataDirectory() {
        return dataDirectory;
    }

    private void loadFromFile(Path path) throws IOException {
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(path), StandardCharsets.UTF_8)) {
            ReforgeConfig loaded = GSON.fromJson(reader, ReforgeConfig.class);
            if (loaded == null) {
                LOGGER.warn("Config parsed as null, using defaults.");
                loaded = new ReforgeConfig();
            }
            if (config != null) {
                // Preserve the existing object reference so services keep seeing updates
                config.updateFrom(loaded);
            } else {
                config = loaded;
            }
        }
    }

    private void createDefault(Path path) throws IOException {
        config = new ReforgeConfig();

        try (InputStream defaultStream = getClass().getClassLoader()
                .getResourceAsStream("default-config.json")) {
            if (defaultStream != null) {
                Files.copy(defaultStream, path);
                loadFromFile(path);
                LOGGER.info("Default config created at {}", path);
                return;
            }
        }

        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
        LOGGER.info("Default config generated at {}", path);
    }
}
