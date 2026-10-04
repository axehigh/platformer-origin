package com.axehigh.platformer.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * GWT super-source of {@code NativeScreenshotWriter} (see the core class for why). The browser has
 * no PNG encoder available to Java, but the emulated {@link Pixmap} exposes the 2D canvas it just
 * painted the framebuffer readback into, so the canvas' own {@code toDataURL} encoder produces the
 * PNG and a synthetic anchor click hands it to the browser's download manager. {@code target}'s
 * folder is irrelevant here - the browser chooses the save location.
 *
 * <p>Only ever compiled by the GWT compiler: {@code html/build.gradle} adds {@code src/emu} as a
 * GWT-only source dir, so plain javac (desktop/Android/iOS) never sees this duplicate class.
 */
public final class NativeScreenshotWriter {

    private NativeScreenshotWriter() {
    }

    /** Encodes the pixmap's canvas vertically flipped to a PNG data URL and triggers a browser download. */
    public static void write(Pixmap pixmap, FileHandle target) {
        download(canvasDataUrlVerticallyFlipped(pixmap), target.name());
    }

    private static native String canvasDataUrlVerticallyFlipped(Pixmap pixmap) /*-{
        var canvas = pixmap.@com.badlogic.gdx.graphics.Pixmap::getCanvasElement()();
        var w = canvas.width;
        var h = canvas.height;
        var tempCanvas = $doc.createElement("canvas");
        tempCanvas.width = w;
        tempCanvas.height = h;
        var ctx = tempCanvas.getContext("2d");
        ctx.translate(0, h);
        ctx.scale(1, -1);
        ctx.drawImage(canvas, 0, 0);
        return tempCanvas.toDataURL("image/png");
    }-*/;

    private static native void download(String dataUrl, String fileName) /*-{
        var link = $doc.createElement("a");
        link.href = dataUrl;
        link.download = fileName;
        link.style.display = "none";
        $doc.body.appendChild(link);
        link.click();
        $doc.body.removeChild(link);
    }-*/;
}
