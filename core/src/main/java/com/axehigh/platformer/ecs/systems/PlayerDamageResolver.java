package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.ecs.components.Mappers.ANIMATION;

/**
 * Shared player-damage resolution used by both {@code EnemyContactSystem} (touch) and
 * {@code EnemyBulletCollisionSystem} (enemy bullet) so the two damage sources react identically:
 * health decrement, a brief hit-stun window that locks movement input (long enough to play the
 * HURT animation once), a small horizontal knockback pop away from the attacker, and the shared
 * invulnerability grace period. The animation counterpart of {@code EnemyDamageResolver}.
 */
final class PlayerDamageResolver {
    /** Shortest hit-stun window; extended to cover the HURT animation if that is longer. */
    private static final float HIT_STUN_DURATION = 0.3f;
    /** Small horizontal push applied to the player on a surviving hit (world units/second). */
    private static final float KNOCKBACK_SPEED_X = 60f;
    /** Full grace period during which further damage is ignored and the sprite blinks. */
    static final float HIT_INVULNERABILITY_DURATION = 1.0f;

    private PlayerDamageResolver() {
    }

    /**
     * Applies a single hit of contact/enemy-bullet damage to {@code player}. The hit is fully
     * ignored (returns {@code false}, no state change) while {@code isDead} or while the
     * invulnerability grace period is still active. On a surviving hit: decrements
     * {@code health} (clamped at 0), pushes {@code movement.velocity.x} away from the attacker
     * (a small horizontal knockback, scaled by {@code unitScale} — no vertical pop, since the
     * player has full gravity and the hitstun lock must stay brief), starts {@code hurtTimer} for
     * a duration matching the HURT animation (min 0.3s, so the clip visibly plays once), and
     * starts the {@code hitInvulnerability} grace period. Returns {@code true} if the hit applied.
     */
    static boolean applyHit(Entity playerEntity, PlayerComponent player, MovementComponent movement, int knockbackDirection, float unitScale) {
        if (player.isDead || player.hitInvulnerability.isActive()) {
            return false;
        }

        player.health = Math.max(0, player.health - 1);
        movement.velocity.x = KNOCKBACK_SPEED_X * knockbackDirection * unitScale;

        float stunDuration = HIT_STUN_DURATION;
        AnimationComponent anim = ANIMATION.get(playerEntity);
        if (anim != null) {
            Animation<TextureRegion> hurtAnim = anim.animations.get(AnimationComponent.State.HURT);
            if (hurtAnim != null) {
                // Use the animation duration, but at least HIT_STUN_DURATION to ensure it's visible.
                stunDuration = Math.max(HIT_STUN_DURATION, hurtAnim.getAnimationDuration());
            }
        }
        player.hurtTimer.start(stunDuration);
        player.hitInvulnerability.start(HIT_INVULNERABILITY_DURATION);
        return true;
    }
}
