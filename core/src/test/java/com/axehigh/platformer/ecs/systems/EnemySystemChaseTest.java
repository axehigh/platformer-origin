package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Headless unit tests for {@code EnemySystem}'s autonomous CHASE behavior: a melee-capable enemy
 * (one carrying an {@code EnemyAttackComponent}) that has detected the player (same centered
 * detection box as {@code EnemyAttackSystem}: {@code attackRange*3} per side, {@code detectionHeight}
 * tall) stops patrolling and instead faces and moves toward the player, holding in place —
 * without turning around — at a wall/ledge/hazard. The chase is autonomous (this engine does NOT
 * include EnemyAttackSystem), so only EnemySystem drives these assertions.
 */
public class EnemySystemChaseTest extends SystemTestBase {

    /** Thin floor just below foot level satisfying the ledge probe; never overlaps the enemy box. */
    private static final Rectangle FLOOR = new Rectangle(-100f, -24f, 300f, 2f);

    private final Array<Rectangle> collisionRects = new Array<>();
    private final Array<Rectangle> oneWayRects = new Array<>();
    private final Array<Rectangle> hazardRects = new Array<>();
    private final RoomState roomState = new RoomState();
    private final EntityFactory entityFactory = mock(EntityFactory.class);
    private Engine engine;
    private EnemySystem system;

    @Before
    public void setUp() {
        system = new EnemySystem(entityFactory, collisionRects, oneWayRects, hazardRects, roomState);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), new com.axehigh.platformer.ecs.components.PlayerComponent(), collision);
        engine.addEntity(entity);
        return entity;
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        MovementComponent movement = movement();
        movement.grounded = true;
        EnemyComponent enemyComponent = new EnemyComponent();
        enemyComponent.originX = x;
        Entity entity = entity(transform, movement, collision, enemyComponent, new EnemyAttackComponent());
        engine.addEntity(entity);
        return entity;
    }

    private Entity flyer(float x, float y) {
        Entity entity = enemy(x, y);
        FlyingEnemyComponent flying = new FlyingEnemyComponent();
        flying.bobAmplitude = 10f;
        flying.bobFrequency = MathUtils.PI;
        entity.add(flying);
        MOVEMENT.get(entity).grounded = false;
        return entity;
    }

    @Test
    public void chaseMovesTowardDetectedPlayer() {
        collisionRects.add(FLOOR);
        Entity enemyEntity = enemy(0f, 0f);
        player(20f, 0f);
        EnemyComponent enemyComp = ENEMY.get(enemyEntity);
        MovementComponent movement = MOVEMENT.get(enemyEntity);
        enemyComp.direction = -1; // facing away — chase must re-face toward the player
        movement.velocity.x = 20f; // nonzero so the frame isn't misread as a wall block

        engine.update(DT);

        assertEquals("chase should face the player (right)", 1, enemyComp.direction);
        assertEquals("chase should move toward the player at full speed", enemyComp.speed, movement.velocity.x, EPSILON);
    }

    @Test
    public void chaseStopsWhenPlayerLeavesDetection() {
        collisionRects.add(FLOOR);
        Entity enemyEntity = enemy(0f, 0f);
        Entity playerEntity = player(20f, 0f);
        EnemyComponent enemyComp = ENEMY.get(enemyEntity);
        MovementComponent movement = MOVEMENT.get(enemyEntity);
        enemyComp.direction = -1;
        movement.velocity.x = 20f;

        engine.update(DT);
        assertEquals("chase active while detected", 1, enemyComp.direction);

        // Player drops out of the detection box (dx > 72); enemy resumes PATROL.
        // Put the enemy far past its patrolRange so a normal patrol would turn at range —
        // proving the chase (which would keep steering toward the player without turning) is off.
        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 300f, 0f);
        enemyComp.originX = -100f; // x=0 exceeds originX + patrolRange(64) = -36
        enemyComp.direction = 1;
        movement.velocity.x = 20f;

        engine.update(DT);

        assertEquals("patrol should flip at range once no longer chasing", -1, enemyComp.direction);
        assertTrue("patrol turn should start a turn pause", enemyComp.turnPause.isActive());
        assertEquals("patrol turn should stop movement", 0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void chaseHoldsAtWallWithoutTurning() {
        Entity enemyEntity = enemy(0f, 0f);
        player(60f, 0f); // within the box (dx=60 <= 72), far right
        EnemyComponent enemyComp = ENEMY.get(enemyEntity);
        MovementComponent movement = MOVEMENT.get(enemyEntity);
        enemyComp.direction = 1; // facing the player (right)
        movement.velocity.x = 0f; // wall-block signal: MovementSystem zeroed velocity while grounded

        engine.update(DT);

        assertEquals("wall hold should leave the enemy still", 0f, movement.velocity.x, EPSILON);
        assertEquals("wall hold must NOT turn the enemy away from the player", 1, enemyComp.direction);
        assertFalse("wall hold must not start a turn pause", enemyComp.turnPause.isActive());
    }

    @Test
    public void flyerChasesHorizontallyKeepingBob() {
        Entity flyerEntity = flyer(0f, 0f);
        player(40f, 0f); // within the box (dx=40 <= 72)
        EnemyComponent enemyComp = ENEMY.get(flyerEntity);
        MovementComponent movement = MOVEMENT.get(flyerEntity);

        engine.update(DT);

        assertEquals("flyer should face the player (right)", 1, enemyComp.direction);
        assertEquals("flyer should move toward the player at full speed", enemyComp.speed, movement.velocity.x, EPSILON);
        float expectedBob = 10f * MathUtils.PI * MathUtils.cos(MathUtils.PI * DT);
        assertEquals("flyer keeps its vertical bob while chasing", expectedBob, movement.velocity.y, 0.05f);
    }
}
