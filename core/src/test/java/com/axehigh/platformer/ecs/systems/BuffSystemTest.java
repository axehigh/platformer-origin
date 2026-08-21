package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;

/** Headless tests for the timed potion buffs and buff-feedback halo managed by {@link BuffSystem}. */
public class BuffSystemTest extends SystemTestBase {

    /** Mirrors {@code BuffSystem.BUFF_LIGHT_ALPHA} (steady halo alpha). */
    private static final float FULL_ALPHA = 0.85f;
    /** Mirrors {@code BuffSystem.BUFF_LIGHT_BLINK_ALPHA} (dim blink-phase alpha). */
    private static final float BLINK_ALPHA = 0.15f;
    /** Frames that make up one {@link GameConstants#BUFF_BLINK_INTERVAL} at 60fps. */
    private static final int BLINK_FRAMES = Math.round(GameConstants.BUFF_BLINK_INTERVAL / DT);

    private BuffSystem system;
    private Engine engine;

    @Before
    public void setUp() {
        system = new BuffSystem(0);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity buffedPlayer(float maxSpeedX) {
        PlayerComponent player = player();
        MovementComponent movement = movement();
        movement.maxSpeedX = maxSpeedX;
        Entity entity = entity(player, movement, new BuffComponent());
        engine.addEntity(entity);
        return entity;
    }

    /**
     * Advances frames until the soonest-expiring <b>active</b> timer has crossed under
     * {@code threshold} (mirrors how {@code BuffSystem} computes {@code minRemaining}); returns
     * frames run. Inactive timers are ignored — they sit at 0 remaining.
     */
    private int tickUntilBelow(BuffComponent buff, float threshold) {
        int frames = 0;
        while (minActiveRemaining(buff) >= threshold) {
            engine.update(DT);
            frames++;
            assertTrue("buff expired before crossing threshold", frames < 100000);
        }
        return frames;
    }

    private static float minActiveRemaining(BuffComponent buff) {
        float min = Float.MAX_VALUE;
        if (buff.strength.isActive()) {
            min = Math.min(min, buff.strength.getRemaining());
        }
        if (buff.speed.isActive()) {
            min = Math.min(min, buff.speed.getRemaining());
        }
        if (buff.invulnerability.isActive()) {
            min = Math.min(min, buff.invulnerability.getRemaining());
        }
        return min;
    }

    /**
     * Steps frames until the halo's alpha phase flips (bright↔dim), tolerating the ±1 frame of
     * float drift in when the blink Timer's zero lands. Fails if no flip occurs within
     * {@code BLINK_FRAMES + 2} updates.
     */
    private void stepToNextPhase(Entity entity) {
        LightComponent light = LIGHT.get(entity);
        float before = light.baseAlpha;
        for (int i = 0; i < BLINK_FRAMES + 2; i++) {
            engine.update(DT);
            float now = LIGHT.get(entity).baseAlpha;
            if (Math.abs(now - before) > EPSILON) {
                return;
            }
        }
        fail("halo alpha never flipped from " + before);
    }

    @Test
    public void speedBuffScalesMaxSpeedWhileActive() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        buff.startSpeed();

        engine.update(DT);

        assertEquals(100f * GameConstants.SPEED_MULTIPLIER, movement.maxSpeedX, EPSILON);
    }

    @Test
    public void speedBuffRestoresBaseMaxSpeedOnExpiry() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        buff.startSpeed();

        engine.update(DT);
        assertEquals(100f * GameConstants.SPEED_MULTIPLIER, movement.maxSpeedX, EPSILON);

        int frames = (int) Math.ceil(GameConstants.SPEED_BUFF_DURATION / DT) + 1;
        for (int i = 0; i < frames; i++) {
            engine.update(DT);
        }

        assertFalse(buff.isSpeedActive());
        assertEquals(100f, movement.maxSpeedX, EPSILON);
    }

    @Test
    public void strengthAndInvulnerabilityBuffsExpireAfterTheirDurations() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startStrength();
        buff.startInvulnerability();

        int frames = (int) Math.ceil(Math.max(GameConstants.STRENGTH_BUFF_DURATION, GameConstants.INVULNERABILITY_DURATION) / DT) + 1;
        for (int i = 0; i < frames; i++) {
            engine.update(DT);
        }

        assertFalse(buff.isStrengthActive());
        assertFalse(buff.isInvulnerabilityActive());
    }

    @Test
    public void startStrength_addsLightWithStrengthColorAndRadius56() {
        Entity entity = buffedPlayer(100f);
        BUFF.get(entity).startStrength();

        engine.update(DT);

        assertTrue(LIGHT.has(entity));
        LightComponent light = LIGHT.get(entity);
        assertEquals(56f, light.radius, EPSILON);
        float[] strengthColor = PotionType.STRENGTH.messageColor();
        assertEquals(strengthColor[0], light.color.r, EPSILON);
        assertEquals(strengthColor[1], light.color.g, EPSILON);
        assertEquals(strengthColor[2], light.color.b, EPSILON);
        assertEquals(1f, light.color.a, EPSILON);
    }

    @Test
    public void startStrength_haloCentersOnCollisionBoxIncludingXOffset() {
        // Trimmed-atlas + facing offsets shift the collision box off the transform origin; the
        // halo must track the box's middle on both axes (same convention as RenderSystem).
        PlayerComponent player = player();
        MovementComponent movement = movement();
        CollisionComponent collision = collision(6f, 4f, 20f, 40f);
        Entity entity = entity(player, movement, new BuffComponent(), collision);
        engine.addEntity(entity);
        BUFF.get(entity).startStrength();

        engine.update(DT);

        assertEquals(6f + 20f / 2f, LIGHT.get(entity).offset.x, EPSILON);
        assertEquals(4f + 40f / 2f, LIGHT.get(entity).offset.y, EPSILON);
    }

    @Test
    public void activeStrengthAndSpeed_blendAverageMessageColors() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startStrength();
        buff.startSpeed();

        engine.update(DT);

        float[] strengthColor = PotionType.STRENGTH.messageColor();
        float[] speedColor = PotionType.SPEED.messageColor();
        LightComponent light = LIGHT.get(entity);
        assertEquals((strengthColor[0] + speedColor[0]) / 2f, light.color.r, EPSILON);
        assertEquals((strengthColor[1] + speedColor[1]) / 2f, light.color.g, EPSILON);
        assertEquals((strengthColor[2] + speedColor[2]) / 2f, light.color.b, EPSILON);
        assertEquals(1f, light.color.a, EPSILON);
    }

    @Test
    public void allTimersExpired_removesLightComponent() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startStrength();
        buff.startSpeed();
        buff.startInvulnerability();

        // Past the invulnerability duration the halo must persist (other buffs still active).
        int invulnFrames = (int) Math.ceil(GameConstants.INVULNERABILITY_DURATION / DT) + 1;
        for (int i = 0; i < invulnFrames; i++) {
            engine.update(DT);
        }
        assertFalse(buff.isInvulnerabilityActive());
        assertTrue(LIGHT.has(entity));

        // Past every duration the halo goes away.
        int totalFrames = (int) Math.ceil(GameConstants.STRENGTH_BUFF_DURATION / DT) + 1;
        for (int i = invulnFrames; i < totalFrames; i++) {
            engine.update(DT);
        }
        assertFalse(buff.isStrengthActive());
        assertFalse(buff.isSpeedActive());
        assertFalse(LIGHT.has(entity));
    }

    @Test
    public void nearExpiry_blinkFlipsBaseAlphaEveryInterval() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startStrength();

        // Entering frame flips to bright (full alpha) and arms the toggle.
        tickUntilBelow(buff, GameConstants.BUFF_BLINK_THRESHOLD);
        assertEquals(FULL_ALPHA, LIGHT.get(entity).baseAlpha, EPSILON);

        // Bright holds for the whole interval, flipping to dim within one frame of the
        // boundary (float accumulation can land the Timer's zero on either frame).
        stepToNextPhase(entity);

        // Dim holds for a full interval, then flips back to bright.
        assertEquals(BLINK_ALPHA, LIGHT.get(entity).baseAlpha, EPSILON);
        stepToNextPhase(entity);
        assertEquals(FULL_ALPHA, LIGHT.get(entity).baseAlpha, EPSILON);
    }

    @Test
    public void refreshDuringBlink_restoresFullAlphaAndStopsBlinking() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startStrength();
        tickUntilBelow(buff, GameConstants.BUFF_BLINK_THRESHOLD);

        // Step into the dim phase so blinking is provably underway.
        stepToNextPhase(entity);
        assertEquals(BLINK_ALPHA, LIGHT.get(entity).baseAlpha, EPSILON);

        buff.startStrength();
        engine.update(DT);
        assertEquals(FULL_ALPHA, LIGHT.get(entity).baseAlpha, EPSILON);

        // Half a second more, still far above the threshold: steady full alpha.
        for (int i = 0; i < 30; i++) {
            engine.update(DT);
            assertEquals("post-refresh frame " + i, FULL_ALPHA, LIGHT.get(entity).baseAlpha, EPSILON);
        }
    }

    @Test
    public void aboveBlinkThreshold_baseAlphaStaysFull() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startStrength();

        engine.update(DT);
        while (buff.strength.getRemaining() > GameConstants.BUFF_BLINK_THRESHOLD) {
            assertEquals(FULL_ALPHA, LIGHT.get(entity).baseAlpha, EPSILON);
            engine.update(DT);
        }
    }

    @Test
    public void drinkAfterExpiry_lightReadded() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startInvulnerability();

        int frames = (int) Math.ceil(GameConstants.INVULNERABILITY_DURATION / DT) + 1;
        for (int i = 0; i < frames; i++) {
            engine.update(DT);
        }
        assertFalse(LIGHT.has(entity));

        buff.startStrength();
        engine.update(DT);

        assertTrue(LIGHT.has(entity));
        assertEquals(56f, LIGHT.get(entity).radius, EPSILON);
    }
}
