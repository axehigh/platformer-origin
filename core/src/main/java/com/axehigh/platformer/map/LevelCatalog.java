package com.axehigh.platformer.map;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/** Static catalog of the game's selectable levels, backed by the existing assets/maps/*.tmx files. */
public final class LevelCatalog {
    public static final int WORLD_1 = 1;
    public static final int WORLD_2 = 2;

    private static final Array<LevelDefinition> LEVELS = new Array<>();

    static {
//        LEVELS.add(new LevelDefinition(0, "demo_room_start", "Demo Room Start", "maps/demo_room_start.tmx"));
//        LEVELS.add(new LevelDefinition(0, "demo_room", "Demo Room", "maps/demo_room.tmx"));
//        LEVELS.add(new LevelDefinition(0, "demo_room_final", "Demo Room Final", "maps/demo_room_final.tmx"));
//        LEVELS.add(new LevelDefinition(0, "sample_room", "Sample Room", "maps/sample_room.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level01", "World 1 - Level 1", "maps/world1/level_01.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level02", "World 1 - Level 2", "maps/world1/level_02.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level03", "World 1 - Level 3", "maps/world1/level_03.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level04", "World 1 - Level 4", "maps/world1/level_04.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level05", "World 1 - Level 5", "maps/world1/level_05.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level06", "World 1 - Level 6", "maps/world1/level_06.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level07", "World 1 - Level 7", "maps/world1/level_07.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level08", "World 1 - Level 8", "maps/world1/level_08.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level09", "World 1 - Level 9", "maps/world1/level_09.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_1, "world1_level10", "World 1 - Level 10", "maps/world1/level_10.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level01", "World 2 - Level 1", "maps/world2/level_01.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level02", "World 2 - Level 2", "maps/world2/level_02.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level03", "World 2 - Level 3", "maps/world2/level_03.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level04", "World 2 - Level 4", "maps/world2/level_04.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level05", "World 2 - Level 5", "maps/world2/level_05.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level06", "World 2 - Level 6", "maps/world2/level_06.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level07", "World 2 - Level 7", "maps/world2/level_07.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level08", "World 2 - Level 8", "maps/world2/level_08.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level09", "World 2 - Level 9", "maps/world2/level_09.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level10", "World 2 - Level 10", "maps/world2/level_10.tmx"));
    }

    private LevelCatalog() {
    }

    public static Array<LevelDefinition> levels() {
        return LEVELS;
    }

    /** The levels belonging to one world, in catalog order. */
    public static Array<LevelDefinition> levelsForWorld(int worldId) {
        Array<LevelDefinition> result = new Array<>();
        for (LevelDefinition level : LEVELS) {
            if (level.worldId == worldId) {
                result.add(level);
            }
        }
        return result;
    }

    /** The distinct world ids present in the catalog, in catalog order. */
    public static IntArray worldIds() {
        IntArray worlds = new IntArray();
        for (LevelDefinition level : LEVELS) {
            if (!worlds.contains(level.worldId)) {
                worlds.add(level.worldId);
            }
        }
        return worlds;
    }
}
