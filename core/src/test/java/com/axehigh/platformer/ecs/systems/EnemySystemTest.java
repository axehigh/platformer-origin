package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent.AiMode;
import com.axehigh.platformer.ecs.components.FlyingEnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Headless unit tests for {@code EnemySystem}: patrol velocity, range/ledge/wall turn-arounds,
 * hazard avoidance, turn-pause behavior, hit-stun and post-hit-idle pauses, room-based freezing,
 * flying bob, the death-timer removal, and the death coin drop. A thin floor rect keeps the ledge
 * probe satisfied so tests can isolate each turn trigger.
 */
public class EnemySystemTest extends SystemTestBase {

    /** Thin floor just below foot level covering the probe ahead; never overlaps the enemy box. */
    private static final Rectangle FLOOR = new Rectangle(-100f, -24f, 300f, 2f);

    private final Array<Rectangle> collisionRects = new Array<>();
    private final Array<Rectangle> hazardRects = new Array<>();
    private final RoomState roomState = new RoomState();
    private final EntityFactory entityFactory = mock(EntityFactory.class);
    private Engine engine;
    private EnemySystem system;

    @Before
    public void setUp() {
        system = new EnemySystem(entityFactory, collisionRects, hazardRects, roomState);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        MovementComponent movement = movement();
        movement.grounded = true;
        EnemyComponent enemyComponent = new EnemyComponent();
        enemyComponent.originX = x;
        Entity entity = entity(transform, movement, collision, enemyComponent);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void patrolMovesInDirection() {
        collisionRects.add(FLOOR);
        Entity entity = enemy(0f, 0f);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals(20f, movement.velocity.x, EPSILON);
        assertEquals(1, ENEMY.get(entity).direction);
    }

    @Test
    public void turnsAroundAtPatrolRange() {
        collisionRects.add(FLOOR);
        Entity entity = enemy(80f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.originX = 0f;
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals(-1, enemyComponent.direction);
        assertTrue(enemyComponent.turnPause.isActive());
        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void turnsAroundWhenGroundedAtLedge() {
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals(-1, enemyComponent.direction);
    }

    @Test
    public void sideToSideIgnoresPatrolRange() {
        collisionRects.add(FLOOR);
        Entity entity = enemy(100f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.aiMode = AiMode.SIDE_TO_SIDE;
        enemyComponent.originX = 0f;
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals(1, enemyComponent.direction);
        assertEquals(20f, movement.velocity.x, EPSILON);
    }

    @Test
    public void sideToSideTurnsBeforeHazard() {
        collisionRects.add(FLOOR);
        hazardRects.add(new Rectangle(16f, -20f, 10f, 40f));
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.aiMode = AiMode.SIDE_TO_SIDE;
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals(-1, enemyComponent.direction);
        assertTrue(enemyComponent.turnPause.isActive());
        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void defaultModeTurnsBeforeHazard() {
        collisionRects.add(FLOOR);
        hazardRects.add(new Rectangle(16f, -20f, 10f, 40f));
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals(-1, enemyComponent.direction);
        assertTrue(enemyComponent.turnPause.isActive());
    }

    @Test
    public void turnPauseStopsEnemyAfterTurn() {
        collisionRects.add(new Rectangle(-30f, -24f, 30f, 2f));
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals(-1, enemyComponent.direction);
        assertTrue(enemyComponent.turnPause.isActive());
        assertEquals(0f, movement.velocity.x, EPSILON);

        engine.update(0.5f);

        assertFalse(enemyComponent.turnPause.isActive());
        assertEquals(-1, enemyComponent.direction);
        assertEquals(-20f, movement.velocity.x, EPSILON);
    }

    @Test
    public void hitStunSkipsPatrol() {
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 50f;
        enemyComponent.hitStun.start(0.5f);

        engine.update(DT);

        assertEquals(50f, movement.velocity.x, EPSILON);
        assertTrue(enemyComponent.hitStun.isActive());
    }

    @Test
    public void postHitIdlePausesBeforeResumingPatrol() {
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 50f;
        enemyComponent.hitStun.start(0.1f);

        engine.update(0.2f);

        assertTrue(enemyComponent.postHitIdle.isActive());
        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void inactiveRoomFreezesEnemy() {
        roomState.activeRoomIndex = 0;
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.roomIndex = 1;
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 30f;
        movement.velocity.y = 10f;
        entity.add(new FlyingEnemyComponent());

        engine.update(DT);

        assertEquals(0f, movement.velocity.x, EPSILON);
        assertEquals(0f, movement.velocity.y, EPSILON);
    }

    @Test
    public void flyingEnemyBobsVertically() {
        Entity entity = enemy(0f, 0f);
        FlyingEnemyComponent flying = new FlyingEnemyComponent();
        flying.bobAmplitude = 10f;
        flying.bobFrequency = MathUtils.PI;
        entity.add(flying);
        MovementComponent movement = MOVEMENT.get(entity);

        engine.update(DT);

        float expected = 10f * MathUtils.PI * MathUtils.cos(MathUtils.PI * DT);
        assertEquals(expected, movement.velocity.y, 0.05f);
    }

    @Test
    public void deadEnemyWaitsForDeathTimer() {
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.isDead = true;
        enemyComponent.deathTimer.start(1f);

        engine.update(DT);

        assertEquals(1, engine.getEntities().size());
        assertTrue(enemyComponent.deathTimer.isActive());
    }

    @Test
    public void deadEnemyIsRemovedAfterDeathTimer() {
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.isDead = true;
        enemyComponent.deathTimer.start(0.1f);

        engine.update(0.2f);

        assertEquals(0, engine.getEntities().size());
    }

    @Test
    public void deadEnemyDropsCoinsOnRemoval() {
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.isDead = true;
        enemyComponent.deathTimer.start(0.1f);
        enemyComponent.maxHealth = 10f;

        engine.update(0.2f);

        assertEquals(0, engine.getEntities().size());
        verify(entityFactory).popCoins(eq(engine), anyFloat(), anyFloat(), eq(2), eq(1f));
    }

    @Test
    public void deadEnemyWithTooLittleHealthDropsNoCoins() {
        Entity entity = enemy(0f, 0f);
        EnemyComponent enemyComponent = ENEMY.get(entity);
        enemyComponent.isDead = true;
        enemyComponent.deathTimer.start(0.1f);
        enemyComponent.maxHealth = 3f;

        engine.update(0.2f);

        assertEquals(0, engine.getEntities().size());
        verify(entityFactory, never()).popCoins(any(), anyFloat(), anyFloat(), anyInt(), anyFloat());
    }
}
