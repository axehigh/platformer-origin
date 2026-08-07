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
        LEVELS.add(new LevelDefinition("lvl1_demo1", "Level 1 demo 1", "maps/level1/level_1_demo.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_demo1b", "Level 1 demo 1b", "maps/level1/level_1_demo_padded.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_demo2", "Level 1 demo 2", "maps/level1/level_1_demo_2.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_demo3", "Level 1 demo 3", "maps/level1/level_1_demo_3.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_generated1", "Long 1", "maps/level1/generated_room.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_generated2", "Single 1", "maps/level1/generated_single_room.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_generated3", "Single 2", "maps/level1/generated_single_room2.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_generated4", "Single Secret Inside", "maps/level1/generated_single_secret_inside.tmx"));
        LEVELS.add(new LevelDefinition("lvl1_demo_secret", "Level 1 demo secret", "maps/level1/level_1_demo_secret.tmx"));
        LEVELS.add(new LevelDefinition("lvl4_demo", "Level 4 demo", "maps/level1/level_4_demo.tmx"));
    }

    private LevelCatalog() {
    }

    public static Array<LevelDefinition> levels() {
        return LEVELS;
    }
}
