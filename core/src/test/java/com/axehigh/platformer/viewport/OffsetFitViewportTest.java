package com.axehigh.platformer.viewport;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@link OffsetFitViewport}'s expand-width behavior: the world widens with the
 * screen aspect (no left/right black bars on phones/wide desktops), clamped between the classic
 * design ratio and {@code GameConstants.MAX_WORLD_ASPECT}, with the bottom touch band respected.
 * Pure camera/viewport math — {@code Gdx.gl}/{@code Gdx.graphics} are stubbed because
 * {@code Viewport.apply()} issues a glViewport.
 */
public class OffsetFitViewportTest {

    private static final float EPSILON = 0.01f;
    private static final float VW = 480f;
    private static final float VH = 272f;

    static {
        GdxNativesLoader.load(); // camera.update() needs the Matrix4 natives
    }

    /** Surface size reported through the {@code Gdx.graphics} stub (used by HdpiUtils.glViewport). */
    private final int[] surface = new int[2];

    private OrthographicCamera camera;
    private OffsetFitViewport viewport;

    @Before
    public void setUp() {
        Gdx.gl = mock(GL20.class);
        Graphics graphics = mock(Graphics.class);
        when(graphics.getWidth()).thenAnswer(inv -> surface[0]);
        when(graphics.getHeight()).thenAnswer(inv -> surface[1]);
        Gdx.graphics = graphics;

        camera = new OrthographicCamera();
        viewport = new OffsetFitViewport(VW, VH, camera);
    }

    private void updateViewport(int width, int height) {
        surface[0] = width;
        surface[1] = height;
        viewport.update(width, height);
    }

    /** Wide phone (1280x591) with a 147px bottom band: world expands to the region's aspect, no side bars. */
    @Test
    public void wideScreenExpandsWorldWidthWithNoSideBars() {
        int band = 147;
        viewport.setBottomBandPx(band);
        updateViewport(1280, 591);

        float availHeight = 591 - band;
        assertEquals(VH * (1280f / availHeight), viewport.getWorldWidth(), EPSILON);
        assertEquals(VH, viewport.getWorldHeight(), EPSILON);

        // Region exactly covered: no pillarboxing, world sits directly above the band.
        assertEquals(0, viewport.getScreenX());
        assertEquals(band, viewport.getScreenY());
        assertEquals(1280, viewport.getScreenWidth());
        assertEquals(availHeight, viewport.getScreenHeight(), EPSILON);

        // Camera tracks the (expanded) world size — CameraSystem reads it live.
        assertEquals(viewport.getWorldWidth(), camera.viewportWidth, EPSILON);
        assertEquals(VH, camera.viewportHeight, EPSILON);
    }

    /** Ultra-wide screen: expansion caps at MAX_WORLD_ASPECT (~21:9); mild pillarbox returns. */
    @Test
    public void ultraWideScreenCapsExpansion() {
        float maxAspect = 21f / 9f;
        viewport.setBottomBandPx(147);
        updateViewport(3000, 591);

        assertEquals(VH * maxAspect, viewport.getWorldWidth(), EPSILON);

        int expectedW = Math.round(VH * maxAspect * (444f / VH)); // 444 = 591 - 147
        assertEquals((3000 - expectedW) / 2, viewport.getScreenX());
        assertTrue("expected visible width narrower than the screen",
            viewport.getScreenWidth() < 3000);
    }

    /** Narrow/portrait screen: clamps back up to the classic ratio; top/bottom bars as before. */
    @Test
    public void narrowScreenKeepsDesignWidthAndVerticalBars() {
        int band = 100;
        viewport.setBottomBandPx(band);
        updateViewport(400, 800);

        assertEquals(VW, viewport.getWorldWidth(), EPSILON);

        int scaleLimitedH = Math.round(VH * (400f / VW)); // 227
        assertEquals(0, viewport.getScreenX());
        assertEquals(band + (700 - scaleLimitedH) / 2, viewport.getScreenY());
        assertEquals(400, viewport.getScreenWidth());
        assertEquals(scaleLimitedH, viewport.getScreenHeight());
    }

    /** Desktop window without a band: tiny expansion beyond 480 already at 16:9. */
    @Test
    public void desktopWindowExpandsSlightlyWithoutBand() {
        viewport.setBottomBandPx(0);
        updateViewport(1280, 720);

        assertEquals(VH * (1280f / 720f), viewport.getWorldWidth(), EPSILON);
        assertEquals(0, viewport.getScreenX());
        assertEquals(0, viewport.getScreenY());
        assertEquals(1280, viewport.getScreenWidth());
        assertEquals(720, viewport.getScreenHeight());
    }

    /** Tile-scale change via setWorldSize keeps expanding proportionally off the new height. */
    @Test
    public void rescaleKeepsExpansionBasedOnNewHeight() {
        viewport.setWorldSize(VW * 8, VH * 8);
        viewport.setBottomBandPx(147);
        updateViewport(1280, 591);

        assertEquals(VH * 8, viewport.getWorldHeight(), EPSILON);
        assertEquals(VH * 8 * (1280f / (591 - 147)), viewport.getWorldWidth(), EPSILON);
    }

    /** update(screen, screen) delegates to centerCamera=false: camera position is never moved. */
    @Test
    public void updateDoesNotRecenterCamera() {
        camera.position.set(123f, 456f, 0f);
        updateViewport(1280, 591);

        assertEquals(123f, camera.position.x, EPSILON);
        assertEquals(456f, camera.position.y, EPSILON);
    }
}
