package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;

/**
 * Shared enemy-damage resolution used by both {@code MeleeAttackSystem} and {@code CollisionSystem}
 * (bullet hits): applies damage, a brief hit-stun grace period, and a knockback pop, all in one
 * place so the two damage sources stay perfectly consistent.
 */
final class EnemyDamageResolver {
    /** Grace period after a hit during which the enemy is immune to further damage/knockback. */
    private static final float HIT_STUN_DURATION = 0.3f;
    private static final float KNOCKBACK_SPEED_X = 90f;
    private static final float KNOCKBACK_SPEED_Y = 140f;

    private EnemyDamageResolver() {
    }

    /**
     * Applies {@code damage} to {@code enemy}, unless it's still within its hit-stun grace period
     * (in which case the hit is fully ignored). On a surviving hit, kicks off a horizontal +
     * vertical knockback pop (away from the attacker, given by {@code knockbackDirection}) and
     * starts the hit-stun timer. Returns {@code true} if the enemy's health reached 0.
     */
    static boolean applyHit(EnemyComponent enemy, MovementComponent movement, float damage, int knockbackDirection) {
        if (enemy.hitStun.isActive()) {
            return false;
        }

        enemy.health -= damage;
        if (enemy.health <= 0f) {
            return true;
        }

        movement.velocity.x = KNOCKBACK_SPEED_X * knockbackDirection;
        movement.velocity.y = KNOCKBACK_SPEED_Y;
        enemy.hitStun.start(HIT_STUN_DURATION);
        return false;
    }
}
