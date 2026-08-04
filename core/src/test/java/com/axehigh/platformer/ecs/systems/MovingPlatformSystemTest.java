package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.MovingPlatformComponent;
import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.Room;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.MOVING_PLATFORM;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for {@code MovingPlatformSystem}: per-axis sine oscillation around the spawn
 * rect, carrying a standing player, one-way top-only landing (no snap while the player is rising),
 * room-activation freezing, clamping a carried player out of static map geometry, and landing smoke
 * identical to a ground landing (spawned from a jump, absent on a ledge fall, scaled by fall speed).
 */
public class MovingPlatformSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private RoomState roomState;
    private MovingPlatformSystem system;
    private Engine engine;

    @Before
    public void setUp() {
        roomState = new RoomState();
        system = new MovingPlatformSystem(collisionRects, roomState);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        return player(engine, x, y);
    }

    private Entity player(Engine target, float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 20f, 20f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), movement(), collision);
        target.addEntity(entity);
        return entity;
    }

    private Entity platform(float x, float y, float w, float h, float ampX, float ampY, float speed, float phase, int roomIndex) {
        return platform(engine, x, y, w, h, ampX, ampY, speed, phase, roomIndex);
    }

    private Entity platform(Engine target, float x, float y, float w, float h, float ampX, float ampY, float speed, float phase, int roomIndex) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, w, h);
        place(transform, collision, x, y);
        MovingPlatformComponent platform = new MovingPlatformComponent();
        platform.baseX = x;
        platform.baseY = y;
        platform.amplitudeX = ampX;
        platform.amplitudeY = ampY;
        platform.speed = speed;
        platform.phase = phase;
        platform.roomIndex = roomIndex;
        Entity entity = entity(transform, collision, platform);
        target.addEntity(entity);
        return entity;
    }

    @Test
    public void oscillatesHorizontallyAroundSpawn() {
        Entity platformEntity = platform(100f, 50f, 100f, 16f, 40f, 0f, 1f, 0f, -1);
        TransformComponent transform = TRANSFORM.get(platformEntity);

        engine.update(DT);

        assertEquals(100f + 40f * (float) MathUtils.sin(DT), transform.position.x, EPSILON);
        assertEquals(50f, transform.position.y, EPSILON);

        engine.update(DT);

        assertEquals(100f + 40f * (float) MathUtils.sin(2f * DT), transform.position.x, EPSILON);
    }

    @Test
    public void oscillatesVerticallyWithPhase() {
        float phase = (float) Math.PI / 2f;
        Entity platformEntity = platform(100f, 50f, 100f, 16f, 0f, 30f, 2f, phase, -1);
        TransformComponent transform = TRANSFORM.get(platformEntity);

        engine.update(DT);

        float expectedY = 50f + 30f * (float) MathUtils.sin(2f * DT + phase);
        assertEquals(expectedY, transform.position.y, EPSILON);
        assertEquals(100f, transform.position.x, EPSILON);
    }

    @Test
    public void carriesStandingPlayerHorizontally() {
        platform(100f, 50f, 100f, 16f, 40f, 0f, 1f, 0f, -1);
        Entity playerEntity = player(110f, 66f);
        MovementComponent movement = MOVEMENT.get(playerEntity);
        PlayerComponent player = PLAYER.get(playerEntity);
        movement.velocity.y = 0f;
        player.jumpCount = 1;

        engine.update(DT);

        float deltaX = 40f * (float) MathUtils.sin(DT);
        assertEquals(110f + deltaX, TRANSFORM.get(playerEntity).position.x, EPSILON);
        assertEquals(66f, TRANSFORM.get(playerEntity).position.y, EPSILON);
        assertTrue(movement.grounded);
        assertEquals(0, player.jumpCount);
    }

    @Test
    public void carriesStandingPlayerDownWithDescendingPlatform() {
        // angle 2 rad sits on the descending slope of the sine wave (cos(2) < 0).
        Entity platformEntity = platform(100f, 100f, 100f, 16f, 0f, 20f, 1f, 0f, -1);
        MOVING_PLATFORM.get(platformEntity).angle = 2f;
        TransformComponent platformTransform = TRANSFORM.get(platformEntity);
        platformTransform.position.set(100f, 100f + 20f * (float) MathUtils.sin(2f));
        COLLISION.get(platformEntity).updateWorldBounds(platformTransform.position);

        float top = platformTransform.position.y + 16f;
        Entity playerEntity = player(110f, top);
        MOVEMENT.get(playerEntity).velocity.y = 0f;

        engine.update(DT);

        float deltaY = 20f * ((float) MathUtils.sin(2f + DT) - (float) MathUtils.sin(2f));
        assertEquals(top + deltaY, TRANSFORM.get(playerEntity).position.y, EPSILON);
        assertTrue(MOVEMENT.get(playerEntity).grounded);
    }

    @Test
    public void keepsCarryingPlayerThroughFullFastVerticalCycle() {
        // amplitudeY * speed = 120 px/s (2 px/frame), more than the old ~1.17 px landing band,
        // so the player would fall through mid-travel. Run a full up-and-down cycle and assert
        // the player stays glued to the top on every frame.
        platform(100f, 100f, 100f, 16f, 0f, 60f, 2f, 0f, -1);
        Entity playerEntity = player(110f, 116f);
        MOVEMENT.get(playerEntity).velocity.y = 0f;

        for (int i = 0; i < 200; i++) {
            engine.update(DT);

            Entity platformEntity = null;
            for (Entity e : engine.getEntities()) {
                if (MOVING_PLATFORM.get(e) != null) {
                    platformEntity = e;
                    break;
                }
            }
            CollisionComponent platformCollision = COLLISION.get(platformEntity);
            float platformTop = platformCollision.worldBounds.y + platformCollision.worldBounds.height;

            CollisionComponent playerCollision = COLLISION.get(playerEntity);
            float playerFeet = playerCollision.worldBounds.y;

            assertEquals("frame " + i, platformTop, playerFeet, EPSILON);
            assertTrue("frame " + i, MOVEMENT.get(playerEntity).grounded);
        }
    }

    @Test
    public void doesNotSnapRisingPlayer() {
        platform(100f, 50f, 100f, 16f, 0f, 0f, 1f, 0f, -1);
        Entity playerEntity = player(110f, 65f);
        MovementComponent movement = MOVEMENT.get(playerEntity);
        movement.velocity.y = 200f;

        engine.update(DT);

        assertEquals(65f, TRANSFORM.get(playerEntity).position.y, EPSILON);
        assertFalse(movement.grounded);
    }

    @Test
    public void freezesWhenOwnerRoomInactive() {
        roomState.rooms.add(new Room(0f, 0f, 400f, 300f));
        roomState.activeRoomIndex = 0;
        Entity platformEntity = platform(100f, 50f, 100f, 16f, 40f, 0f, 1f, 0f, 0);
        TransformComponent transform = TRANSFORM.get(platformEntity);

        engine.update(DT);

        assertEquals(100f + 40f * (float) MathUtils.sin(DT), transform.position.x, EPSILON);

        roomState.activeRoomIndex = 1;

        engine.update(DT);

        assertEquals(100f + 40f * (float) MathUtils.sin(DT), transform.position.x, EPSILON);
    }

    @Test
    public void playerCanStillLandOnFrozenPlatform() {
        roomState.rooms.add(new Room(0f, 0f, 400f, 300f));
        roomState.activeRoomIndex = 1;
        Entity platformEntity = platform(100f, 50f, 100f, 16f, 40f, 0f, 1f, 0f, 0);
        Entity playerEntity = player(110f, 65f);

        engine.update(DT);

        assertEquals(66f, TRANSFORM.get(playerEntity).position.y, EPSILON);
        assertTrue(MOVEMENT.get(playerEntity).grounded);
        assertEquals(100f, TRANSFORM.get(platformEntity).position.x, EPSILON);
    }

    @Test
    public void clampsCarriedPlayerOutOfWall() {
        collisionRects.add(new Rectangle(130f, 0f, 20f, 300f));
        platform(100f, 50f, 100f, 16f, 40f, 0f, 1f, 0f, -1);
        Entity playerEntity = player(110f, 66f);
        MOVEMENT.get(playerEntity).velocity.y = 0f;

        engine.update(DT);

        // The platform moved right, but the player's right edge is held at the wall face (x = 130).
        assertEquals(110f, TRANSFORM.get(playerEntity).position.x, EPSILON);
    }

    @Test
    public void landingAfterJumpSpawnsSmokePuff() {
        PooledEngine pooled = new PooledEngine();
        pooled.addSystem(new MovingPlatformSystem(collisionRects, roomState));

        platform(pooled, 100f, 50f, 100f, 16f, 0f, 0f, 1f, 0f, -1);
        Entity playerEntity = player(pooled, 110f, 65f);
        MOVEMENT.get(playerEntity).velocity.y = -GameConstants.MaxSpeedY;
        PlayerComponent player = PLAYER.get(playerEntity);
        player.jumpCount = 2;
        player.inAir = true;
        player.maxAirHeight = 66f + 20f;
        pooled.update(DT);

        assertEquals(1, particleEntityCount(pooled));
    }

    @Test
    public void shortFallSpawnsNoSmoke() {
        PooledEngine pooled = new PooledEngine();
        pooled.addSystem(new MovingPlatformSystem(collisionRects, roomState));

        platform(pooled, 100f, 50f, 100f, 16f, 0f, 0f, 1f, 0f, -1);
        Entity playerEntity = player(pooled, 110f, 65f);
        MOVEMENT.get(playerEntity).velocity.y = -GameConstants.MaxSpeedY;
        PlayerComponent player = PLAYER.get(playerEntity);
        player.jumpCount = 0;
        player.inAir = true;
        player.maxAirHeight = 66f + 10f;
        pooled.update(DT);

        assertEquals(0, particleEntityCount(pooled));
    }

    @Test
    public void landingPuffScalesWithFallSpeed() {
        PooledEngine pooled = new PooledEngine();
        pooled.addSystem(new MovingPlatformSystem(collisionRects, roomState));

        platform(pooled, 100f, 50f, 100f, 16f, 0f, 0f, 1f, 0f, -1);
        Entity playerEntity = player(pooled, 110f, 65f);
        MOVEMENT.get(playerEntity).velocity.y = -GameConstants.MaxSpeedY;
        PlayerComponent player = PLAYER.get(playerEntity);
        player.jumpCount = 2;
        player.inAir = true;
        player.maxAirHeight = 66f + 20f;
        pooled.update(DT);

        ParticleComponent pc = firstParticle(pooled);
        assertNotNull(pc);
        assertEquals(10f, pc.scale, EPSILON);
    }

    private static ParticleComponent firstParticle(Engine engine) {
        for (Entity entity : engine.getEntities()) {
            ParticleComponent pc = entity.getComponent(ParticleComponent.class);
            if (pc != null) {
                return pc;
            }
        }
        return null;
    }

    private static int particleEntityCount(Engine engine) {
        int count = 0;
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(ParticleComponent.class) != null) {
                count++;
            }
        }
        return count;
    }
}
