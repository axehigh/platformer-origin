package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Applied to spawned projectile entities. */
public class BulletComponent implements Component, Poolable {
    public float damage;
    /** Despawns the bullet after this many seconds if it doesn't hit anything. */
    public float lifetime;
    /** Countdown to the next trail afterimage spawn (decremented each frame by bullet systems). */
    public float trailTimer;
    /**
     * Max horizontal travel distance in world units before the bullet despawns; {@code 0} =
     * unlimited. Player bullets leave this at {@code 0} (unchanged behavior); {@code
     * EnemyShootSystem} sets it from {@code EnemyShooterComponent.shootRange} (already a world-unit
     * distance) so an enemy shot dies after flying exactly its range.
     */
    public float maxTravelDistance = 0f;
    /**
     * Accumulated horizontal distance traveled in world units (summed by {@code
     * EnemyBulletCollisionSystem} as {@code |velocity.x| * deltaTime}); compared against {@code
     * maxTravelDistance} to trigger the distance despawn.
     */
    public float traveledDistance = 0f;

    @Override
    public void reset() {
        damage = 0f;
        lifetime = 0f;
        trailTimer = 0f;
        maxTravelDistance = 0f;
        traveledDistance = 0f;
    }
}
