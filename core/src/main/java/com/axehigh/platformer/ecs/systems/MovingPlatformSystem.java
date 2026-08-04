package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.MovingPlatformComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.MOVING_PLATFORM;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Drives scripted moving platforms (Tiled {@code type="platform"} objects): each platform
 * oscillates around its spawn position as {@code base + amplitude * sin(angle + phase)} per axis,
 * and the player is made to ride it.
 * <p>
 * Runs <b>after</b> {@code MovementSystem}, so the player's own gravity/collision has already
 * been applied this frame. For each platform it then: (1) moves the platform and records the
 * per-frame delta, (2) snaps the player up onto the platform's top if the player's feet are inside
 * a landing band (one-way — landing is top-only; jumping up through is not caught), spawning the
 * same landing smoke as a ground landing when the snap catches a fall from a jump, and (3) if the
 * player is standing on it, carries the player by the platform's delta and clamps the player out
 * of any overlapped static {@code collisionRects} so a platform pushing the player into a wall
 * stops the player while the platform keeps going.
 * <p>
 * Platforms honor the same room-activation rule as enemies: a platform with a non-{-1}
 * {@code roomIndex} only moves while its room is {@code RoomState.activeRoomIndex} (the player can
 * still land on a frozen platform).
 */
public class MovingPlatformSystem extends IteratingSystem {
    /** Extra room given to the landing band beyond the distance fallen this frame. */
    private static final float SNAP_EXTRA_TOLERANCE = 1f;

    private final Array<Rectangle> collisionRects;
    private final RoomState roomState;

    private ImmutableArray<Entity> players;
    private PooledEngine engine;
    private float unitScale = 1f;

    public MovingPlatformSystem(Array<Rectangle> collisionRects, RoomState roomState) {
        this(collisionRects, roomState, 0);
    }

    public MovingPlatformSystem(Array<Rectangle> collisionRects, RoomState roomState, int priority) {
        super(Family.all(TransformComponent.class, CollisionComponent.class, MovingPlatformComponent.class).get(), priority);
        this.collisionRects = collisionRects;
        this.roomState = roomState;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class, MovementComponent.class).get());
        if (engine instanceof PooledEngine) {
            this.engine = (PooledEngine) engine;
        }
    }

    @Override
    protected void processEntity(Entity platformEntity, float deltaTime) {
        MovingPlatformComponent platform = MOVING_PLATFORM.get(platformEntity);
        TransformComponent platformTransform = TRANSFORM.get(platformEntity);
        CollisionComponent platformCollision = COLLISION.get(platformEntity);

        float oldX = platformTransform.position.x;
        float oldY = platformTransform.position.y;

        if (isActive(platform)) {
            platform.angle += platform.speed * deltaTime;
            float offset = platform.angle + platform.phase;
            platformTransform.position.set(
                platform.baseX + platform.amplitudeX * MathUtils.sin(offset),
                platform.baseY + platform.amplitudeY * MathUtils.sin(offset));
        }
        float deltaX = platformTransform.position.x - oldX;
        float deltaY = platformTransform.position.y - oldY;
        platformCollision.updateWorldBounds(platformTransform.position);

        if (players.size() == 0) {
            return;
        }

        Entity playerEntity = players.first();
        TransformComponent playerTransform = TRANSFORM.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        MovementComponent playerMovement = MOVEMENT.get(playerEntity);
        PlayerComponent player = PLAYER.get(playerEntity);

        playerCollision.updateWorldBounds(playerTransform.position);
        Rectangle playerBounds = playerCollision.worldBounds;
        Rectangle platformBounds = platformCollision.worldBounds;

        if (overlapsHorizontally(playerBounds, platformBounds)
            && isWithinLandingBand(playerMovement, playerBounds, platformBounds, deltaTime, deltaY)
            && playerMovement.velocity.y <= 0f) {
            // Carry: move the player by the platform's per-frame delta, then snap the feet exactly
            // onto the (already moved) platform top. Carrying before snapping avoids double-counting
            // the vertical delta when the platform itself moves up/down.
            playerTransform.position.add(deltaX, deltaY);
            playerCollision.updateWorldBounds(playerTransform.position);
            playerBounds = playerCollision.worldBounds;
            playerTransform.position.y += platformBounds.y + platformBounds.height - playerBounds.y;
            MovementSystem.onLanding(engine, player, playerTransform, playerCollision, playerMovement.velocity.y, playerMovement.maxSpeedY, unitScale);
            playerMovement.grounded = true;
            playerMovement.velocity.y = 0f;
            player.jumpCount = 0;
            player.isWallClimbing = false;

            playerCollision.updateWorldBounds(playerTransform.position);
            clampPlayerOutOfStaticCollision(playerTransform, playerCollision);
        }
    }

    private boolean isActive(MovingPlatformComponent platform) {
        return platform.roomIndex < 0 || platform.roomIndex == roomState.activeRoomIndex;
    }

    private boolean overlapsHorizontally(Rectangle playerBounds, Rectangle platformBounds) {
        return playerBounds.x < platformBounds.x + platformBounds.width
            && playerBounds.x + playerBounds.width > platformBounds.x;
    }

    /**
     * The player's feet are "on" the platform when they are inside a band around the platform's
     * top surface. The band is at least {@link #SNAP_EXTRA_TOLERANCE} wide and widens to cover the
     * distance fallen this frame, so a fast fall into a thin platform is still caught. It also
     * widens by the platform's own per-frame vertical travel, because the player is re-dropped by
     * gravity every frame in {@code MovementSystem} (which runs before this system and has no
     * knowledge of platforms): without that extra tolerance a vertically-moving platform travelling
     * faster than the player's per-frame fall would drift out of the band and the player would fall
     * straight through it.
     */
    private boolean isWithinLandingBand(MovementComponent playerMovement, Rectangle playerBounds, Rectangle platformBounds, float deltaTime, float platformDeltaY) {
        float fallDistance = Math.max(0f, -playerMovement.velocity.y * deltaTime);
        float tolerance = SNAP_EXTRA_TOLERANCE + fallDistance + Math.abs(platformDeltaY);
        float feetY = playerBounds.y;
        float platformTopY = platformBounds.y + platformBounds.height;
        return feetY <= platformTopY + tolerance && feetY >= platformTopY - tolerance;
    }

    /**
     * Pushes the player out of any overlapped static map rectangle along the shallowest axis, so a
     * carried player never ends up embedded in a wall. Static rects are re-checked until clean
     * because a single push-out can land the player on top of a neighbouring rect.
     */
    private void clampPlayerOutOfStaticCollision(TransformComponent playerTransform, CollisionComponent playerCollision) {
        Rectangle playerBounds = playerCollision.worldBounds;
        boolean adjusted = true;
        int passes = 0;
        while (adjusted && passes++ < 8) {
            adjusted = false;
            for (Rectangle rect : collisionRects) {
                if (!playerBounds.overlaps(rect)) {
                    continue;
                }
                float pushToRight = rect.x + rect.width - playerBounds.x;
                float pushToLeft = playerBounds.x + playerBounds.width - rect.x;
                float pushUp = rect.y + rect.height - playerBounds.y;
                float pushDown = playerBounds.y + playerBounds.height - rect.y;
                float minHorizontal = Math.min(pushToRight, pushToLeft);
                float minVertical = Math.min(pushUp, pushDown);
                if (minHorizontal < minVertical) {
                    playerTransform.position.x += pushToRight < pushToLeft ? pushToRight : -pushToLeft;
                } else {
                    playerTransform.position.y += pushUp < pushDown ? pushUp : -pushDown;
                }
                playerCollision.updateWorldBounds(playerTransform.position);
                playerBounds = playerCollision.worldBounds;
                adjusted = true;
            }
        }
    }
}
