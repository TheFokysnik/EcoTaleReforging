package com.hypixel.hytale.component;

/**
 * Stub — ECS entity store.
 * NOTE: getComponent returns Component (not generic) to match runtime bytecode descriptor.
 * At runtime: Store.getComponent(Ref, ComponentType) -> Component
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class Store<ECS_TYPE> {

    /**
     * Get a component from an entity reference.
     *
     * @param ref  the entity reference
     * @param type the component type
     * @return the component instance, or null
     */
    public Component getComponent(Ref ref, ComponentType type) {
        return null;
    }
}