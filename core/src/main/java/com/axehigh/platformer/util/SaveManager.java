package com.axehigh.platformer.util;

import com.axehigh.platformer.map.SaveData;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;

/** Serializes/deserializes a single {@link SaveData} snapshot as JSON inside the shared settings {@link Preferences}. */
public final class SaveManager {
    private static final String PREFS_NAME = "axehigh-platformer-settings";
    private static final String KEY_SAVE = "save";

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
}
