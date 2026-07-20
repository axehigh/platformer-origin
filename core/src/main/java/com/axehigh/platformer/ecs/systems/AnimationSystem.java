package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.ecs.components.Mappers.ANIMATION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TEXTURE;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/** Advances the current animation state timer and updates the visible TextureRegion. */
public class AnimationSystem extends IteratingSystem {

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
        }

        if (animationComponent.currentState != animationComponent.previousState) {
            animationComponent.stateTime = 0f;
            animationComponent.previousState = animationComponent.currentState;
        } else {
            animationComponent.stateTime += deltaTime;
        }

        Animation<TextureRegion> animation = animationComponent.animations.get(animationComponent.currentState);
        if (animation != null) {
            textureComponent.region = animation.getKeyFrame(animationComponent.stateTime, true);
        }
    }

    private AnimationComponent.State resolvePlayerState(PlayerComponent player, MovementComponent movement) {
        if (player.meleeAttackTimer > 0f) {
            return AnimationComponent.State.ATTACKING;
        }
        if (player.isWallClimbing) {
            return AnimationComponent.State.WALL_CLIMBING;
        }
        if (!movement.grounded) {
            return player.jumpCount >= 2 ? AnimationComponent.State.DOUBLE_JUMPING : AnimationComponent.State.JUMPING;
        }
        if (Math.abs(movement.velocity.x) > 0.01f) {
            return AnimationComponent.State.RUNNING;
        }
        return AnimationComponent.State.IDLE;
    }
}
