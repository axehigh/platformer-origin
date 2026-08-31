package com.axehigh.platformer.map;

import com.badlogic.gdx.utils.Array;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link LevelCatalog} helper methods, notably the demo-world-excluding
 * last-world detection used by the victory flow.
 */
public class LevelCatalogTest {

    @Test
    public void isLastWorld_treatsWorld2AsFinal() {
        // World 2 is the last real (player-facing) world; the demo world (0) must not
        // displace it as the terminal world.
        assertTrue(LevelCatalog.isLastWorld(LevelCatalog.WORLD_2));
    }

    @Test
    public void isLastWorld_world1IsNotFinal() {
        assertFalse(LevelCatalog.isLastWorld(LevelCatalog.WORLD_1));
    }

    @Test
    public void isLastWorld_demoNeverFinal() {
        // The demo/dev world should never be considered the game-winning world.
        assertFalse(LevelCatalog.isLastWorld(LevelCatalog.WORLD_DEMO));
    }

    @Test
    public void world2_elevenLevels_chainCatchesUpToFinalFile() {
        Array<LevelDefinition> world2 = LevelCatalog.levelsForWorld(LevelCatalog.WORLD_2);
        assertEquals(11, world2.size);
        LevelDefinition last = world2.peek();
        assertEquals("maps/world2/level_10_final.tmx", last.tmxPath);
        // level_09 is the penultimate entry, chaining into the final.
        assertEquals("maps/world2/level_09.tmx", world2.get(9).tmxPath);
    }

    @Test
    public void worldIdForPath_resolvesFinalLevelToWorld2() {
        assertEquals(LevelCatalog.WORLD_2,
            LevelCatalog.worldIdForPath("maps/world2/level_10_final.tmx"));
    }
}
