package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.SlashArcComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for {@code SlashArcSystem}: ages the melee slash-arc VFX, fades the crescent in
 * over the first {@code fadeInRatio} of its life (alpha 0 → 1), fades it back out over the
 * remainder (1 → 0), and removes the entity once {@code age >= lifeTime}. The fade curve,
 * re-derived here rather than guessed, is:
 * <pre>
 *   fadeInTime = lifeTime * fadeInRatio
 *   alpha = age / fadeInTime                        for age < fadeInTime
 *   alpha = 1 - (age - fadeInTime)/(lifeTime - fadeInTime)  otherwise
 * </pre>
 */
public class SlashArcSystemTest extends SystemTestBase {
    private static final float LIFE = 0.2f;
    private static final float FADE_IN_RATIO = 0.2f;
    private static final float FADE_IN_TIME = LIFE * FADE_IN_RATIO; // 0.04s
    /** Age after N frames when each update adds DT — the frame index times DT. */
    private static final int FRAMES_UNTIL_REMOVAL = 12; // age reaches exactly LIFE

    private Engine engine;
    private SlashArcSystem system;
    private Entity arc;
    private SlashArcComponent arcComponent;

    @Before
    public void setUp() {
        system = new SlashArcSystem();
        engine = newEngine();
        engine.addSystem(system);
        arcComponent = new SlashArcComponent();
        arcComponent.lifeTime = LIFE;
        // TextureComponent region may be null — the system never touches it.
        arc = entity(transform(0f, 0f), new TextureComponent(), arcComponent);
        engine.addEntity(arc);
    }

    /** The documented fade-in/fade-out curve, used to assert the exact per-frame alpha. */
    private static float expectedAlpha(float age) {
        if (age < FADE_IN_TIME) {
            return age / FADE_IN_TIME;
        }
        return 1f - (age - FADE_IN_TIME) / (LIFE - FADE_IN_TIME);
    }

    @Test
    public void alphaAtAgeZeroMatchesFadeInCurveOrigin() {
        engine.update(0f);

        assertEquals("at age 0 the fade-in curve is 0/fadeInTime = 0", 0f, TRANSFORM.get(arc).alpha, EPSILON);
        assertTrue(engine.getEntities().contains(arc, true));
    }

    @Test
    public void alphaRisesThroughFadeInWindowTowardOne() {
        // The fade-in window is 0.04s = 2.4 frames, so the curve keeps climbing toward its 1.0
        // peak (age == fadeInTime) through frame 3 before turning back down on frame 4.
        float previous = -1f;
        for (int frame = 1; frame <= 3; frame++) {
            engine.update(DT);
            float alpha = TRANSFORM.get(arc).alpha;
            assertEquals("frame " + frame, expectedAlpha(frame * DT), alpha, EPSILON);
            assertTrue("frame " + frame + ": alpha must keep rising toward the peak", alpha > previous);
            assertTrue("frame " + frame + ": alpha below 1 until the peak", alpha < 1f);
            previous = alpha;
        }
    }

    @Test
    public void alphaFallsAfterFadeInPeak() {
        // Warm up through the peak: age hits fadeInTime (0.04s, the 1.0 peak) during frame 3.
        for (int frame = 1; frame <= 3; frame++) {
            engine.update(DT);
        }
        float previous = TRANSFORM.get(arc).alpha;
        for (int frame = 4; frame < FRAMES_UNTIL_REMOVAL; frame++) {
            engine.update(DT);
            float alpha = TRANSFORM.get(arc).alpha;
            assertEquals("frame " + frame, expectedAlpha(frame * DT), alpha, EPSILON);
            assertTrue("frame " + frame + ": alpha must decay after the fade-in peak", alpha < previous);
            previous = alpha;
        }
    }

    @Test
    public void entityRemovedOnceLifeTimeElapses() {
        for (int frame = 1; frame < FRAMES_UNTIL_REMOVAL; frame++) {
            engine.update(DT);
            assertTrue("frame " + frame + ": arc alive before lifeTime is spent", engine.getEntities().contains(arc, true));
        }

        engine.update(DT); // age == lifeTime

        assertTrue("age >= lifeTime removes the arc", !engine.getEntities().contains(arc, true));
    }

    @Test
    public void poolableResetRestoresDefaults() {
        arcComponent.lifeTime = 9f;
        arcComponent.age = 4f;
        arcComponent.fadeInRatio = 0.9f;

        arcComponent.reset();

        assertEquals(0.2f, arcComponent.lifeTime, EPSILON);
        assertEquals(0f, arcComponent.age, EPSILON);
        assertEquals(0.2f, arcComponent.fadeInRatio, EPSILON);
    }
}
