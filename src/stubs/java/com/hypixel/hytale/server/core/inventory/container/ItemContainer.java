package com.hypixel.hytale.server.core.inventory.container;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;

/**
 * Stub — Hytale item container base class.
 */
public class ItemContainer {
    public ItemStackTransaction addItemStack(ItemStack stack) {
        throw new UnsupportedOperationException("Stub");
    }
    public ItemStack getItemStack(short slot) { throw new UnsupportedOperationException("Stub"); }
    public ItemStackSlotTransaction setItemStackForSlot(short slot, ItemStack stack) { throw new UnsupportedOperationException("Stub"); }
    public SlotTransaction removeItemStackFromSlot(short slot) { throw new UnsupportedOperationException("Stub"); }
    public short getCapacity() { return 0; }
    public boolean isEmpty() { return true; }
}
