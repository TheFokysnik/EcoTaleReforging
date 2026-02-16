package com.crystalrealm.ecotalereforging.util;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts bundled asset-pack resources (Server/, Common/, manifest.json)
 * from the plugin JAR into the plugin's data directory.
 *
 * <p>Hytale creates the data directory automatically when a plugin JAR is
 * placed in {@code UserData/Mods/}, but does <strong>not</strong> extract
 * assets from the JAR. The AssetModule scans the data directory for
 * {@code manifest.json} and loads NPC/Language/UI assets from there.</p>
 *
 * <p>Because the plugin's {@code setup()} runs <em>after</em> AssetModule
 * has already scanned the mods directory, the first server startup after
 * installing will extract files but they won't take effect until the
 * <strong>second</strong> restart. This is expected behavior.</p>
 *
 * <p>Uses an explicit resource list to avoid JAR-filesystem issues
 * with Hytale's custom PluginClassLoader.</p>
 */
public final class AssetExtractor {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    /**
     * Explicit list of every resource file to extract from the JAR.
     * Using ClassLoader.getResourceAsStream() which works reliably
     * with Hytale's PluginClassLoader (unlike JAR FileSystems).
     *
     * <p><strong>IMPORTANT:</strong> When adding new asset files,
     * add them to this list as well!</p>
     */
    private static final String[] RESOURCES = {
            "manifest.json",
            // Reforge Station block (F-key → opens reforge GUI)
            "Server/Item/Items/EcoTaleReforging/EcoTale_Reforge_Station.json",
            // Custom hitbox for Reforge Station (fitted to anvil model)
            "Server/Item/Block/Hitboxes/EcoTaleReforging/EcoTale_Reforge_Station.json",
            // Language files
            "Server/Languages/fallback.lang",
            "Server/Languages/en.lang",
            "Server/Languages/ru.lang",
            "Server/Languages/en-US/server.lang",
            "Server/Languages/ru-RU/server.lang",
            // Reforge Station custom model & texture (based on vanilla Bench_Weapon)
            "Common/Blocks/Benches/ReforgeStation.blockymodel",
            "Common/Blocks/Benches/ReforgeStation_Texture.png",
            // Reforge Station block icon
            "Common/Icons/ItemsGenerated/EcoTale_Reforge_Station.png",
            // UI pages
            "Common/UI/Custom/Pages/CrystalRealm_EcoTaleReforging_ReforgePanel.ui",
            "Common/UI/Custom/Pages/CrystalRealm_EcoTaleReforging_AdminPanel.ui",
    };

    private AssetExtractor() {}

    /**
     * Extract all bundled assets into {@code dataDir}.
     * Existing files are overwritten to ensure updates are applied.
     *
     * @param dataDir the plugin's data directory (from {@code getDataDirectory()})
     */
    public static void extractAssets(@Nonnull Path dataDir) {
        LOGGER.info("Extracting asset-pack resources to {}", dataDir);

        int extracted = 0;
        int failed = 0;

        for (String resource : RESOURCES) {
            try (InputStream in = AssetExtractor.class.getClassLoader()
                    .getResourceAsStream(resource)) {
                if (in == null) {
                    LOGGER.warn("  NOT FOUND in JAR: {}", resource);
                    failed++;
                    continue;
                }
                Path target = dataDir.resolve(resource.replace('/', java.io.File.separatorChar));
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("  Extracted: {}", resource);
                extracted++;
            } catch (IOException e) {
                LOGGER.warn("  FAILED to extract {}: {}", resource, e.getMessage());
                failed++;
            }
        }

        LOGGER.info("Asset extraction complete: {} extracted, {} failed.", extracted, failed);
        if (extracted > 0) {
            LOGGER.info("NOTE: If this is the first run after install, restart server once more");
            LOGGER.info("      so AssetModule can pick up the extracted NPC/Language files.");
        }
    }
}
