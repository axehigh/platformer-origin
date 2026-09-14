package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.FLYING;
import static com.axehigh.platformer.ecs.components.Mappers.PARTICLE;
import static org.junit.Assert.*;

/**
 * Headless tests verifying that {@code EnemyDamageResolver.applyHit} spawns the death-burst
 * VFX (smoke + colored sparks via {@code ParticleHelper.spawnDeathBurst}) on lethal hits,
 * gated by {@code FeatureFlags.isSlashArcEnabled()}.
 */
public class EnemyDamageResolverDeathBurstTest extends SystemTestBase {

    private PooledEngine engine;
    private EnemyComponent enemy;
    private MovementComponent movement;
    private TransformComponent transform;
    private CollisionComponent collision;
    private Entity enemyEntity;

    @Before
    public void setUp() {
        FeatureFlags.setSlashArcEnabled(true);
        engine = new PooledEngine();
        enemy = new EnemyComponent();
        movement = movement();
        transform = transform(100f, 50f);
        collision = collision(0f, 0f, 20f, 20f);
        place(transform, collision, 100f, 50f);
        enemyEntity = entity(enemy, movement, transform, collision);
    }

    @After
    public void tearDown() {
        FeatureFlags.setSlashArcEnabled(true);
    }

    private int countParticles() {
        int count = 0;
        for (Entity e : engine.getEntities()) {
            if (PARTICLE.get(e) != null) count++;
        }
        return count;
    }

    private boolean applyHit(float damage, int direction, boolean isFlying) {
        return EnemyDamageResolver.applyHit(enemyEntity, enemy, movement, damage, direction, isFlying, 1f, engine);
    }

    // ---- Lethal hit spawns death-burst ----

    @Test
    public void lethalHit_spawnsDeathBurst() {
        boolean died = applyHit(10f, 1, false);

        assertTrue(died);
        assertTrue(enemy.isDead);
        // 1 hit spark + 2 death burst (smoke + spark) = 3
        assertEquals("lethal hit must spawn spark + 2 burst particles", 3, countParticles());
    }

    // ---- Surviving hit: only spark, no burst ----

    @Test
    public void survivingHit_spawnsOnlySpark() {
        boolean died = applyHit(5f, 1, false);

        assertFalse(died);
        // 1 hit spark, 0 death burst = 1
        assertEquals("surviving hit must spawn only the spark", 1, countParticles());
    }

    // ---- Flag OFF: no death burst ----

    @Test
    public void lethalHit_flagOff_noDeathBurst() {
        FeatureFlags.setSlashArcEnabled(false);

        boolean died = applyHit(10f, 1, false);

        assertTrue(died);
        assertTrue(enemy.isDead);
        // 1 hit spark, 0 death burst (flag off) = 1
        assertEquals("flag off: lethal hit must NOT spawn burst", 1, countParticles());
    }

    // ---- Null engine (7-arg overload): no crash ----

    @Test
    public void nullEngine_noCrashNoParticles() {
        // 7-arg overload passes null engine — should not throw
        boolean died = EnemyDamageResolver.applyHit(enemyEntity, enemy, movement, 10f, 1, false, 1f);

        assertTrue(died);
        assertTrue(enemy.isDead);
        assertEquals("null engine must not spawn any particles", 0, countParticles());
    }

    // ---- No CollisionComponent: no burst ----

    @Test
    public void noCollisionComponent_noDeathBurst() {
        Entity bareEnemy = entity(new EnemyComponent(), movement());
        boolean died = EnemyDamageResolver.applyHit(bareEnemy, new EnemyComponent(), movement(), 10f, 1, false, 1f, engine);

        assertTrue(died);
        // spawnHitSpark: collision null → 1 spark skipped → 0 total
        // spawnDeathBurst: collision null → skipped → still 0
        assertEquals("no collision component: no particles at all", 0, countParticles());
    }

    // ---- Flyer enemy: death burst still spawns ----

    @Test
    public void flyerEnemy_lethalHit_spawnsDeathBurst() {
        enemyEntity.add(new FlyingEnemyComponent());
        boolean died = applyHit(10f, 1, true);

        assertTrue(died);
        assertTrue(FLYING.get(enemyEntity) != null);
        // 1 spark + 2 burst (flyer color) = 3
        assertEquals("flyer lethal hit must still spawn death-burst", 3, countParticles());
    }

    // ---- Stunned enemy: no hit, no particles ----

    @Test
    public void stunnedEnemy_noHitNoParticles() {
        enemy.hitStun.start(0.3f);

        boolean died = applyHit(10f, 1, false);

        assertFalse(died);
        assertEquals("stunned enemy: no particles spawned", 0, countParticles());
    }
}
