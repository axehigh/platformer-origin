package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/**
 * Marker component layered on top of {@code EnemyComponent} tagging an enemy as able to fire a
 * bullet periodically, resolved by {@code EnemyShootSystem}. The enemy still patrols exactly like
 * a base enemy (driven by {@code EnemySystem}, which already matches this entity's family).
 */
public class EnemyShooterComponent implements Component {
    /** Counts down between shots; once done, {@code EnemyShootSystem} fires and restarts it. */
    public Timer shootCooldown = new Timer();
    /** Seconds between shots. */
    public float shootInterval = 5f;
}
