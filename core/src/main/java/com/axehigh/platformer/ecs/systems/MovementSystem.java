package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
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

import static com.axehigh.platformer.ecs.components.Mappers.*;

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
    /**
     * Minimum fall distance (world units at 1x scale) before landing dust spawns: one tile. At the
     * 16px base tile size this is 16 world units; multiplied by {@code unitScale} for larger tiles.
     */
    private static final float LANDING_DUST_MIN_FALL = 16f;
    /** Tolerances a falling player's pre-move feet position against a one-way platform's top before a top-only landing sticks. */
    private static final float ONE_WAY_LANDING_EPSILON = 0.5f;
    /**
     * Maximum vertical displacement handled in one collision sub-step. Keeps the AABB from
     * tunneling through 128-unit-thick solid floors when a frame hitch produces a huge delta
     * (e.g. Android first frame ~0.3 s).
     */
    private static final float MAX_Y_STEP = 8f;

    private final Array<Rectangle> collisionRects;
    private final Array<Rectangle> oneWayRects;
    private final Rectangle entityBounds = new Rectangle();
    private PooledEngine engine;
    private float unitScale = 1f;

    public MovementSystem(Array<Rectangle> collisionRects) {
        this(collisionRects, new Array<>(), 0);
    }

    public MovementSystem(Array<Rectangle> collisionRects, int priority) {
        this(collisionRects, new Array<>(), priority);
    }

    public MovementSystem(Array<Rectangle> collisionRects, Array<Rectangle> oneWayRects) {
        this(collisionRects, oneWayRects, 0);
    }

    public MovementSystem(Array<Rectangle> collisionRects, Array<Rectangle> oneWayRects, int priority) {
        // Bullets are excluded: CollisionSystem owns their integration/collision/lifetime handling.
        super(Family.all(TransformComponent.class, MovementComponent.class, CollisionComponent.class)
            .exclude(BulletComponent.class).get(), priority);
        this.collisionRects = collisionRects;
        this.oneWayRects = oneWayRects;
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
        boolean hitWallX = moveX(transform, movement, collision, deltaTime, entity);
        float fallSpeed = movement.velocity.y;
        moveY(transform, movement, collision, deltaTime, entity);

        // Popped pickups (e.g. chest-dropped coins) have no ground friction of their own; freeze
        // them dead in place the moment they first touch down instead of sliding indefinitely.
        if (movement.grounded && POPPED_ITEM.get(entity) != null) {
            movement.velocity.x = 0f;
        }

        if (player != null) {
            if (movement.grounded) {
                onLanding(player, transform, collision, fallSpeed, movement.maxSpeedY);
                player.jumpCount = 0;
                player.isWallClimbing = false;
            } else {
                float feetY = transform.position.y + collision.bounds.y;
                if (!player.inAir) {
                    player.inAir = true;
                    player.maxAirHeight = feetY;
                } else if (feetY > player.maxAirHeight) {
                    player.maxAirHeight = feetY;
                }
                if (player.hurtTimer.isActive() || player.isDead) {
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
    }

    /**
     * Landing callback for the player: spawns landing dust only when the fall exceeded one tile,
     * and triggers the landing squash for jump landings. Resets the airborne tracking. Shared with
     * {@code MovingPlatformSystem} so every landing surface behaves identically.
     */
    private void onLanding(PlayerComponent player, TransformComponent transform, CollisionComponent collision, float fallSpeed, float maxSpeedY) {
        onLanding(engine, player, transform, collision, fallSpeed, maxSpeedY, unitScale);
    }

    static void onLanding(PooledEngine engine, PlayerComponent player, TransformComponent transform, CollisionComponent collision, float fallSpeed, float maxSpeedY, float unitScale) {
        if (!player.inAir) {
            return;
        }
        float feetY = transform.position.y + collision.bounds.y;
        float fallDistance = player.maxAirHeight - feetY;
        if (fallDistance > LANDING_DUST_MIN_FALL * unitScale) {
            spawnLandingSmoke(engine, transform, collision, fallSpeed, maxSpeedY);
        }
        if (player.jumpCount > 0 && FeatureFlags.isSquashEnabled()) {
            SquashSystem.trigger(player, transform, false);
        }
        player.inAir = false;
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
    private boolean moveX(TransformComponent transform, MovementComponent movement, CollisionComponent collision, float deltaTime, Entity entity) {
        float deltaX = movement.velocity.x * deltaTime;
        if (deltaX == 0f) {
            return false;
        }

        float newX = transform.position.x + deltaX;
        entityBounds.set(newX + collision.bounds.x, transform.position.y + collision.bounds.y, collision.bounds.width, collision.bounds.height);

        Rectangle hit = findCollision(entityBounds);
        if (hit == null && isOneWaySolid(entity)) {
            hit = findCollision(entityBounds, oneWayRects);
        }
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

    private void moveY(TransformComponent transform, MovementComponent movement, CollisionComponent collision, float deltaTime, Entity entity) {
        float totalDeltaY = movement.velocity.y * deltaTime;
        movement.grounded = false;

        PlayerComponent player = PLAYER.get(entity);
        boolean landedOnOneWay = false;

        // Sub-step vertical movement so a single large delta (e.g. Android's first frame) cannot
        // leap through a solid floor. Each step is small enough that the destination AABB must
        // overlap anything it would have passed through.
        float remaining = totalDeltaY;
        int stepsLeft = 64;
        while (remaining != 0f && stepsLeft-- > 0) {
            float step = MathUtils.clamp(remaining, -MAX_Y_STEP, MAX_Y_STEP);
            if (step == 0f) {
                break;
            }
            float newY = transform.position.y + step;
            entityBounds.set(transform.position.x + collision.bounds.x, newY + collision.bounds.y, collision.bounds.width, collision.bounds.height);

            Rectangle hit = findCollision(entityBounds);
            if (hit == null) {
                if (player != null) {
                    hit = findOneWayCollision(transform, movement, collision, deltaTime, player);
                    landedOnOneWay = hit != null;
                } else if (isOneWaySolid(entity)) {
                    hit = findCollision(entityBounds, oneWayRects);
                }
            }

            if (hit != null) {
                if (step < 0f) {
                    transform.position.y = hit.y + hit.height - collision.bounds.y;
                    movement.grounded = true;
                } else if (step > 0f) {
                    transform.position.y = hit.y - collision.bounds.height - collision.bounds.y;
                }
                movement.velocity.y = 0f;
                if (player != null) {
                    player.onDropTile = landedOnOneWay;
                }
                return;
            }

            transform.position.y = newY;
            remaining -= step;
        }

        if (player != null) {
            player.onDropTile = false;
        }
    }

    /**
     * Finds a drop-through platform the player lands on. One-way platforms are top-only **for the
     * player**: only the player can stand on them (and only while falling, {@code deltaY < 0} —
     * rising through them is always allowed, jump up-through), and a standing player who has just
     * pressed drop passes straight through while the {@code dropWindow} is active. For everyone
     * else, one-way tiles are resolved as fully solid by {@code moveX}/{@code moveY} via
     * {@link #findCollision(Rectangle, Array)} (except flying enemies, which pass through entirely).
     * Landing sticks only when the player's feet were at or above the platform's top before this
     * move, so the sides and underside never block. Returns the rect landed on, or null (fall through).
     */
    private Rectangle findOneWayCollision(TransformComponent transform, MovementComponent movement, CollisionComponent collision, float deltaTime, PlayerComponent player) {
        if (movement.velocity.y >= 0f || player.dropWindow.isActive()) {
            return null;
        }
        float feetY = transform.position.y + collision.bounds.y;
        for (Rectangle rect : oneWayRects) {
            if (feetY >= rect.y + rect.height - ONE_WAY_LANDING_EPSILON && entityBounds.overlaps(rect)) {
                return rect;
            }
        }
        return null;
    }

    /**
     * Whether {@code oneWay} rects act as fully-solid tiles for this entity: everyone except the
     * player (who keeps the drop-through one-way behavior) and flying enemies (which pass through,
     * keeping them airborne). Grounded enemies and popped coins therefore treat one-way tiles like
     * any other solid tile — they can stand on them and are blocked by their sides and underside.
     */
    private boolean isOneWaySolid(Entity entity) {
        return PLAYER.get(entity) == null && FLYING.get(entity) == null;
    }

    private Rectangle findCollision(Rectangle bounds) {
        return findCollision(bounds, collisionRects);
    }

    private Rectangle findCollision(Rectangle bounds, Array<Rectangle> rects) {
        for (Rectangle rect : rects) {
            if (bounds.overlaps(rect)) {
                return rect;
            }
        }
        return null;
    }
}
