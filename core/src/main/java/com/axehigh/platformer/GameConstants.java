package com.axehigh.platformer;

/**
 * Shared, project-wide constants.
 */
public final class GameConstants {


    //Launcher
    public static final int WINDOW_SCREEN_WIDTH = 1280;
    public static final int WINDOW_SCREEN_HEIGHT = 720;

    public static final float SCREEN_WIDTH = 1980f;
    public static final float SCREEN_HEIGHT = 1080f;

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

    /** Duration (seconds) of the fade-in/fade-out transition between screens. */
    public static final float SCREEN_FADE_TIMER = 0.5f;

    //Movement MaxSpeedX/Y
    public static float MaxSpeedX = 100f;
    public static float MaxSpeedY = 400f;

    //Player Stats
    public static int MaxItems = 10;
    public static int SwordDamage = 5;
    public static int MaxHealth = 3;

    //UI
    public static float UI_Button_Action_Size = 150f;
    public static float UI_Button_Move_Size = 165f;
    public static final float UI_PADDING = 33f;
    public static final float UI_BUTTON_ALPHA = 0.6f;
    public static float FontScale = 1f;

    private GameConstants() {
    }
}
