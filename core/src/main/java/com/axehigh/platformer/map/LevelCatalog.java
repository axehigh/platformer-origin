package com.axehigh.platformer.map;

import com.badlogic.gdx.utils.Array;

/** Static catalog of the game's selectable levels, backed by the existing assets/maps/*.tmx files. */
public final class LevelCatalog {
    private static final Array<LevelDefinition> LEVELS = new Array<>();

    static {
//        LEVELS.add(new LevelDefinition("demo_room_start", "Demo Room Start", "maps/demo_room_start.tmx"));
//        LEVELS.add(new LevelDefinition("demo_room", "Demo Room", "maps/demo_room.tmx"));
//        LEVELS.add(new LevelDefinition("demo_room_final", "Demo Room Final", "maps/demo_room_final.tmx"));
//        LEVELS.add(new LevelDefinition("sample_room", "Sample Room", "maps/sample_room.tmx"));
        LEVELS.add(new LevelDefinition("world1_level01", "World 1 - Level 1", "maps/world1/level_01.tmx"));
        LEVELS.add(new LevelDefinition("world1_level02", "World 1 - Level 2", "maps/world1/level_02.tmx"));
        LEVELS.add(new LevelDefinition("world1_level03", "World 1 - Level 3", "maps/world1/level_03.tmx"));
        LEVELS.add(new LevelDefinition("world1_level04", "World 1 - Level 4", "maps/world1/level_04.tmx"));
        LEVELS.add(new LevelDefinition("world1_level05", "World 1 - Level 5", "maps/world1/level_05.tmx"));
        LEVELS.add(new LevelDefinition("world1_level06", "World 1 - Level 6", "maps/world1/level_06.tmx"));
        LEVELS.add(new LevelDefinition("world1_level07", "World 1 - Level 7", "maps/world1/level_07.tmx"));
        LEVELS.add(new LevelDefinition("world1_level08", "World 1 - Level 8", "maps/world1/level_08.tmx"));
        LEVELS.add(new LevelDefinition("world1_level09", "World 1 - Level 9", "maps/world1/level_09.tmx"));
        LEVELS.add(new LevelDefinition("world1_level10", "World 1 - Level 10", "maps/world1/level_10.tmx"));
    }

    private LevelCatalog() {
    }

    public static Array<LevelDefinition> levels() {
        return LEVELS;
    }
}
