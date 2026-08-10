package com.axehigh.platformer.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;

/**
 * Device classes for previewing mobile layouts on desktop. Each class carries the shipped default
 * {@link LayoutMode} and a representative window shape (used to resize the desktop window so the
 * band height, letterboxing, and aspect heuristic behave exactly like the real device).
 *
 * <p>A static {@link #simulated()} override lets a developer force a device class from the pause
 * dialog's "Device" button. It defaults to {@code null} (real platform detection) and survives
 * level reloads within a session, mirroring the static-toggle pattern of the collision-debug flag.
 */
public enum DeviceClass {
    DESKTOP(1280, 720, LayoutMode.CORNER_OVERLAY),
    PHONE(2400, 1080, LayoutMode.BAND_ZOOM),
    TABLET(1600, 1200, LayoutMode.BAND);

    private static DeviceClass simulated;

    private final int windowWidth;
    private final int windowHeight;
    private final LayoutMode defaultLayout;

    DeviceClass(int windowWidth, int windowHeight, LayoutMode defaultLayout) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.defaultLayout = defaultLayout;
    }

    /** The forced device class, or {@code null} when using the real platform's detection. */
    public static DeviceClass simulated() {
        return simulated;
    }

    /** Forces a device class ({@code null} restores real platform detection). */
    public static void setSimulated(DeviceClass deviceClass) {
        simulated = deviceClass;
    }

    /** Whether a device class is being forced. */
    public static boolean isSimulating() {
        return simulated != null;
    }

    /** True if this class presents touch controls (everything but desktop). */
    public boolean isTouch() {
        return this != DESKTOP;
    }

    /** The shipped default {@link LayoutMode} for this class. */
    public LayoutMode defaultLayout() {
        return defaultLayout;
    }

    /** Representative window width for the desktop fake. */
    public int windowWidth() {
        return windowWidth;
    }

    /** Representative window height for the desktop fake. */
    public int windowHeight() {
        return windowHeight;
    }

    /** Cycles to the next class (wraps around). */
    public DeviceClass next() {
        DeviceClass[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Resizes the desktop window to this class's representative shape (no-op on mobile backends). */
    public void applyWindowSize() {
        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            Gdx.graphics.setWindowedMode(windowWidth, windowHeight);
        }
    }

    @Override
    public String toString() {
        return name();
    }
}
