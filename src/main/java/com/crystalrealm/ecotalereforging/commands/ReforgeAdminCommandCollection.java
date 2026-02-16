package com.crystalrealm.ecotalereforging.commands;

import com.crystalrealm.ecotalereforging.config.ConfigManager;
import com.crystalrealm.ecotalereforging.gui.AdminReforgeGui;
import com.crystalrealm.ecotalereforging.lang.LangManager;
import com.crystalrealm.ecotalereforging.util.MiniMessageParser;
import com.crystalrealm.ecotalereforging.util.PermissionHelper;
import com.crystalrealm.ecotalereforging.util.PluginLogger;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin command collection for the reforging system.
 *
 * <p>Commands:</p>
 * <ul>
 *   <li><b>/reforgeadmin</b> — open admin GUI</li>
 *   <li><b>/reforgeadmin reload</b> — reload config</li>
 * </ul>
 */
public class ReforgeAdminCommandCollection extends AbstractCommandCollection {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final ConfigManager configManager;
    private final LangManager   langManager;
    private final String        pluginVersion;

    public ReforgeAdminCommandCollection(@Nonnull ConfigManager configManager,
                                         @Nonnull LangManager langManager,
                                         @Nonnull String pluginVersion) {
        super("reforgeadmin", "EcoTaleReforging — Admin panel");
        this.configManager = configManager;
        this.langManager   = langManager;
        this.pluginVersion = pluginVersion;

        addSubCommand(new OpenSubCommand());
        addSubCommand(new ReloadSubCommand());
    }

    // ═══════════════════════════════════════════════════════
    //  /reforgeadmin reload
    // ═══════════════════════════════════════════════════════

    private class ReloadSubCommand extends AbstractAsyncCommand {
        ReloadSubCommand() { super("reload", "Reload configuration"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalereforging.admin")) return done();

            boolean success = configManager.reload();
            if (success) {
                langManager.reload(configManager.getConfig().getGeneral().getLanguage());
                PermissionHelper.getInstance().reload();
                context.sendMessage(msg(L(sender, "cmd.reload.success")));
            } else {
                context.sendMessage(msg(L(sender, "cmd.reload.fail")));
            }
            return done();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  /reforgeadmin open  — Open admin GUI (default action)
    // ═══════════════════════════════════════════════════════

    private class OpenSubCommand extends AbstractAsyncCommand {
        OpenSubCommand() { super("open", "Open admin settings GUI"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalereforging.admin")) return done();

            openAdminGui(context, sender);
            return done();
        }
    }

    private void openAdminGui(CommandContext context, CommandSender sender) {
        Player player = null;
        if (sender instanceof Player p) {
            player = p;
        } else {
            try {
                java.lang.reflect.Method getPlayer = sender.getClass().getMethod("getPlayer");
                Object result = getPlayer.invoke(sender);
                if (result instanceof Player p) player = p;
            } catch (Exception ignored) {}
        }

        if (player == null) {
            context.sendMessage(msg("<red>Cannot resolve player.</red>"));
            return;
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            context.sendMessage(msg("<red>Player reference not available.</red>"));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        try {
            java.lang.reflect.Method getExt = store.getClass().getMethod("getExternalData");
            Object extData = getExt.invoke(store);
            java.lang.reflect.Method getWorld = extData.getClass().getMethod("getWorld");
            Object worldObj = getWorld.invoke(extData);

            if (worldObj instanceof java.util.concurrent.Executor worldExec) {
                CompletableFuture.runAsync(() -> {
                    try {
                        java.lang.reflect.Method getComp = store.getClass()
                                .getMethod("getComponent", Ref.class, ComponentType.class);
                        Object result = getComp.invoke(store, ref, PlayerRef.getComponentType());
                        if (result instanceof PlayerRef playerRef) {
                            UUID uuid = sender.getUuid();
                            AdminReforgeGui.open(configManager, langManager,
                                    playerRef, ref, store, uuid, pluginVersion);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[reforgeadmin] failed on WorldThread", e);
                    }
                }, worldExec);
            } else {
                context.sendMessage(msg("<red>Failed to open admin GUI.</red>"));
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[reforgeadmin] reflection failed", e);
            context.sendMessage(msg("<red>Failed to open admin GUI.</red>"));
        }
    }

    // ═══════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════

    private String L(CommandSender sender, String key, String... args) {
        return langManager.getForPlayer(sender.getUuid(), key, args);
    }

    private boolean checkPerm(CommandSender sender, CommandContext ctx, String perm) {
        if (ReforgeCommandCollection.hasPermWithWildcard(sender, perm)) return true;
        ctx.sendMessage(msg(L(sender, "cmd.no_permission")));
        return false;
    }

    private static com.hypixel.hytale.server.core.Message msg(String miniMessage) {
        return com.hypixel.hytale.server.core.Message.parse(MiniMessageParser.toJson(miniMessage));
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
