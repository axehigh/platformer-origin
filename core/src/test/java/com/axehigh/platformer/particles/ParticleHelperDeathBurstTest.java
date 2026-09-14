package com.axehigh.platformer.particles;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.ecs.systems.SystemTestBase;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.PARTICLE;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.*;

/**
 * Headless tests verifying {@code ParticleHelper.spawnDeathBurst} directly:
 * the two-entity dummy path (smoke + burst), positions, and ParticleComponent fields.
 */
public class ParticleHelperDeathBurstTest extends SystemTestBase {

    private PooledEngine engine;

    @Before
    public void setUp() {
        engine = new PooledEngine();
    }

    private int countParticles() {
        int count = 0;
        for (Entity e : engine.getEntities()) {
            if (PARTICLE.get(e) != null) count++;
        }
        return count;
    }

    @Test
    public void spawnDeathBurst_createsTwoParticleEntities() {
        ParticleHelper.spawnDeathBurst(engine, 5f, 7f, GameConstants.DEATH_BURST_COLOR_ORGANIC);

        assertEquals("must create exactly 2 particle entities (smoke + burst)", 2, countParticles());
    }

    @Test
    public void spawnDeathBurst_positionsMatchInput() {
        ParticleHelper.spawnDeathBurst(engine, 5f, 7f, GameConstants.DEATH_BURST_COLOR_ORGANIC);

        for (Entity e : engine.getEntities()) {
            TransformComponent tc = TRANSFORM.get(e);
            if (tc != null) {
                assertEquals("smoke position.x", 5f, tc.position.x, EPSILON);
                assertEquals("smoke position.y", 7f, tc.position.y, EPSILON);
            }
        }
    }

    @Test
    public void spawnDeathBurst_nullEngine_noCrash() {
        ParticleHelper.spawnDeathBurst(null, 5f, 7f, GameConstants.DEATH_BURST_COLOR_ORGANIC);
        // No assertion needed — just must not throw
    }

    @Test
    public void spawnDeathBurst_smokeDummy_scaleAndLifetime() {
        ParticleHelper.spawnDeathBurst(engine, 5f, 7f, GameConstants.DEATH_BURST_COLOR_ORGANIC);

        // The smoke dummy is created first, so it should be the first particle entity encountered.
        // Check that at least one entity has smoke-scale and one has burst-scale.
        boolean foundSmoke = false;
        boolean foundBurst = false;
        for (Entity e : engine.getEntities()) {
            ParticleComponent pc = PARTICLE.get(e);
            if (pc == null) continue;
            if (pc.scale == 6.0f) {
                // DEATH_BURST_SMOKE_SCALE
                foundSmoke = true;
                assertEquals("smoke maxLifetime", 0.6f, pc.maxLifetime, EPSILON);
                assertEquals("smoke delay", 0f, pc.delay, EPSILON);
            } else if (pc.scale == 1.0f) {
                // DEATH_BURST_SCALE
                foundBurst = true;
                assertEquals("burst maxLifetime", 0.6f, pc.maxLifetime, EPSILON);
                assertEquals("burst delay", 0f, pc.delay, EPSILON);
            }
        }
        assertTrue("must find a smoke dummy (scale 6.0)", foundSmoke);
        assertTrue("must find a burst dummy (scale 1.0)", foundBurst);
    }

    @Test
    public void spawnDeathBurst_twoDistinctEntities() {
        ParticleHelper.spawnDeathBurst(engine, 5f, 7f, GameConstants.DEATH_BURST_COLOR_ORGANIC);

        Entity first = null;
        Entity second = null;
        for (Entity e : engine.getEntities()) {
            if (PARTICLE.get(e) != null) {
                if (first == null) first = e;
                else { second = e; break; }
            }
        }
        assertNotNull("first particle entity must exist", first);
        assertNotNull("second particle entity must exist", second);
        assertNotSame("smoke and burst must be different entities", first, second);
    }

    @Test
    public void spawnDeathBurst_flyerColorStillCreatesTwoEntities() {
        ParticleHelper.spawnDeathBurst(engine, 3f, 4f, GameConstants.DEATH_BURST_COLOR_FLYER);

        assertEquals("flyer color must still create 2 entities", 2, countParticles());
    }

    @Test
    public void spawnDeathBurst_differentPosition() {
        ParticleHelper.spawnDeathBurst(engine, 99f, 42f, GameConstants.DEATH_BURST_COLOR_ORGANIC);

        for (Entity e : engine.getEntities()) {
            TransformComponent tc = TRANSFORM.get(e);
            if (tc != null) {
                assertEquals(99f, tc.position.x, EPSILON);
                assertEquals(42f, tc.position.y, EPSILON);
            }
        }
    }
}
