package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.GameConstants.*;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.FLYING;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.POPPED_ITEM;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Integrates velocity into position and resolves AABB collisions against the static map boundary
 * set. Also drives the player-specific jump-count reset and wall-climb latch/release.
 */
public class MovementSystem extends IteratingSystem {
    private static final float GRAVITY = -600f;
    private static final float WALL_SLIDE_GRAVITY = -100f;
    private static final float WALL_SLIDE_MAX_FALL_SPEED = -40f;

    private final Array<Rectangle> collisionRects;
    private final Rectangle entityBounds = new Rectangle();
    private float unitScale = 1f;

    public MovementSystem(Array<Rectangle> collisionRects) {
        this(collisionRects, 0);
    }

    public MovementSystem(Array<Rectangle> collisionRects, int priority) {
        // Bullets are excluded: CollisionSystem owns their integration/collision/lifetime handling.
        super(Family.all(TransformComponent.class, MovementComponent.class, CollisionComponent.class)
            .exclude(BulletComponent.class).get(), priority);
        this.collisionRects = collisionRects;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = TRANSFORM.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        PlayerComponent player = PLAYER.get(entity);

        boolean wallClimbing = player != null && player.isWallClimbing;
        boolean flying = FLYING.get(entity) != null;

        if (player != null) {
            float targetOffset = (player.facingDirection > 0) ? PlayerOffsetRight : PlayerOffsetLeft;
            targetOffset *= Math.abs(transform.scale.x);

            // Smoothly interpolate the offset to avoid "jumping" when turning against walls
            float lerpFactor = 15f * deltaTime;
            collision.currentOffsetX = MathUtils.lerp(collision.currentOffsetX, targetOffset, Math.min(lerpFactor, 1f));

            collision.bounds.setX(collision.baseOffsetX + collision.currentOffsetX);
            collision.bounds.setY(collision.baseOffsetY + collision.currentOffsetY);
        }

        if (!flying) {
            movement.velocity.y += (wallClimbing ? WALL_SLIDE_GRAVITY * unitScale : GRAVITY * unitScale) * deltaTime;
            if (wallClimbing) {
                movement.velocity.y = Math.max(movement.velocity.y, WALL_SLIDE_MAX_FALL_SPEED * unitScale);
            }
        }
        movement.velocity.x = MathUtils.clamp(movement.velocity.x, -movement.maxSpeedX, movement.maxSpeedX);
        movement.velocity.y = MathUtils.clamp(movement.velocity.y, -movement.maxSpeedY, movement.maxSpeedY);

        float attemptedDeltaX = movement.velocity.x;
        boolean hitWallX = moveX(transform, movement, collision, deltaTime);
        moveY(transform, movement, collision, deltaTime);

        // Popped pickups (e.g. chest-dropped coins) have no ground friction of their own; freeze
        // them dead in place the moment they first touch down instead of sliding indefinitely.
        if (movement.grounded && POPPED_ITEM.get(entity) != null) {
            movement.velocity.x = 0f;
        }

        if (player != null) {
            if (movement.grounded) {
                player.jumpCount = 0;
                player.isWallClimbing = false;
            } else if (hitWallX && attemptedDeltaX != 0f) {
                player.isWallClimbing = true;
                player.jumpCount = 1;
            } else if (player.isWallClimbing) {
                player.isWallClimbing = false;
            }
        }
    }

    /**
     * Moves the entity along the X axis, resolving collisions. Returns true if a wall was hit.
     */
    private boolean moveX(TransformComponent transform, MovementComponent movement, CollisionComponent collision, float deltaTime) {
        float deltaX = movement.velocity.x * deltaTime;
        if (deltaX == 0f) {
            return false;
        }

        float newX = transform.position.x + deltaX;
        entityBounds.set(newX + collision.bounds.x, transform.position.y + collision.bounds.y, collision.bounds.width, collision.bounds.height);

        Rectangle hit = findCollision(entityBounds);
        if (hit == null) {
            transform.position.x = newX;
            return false;
        }

        if (deltaX > 0f) {
            transform.position.x = hit.x - collision.bounds.width - collision.bounds.x;
        } else {
            transform.position.x = hit.x + hit.width - collision.bounds.x;
        }
        movement.velocity.x = 0f;
        return true;
    }

    private void moveY(TransformComponent transform, MovementComponent movement, CollisionComponent collision, float deltaTime) {
        float deltaY = movement.velocity.y * deltaTime;
        movement.grounded = false;

        float newY = transform.position.y + deltaY;
        entityBounds.set(transform.position.x + collision.bounds.x, newY + collision.bounds.y, collision.bounds.width, collision.bounds.height);

        Rectangle hit = findCollision(entityBounds);
        if (hit == null) {
            transform.position.y = newY;
            return;
        }

        if (deltaY < 0f) {
            transform.position.y = hit.y + hit.height - collision.bounds.y;
            movement.grounded = true;
        } else if (deltaY > 0f) {
            transform.position.y = hit.y - collision.bounds.height - collision.bounds.y;
        }
        movement.velocity.y = 0f;
    }

    private Rectangle findCollision(Rectangle bounds) {
        for (Rectangle rect : collisionRects) {
            if (bounds.overlaps(rect)) {
                return rect;
            }
        }
        return null;
    }
}
