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

    /**
     * Restores the pristine pre-init state. Test support (same package): headless tests must call
     * this before/after exercising the static so no state bleeds across test methods.
     */
    static void resetForTests() {
        wallClimbingEnabled = GamePreferences.DEFAULT_WALL_CLIMB_ENABLED;
        initialized = false;
    }
}
