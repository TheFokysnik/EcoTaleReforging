package com.hypixel.hytale.server.core.modules.entity.damage;

import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Stub — base class for damage event handling systems.
 * Real class: com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
 *
 * <p>Extend this class to intercept and modify damage events in the ECS pipeline.</p>
 */
public abstract class DamageEventSystem extends EntityEventSystem<EntityStore, Damage> {

    protected DamageEventSystem() {
        super(Damage.class);
    }
}
