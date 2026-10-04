package com.axehigh.platformer.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

/**
 * Writes a captured framebuffer {@link Pixmap} out as a PNG file. Used by every native backend
 * (LWJGL3 desktop, Android, iOS/RoboVM).
 *
 * <p>The GWT/HTML build cannot see this class: {@code PixmapIO} is excluded from the GWT source set
 * (no {@code DeflaterOutputStream} in the browser JRE emulation). {@code html/src/emu} therefore
 * super-sources this file with a build that hands the pixmap's canvas to the browser's own PNG
 * encoder and downloads it instead - same signature, so {@link ScreenshotManager} is platform-agnostic.
 */
public final class NativeScreenshotWriter {

    private NativeScreenshotWriter() {
    }

    /** Creates any missing parent folders, then writes {@code pixmap} flipped vertically to {@code target} as a PNG. */
    public static void write(Pixmap pixmap, FileHandle target) {
        FileHandle parent = target.parent();
        if (parent != null) {
            parent.mkdirs();
        }
        Pixmap flipped = flipVertically(pixmap);
        try {
            PixmapIO.writePNG(target, flipped);
        } finally {
            flipped.dispose();
        }
    }

    /** Returns a new pixmap that is a vertical mirror of the source pixmap. */
    public static Pixmap flipVertically(Pixmap src) {
        int width = src.getWidth();
        int height = src.getHeight();
        Pixmap flipped = new Pixmap(width, height, src.getFormat());
        flipped.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flipped.drawPixel(x, height - 1 - y, src.getPixel(x, y));
            }
        }
        return flipped;
    }
}
