package com.hypixel.hytale.server.core.modules.entity.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Stub — Interactable entity component.
 * Real class: com.hypixel.hytale.server.core.modules.entity.component.Interactable
 *
 * <p>Makes an entity interactable by players (F key shows prompt).</p>
 */
public class Interactable implements Component<EntityStore> {

    public static final Interactable INSTANCE = new Interactable();

    public static ComponentType<EntityStore, Interactable> getComponentType() {
        return new ComponentType<>();
    }
}
