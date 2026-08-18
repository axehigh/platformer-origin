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

    //Parallax background
    /** Scroll speed of the far background layer ({@code Background_01}) relative to the world:
     * 0 = glued to the camera (skybox), 1 = world-locked. Kept below the near layer so it recedes. */
    public static final float PARALLAX_BG_FAR = 0.25f;
    /** Scroll speed of the near background layer ({@code Background_02}) relative to the world. */
    public static final float PARALLAX_BG_NEAR = 0.5f;
    /** Height of one tile in world units (VIRTUAL_HEIGHT / 17 rows), used to offset parallax
     *  layers so they don't waste imagery behind the collision ground layer. */
    public static final float PARALLAX_TILE_HEIGHT = 16f;

    /** Maximum overshoot (world units) of an oversized tile beyond its grid cell. A 256px tile
     *  in a 16px grid protrudes 240px past its cell edge — the renderer must keep the tile's
     *  cell in the culling range until the visual is fully off-screen. */
    public static final float TILE_MAX_OVERSHOOT = 240f;

    //Floating message colors
    public static final float[] MESSAGE_COLOR_DAMAGE = {1f, 0.2f, 0.2f};
    public static final float[] MESSAGE_COLOR_COINS = {1f, 0.85f, 0f};
    public static final float[] MESSAGE_COLOR_HEAL = {0.2f, 1f, 0.3f};
    public static final float[] MESSAGE_COLOR_STRENGTH = {1f, 0.6f, 0f};
    public static final float[] MESSAGE_COLOR_SPEED = {0f, 0.9f, 1f};
    public static final float[] MESSAGE_COLOR_INVULN = {1f, 1f, 1f};

    /** Duration (seconds) of the fade-in/fade-out transition between screens. */
    public static final float SCREEN_FADE_TIMER = 0.5f;

    //Movement MaxSpeedX/Y
    public static float MaxSpeedX = 60f;
    public static float MaxSpeedY = 300f;

    //Player Stats
    public static int MaxItems = 10;
    public static int SwordDamage = 5;
    public static int MaxHealth = 3;

    //Potions & buffs
    /** How many of each potion type the player can hold at once. */
    public static int POTION_CAP = 5;
    /** Coins granted instead of a potion pickup when the player is already at {@link #POTION_CAP}. */
    public static int POTION_OVERFLOW_COINS = 5;
    /** Hearts restored by one Healing potion (capped at maxHealth). */
    public static int HEALING_POTION_HEAL = 1;
    /** Minimum delay between two potion drinks (seconds), preventing consumption spam. */
    public static float POTION_USE_COOLDOWN = 0.4f;
    /** Debounce window for batching coin-pickup floating messages (seconds). */
    public static float COIN_MESSAGE_COOLDOWN = 0.3f;
    /** Strength buff: extra melee damage while active. */
    public static int STRENGTH_DAMAGE_BONUS = 3;
    /** Strength buff duration (seconds). */
    public static float STRENGTH_BUFF_DURATION = 20f;
    /** Speed buff: horizontal move-speed multiplier while active. */
    public static float SPEED_MULTIPLIER = 1.5f;
    /** Speed buff duration (seconds). */
    public static float SPEED_BUFF_DURATION = 15f;
    /** Invulnerability buff duration (seconds). */
    public static float INVULNERABILITY_DURATION = 10f;

    //UI

    //UI Only for touch control
    public static float UI_Button_Action_Size = 170f;
    public static float UI_Button_Move_Size = 195f;
    public static final float UI_Button_Jump_Size = 200f;
    public static final float UI_Button_Contextual_Size = 135f;
    public static final float UI_BUTTON_ALPHA = 0.4f;
    public static final float UI_BUTTON_ALPHA_SOLID = 0.85f;
    public static final float UI_BUTTON_PRESS_SCALE = 0.95f;
    public static final float UI_BUTTON_SCALE_DURATION = 0.05f;
    public static final float UI_PADDING_TOUCH = 16f;
    public static final float UI_BOTTOM_PAD = 10f;

    /**
     * Invisible hit-area expansion (UI units, 1980x1080 design space) beyond each touch button's
     * drawn bounds, so the fattened touch target is larger than the visible button without any
     * visual size increase or reserved-band change (see {@code TouchButton#hit}).
     */
    public static final float UI_TOUCH_HIT_PAD = 10f;

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
