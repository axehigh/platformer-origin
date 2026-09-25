package com.axehigh.platformer.util;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * End-of-frame framebuffer capture, armed by the {@code P} hotkey or the Pause dialog's
 * "Screenshot" button and flushed by {@code GameScreen.render()} once every stage has drawn
 * (world, HUD, touch overlay, inventory), so the shot contains the whole composited frame.
 *
 * <p>Arm-then-flush (rather than capturing straight from the key handler) is what keeps the shot
 * free of input artifacts: the request is recorded while the key goes down, and the readback
 * happens at the tail of the frame that renders the finished picture.
 *
 * <p>Output lands in a {@code screenshots} folder next to the app and the absolute path is logged
 * under the {@code Screenshot} tag. File names are {@code origin_<label>_<index>.png} - a counter,
 * not a clock, because {@code SimpleDateFormat} is not emulated by GWT.
 */
public final class ScreenshotManager {

    private static final String TAG = "Screenshot";
    private static final String DIRECTORY = "screenshots";
    private static final String PREFIX = "origin_";
    private static final String EXTENSION = ".png";
    private static final int INDEX_DIGITS = 3;

    private static boolean pending;
    private static int captureIndex;

    private ScreenshotManager() {
    }

    /** Arms a capture for the end of the current frame. Repeated calls before the flush collapse into one shot. */
    public static void request() {
        pending = true;
    }

    /** True while an armed capture is still waiting for the end of the frame. */
    public static boolean isPending() {
        return pending;
    }

    /**
     * Reads the framebuffer back and writes it to disk if a capture is armed. Never throws: a failed
     * capture is logged so a bad hotkey press can never break the game loop.
     *
     * @param label free-form scene label used in the file name (e.g. "World 1 - Level 3")
     */
    public static void captureIfPending(String label) {
        if (!pending) {
            return;
        }
        pending = false;
        if (Gdx.graphics == null || Gdx.app == null) {
            return;
        }
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        if (width <= 0 || height <= 0) {
            Gdx.app.error(TAG, "capture skipped: no backbuffer (" + width + "x" + height + ")");
            return;
        }
        Pixmap pixmap = null;
        try {
            pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
            FileHandle target = nextFile(label);
            NativeScreenshotWriter.write(pixmap, target);
            Gdx.app.log(TAG, "saved " + target.path() + " (" + width + "x" + height + ")");
        } catch (Exception e) {
            Gdx.app.error(TAG, "capture failed", e);
        } finally {
            if (pixmap != null) {
                pixmap.dispose();
            }
        }
    }

    /** Resolves the next unused file inside the platform's screenshot folder. */
    private static FileHandle nextFile(String label) {
        FileHandle directory = directory();
        directory.mkdirs();
        String name;
        FileHandle candidate;
        do {
            name = fileName(label, captureIndex++);
            candidate = directory.child(name);
        } while (candidate.exists());
        return candidate;
    }

    /** {@code origin_<sanitized label>_<counter>.png}, e.g. {@code origin_world_1_level_3_004.png}. */
    static String fileName(String label, int index) {
        return PREFIX + sanitize(label) + "_" + NumFormat.pad(index, INDEX_DIGITS) + EXTENSION;
    }

    /** Lowercases the label and collapses every run of non-alphanumerics into a single underscore. */
    static String sanitize(String label) {
        if (label == null) {
            return "shot";
        }
        StringBuilder out = new StringBuilder(label.length());
        boolean pendingSeparator = false;
        for (int i = 0; i < label.length(); i++) {
            char c = Character.toLowerCase(label.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (pendingSeparator && out.length() > 0) {
                    out.append('_');
                }
                pendingSeparator = false;
                out.append(c);
            } else {
                pendingSeparator = true;
            }
        }
        return out.length() == 0 ? "shot" : out.toString();
    }

    /**
     * Best-effort writable folder for the current platform: app-scoped external storage on Android
     * (where a screenshot gallery can actually see it), the local/app folder everywhere else.
     */
    private static FileHandle directory() {
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            try {
                return Gdx.files.external(DIRECTORY);
            } catch (Exception e) {
                Gdx.app.log(TAG, "external storage unavailable, falling back to local: " + e.getMessage());
            }
        }
        return Gdx.files.local(DIRECTORY);
    }

    /** Test seam: drops the armed flag and rewinds the file counter. */
    static void resetForTests() {
        pending = false;
        captureIndex = 0;
    }
}
