package com.axehigh.platformer.viewport;

import com.axehigh.platformer.GameConstants;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * A {@link FitViewport} with two adjustments over plain letterbox fitting:
 *
 * <ul>
 *   <li><strong>Bottom band:</strong> reserves a fixed band of physical pixels along the bottom of
 *       the screen for on-screen touch controls — the world fits into the region above the band, so
 *       the controls can never cover the play area.</li>
 *   <li><strong>Horizontal expansion:</strong> instead of pillarboxing whenever the screen is wider
 *       than the classic 16:9-ish world ratio (black bars left/right on phones and wide desktops),
 *       the world's <em>width</em> grows toward the drawn region's aspect ratio. The world's height
 *       always stays at the value set via {@link #setWorldSize} (i.e.
 *       {@code VIRTUAL_HEIGHT &times; tile scale}); the width is floored by the classic design ratio
 *       ({@code VIRTUAL_WIDTH/VIRTUAL_HEIGHT}) — so narrow or portrait screens keep their top/bottom
 *       bars — and capped by {@code GameConstants.MAX_WORLD_ASPECT} (&asymp;21:9) only for screens
 *       that are <em>physically</em> wider than that, so ultra-wide monitors never reveal more world
 *       than narrower devices. The touch band is ignored for the cap decision: it shrinks available
 *       height and would otherwise cost tall phones extra side bars. With an unclamped expansion the
 *       world aspect matches the drawn region's aspect, so pixels stay square at any
 *       {@code camera.zoom} (zoom cancels out of the ratio).</li>
 * </ul>
 */
public class OffsetFitViewport extends FitViewport {
    /** The classic design ratio ({@code VIRTUAL_WIDTH / VIRTUAL_HEIGHT}); the minimum world aspect. */
    private final float minWorldAspect;
    private int bottomBandPx;

    public OffsetFitViewport(float worldWidth, float worldHeight, Camera camera) {
        super(worldWidth, worldHeight, camera);
        this.minWorldAspect = worldWidth / worldHeight;
    }

    /** Sets the reserved band height in physical pixels (clamped to non-negative). */
    public void setBottomBandPx(int bottomBandPx) {
        this.bottomBandPx = Math.max(0, bottomBandPx);
    }

    @Override
    public void update(int screenWidth, int screenHeight, boolean centerCamera) {
        float worldHeight = getWorldHeight();

        // Expand the world horizontally toward the drawn region's aspect so there are no left/right
        // black bars (pixels stay square: zoom cancels out of the aspect ratio). Two bounds apply:
        // the classic design ratio is the floor (narrow/portrait screens keep top/bottom bars), and
        // MAX_WORLD_ASPECT caps only screens that are PHYSICALLY wider than ~21:9 (ultra-wide
        // monitors keep mild side bars so they never reveal more world than narrower devices).
        // The touch band is deliberately ignored here: it shrinks available height, which would
        // otherwise cost tall phones extra side bars. Height never changes.
        float physicalAspect = screenWidth / (float) screenHeight;
        boolean ultraWide = physicalAspect > GameConstants.MAX_WORLD_ASPECT;
        int availHeight = Math.max(1, screenHeight - bottomBandPx);
        float regionAspect = screenWidth / (float) availHeight;
        float targetAspect = Math.max(minWorldAspect, ultraWide
            ? GameConstants.MAX_WORLD_ASPECT
            : regionAspect);
        setWorldSize(worldHeight * targetAspect, worldHeight);

        // Fit into the region above the band; with an unclamped expansion this fills it exactly.
        float scale = Math.min(screenWidth / getWorldWidth(), availHeight / worldHeight);

        int w = Math.round(getWorldWidth() * scale);
        int h = Math.round(worldHeight * scale);
        int x = (screenWidth - w) / 2;
        int y = bottomBandPx + Math.max(0, (availHeight - h) / 2);

        setScreenBounds(x, y, w, h);
        apply(centerCamera);
    }
}
