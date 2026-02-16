package com.hypixel.hytale.event.server;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Stub — Fired when a player uses the interact key (F) on an entity.
 *
 * <p>Based on Hytale docs: InteractionType.Use (5) = F key interaction.
 * This fires when the player presses F on an interactable entity.</p>
 *
 * <p>Note: The old PlayerInteractEvent was deprecated and never fired.
 * This event replaces it for entity-specific interactions.</p>
 */
public class PlayerEntityInteractEvent {

    /** The player who initiated the interaction. */
    public Player getPlayer() { return null; }

    /** The entity reference that was interacted with. */
    public Ref<EntityStore> getTargetEntityRef() { return null; }

    /** Interaction type ID. Use = 5 (F key). */
    public int getInteractionType() { return 0; }
}
