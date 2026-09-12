package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.PlayerConfig.PLAYER_IDLE_DELAY;
import static com.axehigh.platformer.ecs.components.Mappers.*;

/** Advances the current animation state timer and updates the visible TextureRegion. */
public class AnimationSystem extends IteratingSystem {

    /** Blink frequency (Hz): number of visibility toggles per second while invulnerable. */
    private static final float BLINK_FREQUENCY = 10f;

    public AnimationSystem() {
        this(0);
    }

    public AnimationSystem(int priority) {
        super(Family.all(AnimationComponent.class, TextureComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AnimationComponent animationComponent = ANIMATION.get(entity);
        TextureComponent textureComponent = TEXTURE.get(entity);

        if (PLAYER.has(entity) && MOVEMENT.has(entity)) {
            PlayerComponent player = PLAYER.get(entity);
            animationComponent.currentState = resolvePlayerState(player, animationComponent, MOVEMENT.get(entity));

            TransformComponent transform = TRANSFORM.get(entity);
            if (transform != null) {
                transform.scale.x = Math.abs(transform.scale.x) * player.facingDirection;
            }
        } else if (ENEMY.has(entity) && MOVEMENT.has(entity)) {
            EnemyComponent enemy = ENEMY.get(entity);
            animationComponent.currentState = resolveEnemyState(entity, enemy, MOVEMENT.get(entity));

            TransformComponent transform = TRANSFORM.get(entity);
            if (transform != null) {
                transform.scale.x = Math.abs(transform.scale.x) * enemy.direction;
            }
        }

        if (animationComponent.currentState != animationComponent.previousState) {
            animationComponent.stateTime = 0f;
            animationComponent.previousState = animationComponent.currentState;
        } else {
            animationComponent.stateTime += deltaTime;
        }

        Animation<TextureRegion> animation = animationComponent.animations.get(animationComponent.currentState);
        if (animation != null) {
            boolean looping = animationComponent.currentState != AnimationComponent.State.HURT &&
                             animationComponent.currentState != AnimationComponent.State.DEATH &&
                             animationComponent.currentState != AnimationComponent.State.ATTACKING &&
                             animationComponent.currentState != AnimationComponent.State.SPLASHING;
            textureComponent.region = animation.getKeyFrame(animationComponent.stateTime, looping);
        }

        // Invulnerability blink: once the HURT clip has finished (hurtTimer done) but while the
        // hit-invulnerability grace period is still running, flash the sprite at ~10Hz so the
        // remaining invulnerability is readable without freezing the player in the hurt pose.
        // Enemy death blink: while a dead enemy's deathTimer is in its final DEATH_FLASH_DURATION
        // window, flash the corpse at the same ~10Hz so the body lingers, then blinks out just
        // before removal. A null region is skipped by RenderSystem, giving the blink effect.
        PlayerComponent player = PLAYER.get(entity);
        EnemyComponent enemy = ENEMY.get(entity);
        // Tick the idle-entry grace timer (started in resolvePlayerState when the player stops) so
        // the held run/walk pose actually expires into IDLE after PLAYER_IDLE_DELAY.
        if (player != null) {
            player.idleHold.update(deltaTime);
        }
        boolean blinking = player != null
            ? player.hitInvulnerability.isActive() && !player.hurtTimer.isActive() && !player.isDead
            : enemy != null && enemy.isDead && enemy.deathTimer.isActive()
                && enemy.deathTimer.getRemaining() <= EnemyDamageResolver.DEATH_FLASH_DURATION;
        if (blinking) {
            animationComponent.blinkTimer += deltaTime;
            if (((int) (animationComponent.blinkTimer * BLINK_FREQUENCY)) % 2 == 1) {
                textureComponent.region = null;
            }
        } else {
            animationComponent.blinkTimer = 0f;
        }
    }

    private AnimationComponent.State resolvePlayerState(PlayerComponent player, AnimationComponent animationComponent,
                                                        MovementComponent movement) {
        if (player.isDead) {
            return AnimationComponent.State.DEATH;
        }
        if (player.hurtTimer.isActive()) {
            return AnimationComponent.State.HURT;
        }
        if (player.meleeAttack.isActive()) {
            return AnimationComponent.State.ATTACKING;
        }
        if (player.isWallClimbing) {
            return AnimationComponent.State.WALL_CLIMBING;
        }
        if (!movement.grounded) {
            return player.jumpCount >= 2 ? AnimationComponent.State.DOUBLE_JUMPING : AnimationComponent.State.JUMPING;
        }
        float speedX = Math.abs(movement.velocity.x);
        if (speedX > 0.01f) {
            player.idleHold.reset();
            player.idleHoldArmed = false;
            return speedX < 50f ? AnimationComponent.State.WALKING : AnimationComponent.State.RUNNING;
        }
        // Stopped and grounded: with the soft-stop feature flag ON (FeatureFlags.isSoftStopEnabled()),
        // instead of snapping to IDLE the instant velocity zeroes, hold the last run/walk pose for
        // PLAYER_IDLE_DELAY so the character "stops" visibly before easing into idle. The hold arms
        // exactly once per stop (idleHoldArmed); once the grace elapses the code falls through to
        // IDLE without re-arming, and a landing that never ran (currentState is JUMPING, not a
        // movement state) still goes straight to IDLE. When the flag is OFF the player snaps
        // straight to IDLE the frame velocity zeroes (pre-easing behavior; the idleHold timer is
        // never armed and ticks harmlessly).
        AnimationComponent.State last = animationComponent.currentState;
        if (FeatureFlags.isSoftStopEnabled() && !player.idleHoldArmed) {
            player.idleHold.start(PLAYER_IDLE_DELAY);
            player.idleHoldArmed = true;
        }
        if (FeatureFlags.isSoftStopEnabled() && player.idleHold.isActive() && (last == AnimationComponent.State.WALKING || last == AnimationComponent.State.RUNNING)) {
            return last;
        }
        return AnimationComponent.State.IDLE;
    }

    private AnimationComponent.State resolveEnemyState(Entity entity, EnemyComponent enemy, MovementComponent movement) {
        if (enemy.isDead) {
            return AnimationComponent.State.DEATH;
        }
        if (enemy.hitStun.isActive()) {
            return AnimationComponent.State.HURT;
        }
        if (enemy.postHitIdle.isActive()) {
            return AnimationComponent.State.IDLE;
        }
        EnemyAttackComponent attack = ENEMY_ATTACK.get(entity);
        if (attack != null && attack.isAttacking) {
            return AnimationComponent.State.ATTACKING;
        }
        if (Math.abs(movement.velocity.x) > 0.01f) {
            return AnimationComponent.State.WALKING;
        }
        return AnimationComponent.State.IDLE;
    }
}
