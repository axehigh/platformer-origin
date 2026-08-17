package com.axehigh.platformer.map;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/** Static catalog of the game's selectable levels, backed by the existing assets/maps/*.tmx files. */
public final class LevelCatalog {
    public static final int WORLD_DEMO = 0;
    public static final int WORLD_1 = 1;
    public static final int WORLD_2 = 2;

    private static final Array<LevelDefinition> LEVELS = new Array<>();

    static {
        //demo
        LEVELS.add(new LevelDefinition(WORLD_DEMO, "demo_platforming_24x10", "Platforming 24x10", "maps/world_demo/platforming_24x10.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_DEMO, "demo_platforming_30x17", "Platforming 30x17", "maps/world_demo/platforming_30x17.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_DEMO, "demo_platforming_clamp", "Platforming Clamp", "maps/world_demo/platforming_clamp.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_DEMO, "demo_platforming_grid", "Platforming Grid", "maps/world_demo/platforming_grid.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_DEMO, "demo_platforming_secret", "Platforming Secret", "maps/world_demo/platforming_secret.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_DEMO, "demo_template", "Template Demo", "maps/world_demo/template_demo.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_DEMO, "demo_my_map", "My Map", "maps/world_demo/my_map.tmx"));

        //world 1
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

        //world 2
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level01", "World 2 - Level 1", "maps/world2/level_01.tmx"));
        LEVELS.add(new LevelDefinition(WORLD_2, "world2_level01b", "World 2 - Level 1b", "maps/world2/level_01b.tmx"));
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

    /** Human-readable tab label for a world id. */
    public static String worldName(int worldId) {
        if (worldId == WORLD_DEMO) return "Demo";
        return "World " + worldId;
    }
}
