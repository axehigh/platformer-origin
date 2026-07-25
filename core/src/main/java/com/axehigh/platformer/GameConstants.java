package com.axehigh.platformer;

/**
 * Shared, project-wide constants.
 */
public final class GameConstants {
    /**
     * Virtual resolution width, in world units (1 unit == 1 pixel), used by the FitViewport.
     */
    public static final float VIRTUAL_WIDTH = 480f;
    /**
     * Virtual resolution height, in world units (1 unit == 1 pixel), used by the FitViewport.
     * Kept as an exact multiple of the 16px tile size (17 rows) so room-sized map blocks tile
     * perfectly and the flip-screen {@code CameraSystem} never desyncs from the Tiled room grid.
     */
    public static final float VIRTUAL_HEIGHT = 272f;

    //Movement MaxSpeedX/Y
    public static float MaxSpeedX = 100f;
    public static float MaxSpeedY = 400f;
    public static int MaxItems = 10;
    public static int SwordDamage =5;
    public static int MaxHealth =3;

    private GameConstants() {
    }
}
