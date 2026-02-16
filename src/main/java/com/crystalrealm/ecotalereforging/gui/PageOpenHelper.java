package com.crystalrealm.ecotalereforging.gui;

import com.crystalrealm.ecotalereforging.util.PluginLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * Utility that opens/closes InteractiveCustomUIPage through 100% reflection.
 */
public final class PageOpenHelper {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static Class<?> PLAYER_CLASS;
    private static Object PLAYER_COMP_TYPE;

    static {
        try {
            PLAYER_CLASS = Class.forName(
                    "com.hypixel.hytale.server.core.entity.entities.Player");
            Method getCompType = PLAYER_CLASS.getMethod("getComponentType");
            PLAYER_COMP_TYPE = getCompType.invoke(null);
        } catch (Exception e) {
            LOGGER.error("[PageOpenHelper] Failed to resolve Player class: {}", e.getMessage());
        }
    }

    private PageOpenHelper() {}

    public static void openPage(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull InteractiveCustomUIPage<?> page) {
        try {
            if (PLAYER_CLASS == null || PLAYER_COMP_TYPE == null) {
                throw new IllegalStateException("Player class was not resolved during init");
            }

            Method getComp = findMethod(store.getClass(), "getComponent", 2);
            Object player = getComp.invoke(store, ref, PLAYER_COMP_TYPE);
            if (player == null) {
                throw new IllegalStateException("Player component is null for ref " + ref);
            }

            Method getPm = player.getClass().getMethod("getPageManager");
            Object pm = getPm.invoke(player);
            if (pm == null) {
                throw new IllegalStateException("PageManager is null");
            }

            Method openMethod = findMethod(pm.getClass(), "openCustomPage", 3);
            openMethod.invoke(pm, ref, store, page);

        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null) {
                cause = ite.getCause();
            }
            LOGGER.error("[PageOpenHelper] Failed to open page: {} — {}", cause.getClass().getName(), cause.getMessage());
            // Print full stack trace for diagnostics
            java.io.StringWriter sw = new java.io.StringWriter();
            cause.printStackTrace(new java.io.PrintWriter(sw));
            LOGGER.error("[PageOpenHelper] Stack trace:\n{}", sw.toString());
        }
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (name.equals(m.getName()) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        throw new IllegalStateException("Method " + name + " with " + paramCount
                + " params not found on " + clazz.getName());
    }
}
