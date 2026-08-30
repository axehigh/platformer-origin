package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for {@code PlayerBulletSystem} (player bullets): lifetime expiry, velocity
 * integration, wall impact removal (after the spawn grace window), enemy impact damage + removal,
 * and the knockback direction / flying vertical-hop handling routed through
 * {@code EnemyDamageResolver}.
 */
public class CollisionSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private Engine engine;
    private PlayerBulletSystem system;

    @Before
    public void setUp() {
        system = new PlayerBulletSystem(collisionRects);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity bullet(float x, float y, float velocityX, float lifetime) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 10f, 10f);
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
        CollisionComponent collision = collision(0f, 0f, 20f, 40f);
        place(transform, collision, x, y);
        EnemyComponent enemyComponent = new EnemyComponent();
        enemyComponent.health = 5f;
        Entity entity = entity(transform, movement(), collision, enemyComponent);
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

        // Steps the bullet well past the spawn-grace window (0.12s) so the wall check can run.
        for (int i = 0; i < 10; i++) {
            engine.update(DT);
        }

        assertEquals(0, engine.getEntities().size());
    }

    @Test
    public void bulletDamagesEnemyAndIsRemoved() {
        Entity enemy = enemy(0f, 0f);
        Entity bullet = bullet(0f, 0f, 0f, 1f);
        BULLET.get(bullet).damage = 2f;

        engine.update(0f);

        assertEquals(1, engine.getEntities().size());
        assertEquals(3f, ENEMY.get(enemy).health, EPSILON);
    }

    @Test
    public void lethalBulletKillsEnemy() {
        Entity enemy = enemy(0f, 0f);
        Entity bullet = bullet(0f, 0f, 0f, 1f);
        BULLET.get(bullet).damage = 10f;

        engine.update(0f);

        EnemyComponent enemyComponent = ENEMY.get(enemy);
        assertTrue(enemyComponent.isDead);
        assertTrue(enemyComponent.deathTimer.isActive());
    }

    @Test
    public void survivingEnemyGetsHorizontalKnockbackOnlyWhenFlying() {
        Entity enemy = enemy(0f, 0f);
        enemy.add(new FlyingEnemyComponent());
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        MovementComponent enemyMovement = MOVEMENT.get(enemy);
        Entity bullet = bullet(0f, 0f, 0f, 1f);
        BULLET.get(bullet).damage = 2f;

        engine.update(0f);

        assertEquals(3f, enemyComponent.health, 0.001f);
        assertEquals(90f, enemyMovement.velocity.x, 0.001f);
        assertEquals(0f, enemyMovement.velocity.y, 0.001f);
    }

    @Test
    public void groundedEnemyGetsVerticalHopToo() {
        Entity enemy = enemy(0f, 0f);
        MovementComponent enemyMovement = MOVEMENT.get(enemy);
        Entity bullet = bullet(0f, 0f, 0f, 1f);
        BULLET.get(bullet).damage = 2f;

        engine.update(0f);

        assertEquals(90f, enemyMovement.velocity.x, 0.001f);
        assertEquals(140f, enemyMovement.velocity.y, 0.001f);
    }

    @Test
    public void bulletHittingWallSpawnsSpark() {
        // Fresh PooledEngine so PlayerBulletSystem captures a PooledEngine and ParticleHelper's
        // headless path can create a dummy ParticleComponent entity on the impact.
        engine = new PooledEngine();
        engine.addSystem(system);
        collisionRects.add(new Rectangle(0f, -5f, 5f, 10f));
        bullet(0f, 0f, 0f, 1f);

        // Step past the spawn-grace window (0.12s) so the wall check runs and spawns the spark.
        for (int i = 0; i < 10; i++) {
            engine.update(DT);
        }

        // Bullet is removed; a spark particle entity remains.
        assertEquals(1, engine.getEntities().size());
        assertTrue(PARTICLE.get(engine.getEntities().get(0)) != null);
    }
}
