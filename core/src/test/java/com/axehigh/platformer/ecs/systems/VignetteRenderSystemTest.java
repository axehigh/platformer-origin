package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.util.FeatureFlags;
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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Headless tests for {@code VignetteRenderSystem}: it draws a single full-view quad (with a
 * procedurally generated box-gradient texture) covering the camera's *effective* view
 * ({@code viewportWidth/Height * zoom}), and is a safe no-op when no GL context is available
 * (e.g. outside a running game). {@code Gdx.gl} is stubbed with a Mockito {@code GL20} so the
 * {@code Pixmap}→{@code Texture} generation runs; the {@code SpriteBatch} is mocked (no real GL),
 * the camera is a real {@code OrthographicCamera}.
 */
public class VignetteRenderSystemTest extends SystemTestBase {

    private GL20 previousGl;
    private Graphics previousGraphics;
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private VignetteRenderSystem system;

    @Before
    public void setUp() {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        // Full GL/Gfx stubs so the Pixmap -> Texture generation (glGenTexture, glTexImage2D,
        // glTexParameteri, anisotropic-extension query) runs without a real context.
        Gdx.gl = mock(GL20.class);
        Gdx.graphics = mock(Graphics.class);
        batch = mock(SpriteBatch.class);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 480, 272);
        camera.update();
        system = new VignetteRenderSystem(batch, camera, 0);
    }

    @After
    public void tearDown() {
        FeatureFlags.setVignetteEnabled(true);
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

    @Test
    public void drawsSingleQuadCoveringEffectiveView() {
        system.update(DT);

        ArgumentCaptor<Float> floatCap = ArgumentCaptor.forClass(Float.class);
        verify(batch, times(1)).draw(any(Texture.class),
            floatCap.capture(), floatCap.capture(), floatCap.capture(), floatCap.capture());

        // Camera centered at (240, 136) at zoom 1: quad exactly covers the ortho view.
        float x = floatCap.getAllValues().get(0);
        float y = floatCap.getAllValues().get(1);
        float w = floatCap.getAllValues().get(2);
        float h = floatCap.getAllValues().get(3);
        assertEquals(0f, x, EPSILON);
        assertEquals(0f, y, EPSILON);
        assertEquals(480f, w, EPSILON);
        assertEquals(272f, h, EPSILON);

        verify(batch).setProjectionMatrix(camera.combined);
        verify(batch, times(1)).begin();
        verify(batch, times(1)).end();
    }

    @Test
    public void quadScalesWithCameraZoom() {
        camera.zoom = 0.55f;
        camera.update();

        system.update(DT);

        ArgumentCaptor<Float> floatCap = ArgumentCaptor.forClass(Float.class);
        verify(batch, times(1)).draw(any(Texture.class),
            floatCap.capture(), floatCap.capture(), floatCap.capture(), floatCap.capture());

        float x = floatCap.getAllValues().get(0);
        float y = floatCap.getAllValues().get(1);
        float w = floatCap.getAllValues().get(2);
        float h = floatCap.getAllValues().get(3);
        assertEquals(480f * 0.55f, w, EPSILON);
        assertEquals(272f * 0.55f, h, EPSILON);
        // Centered on the (unchanged) camera position.
        assertEquals(camera.position.x - w / 2f, x, EPSILON);
        assertEquals(camera.position.y - h / 2f, y, EPSILON);
    }

    @Test
    public void noGlContextIsSafeNoOp() {
        Gdx.gl = null;

        system.update(DT);

        verify(batch, never()).begin();
        verify(batch, never()).end();
        verify(batch, never()).setProjectionMatrix(any());
        verify(batch, never()).draw(any(Texture.class),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    public void triggeredPulseStillDrawsSingleFullViewQuad() {
        VignetteRenderSystem.triggerPulse();

        system.update(DT);

        verify(batch, times(1)).draw(any(Texture.class),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
        verify(batch, times(1)).begin();
        verify(batch, times(1)).end();
        verify(batch).setProjectionMatrix(camera.combined);
    }

    @Test
    public void disabledFlagSkipsBatchEntirely() {
        FeatureFlags.setVignetteEnabled(false);

        system.update(DT);

        verify(batch, never()).begin();
        verify(batch, never()).end();
        verify(batch, never()).setProjectionMatrix(any());
        verify(batch, never()).draw(any(Texture.class),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
    }
}