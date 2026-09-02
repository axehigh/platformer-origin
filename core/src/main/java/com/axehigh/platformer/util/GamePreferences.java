package com.axehigh.platformer.util;

import com.axehigh.platformer.ui.DeviceClass;
import com.axehigh.platformer.ui.LayoutMode;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Thin wrapper around libGDX {@link Preferences}, storing placeholder settings for future use. */
public class GamePreferences {
    private static final String PREFS_NAME = "axehigh-platformer-settings";
    private static final String KEY_MUSIC_VOLUME = "musicVolume";
    private static final String KEY_SFX_VOLUME = "sfxVolume";
    private static final String KEY_MUSIC_ENABLED = "musicEnabled";
    private static final String KEY_SFX_ENABLED = "sfxEnabled";
    private static final String KEY_DEBUG_MODE = "debugMode";
    private static final String KEY_WALL_CLIMB_ENABLED = "wallClimbEnabled";
    private static final String KEY_SQUASH_ENABLED = "squashEnabled";
    private static final String KEY_SELECT_LEVEL_ENABLED = "selectLevelEnabled";
    private static final String KEY_LEVEL_OPEN = "levelOpen";
    private static final String KEY_EMBERS_ENABLED = "embersEnabled";
    private static final String KEY_VIGNETTE_PLAYER_CENTRIC = "vignettePlayerCentricEnabled";
    private static final String KEY_VIGNETTE_CINEMATIC = "vignetteCinematicEnabled";
    private static final String KEY_DEVICE_CLASS = "deviceClass";
    private static final String KEY_LAYOUT_MODE = "layoutMode";
    private static final String KEY_UI_ICON_SCALE = "uiIconScale";

    private static final float DEFAULT_MUSIC_VOLUME = 100f;
    private static final float DEFAULT_SFX_VOLUME = 100f;
    private static final boolean DEFAULT_MUSIC_ENABLED = true;
    private static final boolean DEFAULT_SFX_ENABLED = true;
    private static final boolean DEFAULT_DEBUG_MODE = false;
    private static final float DEFAULT_UI_ICON_SCALE = 2f;
    /** Shared with {@code FeatureFlags} so the runtime default and the persisted default never diverge. */
    static final boolean DEFAULT_WALL_CLIMB_ENABLED = true;
    /** Shared with {@code FeatureFlags} so the runtime default and the persisted default never diverge. */
    static final boolean DEFAULT_SQUASH_ENABLED = false;
    /** Shared with {@code FeatureFlags} so the runtime default and the persisted default never diverge. */
    static final boolean DEFAULT_SELECT_LEVEL_ENABLED = true;
    /** Shared with {@code FeatureFlags} so the runtime default and the persisted default never diverge. */
    static final boolean DEFAULT_LEVEL_OPEN = true;
    /** Shared with {@code FeatureFlags} so the runtime default and the persisted default never diverge. */
    static final boolean DEFAULT_EMBERS_ENABLED = true;
    /** Shared with {@code FeatureFlags} so the runtime default and the persisted default never diverge. */
    static final boolean DEFAULT_VIGNETTE_PLAYER_CENTRIC = true;
    /** Shared with {@code FeatureFlags} so the runtime default and the persisted default never diverge. */
    static final boolean DEFAULT_VIGNETTE_CINEMATIC = true;

    private final Preferences preferences;

    public GamePreferences() {
        this.preferences = Gdx.app.getPreferences(PREFS_NAME);
    }

    public float getMusicVolume() {
        return preferences.getFloat(KEY_MUSIC_VOLUME, DEFAULT_MUSIC_VOLUME);
    }

    public void setMusicVolume(float musicVolume) {
        preferences.putFloat(KEY_MUSIC_VOLUME, musicVolume);
        preferences.flush();
    }

    public float getSfxVolume() {
        return preferences.getFloat(KEY_SFX_VOLUME, DEFAULT_SFX_VOLUME);
    }

    public void setSfxVolume(float sfxVolume) {
        preferences.putFloat(KEY_SFX_VOLUME, sfxVolume);
        preferences.flush();
    }

    public boolean isMusicEnabled() {
        return preferences.getBoolean(KEY_MUSIC_ENABLED, DEFAULT_MUSIC_ENABLED);
    }

    public void setMusicEnabled(boolean musicEnabled) {
        preferences.putBoolean(KEY_MUSIC_ENABLED, musicEnabled);
        preferences.flush();
    }

    public boolean isSfxEnabled() {
        return preferences.getBoolean(KEY_SFX_ENABLED, DEFAULT_SFX_ENABLED);
    }

    public void setSfxEnabled(boolean sfxEnabled) {
        preferences.putBoolean(KEY_SFX_ENABLED, sfxEnabled);
        preferences.flush();
    }

    public boolean isDebugMode() {
        return preferences.getBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
    }

    public void setDebugMode(boolean debugMode) {
        preferences.putBoolean(KEY_DEBUG_MODE, debugMode);
        preferences.flush();
    }

    /**
     * Whether the wall-climb traversal feature (wall-slide + wall-jump) is enabled. A developer
     * feature flag, toggled from the in-game pause dialog; see {@code FeatureFlags}.
     */
    public boolean isWallClimbingEnabled() {
        return preferences.getBoolean(KEY_WALL_CLIMB_ENABLED, DEFAULT_WALL_CLIMB_ENABLED);
    }

    public void setWallClimbingEnabled(boolean wallClimbingEnabled) {
        preferences.putBoolean(KEY_WALL_CLIMB_ENABLED, wallClimbingEnabled);
        preferences.flush();
    }

    /**
     * Whether the player landing squash-and-stretch pulse is enabled. A developer feature flag
     * (default {@code false} — the squash look is not final); see {@code FeatureFlags}.
     */
    public boolean isSquashEnabled() {
        return preferences.getBoolean(KEY_SQUASH_ENABLED, DEFAULT_SQUASH_ENABLED);
    }

    public void setSquashEnabled(boolean squashEnabled) {
        preferences.putBoolean(KEY_SQUASH_ENABLED, squashEnabled);
        preferences.flush();
    }

    /**
     * Whether the main menu shows the Select Level entry. A developer feature flag (default
     * {@code true}); see {@code FeatureFlags}.
     */
    public boolean isSelectLevelEnabled() {
        return preferences.getBoolean(KEY_SELECT_LEVEL_ENABLED, DEFAULT_SELECT_LEVEL_ENABLED);
    }

    public void setSelectLevelEnabled(boolean selectLevelEnabled) {
        preferences.putBoolean(KEY_SELECT_LEVEL_ENABLED, selectLevelEnabled);
        preferences.flush();
    }

    /**
     * Whether all levels are open/selectable in level select or restricted to completed/next.
     * A feature flag (default {@code true}); see {@code FeatureFlags}.
     */
    public boolean isLevelOpen() {
        return preferences.getBoolean(KEY_LEVEL_OPEN, DEFAULT_LEVEL_OPEN);
    }

    public void setLevelOpen(boolean levelOpen) {
        preferences.putBoolean(KEY_LEVEL_OPEN, levelOpen);
        preferences.flush();
    }

    public boolean isEmbersEnabled() {
        return preferences.getBoolean(KEY_EMBERS_ENABLED, DEFAULT_EMBERS_ENABLED);
    }

    public void setEmbersEnabled(boolean embersEnabled) {
        preferences.putBoolean(KEY_EMBERS_ENABLED, embersEnabled);
        preferences.flush();
    }

    public boolean isVignettePlayerCentricEnabled() {
        return preferences.getBoolean(KEY_VIGNETTE_PLAYER_CENTRIC, DEFAULT_VIGNETTE_PLAYER_CENTRIC);
    }

    public void setVignettePlayerCentricEnabled(boolean enabled) {
        preferences.putBoolean(KEY_VIGNETTE_PLAYER_CENTRIC, enabled);
        preferences.flush();
    }

    public boolean isVignetteCinematicEnabled() {
        return preferences.getBoolean(KEY_VIGNETTE_CINEMATIC, DEFAULT_VIGNETTE_CINEMATIC);
    }

    public void setVignetteCinematicEnabled(boolean enabled) {
        preferences.putBoolean(KEY_VIGNETTE_CINEMATIC, enabled);
        preferences.flush();
    }

    /**
     * The persisted {@link DeviceClass} override, or {@code null} when unset / "Auto" (real
     * platform detection).
     */
    public DeviceClass getDeviceClass() {
        return enumOrNull(KEY_DEVICE_CLASS, DeviceClass.class);
    }

    /** Persists the forced {@link DeviceClass}; {@code null} stores "Auto" (real detection). */
    public void setDeviceClass(DeviceClass deviceClass) {
        putEnum(KEY_DEVICE_CLASS, deviceClass);
    }

    /** The persisted {@link LayoutMode}, or {@code null} when unset (fall back to the default). */
    public LayoutMode getLayoutMode() {
        return enumOrNull(KEY_LAYOUT_MODE, LayoutMode.class);
    }

    /** Persists the chosen {@link LayoutMode}; {@code null} stores "unset". */
    public void setLayoutMode(LayoutMode layoutMode) {
        putEnum(KEY_LAYOUT_MODE, layoutMode);
    }

    public float getUiIconScale() {
        return preferences.getFloat(KEY_UI_ICON_SCALE, DEFAULT_UI_ICON_SCALE);
    }

    public void setUiIconScale(float scale) {
        preferences.putFloat(KEY_UI_ICON_SCALE, scale);
        preferences.flush();
    }

    private <E extends Enum<E>> E enumOrNull(String key, Class<E> enumType) {
        String name = preferences.getString(key, "");
        if (name.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void putEnum(String key, Enum<?> value) {
        preferences.putString(key, value == null ? "" : value.name());
        preferences.flush();
    }
}
