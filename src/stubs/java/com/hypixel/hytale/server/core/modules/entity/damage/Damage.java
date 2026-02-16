package com.hypixel.hytale.server.core.modules.entity.damage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Stub — Hytale damage instance.
 * Real class: com.hypixel.hytale.server.core.modules.entity.damage.Damage
 *
 * <p>Represents a single damage event. Use {@link #getSource()} to determine
 * the origin of the damage (entity, environment, etc.).</p>
 */
public class Damage extends EcsEvent {

    /** Whether this damage event was cancelled. */
    public boolean isCancelled() { return false; }

    /** The source that caused this damage. */
    public Source getSource() { return null; }

    /** The amount of damage dealt. */
    public float getAmount() { return 0f; }

    /** Set the amount of damage to deal. */
    public void setAmount(float amount) {}

    // ─── Source hierarchy ───────────────────────────────────

    /** Base interface for all damage sources. */
    public interface Source {}

    /**
     * Damage caused by another entity (player, NPC, etc.).
     * Use {@link #getRef()} to get the attacker's entity reference.
     */
    public static class EntitySource implements Source {
        /** Reference to the attacking entity. */
        public Ref<EntityStore> getRef() { return null; }
    }
}
