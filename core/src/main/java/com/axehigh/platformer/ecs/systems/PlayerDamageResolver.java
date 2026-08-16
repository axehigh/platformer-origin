package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.ecs.components.Mappers.ANIMATION;
import static com.axehigh.platformer.ecs.components.Mappers.BUFF;

/**
 * Shared player-damage resolution used by {@code EnemyContactSystem} (touch),
 * {@code EnemyBulletCollisionSystem} (enemy bullet), and {@code HazardSystem} (spikes/lava, via the
 * no-knockback variant) so the damage sources react identically: health decrement, a brief hit-stun
 * window that locks movement input (long enough to play the HURT animation once), an optional small
 * horizontal knockback pop away from the attacker (hazards omit it), and the shared invulnerability
 * grace period. The Invulnerability potion buff also fully blocks damage while active. The animation
 * counterpart of {@code EnemyDamageResolver}.
 */
final class PlayerDamageResolver {
    /** Shortest hit-stun window; extended to cover the HURT animation if that is longer. */
    private static final float HIT_STUN_DURATION = 0.3f;
    /** Small horizontal push applied to the player on a surviving hit (world units/second). */
    private static final float KNOCKBACK_SPEED_X = 60f;
    /** Full grace period during which further damage is ignored and the sprite blinks. */
    static final float HIT_INVULNERABILITY_DURATION = 2.0f;

    private PlayerDamageResolver() {
    }

    /**
     * Applies a single hit of contact/enemy-bullet damage to {@code player}. The hit is fully
     * ignored (returns {@code false}, no state change) while {@code isDead}, while the
     * invulnerability grace period is still active, or while an Invulnerability potion buff is
     * active. On a surviving hit: decrements {@code health} (clamped at 0), pushes
     * {@code movement.velocity.x} away from the attacker (a small horizontal knockback, scaled by
     * {@code unitScale} — no vertical pop, since the player has full gravity and the hitstun lock
     * must stay brief), starts {@code hurtTimer} for a duration matching the HURT animation
     * (min 0.3s, so the clip visibly plays once), and starts the {@code hitInvulnerability} grace
     * period. Returns {@code true} if the hit applied.
     */
    static boolean applyHit(Entity playerEntity, PlayerComponent player, MovementComponent movement, int knockbackDirection, float unitScale) {
        if (player.isDead || player.hitInvulnerability.isActive() || isBuffInvulnerable(playerEntity)) {
            return false;
        }

        player.health = Math.max(0, player.health - 1);
        movement.velocity.x = KNOCKBACK_SPEED_X * knockbackDirection * unitScale;
        applyStunAndGrace(playerEntity, player);
        return true;
    }

    /**
     * Same as {@link #applyHit(Entity, PlayerComponent, MovementComponent, int, float)} but with
     * no knockback: the player's horizontal velocity is left untouched. Used by {@code HazardSystem}
     * for tile hazards (spikes/lava), where a directional push makes no sense and could shove the
     * player back into the hazard. Hit-stun lock, the invulnerability grace period, and the
     * Invulnerability potion buff still apply.
     */
    static boolean applyHitWithoutKnockback(Entity playerEntity, PlayerComponent player) {
        if (player.isDead || player.hitInvulnerability.isActive() || isBuffInvulnerable(playerEntity)) {
            return false;
        }

        player.health = Math.max(0, player.health - 1);
        applyStunAndGrace(playerEntity, player);
        return true;
    }

    /** True while the player's Invulnerability potion buff is active (no-op without a buff). */
    private static boolean isBuffInvulnerable(Entity playerEntity) {
        BuffComponent buff = BUFF.get(playerEntity);
        return buff != null && buff.isInvulnerabilityActive();
    }

    private static void applyStunAndGrace(Entity playerEntity, PlayerComponent player) {
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
    }
}
