package com.crystalrealm.ecotalereforging.system;

import com.crystalrealm.ecotalereforging.config.ReforgeConfig;
import com.crystalrealm.ecotalereforging.service.ReforgeDataStore;
import com.crystalrealm.ecotalereforging.util.PluginLogger;
import com.crystalrealm.ecotalereforging.util.ReforgeMetadataHelper;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;

/**
 * ECS DamageEventSystem that applies reforge-level damage bonuses.
 *
 * <p>When a player attacks an entity, this system checks the player's held weapon,
 * looks up the reforge level from {@link ReforgeDataStore}, and applies the
 * configured {@code weaponDamageBonus} from {@link ReforgeConfig.LevelConfig}.</p>
 *
 * <p>The bonus is ADDITIVE — the flat damage bonus from config is added to the
 * base damage amount. For example, a +3 weapon with {@code weaponDamageBonus=6.0}
 * will deal {@code baseDamage + 6.0} damage.</p>
 */
public class ReforgeDamageSystem extends DamageEventSystem {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final ReforgeDataStore dataStore;
    private final ReforgeConfig    config;

    /** Cached SystemGroup from DamageModule reflection. */
    private SystemGroup<EntityStore> cachedGroup;

    public ReforgeDamageSystem(ReforgeDataStore dataStore, ReforgeConfig config) {
        this.dataStore = dataStore;
        this.config    = config;
    }

    // ── ECS callbacks ──────────────────────────────────────

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {

        if (damage.isCancelled()) return;

        // ── ATTACKER: weapon damage bonus ───────────────────
        applyWeaponBonus(store, damage);

        // ── DEFENDER: armor defense reduction ───────────────
        applyArmorDefense(index, chunk, store, damage);
    }

    /**
     * If the attacker is a player holding a reforged weapon,
     * add the cumulative weaponDamageBonus to the damage.
     */
    private void applyWeaponBonus(Store<EntityStore> store, Damage damage) {
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource)) return;

        Ref<EntityStore> attackerRef = ((Damage.EntitySource) source).getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player player;
        try {
            player = (Player) store.getComponent(attackerRef, Player.getComponentType());
        } catch (Exception e) { return; }
        if (player == null) return;

        ItemStack held;
        try { held = player.getInventory().getItemInHand(); } catch (Exception e) { return; }
        if (held == null || held.isEmpty()) return;

        String itemId = held.getItemId();
        if (itemId == null || itemId.isEmpty()) return;
        if (!config.getAllowedItems().isWeapon(itemId)) return;

        int reforgeLevel = ReforgeMetadataHelper.getReforgeLevel(held);
        if (reforgeLevel <= 0 && !ReforgeMetadataHelper.isAvailable()) {
            String bareId = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
            reforgeLevel = dataStore.getLevel(player.getUuid(), bareId);
        }
        if (reforgeLevel <= 0) return;

        double totalBonus = 0;
        for (int lvl = 1; lvl <= reforgeLevel; lvl++) {
            ReforgeConfig.LevelConfig lc = config.getLevelConfig(lvl);
            if (lc != null) totalBonus += lc.getWeaponDamageBonus();
        }
        if (totalBonus <= 0) return;

        float orig = damage.getAmount();
        damage.setAmount(orig + (float) totalBonus);

        LOGGER.debug("[Reforge] Weapon bonus: {} → {} (+{})",
                String.format("%.1f", orig),
                String.format("%.1f", orig + totalBonus),
                String.format("%.1f", totalBonus));
    }

    /**
     * If the defender is a player wearing reforged armor,
     * reduce incoming damage by the cumulative armorDefenseBonus
     * across all equipped armor pieces.
     */
    private void applyArmorDefense(int index,
                                    ArchetypeChunk<EntityStore> chunk,
                                    Store<EntityStore> store,
                                    Damage damage) {
        // Get the defender entity
        Ref<EntityStore> defenderRef;
        try {
            defenderRef = chunk.getReferenceTo(index);
        } catch (Exception e) { return; }
        if (defenderRef == null || !defenderRef.isValid()) return;

        Player defender;
        try {
            defender = (Player) store.getComponent(defenderRef, Player.getComponentType());
        } catch (Exception e) { return; }
        if (defender == null) return;

        // Scan defender's inventory for reforged armor
        double totalDefBonus = 0;
        try {
            ItemContainer container = defender.getInventory().getCombinedHotbarFirst();
            if (container == null) return;
            short capacity = container.getCapacity();
            for (short s = 0; s < capacity; s++) {
                try {
                    ItemStack stack = container.getItemStack(s);
                    if (stack == null || stack.isEmpty()) continue;
                    String id = stack.getItemId();
                    if (id == null || !config.getAllowedItems().isArmor(id)) continue;

                    int armorLevel = ReforgeMetadataHelper.getReforgeLevel(stack);
                    if (armorLevel <= 0) continue;

                    for (int lvl = 1; lvl <= armorLevel; lvl++) {
                        ReforgeConfig.LevelConfig lc = config.getLevelConfig(lvl);
                        if (lc != null) {
                            totalDefBonus += lc.getArmorDefenseBonus();
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) { return; }

        if (totalDefBonus <= 0) return;

        // Apply DEF reduction
        float orig = damage.getAmount();
        float reduced = Math.max(1.0f, orig - (float) totalDefBonus);
        damage.setAmount(reduced);

        LOGGER.debug("[Reforge] Armor defense: {} → {} (DEF -{})",
                String.format("%.1f", orig),
                String.format("%.1f", (double) reduced),
                String.format("%.1f", totalDefBonus));
    }

    @Override
    @SuppressWarnings("unchecked")
    public SystemGroup<EntityStore> getGroup() {
        if (cachedGroup != null) return cachedGroup;

        try {
            // DamageModule.get().getFilterDamageGroup()
            Class<?> dmClass = Class.forName(
                    "com.hypixel.hytale.server.core.modules.entity.damage.DamageModule");
            Method getMethod = dmClass.getMethod("get");
            Object dmInstance = getMethod.invoke(null);
            Method getGroupMethod = dmClass.getMethod("getFilterDamageGroup");
            cachedGroup = (SystemGroup<EntityStore>) getGroupMethod.invoke(dmInstance);
        } catch (Exception e) {
            LOGGER.error("[Reforge] Failed to resolve DamageModule group: {}", e.getMessage());
        }
        return cachedGroup;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Query<EntityStore> getQuery() {
        // Use reflection to avoid compiled method descriptor mismatch
        // (our stub returns Query<T>, real runtime returns AnyQuery)
        try {
            java.lang.reflect.Method anyMethod = Query.class.getMethod("any");
            return (Query<EntityStore>) anyMethod.invoke(null);
        } catch (Exception e) {
            LOGGER.error("[Reforge] Failed to resolve Query.any(): {}", e.getMessage());
            return null;
        }
    }
}
