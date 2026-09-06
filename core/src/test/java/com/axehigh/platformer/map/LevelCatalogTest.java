package com.axehigh.platformer.map;

import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link LevelCatalog} helper methods, notably the demo-world-excluding
 * last-world detection used by the victory flow.
 */
public class LevelCatalogTest {

    @Test
    public void isLastWorld_treatsWorld3AsFinal() {
        // World 3 is the last real (player-facing) world; World 2 is now mid-campaign.
        assertTrue(LevelCatalog.isLastWorld(LevelCatalog.WORLD_3));
        assertFalse(LevelCatalog.isLastWorld(LevelCatalog.WORLD_2));
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
    public void world3_tenLevels_chainEndsInFinalFile() {
        Array<LevelDefinition> world3 = LevelCatalog.levelsForWorld(LevelCatalog.WORLD_3);
        assertEquals(10, world3.size);
        assertEquals("maps/world3/level_01.tmx", world3.first().tmxPath);
        assertEquals("maps/world3/level_10_final.tmx", world3.peek().tmxPath);
        // level_09 is the penultimate entry, chaining into the final.
        assertEquals("maps/world3/level_09.tmx", world3.get(8).tmxPath);
    }

    @Test
    public void worldIdForPath_resolvesFinalLevelToWorld3() {
        assertEquals(LevelCatalog.WORLD_3,
            LevelCatalog.worldIdForPath("maps/world3/level_10_final.tmx"));
        assertEquals(LevelCatalog.WORLD_2,
            LevelCatalog.worldIdForPath("maps/world2/level_10_final.tmx"));
    }
}
