package com.hypixel.hytale.server.core.inventory;

public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();
    public ItemStack() {}
    public ItemStack(String itemId, int count) {}
    public String getItemId() { return ""; }
    public int getQuantity() { return 0; }
    public boolean isEmpty() { return true; }
    public double getDurability() { return 0; }
    public double getMaxDurability() { return 0; }
    public ItemStack withDurability(double durability) { return this; }
    public ItemStack withQuantity(int quantity) { return this; }
    public boolean isValid() { return false; }
    public boolean isBroken() { return false; }
    public boolean isUnbreakable() { return false; }
    public boolean isStackableWith(ItemStack other) { return false; }
}
