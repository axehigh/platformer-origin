package com.axehigh.platformer.viewport;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * A {@link FitViewport} that reserves a fixed band of physical pixels along the bottom of the
 * screen for on-screen touch controls: the world fits into the region above the band instead of
 * the whole screen, so the controls can never cover the play area. With a band of zero it behaves
 * exactly like a plain {@link FitViewport}.
 */
public class OffsetFitViewport extends FitViewport {
    private int bottomBandPx;

    public OffsetFitViewport(float worldWidth, float worldHeight, Camera camera) {
        super(worldWidth, worldHeight, camera);
    }

    /** Sets the reserved band height in physical pixels (clamped to non-negative). */
    public void setBottomBandPx(int bottomBandPx) {
        this.bottomBandPx = Math.max(0, bottomBandPx);
    }

    @Override
    public void update(int screenWidth, int screenHeight, boolean centerCamera) {
        float worldWidth = getWorldWidth();
        float worldHeight = getWorldHeight();
        int availHeight = Math.max(1, screenHeight - bottomBandPx);
        float screenAspectRatio = screenWidth / (float) availHeight;
        float worldAspectRatio = worldWidth / worldHeight;

        float scale = screenAspectRatio > worldAspectRatio
            ? availHeight / worldHeight
            : screenWidth / worldWidth;

        int w = Math.round(worldWidth * scale);
        int h = Math.round(worldHeight * scale);
        int x = (screenWidth - w) / 2;
        int y = bottomBandPx + Math.max(0, (availHeight - h) / 2);

        setScreenBounds(x, y, w, h);
        apply(centerCamera);
    }
}
