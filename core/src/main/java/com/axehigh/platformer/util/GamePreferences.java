package com.axehigh.platformer.util;

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

    private static final float DEFAULT_MUSIC_VOLUME = 100f;
    private static final float DEFAULT_SFX_VOLUME = 100f;
    private static final boolean DEFAULT_MUSIC_ENABLED = true;
    private static final boolean DEFAULT_SFX_ENABLED = true;
    private static final boolean DEFAULT_DEBUG_MODE = false;

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
}
