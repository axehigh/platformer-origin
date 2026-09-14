package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.HitFlashComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.GameConstants.HIT_FLASH_DURATION;
import static com.axehigh.platformer.ecs.components.Mappers.HIT_FLASH;
import static org.junit.Assert.*;

/**
 * Headless tests for {@code HitFlashSystem}: counts down the hit-flash timer, clamps at zero,
 * and never removes entities.
 */
public class HitFlashSystemTest extends SystemTestBase {

    private Engine engine;
    private HitFlashSystem system;

    @Before
    public void setUp() {
        system = new HitFlashSystem();
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity flashEntity() {
        Entity e = entity(new HitFlashComponent());
        engine.addEntity(e);
        return e;
    }

    @Test
    public void start_setsActiveImmediately() {
        Entity e = flashEntity();
        HIT_FLASH.get(e).start(HIT_FLASH_DURATION);

        assertTrue(HIT_FLASH.get(e).isActive());
        assertEquals(HIT_FLASH_DURATION, HIT_FLASH.get(e).flashTimer, EPSILON);
        assertEquals(HIT_FLASH_DURATION, HIT_FLASH.get(e).flashDuration, EPSILON);
    }

    @Test
    public void ticking_countsDownTimerByDelta() {
        Entity e = flashEntity();
        HIT_FLASH.get(e).start(HIT_FLASH_DURATION);

        engine.update(DT);

        assertEquals(HIT_FLASH_DURATION - DT, HIT_FLASH.get(e).flashTimer, EPSILON);
        assertTrue(HIT_FLASH.get(e).isActive());
    }

    @Test
    public void timerClampsAtZero_doesNotGoNegative() {
        Entity e = flashEntity();
        HIT_FLASH.get(e).start(HIT_FLASH_DURATION);

        // DT * 5 ≈ 0.0833 > 0.08, so timer should hit zero
        for (int i = 0; i < 6; i++) {
            engine.update(DT);
        }

        assertEquals(0f, HIT_FLASH.get(e).flashTimer, EPSILON);
        assertFalse(HIT_FLASH.get(e).isActive());
    }

    @Test
    public void entityStaysInEngine_afterTimerExpires() {
        Entity e = flashEntity();
        HIT_FLASH.get(e).start(HIT_FLASH_DURATION);

        // Tick past expiry
        for (int i = 0; i < 10; i++) {
            engine.update(DT);
        }

        assertTrue("entity must persist after flash expires", engine.getEntities().contains(e, true));
        assertFalse(HIT_FLASH.get(e).isActive());
    }

    @Test
    public void zeroDelta_noChange() {
        Entity e = flashEntity();
        HIT_FLASH.get(e).start(HIT_FLASH_DURATION);

        engine.update(0f);

        assertEquals(HIT_FLASH_DURATION, HIT_FLASH.get(e).flashTimer, EPSILON);
        assertTrue(HIT_FLASH.get(e).isActive());
    }

    @Test
    public void multipleEntities_countDownIndependently() {
        Entity a = flashEntity();
        Entity b = flashEntity();
        HIT_FLASH.get(a).start(HIT_FLASH_DURATION);
        HIT_FLASH.get(b).start(HIT_FLASH_DURATION * 2f);

        engine.update(DT);

        assertEquals(HIT_FLASH_DURATION - DT, HIT_FLASH.get(a).flashTimer, EPSILON);
        assertEquals(HIT_FLASH_DURATION * 2f - DT, HIT_FLASH.get(b).flashTimer, EPSILON);
    }
}
