package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.particles.GlobalParticles;
import com.axehigh.platformer.particles.ParticleHelper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.util.SaveManager.*;

/**
 * Shared enemy-damage resolution used by both {@code MeleeAttackSystem} and {@code CollisionSystem}
 * (bullet hits): applies damage, a brief hit-stun grace period, and a knockback pop, all in one
 * place so the two damage sources stay perfectly consistent. A surviving hit also spawns a spark
 * burst (via {@code ParticleHelper}) when a {@link PooledEngine} is supplied.
 */
final class EnemyDamageResolver {
    /** Grace period after a hit during which the enemy is immune to further damage/knockback. */
    private static final float HIT_STUN_DURATION = 0.3f;
    static final float POST_HIT_IDLE_DURATION = 0.5f;
    private static final float KNOCKBACK_SPEED_X = 90f;
    private static final float KNOCKBACK_SPEED_Y = 140f;
    static final float HIT_SPARK_SCALE = 1.2f;
    /** Hit sparks must not linger: forced removal once this many seconds have elapsed (the sparks
     * effect is continuous, so it never self-completes). Shared with the sword-clank wall spark. */
    static final float HIT_SPARK_MAX_LIFETIME = 1.0f;
    /**
     * Post-death blink window: after the death animation plays out, the corpse blinks at ~10Hz for
     * this long (via {@code AnimationSystem}) before {@code EnemySystem} removes it. Added to the
     * death-animation duration when {@code deathTimer} is started.
     */
    static final float DEATH_FLASH_DURATION = 0.8f;

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
        return applyHit(enemyEntity, enemy, movement, damage, knockbackDirection, isFlying, unitScale, null);
    }

    /**
     * Same as {@link #applyHit(Entity, EnemyComponent, MovementComponent, float, int, boolean, float)}
     * plus a {@link PooledEngine} used to spawn a spark burst at the enemy on an applied hit.
     */
    static boolean applyHit(Entity enemyEntity, EnemyComponent enemy, MovementComponent movement, float damage, int knockbackDirection, boolean isFlying, float unitScale, PooledEngine engine) {
        if (enemy.isDead || enemy.hitStun.isActive()) {
            return false;
        }

        spawnHitSpark(enemyEntity, engine);

        enemy.health -= damage;
        if (enemy.health <= 0f) {
            enemy.isDead = true;
            movement.velocity.set(0, 0);

            // Track kill in SaveData if save exists or can be updated
            if (hasSave()) {
                SaveData save = load();
                save.enemiesKilled++;
                save(save);
            }

            AnimationComponent anim = Mappers.ANIMATION.get(enemyEntity);
            float duration = 0.5f;
            if (anim != null) {
                Animation<TextureRegion> deathAnim = anim.animations.get(AnimationComponent.State.DEATH);
                if (deathAnim != null) {
                    duration = deathAnim.getAnimationDuration();
                }
            }
            enemy.deathTimer.start(duration + DEATH_FLASH_DURATION);
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

    /** One-shot spark burst at the enemy's collision-center; a no-op without a PooledEngine. */
    private static void spawnHitSpark(Entity enemyEntity, PooledEngine engine) {
        if (engine == null) {
            return;
        }
        CollisionComponent collision = Mappers.COLLISION.get(enemyEntity);
        if (collision == null) {
            return;
        }
        float centerX = collision.worldBounds.x + collision.worldBounds.width / 2f;
        float centerY = collision.worldBounds.y + collision.worldBounds.height / 2f;
        ParticleHelper.spawnParticle(engine, GlobalParticles.SPARKS, centerX, centerY, 0f, HIT_SPARK_SCALE, HIT_SPARK_MAX_LIFETIME);
    }
}
