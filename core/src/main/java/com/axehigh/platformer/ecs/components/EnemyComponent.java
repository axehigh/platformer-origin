package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/**
 * Damageable enemy entity with simple back-and-forth patrol movement, resolved by
 * {@code EnemySystem}. Bullets/melee strikes can damage it via {@code CollisionSystem}/
 * {@code MeleeAttackSystem}, which also apply a brief hit-stun/knockback via {@code hitStun}.
 */
public class EnemyComponent implements Component {
    public float health = 10f;
    /** Starting/full health, set alongside {@code health} by {@code EntityFactory}; used to size coin drops on death. */
    public float maxHealth = 10f;
    /** Horizontal patrol speed, in world units/second. */
    public float speed = 20f;
    /** Current patrol direction: {@code 1} for right, {@code -1} for left. */
    public int direction = 1;
    /** Max distance the enemy walks away from {@code originX} in either direction before turning around. */
    public float patrolRange = 32f;
    /** World-space X position the enemy was spawned at; the center of its patrol path. */
    public float originX = 0f;
    /**
     * Grace period after taking damage during which the enemy ignores further hits and its
     * knockback pop plays out uninterrupted (patrol AI is paused while this is active).
     */
    public Timer hitStun = new Timer();
    /**
     * Brief pause after hitStun ends during which the enemy stays idle before resuming patrol.
     */
    public final Timer postHitIdle = new Timer();
    /**
     * Set to {@code true} when health reaches zero; triggers the death sequence (animation
     * followed by entity removal).
     */
    public boolean isDead = false;
    /** Tracks the duration of the death animation before the entity is removed. */
    public Timer deathTimer = new Timer();
    /**
     * Index into {@code RoomState.rooms} of the Room rectangle this enemy was spawned inside of,
     * or {@code -1} if it wasn't inside any known room. {@code EnemySystem}/{@code
     * EnemyShootSystem} freeze this enemy's AI/firing while its room isn't the currently active
     * one (see {@code RoomState.activeRoomIndex}); {@code -1} means it's always active.
     */
    public int roomIndex = -1;
}
