package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/** Tracks a chest entity's open state after being melee-struck. */
public class ChestComponent implements Component {
    public boolean opened = false;
    /** Counts down after opening; on reaching done, the chest drops its loot. */
    public Timer disappearTimer = new Timer();
    /** Guards the one-shot loot drop: loot pops out exactly once per opened chest. */
    public boolean coinsDropped = false;
    /**
     * If non-null, the chest drops a potion of this type instead of coins when opened.
     * Set from the {@code potionType} Tiled object property; {@code null} = coin-only chest.
     */
    public PotionType potionType = null;
}
