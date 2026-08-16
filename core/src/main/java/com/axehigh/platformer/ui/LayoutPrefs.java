package com.axehigh.platformer.ui;

import com.axehigh.platformer.util.GamePreferences;
import com.badlogic.gdx.Gdx;

/**
 * Persisted-device channel for the pause dialog's "Device" and "Mobile Layout" buttons. The chosen
 * {@link DeviceClass} override and {@link LayoutMode} are saved so they survive app restarts; reads
 * fall back to {@code null} (real platform detection / shipped default) when nothing was saved.
 *
 * <p>Reading/writing is safe without a running {@code Gdx} application (e.g. headless unit tests):
 * reads return {@code null} and writes are no-ops.
 */
public final class LayoutPrefs {

    private LayoutPrefs() {
    }

    /** The saved {@link DeviceClass} override, or {@code null} for "Auto" (real detection). */
    public static DeviceClass savedDevice() {
        if (Gdx.app == null) {
            return null;
        }
        return new GamePreferences().getDeviceClass();
    }

    /** The saved {@link LayoutMode}, or {@code null} when unset (fall back to the shipped default). */
    public static LayoutMode savedLayout() {
        if (Gdx.app == null) {
            return null;
        }
        return new GamePreferences().getLayoutMode();
    }

    /** Persists the current device override (nullable = "Auto") and layout mode. */
    public static void save(DeviceClass deviceClass, LayoutMode layoutMode) {
        if (Gdx.app == null) {
            return;
        }
        GamePreferences preferences = new GamePreferences();
        preferences.setDeviceClass(deviceClass);
        preferences.setLayoutMode(layoutMode);
    }
}
