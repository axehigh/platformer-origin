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
    public void isLastWorld_treatsWorld2AsFinal() {
        // World 2 is the last real (player-facing) world.
        assertTrue(com.axehigh.platformer.map.LevelCatalog.isLastWorld(com.axehigh.platformer.map.LevelCatalog.WORLD_2));
        assertFalse(com.axehigh.platformer.map.LevelCatalog.isLastWorld(com.axehigh.platformer.map.LevelCatalog.WORLD_1));
    }

    @Test
    public void isLastWorld_tutorialIsNotFinal() {
        assertFalse(com.axehigh.platformer.map.LevelCatalog.isLastWorld(com.axehigh.platformer.map.LevelCatalog.WORLD_TUTORIAL));
    }

    @Test
    public void isLastWorld_demoNeverFinal() {
        // The demo/dev world should never be considered the game-winning world.
        assertFalse(com.axehigh.platformer.map.LevelCatalog.isLastWorld(com.axehigh.platformer.map.LevelCatalog.WORLD_DEMO));
    }

    @Test
    public void world2_elevenLevels_chainCatchesUpToFinalFile() {
        Array<com.axehigh.platformer.map.LevelDefinition> world2 = com.axehigh.platformer.map.LevelCatalog.levelsForWorld(com.axehigh.platformer.map.LevelCatalog.WORLD_2);
        assertEquals(11, world2.size);
        com.axehigh.platformer.map.LevelDefinition last = world2.peek();
        assertEquals("maps/world2/level_10_final.tmx", last.tmxPath);
        // level_09 is the penultimate entry, chaining into the final.
        assertEquals("maps/world2/level_09.tmx", world2.get(9).tmxPath);
    }

    @Test
    public void worldIdForPath_resolvesFinalLevelToWorld2() {
        assertEquals(com.axehigh.platformer.map.LevelCatalog.WORLD_2,
            com.axehigh.platformer.map.LevelCatalog.worldIdForPath("maps/world2/level_10_final.tmx"));
    }
}
