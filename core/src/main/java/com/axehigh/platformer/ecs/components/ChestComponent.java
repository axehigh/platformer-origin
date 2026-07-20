package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/** Tracks a chest entity's open/disappear state after being melee-struck. */
public class ChestComponent implements Component {
    public boolean opened = false;
    /** Counts down after opening; on reaching 0, the chest is removed and drops coins. */
    public float disappearTimer = 0f;
}
