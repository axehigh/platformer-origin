package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

/**
 * Integration tests proving that PlayerDamageResolver.applyHit / applyHitWithoutKnockback
 * trigger the VignetteRenderSystem's red pulse path. Verifies: (1) no-damage draws the normal
 * black vignette texture, (2) after a hit the RED pulse texture is drawn, (3) after
 * VIGNETTE_PULSE_DURATION the system reverts to the black vignette, (4) both applyHit and
 * applyHitWithoutKnockback trigger the pulse, (5) vignette disabled skips all draws, and
 * (6) the pulse texture is drawn for exactly VIGNETTE_PULSE_DURATION worth of frames.
 */
public class PlayerDamageResolverVignettePulseTest extends SystemTestBase {

    private GL20 previousGl;
    private Graphics previousGraphics;
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private VignetteRenderSystem system;
    private Texture vignetteTexture;
    private Texture pulseTexture;

    @Before
    public void setUp() throws Exception {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        Gdx.gl = mock(GL20.class);
        Gdx.graphics = mock(Graphics.class);

        batch = mock(SpriteBatch.class);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 480, 272);
        camera.update();

        FeatureFlags.setVignetteEnabled(true);
        FeatureFlags.setSlashArcEnabled(true);

        system = new VignetteRenderSystem(batch, camera, 0);

        // Force texture generation so we can capture references for identity checks.
        system.update(DT);
        reset(batch);

        // Extract the private textures via reflection for identity comparisons.
        vignetteTexture = getPrivateField(system, "vignetteTexture");
        pulseTexture = getPrivateField(system, "pulseTexture");
        assertNotNull("vignetteTexture created", vignetteTexture);
        assertNotNull("pulseTexture created", pulseTexture);
    }

    @After
    public void tearDown() {
        FeatureFlags.setVignetteEnabled(true);
        system.dispose();
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return (T) f.get(obj);
    }

    private Entity playerFixture(int health) {
        TransformComponent t = transform(100f, 100f);
        CollisionComponent c = collision(0f, 0f, 20f, 32f);
        place(t, c, 100f, 100f);
        PlayerComponent p = new PlayerComponent();
        p.health = health;
        MovementComponent m = movement();
        HitFlashComponent flash = new HitFlashComponent();
        return entity(t, c, p, m, flash);
    }

    private Texture captureDrawnTexture() {
        ArgumentCaptor<Texture> texCap = ArgumentCaptor.forClass(Texture.class);
        verify(batch, times(1)).draw(texCap.capture(),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
        return texCap.getValue();
    }

    @Test
    public void noDamage_DrawsBlackVignetteTexture() {
        system.update(DT);

        assertSame("normal vignette draws the black texture",
            vignetteTexture, captureDrawnTexture());
    }

    @Test
    public void afterApplyHit_DrawsPulseTexture() {
        Entity player = playerFixture(3);
        PlayerComponent pc = PLAYER.get(player);
        MovementComponent mc = MOVEMENT.get(player);

        boolean applied = PlayerDamageResolver.applyHit(
            player, pc, mc, 1, 1f);
        assertTrue("hit applied", applied);
        assertEquals(2, pc.health);

        system.update(DT);

        assertSame("pulse texture drawn after damage",
            pulseTexture, captureDrawnTexture());
    }

    @Test
    public void pulseRevertsToBlackAfterDuration() {
        Entity player = playerFixture(3);
        PlayerComponent pc = PLAYER.get(player);
        MovementComponent mc = MOVEMENT.get(player);

        PlayerDamageResolver.applyHit(player, pc, mc, 1, 1f);

        // Drain pendingPulse and simulate VIGNETTE_PULSE_DURATION + 1 extra frame.
        float totalSim = GameConstants.VIGNETTE_PULSE_DURATION + DT;
        for (float sim = 0; sim < totalSim; sim += DT) {
            reset(batch);
            system.update(DT);
        }

        // The last update drew something — check it's back to the black vignette.
        reset(batch);
        system.update(DT);
        assertSame("after pulse expires, draws black vignette",
            vignetteTexture, captureDrawnTexture());
    }

    @Test
    public void applyHitWithoutKnockback_AlsoTriggersPulse() {
        Entity player = playerFixture(3);
        PlayerComponent pc = PLAYER.get(player);

        boolean applied = PlayerDamageResolver.applyHitWithoutKnockback(player, pc);
        assertTrue("hit applied", applied);

        system.update(DT);

        assertSame("pulse texture drawn after no-knockback hit",
            pulseTexture, captureDrawnTexture());
    }

    @Test
    public void vignetteDisabled_SkipsDrawEvenAfterHit() {
        Entity player = playerFixture(3);
        PlayerComponent pc = PLAYER.get(player);
        MovementComponent mc = MOVEMENT.get(player);

        PlayerDamageResolver.applyHit(player, pc, mc, 1, 1f);

        FeatureFlags.setVignetteEnabled(false);
        system.update(DT);

        verify(batch, never()).begin();
        verify(batch, never()).draw(any(Texture.class),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    public void pulseDrawnForCorrectDuration() {
        Entity player = playerFixture(3);
        PlayerComponent pc = PLAYER.get(player);
        MovementComponent mc = MOVEMENT.get(player);

        PlayerDamageResolver.applyHit(player, pc, mc, 1, 1f);

        // Count how many frames draw the pulse texture before it reverts.
        int pulseFrames = 0;
        for (int i = 0; i < 60; i++) {
            reset(batch);
            system.update(DT);
            ArgumentCaptor<Texture> texCap = ArgumentCaptor.forClass(Texture.class);
            verify(batch, times(1)).draw(texCap.capture(),
                anyFloat(), anyFloat(), anyFloat(), anyFloat());
            if (texCap.getValue() == pulseTexture) {
                pulseFrames++;
            } else {
                break; // reverted to black vignette
            }
        }

        // VIGNETTE_PULSE_DURATION = 0.5s, DT = 1/60s => ~30 frames.
        // Allow a small window for rounding.
        assertTrue("pulse should last ~" +
            (int) (GameConstants.VIGNETTE_PULSE_DURATION / DT) + " frames, got " + pulseFrames,
            pulseFrames >= 25 && pulseFrames <= 35);
    }
}
