package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Damageable enemy entity with simple back-and-forth patrol movement, resolved by
 * {@code EnemySystem}. Bullets/melee strikes can damage it via {@code CollisionSystem}/
 * {@code MeleeAttackSystem}.
 */
public class EnemyComponent implements Component {
    public float health = 1f;
    /** Horizontal patrol speed, in world units/second. */
    public float speed = 20f;
    /** Current patrol direction: {@code 1} for right, {@code -1} for left. */
    public int direction = 1;
    /** Max distance the enemy walks away from {@code originX} in either direction before turning around. */
    public float patrolRange = 32f;
    /** World-space X position the enemy was spawned at; the center of its patrol path. */
    public float originX = 0f;
}
