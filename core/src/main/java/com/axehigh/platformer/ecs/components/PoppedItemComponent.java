package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Marker component tagging a pickup entity that was "popped" out with an initial velocity (e.g.
 * chest-dropped coins). Checked by {@code MovementSystem}: as soon as the entity's first ground
 * contact sets {@code MovementComponent.grounded}, its horizontal velocity is also zeroed so it
 * comes to a dead stop right where it lands instead of sliding indefinitely (there is no ground
 * friction elsewhere in the system).
 */
public class PoppedItemComponent implements Component {
}
