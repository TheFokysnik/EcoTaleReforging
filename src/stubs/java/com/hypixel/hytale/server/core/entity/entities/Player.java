package com.hypixel.hytale.server.core.entity.entities;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Stub — Hytale Player entity.
 * Real class: com.hypixel.hytale.server.core.entity.entities.Player
 */
public class Player implements CommandSender {

    public UUID getUuid() { return UUID.randomUUID(); }

    public String getDisplayName() { return ""; }

    public boolean hasPermission(String permission) { return false; }

    public void sendMessage(Message message) {}

    public void sendMessage(String message) {}

    public void sendActionBar(String message) {}

    /** @deprecated Use {@code getInventory().getItemInHand()} instead. */
    @Deprecated
    public ItemStack getHeldItemStack() {
        return getInventory().getItemInHand();
    }

    /** @deprecated Use {@code getInventory().setItemInHand(ItemStack)} instead. */
    @Deprecated
    public void setHeldItemStack(ItemStack itemStack) {
        getInventory().setItemInHand(itemStack);
    }

    /** Get the player's inventory. */
    public Inventory getInventory() {
        throw new UnsupportedOperationException("Stub");
    }

    /** Get the world this player is in. */
    public World getWorld() {
        throw new UnsupportedOperationException("Stub");
    }

    /** Get the PlayerRef ECS component for this player. */
    public PlayerRef getPlayerRef() {
        throw new UnsupportedOperationException("Stub");
    }

    /** Get the entity reference for ECS operations. */
    @SuppressWarnings("unchecked")
    public Ref<EntityStore> getReference() {
        throw new UnsupportedOperationException("Stub");
    }

    /** Get the UI page manager for this player. */
    public PageManager getPageManager() {
        return new PageManager();
    }

    public static ComponentType<EntityStore, Player> getComponentType() {
        return new ComponentType<>();
    }
}
