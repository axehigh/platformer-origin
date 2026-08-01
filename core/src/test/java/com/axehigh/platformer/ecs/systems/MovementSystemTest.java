package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.FlyingEnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PoppedItemComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for {@code MovementSystem}: gravity integration, velocity clamping, AABB
 * collision resolution against the static map rects (wall stop + floor landing), the player's
 * jump-count reset / wall-climb latch, popped-item ground freeze, and the flying gravity opt-out.
 * Uses {@code transform.scale.x = 0} for the player so the dynamic offset lerp keeps the collision
 * box stationary and positions stay exactly predictable.
 */
public class MovementSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private Engine engine;
    private MovementSystem system;

    @Before
    public void setUp() {
        system = new MovementSystem(collisionRects);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        transform.scale.x = 0f;
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), movement(), collision);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void gravityPullsNonFlyingEntityDown() {
        Entity entity = player(0f, 130f);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);

        engine.update(DT);

        assertEquals(-600f * DT, movement.velocity.y, EPSILON);
        assertEquals(130f - 600f * DT * DT, transform.position.y, EPSILON);
        assertFalse(movement.grounded);
    }

    @Test
    public void horizontalVelocityIsClampedToMaxSpeed() {
        Entity entity = player(0f, 130f);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 500f;

        engine.update(DT);

        assertEquals(GameConstants.MaxSpeedX, movement.velocity.x, EPSILON);
    }

    @Test
    public void wallStopsHorizontalMovement() {
        collisionRects.add(new Rectangle(100f, 0f, 50f, 300f));
        Entity entity = player(85f, 130f);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        movement.velocity.x = 100f;

        engine.update(DT);

        assertEquals(85f, transform.position.x, EPSILON);
        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void floorStopsFallGroundsAndResetsJumpCount() {
        collisionRects.add(new Rectangle(0f, 0f, 300f, 100f));
        Entity entity = player(0f, 130f);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        PlayerComponent player = PLAYER.get(entity);
        player.jumpCount = 2;

        engine.update(DT);

        assertTrue(movement.grounded);
        assertEquals(130f, transform.position.y, EPSILON);
        assertEquals(0f, movement.velocity.y, EPSILON);
        assertEquals(0, player.jumpCount);
    }

    @Test
    public void wallClimbLatchesWhileAirborne() {
        collisionRects.add(new Rectangle(100f, 0f, 50f, 300f));
        Entity entity = player(85f, 130f);
        MovementComponent movement = MOVEMENT.get(entity);
        PlayerComponent player = PLAYER.get(entity);
        movement.velocity.x = 100f;

        engine.update(DT);

        assertFalse(movement.grounded);
        assertTrue(player.isWallClimbing);
        assertEquals(1, player.jumpCount);
    }

    @Test
    public void poppedItemFreezesOnLanding() {
        collisionRects.add(new Rectangle(0f, 0f, 300f, 100f));
        TransformComponent transform = transform(0f, 130f);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, 0f, 130f);
        MovementComponent movement = movement();
        movement.velocity.x = 50f;
        Entity entity = entity(transform, movement, collision, new PoppedItemComponent());
        engine.addEntity(entity);

        engine.update(DT);

        assertTrue(movement.grounded);
        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void flyingEnemySkipsGravity() {
        TransformComponent transform = transform(0f, 130f);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, 0f, 130f);
        MovementComponent movement = movement();
        movement.velocity.y = 5f;
        Entity entity = entity(transform, movement, collision, new FlyingEnemyComponent());
        engine.addEntity(entity);

        engine.update(DT);

        assertEquals(5f, movement.velocity.y, EPSILON);
    }

    @Test
    public void landingAfterJumpSpawnsSmokePuff() {
        PooledEngine pooled = new PooledEngine();
        MovementSystem pooledSystem = new MovementSystem(collisionRects);
        pooledSystem.setUnitScale(1f);
        pooled.addSystem(pooledSystem);

        collisionRects.add(new Rectangle(0f, 0f, 300f, 100f));
        TransformComponent transform = transform(0f, 130f);
        transform.scale.x = 0f;
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, 0f, 130f);
        MovementComponent movement = movement();
        PlayerComponent player = player();
        player.jumpCount = 2;
        pooled.addEntity(entity(transform, movement, collision, player));

        pooled.update(DT);

        assertEquals(1, particleEntityCount(pooled));
    }

    @Test
    public void landingAfterLedgeFallSpawnsNoSmoke() {
        PooledEngine pooled = new PooledEngine();
        MovementSystem pooledSystem = new MovementSystem(collisionRects);
        pooledSystem.setUnitScale(1f);
        pooled.addSystem(pooledSystem);

        collisionRects.add(new Rectangle(0f, 0f, 300f, 100f));
        TransformComponent transform = transform(0f, 130f);
        transform.scale.x = 0f;
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, 0f, 130f);
        MovementComponent movement = movement();
        PlayerComponent player = player();
        player.jumpCount = 0;
        pooled.addEntity(entity(transform, movement, collision, player));

        pooled.update(DT);

        assertEquals(0, particleEntityCount(pooled));
    }

    @Test
    public void landingPuffScalesWithFallSpeed() {
        PooledEngine pooled = new PooledEngine();
        MovementSystem pooledSystem = new MovementSystem(collisionRects);
        pooledSystem.setUnitScale(1f);
        pooled.addSystem(pooledSystem);

        collisionRects.add(new Rectangle(0f, 0f, 300f, 100f));
        TransformComponent transform = transform(0f, 130f);
        transform.scale.x = 0f;
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, 0f, 130f);
        MovementComponent movement = movement();
        movement.velocity.y = -GameConstants.MaxSpeedY;
        PlayerComponent player = player();
        player.jumpCount = 2;
        pooled.addEntity(entity(transform, movement, collision, player));

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
