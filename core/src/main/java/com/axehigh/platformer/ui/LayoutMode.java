package com.axehigh.platformer.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;

/**
 * Touch-control layout modes, A/B-testable at runtime via the in-game Pause dialog
 * ("Mobile Layout" button), with {@link #BAND_ZOOM} as the shipped default for every view —
 * desktop, mobile, and tablet (see {@link #defaultForDevice()}). Each mode trades on-screen world
 * size against control-overlap:
 *
 * <ul>
 *   <li>{@link #CORNER_OVERLAY} — no reserved band; the world renders at full screen size and the
 *       controls float, semi-transparent, in the bottom corners / side bars. Biggest world, at the
 *       cost of the bottom corners of the frame sitting under the buttons.</li>
 *   <li>{@link #BAND} — a full-width band along the bottom is reserved for the controls and the
 *       world renders above it (never overlapped), but the world is physically smaller.</li>
 *   <li>{@link #BAND_ZOOM} — the same reserved band, plus a camera zoom so tiles stay big; the
 *       zoomed frame no longer fits screen-sized rooms, so flip rooms behave as dead-zone scroll
 *       rooms (see {@code CameraSystem}). On non-touch devices the band is skipped and only the
 *       camera zoom applies.</li>
 * </ul>
 */
public enum LayoutMode {
    CORNER_OVERLAY,
    BAND,
    BAND_ZOOM;

    /** Cycles to the next mode (wraps around). */
    public LayoutMode next() {
        LayoutMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** True on touch-only platforms (Android/iOS), or any faked non-desktop {@link DeviceClass}. */
    public static boolean isTouchDevice() {
        DeviceClass simulated = DeviceClass.simulated();
        if (simulated != null) {
            return simulated.isTouch();
        }
        Application.ApplicationType type = Gdx.app.getType();
        return type == Application.ApplicationType.Android || type == Application.ApplicationType.iOS;
    }

    /**
     * Shipped default: every view — desktop, mobile, and tablet — renders with the
     * {@link #BAND_ZOOM} world (reserved control band + camera zoom so tiles stay big on touch
     * devices; on non-touch devices the band is skipped and only the zoom applies). A faked
     * {@link DeviceClass} returns its own per-class default (currently {@link #BAND_ZOOM} for all
     * classes).
     */
    public static LayoutMode defaultForDevice() {
        DeviceClass simulated = DeviceClass.simulated();
        if (simulated != null) {
            return simulated.defaultLayout();
        }
        return BAND_ZOOM;
    }

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }
}
