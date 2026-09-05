package com.axehigh.platformer.util;

import com.axehigh.platformer.map.ProgressData;
import com.axehigh.platformer.map.SaveData;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;

/**
 * Two-layer persistence inside the shared settings {@link Preferences}: the run-level {@link
 * SaveData} snapshot ({@link #save(SaveData)}/{@link #load()}/{@link #clear()}) and the durable
 * account record of earned stars ({@link #saveProgress(ProgressData)}/{@link #loadProgress()}/
 * {@link #clearProgress()}). {@link #clear()} touches ONLY the run save, never progress.
 */
public final class SaveManager {
    private static final String PREFS_NAME = "axehigh-platformer-settings";
    private static final String KEY_SAVE = "save";
    private static final String KEY_PROGRESS = "progress";

    private SaveManager() {
    }

    public static boolean hasSave() {
        if (Gdx.app == null) {
            return false;
        }
        Preferences preferences = Gdx.app.getPreferences(PREFS_NAME);
        String json = preferences.getString(KEY_SAVE, "");
        return json != null && !json.isEmpty();
    }

    public static void save(SaveData data) {
        Preferences preferences = Gdx.app.getPreferences(PREFS_NAME);
        String json = new Json().toJson(data);
        preferences.putString(KEY_SAVE, json);
        preferences.flush();
    }

    public static void clear() {
        Preferences preferences = Gdx.app.getPreferences(PREFS_NAME);
        preferences.remove(KEY_SAVE);
        preferences.flush();
    }

    /** Returns the persisted save, or {@code null} if none exists. */
    public static SaveData load() {
        if (!hasSave()) {
            return null;
        }
        Preferences preferences = Gdx.app.getPreferences(PREFS_NAME);
        String json = preferences.getString(KEY_SAVE, "");
        return new Json().fromJson(SaveData.class, json);
    }

    public static void saveProgress(ProgressData data) {
        Preferences preferences = Gdx.app.getPreferences(PREFS_NAME);
        String json = new Json().toJson(data);
        preferences.putString(KEY_PROGRESS, json);
        preferences.flush();
    }

    /** Returns the persisted durable progress record, never {@code null}. */
    public static ProgressData loadProgress() {
        if (Gdx.app == null) {
            return new ProgressData();
        }
        Preferences preferences = Gdx.app.getPreferences(PREFS_NAME);
        String json = preferences.getString(KEY_PROGRESS, "");
        if (json == null || json.isEmpty()) {
            return new ProgressData();
        }
        return new Json().fromJson(ProgressData.class, json);
    }

    public static void clearProgress() {
        Preferences preferences = Gdx.app.getPreferences(PREFS_NAME);
        preferences.remove(KEY_PROGRESS);
        preferences.flush();
    }
}
