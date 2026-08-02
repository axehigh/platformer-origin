package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.particles.ParticleHelper;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.FLYING;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.POPPED_ITEM;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Integrates velocity into position and resolves AABB collisions against the static map boundary
 * set. Also drives the player-specific jump-count reset and wall-climb latch/release, the latter
 * gated on {@code FeatureFlags.isWallClimbingEnabled()} (when wall-climb is disabled the player
 * falls at full gravity along walls and never gains the extra wall-jump latch).
 */
public class MovementSystem extends IteratingSystem {
    private static final float GRAVITY = -600f;
    private static final float WALL_SLIDE_GRAVITY = -100f;
    private static final float WALL_SLIDE_MAX_FALL_SPEED = -40f;
    /** Landing smoke scale range: base size, plus up to this extra based on fall impact speed. */
    private static final float LANDING_SMOKE_BASE_SCALE = 5f;
    private static final float LANDING_SMOKE_EXTRA_SCALE = 5f;

    private final Array<Rectangle> collisionRects;
    private final Rectangle entityBounds = new Rectangle();
    private PooledEngine engine;
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
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        // Headless unit tests use a plain Engine; particle spawns need a PooledEngine.
        if (engine instanceof PooledEngine) {
            this.engine = (PooledEngine) engine;
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = TRANSFORM.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        PlayerComponent player = PLAYER.get(entity);

        boolean wallClimbing = player != null && player.isWallClimbing && FeatureFlags.isWallClimbingEnabled();
        boolean flying = FLYING.get(entity) != null;

        if (player != null) {
            float targetOffset = (player.facingDirection > 0) ? SpriteConstants.PlayerOffsetRight : SpriteConstants.PlayerOffsetLeft;
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
        float fallSpeed = movement.velocity.y;
        moveY(transform, movement, collision, deltaTime);

        // Popped pickups (e.g. chest-dropped coins) have no ground friction of their own; freeze
        // them dead in place the moment they first touch down instead of sliding indefinitely.
        if (movement.grounded && POPPED_ITEM.get(entity) != null) {
            movement.velocity.x = 0f;
        }

        if (player != null) {
            if (movement.grounded) {
                if (player.jumpCount > 0) {
                    spawnLandingSmoke(transform, collision, fallSpeed, movement.maxSpeedY);
                }
                player.jumpCount = 0;
                player.isWallClimbing = false;
            } else if (player.hurtTimer.isActive() || player.isDead) {
                // While hurt, a knockback push into a wall must not fake a wall-grab; a dead
                // player must not latch onto walls either.
                player.isWallClimbing = false;
            } else if (FeatureFlags.isWallClimbingEnabled() && hitWallX && attemptedDeltaX != 0f) {
                player.isWallClimbing = true;
                player.jumpCount = 1;
            } else if (player.isWallClimbing) {
                player.isWallClimbing = false;
            }
        }
    }

    /**
     * Spawns a smoke puff at a random spot under the player's feet when landing from a jump.
     * Only fires on the exact landing frame: {@code player.jumpCount > 0} proves a jump happened,
     * so walking off a ledge and falling back down spawns nothing. The puff scales up with impact
     * speed (a proxy for fall duration): small for short hops, larger for long falls.
     */
    private void spawnLandingSmoke(TransformComponent transform, CollisionComponent collision, float fallSpeed, float maxSpeedY) {
        spawnLandingSmoke(engine, transform, collision, fallSpeed, maxSpeedY);
    }

    /**
     * Shared landing-smoke emitter used by both ground landings (this system) and platform
     * landings ({@code MovingPlatformSystem}), so every landing surface produces identical smoke.
     * Requires a {@link PooledEngine} (particle spawns use it); a no-op under a plain {@code
     * Engine} (e.g. headless tests).
     */
    static void spawnLandingSmoke(PooledEngine engine, TransformComponent transform, CollisionComponent collision, float fallSpeed, float maxSpeedY) {
        if (engine == null) {
            return;
        }
        float feetX = transform.position.x + collision.bounds.x + MathUtils.random(collision.bounds.width);
        float feetY = transform.position.y + collision.bounds.y;
        float fallRatio = (maxSpeedY > 0f) ? MathUtils.clamp(Math.abs(fallSpeed) / maxSpeedY, 0f, 1f) : 0f;
        float scale = LANDING_SMOKE_BASE_SCALE + fallRatio * LANDING_SMOKE_EXTRA_SCALE;
        ParticleHelper.spawnSmallSmoke(engine, feetX, feetY, scale);
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
