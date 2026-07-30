package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.Mappers;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Shared enemy-damage resolution used by both {@code MeleeAttackSystem} and {@code CollisionSystem}
 * (bullet hits): applies damage, a brief hit-stun grace period, and a knockback pop, all in one
 * place so the two damage sources stay perfectly consistent.
 */
final class EnemyDamageResolver {
    /** Grace period after a hit during which the enemy is immune to further damage/knockback. */
    private static final float HIT_STUN_DURATION = 0.3f;
    static final float POST_HIT_IDLE_DURATION = 0.5f;
    private static final float KNOCKBACK_SPEED_X = 90f;
    private static final float KNOCKBACK_SPEED_Y = 140f;

    private EnemyDamageResolver() {
    }

    /**
     * Applies {@code damage} to {@code enemy}, unless it's still within its hit-stun grace period
     * (in which case the hit is fully ignored). On a surviving hit, kicks off a horizontal +
     * vertical knockback pop (away from the attacker, given by {@code knockbackDirection}) and
     * starts the hit-stun timer. If {@code isFlying} is {@code true}, the vertical hop is skipped
     * (a flying enemy has no gravity to pull it back down, so it would otherwise drift upward
     * forever) while the horizontal knockback and hit-stun still apply. Returns {@code true} if
     * the enemy's health reached 0.
     */
    static boolean applyHit(Entity enemyEntity, EnemyComponent enemy, MovementComponent movement, float damage, int knockbackDirection, boolean isFlying, float unitScale) {
        if (enemy.isDead || enemy.hitStun.isActive()) {
            return false;
        }

        enemy.health -= damage;
        if (enemy.health <= 0f) {
            enemy.isDead = true;
            movement.velocity.set(0, 0);

            AnimationComponent anim = Mappers.ANIMATION.get(enemyEntity);
            float duration = 0.5f;
            if (anim != null) {
                Animation<TextureRegion> deathAnim = anim.animations.get(AnimationComponent.State.DEATH);
                if (deathAnim != null) {
                    duration = deathAnim.getAnimationDuration();
                }
            }
            enemy.deathTimer.start(duration);
            return true;
        }

        movement.velocity.x = KNOCKBACK_SPEED_X * knockbackDirection * unitScale;
        if (!isFlying) {
            movement.velocity.y = KNOCKBACK_SPEED_Y * unitScale;
        }

        float stunDuration = HIT_STUN_DURATION;
        AnimationComponent anim = Mappers.ANIMATION.get(enemyEntity);
        if (anim != null) {
            Animation<TextureRegion> hurtAnim = anim.animations.get(AnimationComponent.State.HURT);
            if (hurtAnim != null) {
                // Use the animation duration, but at least HIT_STUN_DURATION to ensure it's visible
                stunDuration = Math.max(HIT_STUN_DURATION, hurtAnim.getAnimationDuration());
            }
        }
        enemy.hitStun.start(stunDuration);
        enemy.postHitIdle.reset();
        return false;
    }
}
