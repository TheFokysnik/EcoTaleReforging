package com.crystalrealm.ecotalereforging.commands;

import com.crystalrealm.ecotalereforging.config.ConfigManager;
import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.lang.LangManager;
import com.crystalrealm.ecotalereforging.service.ItemValidationService;
import com.crystalrealm.ecotalereforging.service.ReforgeService;
import com.crystalrealm.ecotalereforging.service.WeaponStatsService;
import com.crystalrealm.ecotalereforging.util.MessageUtil;
import com.crystalrealm.ecotalereforging.util.MiniMessageParser;
import com.crystalrealm.ecotalereforging.util.PermissionHelper;
import com.crystalrealm.ecotalereforging.util.PluginLogger;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command collection for the reforging system.
 *
 * <p>Commands:</p>
 * <ul>
 *   <li><b>/reforge info</b> — show held item reforge info</li>
 *   <li><b>/reforge help</b> — show help</li>
 * </ul>
 *
 * <p>The reforge GUI is opened exclusively via the Reforge Station block
 * (F-key → {@code UseBlockEvent.Pre} → Java GUI).</p>
 */
public class ReforgeCommandCollection extends AbstractCommandCollection {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final Set<String> COMMAND_KEYWORDS = Set.of(
            "reforge", "info", "help"
    );

    // ── Dependencies ────────────────────────────────────────
    private final ConfigManager        configManager;
    private final LangManager          langManager;
    private final ReforgeService       reforgeService;
    private final ItemValidationService validator;
    private final WeaponStatsService   weaponStatsService;
    private final String               pluginVersion;

    public ReforgeCommandCollection(@Nonnull ConfigManager configManager,
                                    @Nonnull LangManager langManager,
                                    @Nonnull ReforgeService reforgeService,
                                    @Nonnull ItemValidationService validator,
                                    @Nonnull WeaponStatsService weaponStatsService,
                                    @Nonnull String pluginVersion) {
        super("reforge", "EcoTaleReforging — Reforging system");
        this.setPermissionGroups("Adventure");

        this.configManager  = configManager;
        this.langManager    = langManager;
        this.reforgeService = reforgeService;
        this.validator      = validator;
        this.weaponStatsService = weaponStatsService;
        this.pluginVersion  = pluginVersion;

        addSubCommand(new InfoSubCommand());
        addSubCommand(new HelpSubCommand());
    }

    // ═══════════════════════════════════════════════════════
    //  /reforge open <player>  — Console/NPC opens GUI for a player
    // ═══════════════════════════════════════════════════════
    //  /reforge info  — Show held item info
    // ═══════════════════════════════════════════════════════

    private class InfoSubCommand extends AbstractAsyncCommand {
        InfoSubCommand() { super("info", "Show held item reforge info"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalereforging.use")) return done();

            Player player = resolvePlayer(sender);
            if (player == null) {
                context.sendMessage(msg("<red>Cannot resolve player.</red>"));
                return done();
            }

            ItemStack heldItem = player.getInventory().getItemInHand();
            if (heldItem == null || heldItem.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.reforge.no_item")));
                return done();
            }

            if (!validator.isReforgeable(heldItem)) {
                context.sendMessage(msg(L(sender, "cmd.reforge.not_reforgeable")));
                return done();
            }

            String itemName = validator.getDisplayName(heldItem.getItemId());
            int level = validator.getReforgeLevel(heldItem, player.getUuid());
            boolean isWeapon = validator.isWeapon(heldItem);
            int maxLevel = configManager.getConfig().getGeneral().getMaxReforgeLevel();

            context.sendMessage(msg(L(sender, "cmd.info.header")));
            context.sendMessage(msg(L(sender, "cmd.info.item", "item", itemName)));
            context.sendMessage(msg(L(sender, "cmd.info.category",
                    "category", validator.getItemCategory(heldItem.getItemId()))));
            context.sendMessage(msg(L(sender, "cmd.info.level",
                    "level", String.valueOf(level),
                    "max", String.valueOf(maxLevel))));

            if (level > 0) {
                double bonus = isWeapon
                        ? reforgeService.getDamageBonus(level)
                        : reforgeService.getDefenseBonus(level);
                String statLabel = isWeapon ? "DMG" : "DEF";
                context.sendMessage(msg(L(sender, "cmd.info.bonus",
                        "bonus", MessageUtil.formatBonus(bonus),
                        "stat", statLabel)));
            }

            if (level < maxLevel) {
                double chance = reforgeService.getSuccessChance(level);
                double cost = reforgeService.getCoinCost(level);
                context.sendMessage(msg(L(sender, "cmd.info.next_chance",
                        "chance", MessageUtil.formatPercent(chance))));
                context.sendMessage(msg(L(sender, "cmd.info.next_cost",
                        "cost", MessageUtil.formatCoins(cost))));
            } else {
                context.sendMessage(msg(L(sender, "cmd.info.max_reached")));
            }

            context.sendMessage(msg(L(sender, "cmd.info.footer")));
            return done();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  /reforge help
    // ═══════════════════════════════════════════════════════

    private class HelpSubCommand extends AbstractAsyncCommand {
        HelpSubCommand() { super("help", "Show help"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            CommandSender sender = context.sender();

            context.sendMessage(msg(L(sender, "cmd.help.header")));
            context.sendMessage(msg(L(sender, "cmd.help.reforge")));
            context.sendMessage(msg(L(sender, "cmd.help.reforge_info")));
            context.sendMessage(msg(L(sender, "cmd.help.reforgeadmin")));
            context.sendMessage(msg(L(sender, "cmd.help.help")));
            context.sendMessage(msg(L(sender, "cmd.help.footer")));
            return done();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════

    private Player resolvePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;

        try {
            java.lang.reflect.Method getPlayer = sender.getClass().getMethod("getPlayer");
            Object result = getPlayer.invoke(sender);
            if (result instanceof Player p) return p;
        } catch (Exception ignored) {}

        try {
            java.lang.reflect.Method getHandle = sender.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(sender);
            if (handle instanceof Player p) return p;
        } catch (Exception ignored) {}

        return null;
    }

    private String parseTrailingArg(CommandContext context) {
        try {
            String input = context.getInputString();
            if (input == null || input.isBlank()) return null;

            String[] parts = input.trim().split("\\s+");
            List<String> args = new ArrayList<>();
            for (String part : parts) {
                String lower = part.toLowerCase();
                if (lower.startsWith("/")) lower = lower.substring(1);
                if (!COMMAND_KEYWORDS.contains(lower)) {
                    args.add(part);
                }
            }
            return args.isEmpty() ? null : args.get(args.size() - 1);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse trailing arg: {}", e.getMessage());
        }
        return null;
    }

    private String L(CommandSender sender, String key, String... args) {
        return langManager.getForPlayer(sender.getUuid(), key, args);
    }

    private boolean checkPerm(CommandSender sender, CommandContext ctx, String perm) {
        if (hasPermWithWildcard(sender, perm)) return true;
        ctx.sendMessage(msg(L(sender, "cmd.no_permission")));
        return false;
    }

    static boolean hasPermWithWildcard(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return true;
        String[] parts = perm.split("\\.");
        for (int i = parts.length - 1; i >= 1; i--) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                if (j > 0) sb.append('.');
                sb.append(parts[j]);
            }
            sb.append(".*");
            if (sender.hasPermission(sb.toString())) return true;
        }
        if (sender.hasPermission("*")) return true;
        return PermissionHelper.getInstance().hasPermission(sender.getUuid(), perm);
    }

    private static com.hypixel.hytale.server.core.Message msg(String miniMessage) {
        return com.hypixel.hytale.server.core.Message.parse(MiniMessageParser.toJson(miniMessage));
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
