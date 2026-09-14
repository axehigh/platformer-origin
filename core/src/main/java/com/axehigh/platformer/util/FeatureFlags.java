package com.axehigh.platformer.util;

import com.badlogic.gdx.Gdx;

/**
 * Session-wide runtime channel for developer feature flags (mirroring the static toggle pattern of
 * {@code DebugRenderSystem#debugEnabled}, but persistence-backed). The static values are seeded once
 * from {@link GamePreferences} on first read and survive level reloads within a session; {@code
 * set...} methods also persist them so they survive app restarts.
 * <p>
 * Reading/writing is safe without a running {@code Gdx} application (e.g. headless unit tests):
 * reads fall back to the documented default, writes update the static without persisting.
 */
public final class FeatureFlags {
    private static boolean wallClimbingEnabled = GamePreferences.DEFAULT_WALL_CLIMB_ENABLED;
    private static boolean squashEnabled = GamePreferences.DEFAULT_SQUASH_ENABLED;
    private static boolean selectLevelEnabled = GamePreferences.DEFAULT_SELECT_LEVEL_ENABLED;
    private static boolean levelOpen = GamePreferences.DEFAULT_LEVEL_OPEN;
    private static boolean embersEnabled = GamePreferences.DEFAULT_EMBERS_ENABLED;
    private static boolean wallClankEnabled = GamePreferences.DEFAULT_WALL_CLANK_ENABLED;
    private static boolean softStopEnabled = GamePreferences.DEFAULT_SOFT_STOP_ENABLED;
    private static boolean vignetteEnabled = GamePreferences.DEFAULT_VIGNETTE_ENABLED;
    private static boolean slashArcEnabled = GamePreferences.DEFAULT_SLASH_ARC_ENABLED;
    private static boolean initialized = false;

    private FeatureFlags() {
    }

    private static void ensureInitialized() {
        if (initialized || Gdx.app == null) {
            return;
        }
        initialized = true;
        GamePreferences preferences = new GamePreferences();
        wallClimbingEnabled = preferences.isWallClimbingEnabled();
        squashEnabled = preferences.isSquashEnabled();
        selectLevelEnabled = preferences.isSelectLevelEnabled();
        levelOpen = preferences.isLevelOpen();
        embersEnabled = preferences.isEmbersEnabled();
        wallClankEnabled = preferences.isWallClankEnabled();
        softStopEnabled = preferences.isSoftStopEnabled();
        vignetteEnabled = preferences.isVignetteEnabled();
        slashArcEnabled = preferences.isSlashArcEnabled();
    }

    /** Whether wall-climb (wall-slide gravity + wall-jump latch) is enabled. Defaults to {@code true}. */
    public static boolean isWallClimbingEnabled() {
        ensureInitialized();
        return wallClimbingEnabled;
    }

    /** Enables/disables wall-climb for the whole session and persists the choice. */
    public static void setWallClimbingEnabled(boolean enabled) {
        wallClimbingEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setWallClimbingEnabled(enabled);
        }
    }

    /** Whether the player landing squash pulse is enabled. Defaults to {@code false} (disabled for now). */
    public static boolean isSquashEnabled() {
        ensureInitialized();
        return squashEnabled;
    }

    /** Enables/disables the squash pulse for the whole session and persists the choice. */
    public static void setSquashEnabled(boolean enabled) {
        squashEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setSquashEnabled(enabled);
        }
    }

    /**
     * Whether the main menu shows its Select Level entry. Defaults to {@code true}.
     */
    public static boolean isSelectLevelEnabled() {
        ensureInitialized();
        return selectLevelEnabled;
    }

    /** Shows/hides the main menu's Select Level entry for the whole session and persists the choice. */
    public static void setSelectLevelEnabled(boolean enabled) {
        selectLevelEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setSelectLevelEnabled(enabled);
        }
    }

    public static boolean isEmbersEnabled() {
        ensureInitialized();
        return embersEnabled;
    }

    public static void setEmbersEnabled(boolean enabled) {
        embersEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setEmbersEnabled(enabled);
        }
    }

    /**
     * Whether all levels are open in level select. Defaults to {@code true}.
     */
    public static boolean isLevelOpen() {
        ensureInitialized();
        return levelOpen;
    }

    /** Sets whether all levels are open in level select for the whole session and persists the choice. */
    public static void setLevelOpen(boolean enabled) {
        levelOpen = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setLevelOpen(enabled);
        }
    }

    public static boolean isWallClankEnabled() {
        ensureInitialized();
        return wallClankEnabled;
    }

    public static void setWallClankEnabled(boolean enabled) {
        wallClankEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setWallClankEnabled(enabled);
        }
    }

    /**
     * Whether the player's soft ramp-down into idle is enabled. Defaults to {@code true}; gates
     * the player's friction deceleration in {@code PlayerInputSystem} plus the
     * {@code PLAYER_IDLE_DELAY} pose hold in {@code AnimationSystem}; when OFF the player stops
     * and idles instantly. Toggled from the pause dialog's "Soft Stop" button.
     */
    public static boolean isSoftStopEnabled() {
        ensureInitialized();
        return softStopEnabled;
    }

    /** Sets whether the player's soft ramp-down into idle is enabled for the whole session and persists the choice. */
    public static void setSoftStopEnabled(boolean enabled) {
        softStopEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setSoftStopEnabled(enabled);
        }
    }

    /**
     * Whether the screen-framed vignette (darkened room/map edges) is enabled. Defaults to
     * {@code true}; gates {@code VignetteRenderSystem}. Toggled from the pause dialog's Debug tab
     * and the Settings screen's Debug tab.
     */
    public static boolean isVignetteEnabled() {
        ensureInitialized();
        return vignetteEnabled;
    }

    /** Sets whether the screen-framed vignette is enabled for the whole session and persists the choice. */
    public static void setVignetteEnabled(boolean enabled) {
        vignetteEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setVignetteEnabled(enabled);
        }
    }

    /**
     * Whether the melee slash-arc VFX (cosmetic crescent sprite spawned on each swing) is enabled.
     * Defaults to {@code true}; gates the arc spawn in {@code PlayerInputSystem}.
     */
    public static boolean isSlashArcEnabled() {
        ensureInitialized();
        return slashArcEnabled;
    }

    /** Enables/disables the melee slash-arc VFX for the whole session and persists the choice. */
    public static void setSlashArcEnabled(boolean enabled) {
        slashArcEnabled = enabled;
        initialized = true;
        if (Gdx.app != null) {
            new GamePreferences().setSlashArcEnabled(enabled);
        }
    }

    static void resetForTests() {
        wallClimbingEnabled = GamePreferences.DEFAULT_WALL_CLIMB_ENABLED;
        squashEnabled = GamePreferences.DEFAULT_SQUASH_ENABLED;
        selectLevelEnabled = GamePreferences.DEFAULT_SELECT_LEVEL_ENABLED;
        levelOpen = GamePreferences.DEFAULT_LEVEL_OPEN;
        embersEnabled = GamePreferences.DEFAULT_EMBERS_ENABLED;
        wallClankEnabled = GamePreferences.DEFAULT_WALL_CLANK_ENABLED;
        softStopEnabled = GamePreferences.DEFAULT_SOFT_STOP_ENABLED;
        vignetteEnabled = GamePreferences.DEFAULT_VIGNETTE_ENABLED;
        slashArcEnabled = GamePreferences.DEFAULT_SLASH_ARC_ENABLED;
        initialized = false;
    }
}
