package com.axehigh.platformer;

/** Shared, project-wide constants. */
public final class GameConstants {
    /** Virtual resolution width, in world units (1 unit == 1 pixel), used by the FitViewport. */
    public static final float VIRTUAL_WIDTH = 480f;
    /**
     * Virtual resolution height, in world units (1 unit == 1 pixel), used by the FitViewport.
     * Kept as an exact multiple of the 16px tile size (17 rows) so room-sized map blocks tile
     * perfectly and the flip-screen {@code CameraSystem} never desyncs from the Tiled room grid.
     */
    public static final float VIRTUAL_HEIGHT = 272f;

    private GameConstants() {
    }
}
