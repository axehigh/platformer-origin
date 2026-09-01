package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

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
            animationComponent.currentState = resolvePlayerState(player, MOVEMENT.get(entity));

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

    private AnimationComponent.State resolvePlayerState(PlayerComponent player, MovementComponent movement) {
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
        if (Math.abs(movement.velocity.x) > 0.01f) {
            return Math.abs(movement.velocity.x) < 50f ? AnimationComponent.State.WALKING : AnimationComponent.State.RUNNING;
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
