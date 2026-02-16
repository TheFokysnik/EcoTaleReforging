package com.hypixel.hytale.server.core.inventory;

import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

/**
 * Stub — Hytale player Inventory.
 */
public class Inventory {

    /** Get the item stack currently held in the player's hand (active hotbar or tool slot). */
    public ItemStack getItemInHand() {
        throw new UnsupportedOperationException("Stub");
    }

    /** Set the item stack in the player's active hand slot. */
    public void setItemInHand(ItemStack itemStack) {
        throw new UnsupportedOperationException("Stub");
    }

    public CombinedItemContainer getCombinedHotbarFirst() {
        throw new UnsupportedOperationException("Stub");
    }
}
