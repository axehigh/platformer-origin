package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/** Pickup component for a potion that adds to the player's held count of a potion type. */
public class PotionPickupComponent implements Component {
    /** Which potion type this pickup grants. */
    public PotionType type = PotionType.HEALING;
    /** Units granted on pickup (usually 1); excess over the cap converts to coins. */
    public int amount = 1;
}
