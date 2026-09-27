package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/**
 * Marker component tagging a pickup entity that was "popped" out with an initial velocity (e.g.
 * chest-dropped coins). Checked by {@code MovementSystem}: as soon as the entity's first ground
 * contact sets {@code MovementComponent.grounded}, its horizontal velocity is also zeroed so it
 * comes to a dead stop right where it lands instead of sliding indefinitely (there is no ground
 * friction elsewhere in the system). Also holds a collection grace period timer so popped loot
 * cannot be collected immediately on spawn.
 */
public class PoppedItemComponent implements Component {
    public final Timer collectionDelay = new Timer();

    public PoppedItemComponent() {
        collectionDelay.start(GameConstants.LOOT_COLLECTION_DELAY);
    }
}
