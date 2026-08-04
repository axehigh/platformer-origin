package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/** Tracks a chest entity's open state after being melee-struck. */
public class ChestComponent implements Component {
    public boolean opened = false;
    /** Counts down after opening; on reaching done, the chest drops its coins. */
    public Timer disappearTimer = new Timer();
    /** Guards the one-shot coin drop: coins pop out exactly once per opened chest. */
    public boolean coinsDropped = false;
}
