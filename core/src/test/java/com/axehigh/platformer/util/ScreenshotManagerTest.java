package com.axehigh.platformer.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Covers the pure naming helpers of {@link ScreenshotManager}; the framebuffer readback needs a GL context. */
public class ScreenshotManagerTest {

    @Before
    public void setUp() {
        com.axehigh.platformer.util.ScreenshotManager.resetForTests();
    }

    @After
    public void tearDown() {
        com.axehigh.platformer.util.ScreenshotManager.resetForTests();
    }

    @Test
    public void labelCollapsesToLowercaseUnderscoredWords() {
        assertEquals("world_1_level_3", com.axehigh.platformer.util.ScreenshotManager.sanitize("World 1 - Level 3"));
        assertEquals("dungeon_2_level_10_final", com.axehigh.platformer.util.ScreenshotManager.sanitize("Dungeon 2/Level 10 (final)"));
    }

    @Test
    public void blankOrNullLabelFallsBackToGenericName() {
        assertEquals("shot", com.axehigh.platformer.util.ScreenshotManager.sanitize(null));
        assertEquals("shot", com.axehigh.platformer.util.ScreenshotManager.sanitize("   "));
        assertEquals("shot", com.axehigh.platformer.util.ScreenshotManager.sanitize("***"));
    }

    @Test
    public void fileNamePadsTheCounterAndKeepsTheExtension() {
        assertEquals("origin_world_1_level_1_000.png",
            com.axehigh.platformer.util.ScreenshotManager.fileName("World 1 - Level 1", 0));
        assertEquals("origin_world_1_level_1_042.png",
            com.axehigh.platformer.util.ScreenshotManager.fileName("World 1 - Level 1", 42));
    }

    @Test
    public void requestArmsExactlyOneCaptureUntilFlushed() {
        assertFalse(com.axehigh.platformer.util.ScreenshotManager.isPending());
        com.axehigh.platformer.util.ScreenshotManager.request();
        com.axehigh.platformer.util.ScreenshotManager.request();
        assertTrue(com.axehigh.platformer.util.ScreenshotManager.isPending());
        com.axehigh.platformer.util.ScreenshotManager.resetForTests();
        assertFalse("flushing clears the armed flag", com.axehigh.platformer.util.ScreenshotManager.isPending());
    }

    @Test
    public void flipVerticallyReversesYAxis() {
        com.badlogic.gdx.graphics.Pixmap src = new com.badlogic.gdx.graphics.Pixmap(2, 2, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        src.drawPixel(0, 0, 0xFF0000FF);
        src.drawPixel(0, 1, 0x00FF00FF);

        com.badlogic.gdx.graphics.Pixmap flipped = com.axehigh.platformer.util.NativeScreenshotWriter.flipVertically(src);

        assertEquals(0x00FF00FF, flipped.getPixel(0, 0));
        assertEquals(0xFF0000FF, flipped.getPixel(0, 1));

        src.dispose();
        flipped.dispose();
    }
}
