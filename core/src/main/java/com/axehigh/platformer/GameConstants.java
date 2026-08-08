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
     * Kept as an exact multiple of the 16px tile size (17 rows) so map blocks tile perfectly and
     * screen-sized room blocks align cleanly with the camera viewport.
     */
    public static final float VIRTUAL_HEIGHT = 272f;

    /**
     * Dead-zone scroll margin (world units, 1 unit == 1 pixel) used by {@code CameraSystem} in
     * scroll rooms: the camera holds still while the player roams anywhere more than this many
     * pixels from a screen edge, and only starts scrolling once the player crosses that margin.
     * Applies at zoom 1 (desktop/tablet, non-zoomed mobile layouts).
     */
    public static final float CAMERA_SCROLL_MARGIN = 320f;

    /**
     * Dead-zone scroll margin as a fraction of the *effective* (zoomed) view size per axis, used
     * by {@code CameraSystem} when the camera is zoomed in (mobile {@code BAND_ZOOM}). A zoomed
     * camera shows a smaller slice of the world, so a fixed pixel margin would feel proportionally
     * huge; using a fraction of the visible view keeps the player tracking well clear of the
     * screen edges (≈30% from the leading edge).
     */
    public static final float MOBILE_SCROLL_MARGIN_FRACTION = 0.3f;

    /**
     * Mobile {@link com.axehigh.platformer.ui.LayoutMode#BAND_ZOOM} camera zoom: values &lt; 1 zoom in,
     * so on phones the world tiles stay large above the control band while the frame shows a
     * cropped slice of the world (flip rooms then scroll via the dead-zone camera).
     */
    public static final float MOBILE_ZOOM = 0.55f;

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

    //UI Only for touch control
    public static float UI_Button_Action_Size = 130f;
    public static float UI_Button_Move_Size = 150f;
    public static final float UI_Button_Jump_Size = 170f;
    public static final float UI_Button_Contextual_Size = 95f;
    public static final float UI_BUTTON_ALPHA = 0.4f;
    public static final float UI_BUTTON_ALPHA_SOLID = 0.85f;
    public static final float UI_BUTTON_PRESS_SCALE = 0.95f;
    public static final float UI_BUTTON_SCALE_DURATION = 0.05f;
    public static final float UI_PADDING_TOUCH = 16f;
    public static final float UI_BOTTOM_PAD = 24f;

    /**
     * Height of the bottom touch-control zone, in UI units (1980x1080 design space), kept at a
     * quarter of the design height so the control cluster (sized from the {@code UI_Button_*}
     * constants) fits inside it. Both the control layout and the reserved game-viewport band are
     * sized from this so the controls and the band always agree.
     */
    public static final float UI_CONTROL_BAND_HEIGHT = 270f;

    /**
     * Hard cap for the reserved touch-control band, as a fraction of the screen height. The band
     * never exceeds a quarter of the screen, so the world always keeps at least 75% of the screen
     * above the controls (see {@code GameScreen#applyLayoutMode()}).
     */
    public static final float UI_BAND_SCREEN_FRACTION = 0.25f;

    //UI Menu, everything else.
    public static final float UI_PADDING = 33f;
    public static float FontScale = 1f;

    private GameConstants() {
    }
}
