package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/** Marker component for a dagger pickup entity that replenishes the player's shoot ammo. */
public class DaggerPickupComponent implements Component {
    /** Ammo granted to PlayerComponent.items on pickup (capped at maxItems). */
    public int amount = 5;
}
