package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.FlyingEnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.BULLET;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for {@code CollisionSystem} (player bullets): lifetime expiry, velocity
 * integration, wall impact removal, enemy impact damage + removal, and the knockback direction /
 * flying vertical-hop handling routed through {@code EnemyDamageResolver}.
 */
public class CollisionSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private Engine engine;
    private CollisionSystem system;

    @Before
    public void setUp() {
        system = new CollisionSystem(collisionRects);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity bullet(float x, float y, float velocityX, float lifetime) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        MovementComponent movement = movement();
        movement.velocity.x = velocityX;
        BulletComponent bulletComponent = new BulletComponent();
        bulletComponent.lifetime = lifetime;
        bulletComponent.damage = 10f;
        Entity entity = entity(transform, movement, collision, bulletComponent);
        engine.addEntity(entity);
        return entity;
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), collision, new EnemyComponent());
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void bulletExpiresAfterLifetime() {
        bullet(0f, 0f, 0f, 0.05f);

        engine.update(0.1f);

        assertEquals(0, engine.getEntities().size());
    }

    @Test
    public void bulletIntegratesVelocity() {
        Entity bullet = bullet(0f, 0f, 100f, 1f);

        engine.update(DT);

        assertEquals(100f * DT, TRANSFORM.get(bullet).position.x, EPSILON);
    }

    @Test
    public void bulletIsRemovedOnWallImpact() {
        collisionRects.add(new Rectangle(0f, -5f, 5f, 10f));
        bullet(0f, 0f, 0f, 1f);

        engine.update(DT);

        assertEquals(0, engine.getEntities().size());
    }

    @Test
    public void bulletDamagesEnemyAndIsRemoved() {
        enemy(6f, 5f);
        bullet(0f, 0f, 0f, 1f);

        engine.update(0f);

        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void lethalBulletKillsEnemy() {
        Entity enemy = enemy(6f, 5f);
        bullet(0f, 0f, 0f, 1f);

        engine.update(0f);

        EnemyComponent enemyComponent = ENEMY.get(enemy);
        assertTrue(enemyComponent.isDead);
        assertTrue(enemyComponent.deathTimer.isActive());
    }

    @Test
    public void survivingEnemyGetsHorizontalKnockbackOnlyWhenFlying() {
        Entity enemy = enemy(6f, 5f);
        enemy.add(new FlyingEnemyComponent());
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        MovementComponent enemyMovement = MOVEMENT.get(enemy);
        Entity bullet = bullet(0f, 0f, 0f, 1f);
        BULLET.get(bullet).damage = 5f;

        engine.update(0f);

        assertEquals(5f, enemyComponent.health, EPSILON);
        assertEquals(90f, enemyMovement.velocity.x, EPSILON);
        assertEquals(0f, enemyMovement.velocity.y, EPSILON);
    }

    @Test
    public void groundedEnemyGetsVerticalHopToo() {
        Entity enemy = enemy(6f, 5f);
        MovementComponent enemyMovement = MOVEMENT.get(enemy);
        Entity bullet = bullet(0f, 0f, 0f, 1f);
        BULLET.get(bullet).damage = 5f;

        engine.update(0f);

        assertEquals(90f, enemyMovement.velocity.x, EPSILON);
        assertEquals(140f, enemyMovement.velocity.y, EPSILON);
    }
}
