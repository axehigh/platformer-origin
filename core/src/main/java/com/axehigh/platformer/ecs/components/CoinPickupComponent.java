package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/** Marker component for a coin pickup entity that adds to the player's coin count. */
public class CoinPickupComponent implements Component {
    /** Coins granted to PlayerComponent.coins on pickup (uncapped). */
    public int amount = 1;
}
