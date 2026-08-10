package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/**
 * Damageable enemy entity with simple back-and-forth patrol movement, resolved by
 * {@code EnemySystem}. Bullets/melee strikes can damage it via {@code CollisionSystem}/
 * {@code MeleeAttackSystem}, which also apply a brief hit-stun/knockback via {@code hitStun}.
 */
public class EnemyComponent implements Component {

    /** Selects how the enemy's horizontal patrol behaves; see {@link AiMode}. */
    public enum AiMode {
        /** Origin-bounded patrol: turns at {@code patrolRange} from spawn, plus walls/ledges/hazards. */
        PATROL,
        /** Endless walking: turns only on walls, ledges, and hazards ({@code patrolRange} is ignored). */
        SIDE_TO_SIDE
    }

    public float health = 10f;
    /** Starting/full health, set alongside {@code health} by {@code EntityFactory}; used to size coin drops on death. */
    public float maxHealth = 10f;
    /** Horizontal patrol speed, in world units/second. */
    public float speed = 20f;
    /** Current patrol direction: {@code 1} for right, {@code -1} for left. */
    public int direction = 1;
    /** Max distance the enemy walks away from {@code originX} in either direction before turning around (PATROL mode only). */
    public float patrolRange = 64f;
    /** Which patrol behavior drives this enemy (see {@link AiMode}); set from the Tiled {@code aiMode} property. */
    public AiMode aiMode = AiMode.PATROL;
    /**
     * Brief pause after a turn-around (wall, ledge, or hazard) during which the enemy stays
     * stationary before resuming its patrol, so direction changes are visible instead of instant.
     */
    public final Timer turnPause = new Timer();
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
     * Set to {@code true} when health reaches zero; triggers the death sequence (death animation,
     * then a brief blink before entity removal).
     */
    public boolean isDead = false;
    /**
     * Tracks the death animation duration plus the {@code EnemyDamageResolver.DEATH_FLASH_DURATION}
     * post-animation blink window; {@code EnemySystem} removes the entity once it elapses.
     */
    public Timer deathTimer = new Timer();
    /**
     * Guard so the death coin drop fires exactly once, on the first frame {@code EnemySystem}
     * observes the death — i.e. immediately on the kill, before the corpse lingers/flashes.
     */
    public boolean deathCoinsSpawned = false;
    /**
     * Index into {@code RoomState.rooms} of the Room rectangle this enemy was spawned inside of,
     * or {@code -1} if it wasn't inside any known room. {@code EnemySystem}/{@code
     * EnemyShootSystem} freeze this enemy's AI/firing while its room isn't the currently active
     * one (see {@code RoomState.activeRoomIndex}); {@code -1} means it's always active.
     */
    public int roomIndex = -1;
    /**
     * Set to {@code true} while this enemy's owning room is inactive (its AI frozen with velocity
     * zeroed by {@code EnemySystem}); cleared on the first active frame so the just-zeroed velocity
     * isn't misread as a wall block (which would flip direction + start a turn pause for every
     * enemy in the room simultaneously on re-entry).
     */
    public boolean wasFrozen = false;
}
