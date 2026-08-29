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

    /**
     * Restores the pristine pre-init state. Test support (same package): headless tests must call
     * this before/after exercising the static so no state bleeds across test methods.
     */
    static void resetForTests() {
        wallClimbingEnabled = GamePreferences.DEFAULT_WALL_CLIMB_ENABLED;
        squashEnabled = GamePreferences.DEFAULT_SQUASH_ENABLED;
        selectLevelEnabled = GamePreferences.DEFAULT_SELECT_LEVEL_ENABLED;
        levelOpen = GamePreferences.DEFAULT_LEVEL_OPEN;
        embersEnabled = GamePreferences.DEFAULT_EMBERS_ENABLED;
        initialized = false;
    }
}
