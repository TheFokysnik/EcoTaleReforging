package com.hypixel.hytale.server.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.util.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stub — NPC Plugin singleton.
 * Real class: com.hypixel.hytale.server.npc.NPCPlugin
 *
 * <p>Provides NPC management: spawn, role lookup, entity creation.</p>
 */
public class NPCPlugin {

    private static final NPCPlugin INSTANCE = new NPCPlugin();

    public static NPCPlugin get() { return INSTANCE; }

    /**
     * Get role index by name.
     * @param roleName the role name (e.g. "Blacksmith")
     * @return role index, or -1 if not found
     */
    public int getIndex(@Nonnull String roleName) { return -1; }

    /**
     * Spawn an NPC entity.
     *
     * @param store      entity store
     * @param roleIndex  index from getIndex()
     * @param posX       world X
     * @param posY       world Y
     * @param posZ       world Z
     * @param rotY       yaw rotation
     * @param modelId    optional model override
     * @param postSpawn  callback after spawn (may be null)
     * @return pair of entity ref and NPCEntity component
     */
    @Nullable
    public Pair<Ref<EntityStore>, NPCEntity> spawnEntity(
            @Nonnull Store<EntityStore> store,
            int roleIndex,
            float posX, float posY, float posZ,
            float rotY,
            @Nullable String modelId,
            @Nullable Runnable postSpawn) {
        return null;
    }
}
