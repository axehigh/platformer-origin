package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/** Marker component for a crystal pickup entity that adds to the player's crystal objective count. */
public class CrystalPickupComponent implements Component {
    public int amount = 1;
}