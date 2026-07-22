package com.axehigh.platformer.map;

import com.badlogic.gdx.utils.Array;

/** Static catalog of the game's selectable levels, backed by the existing assets/maps/*.tmx files. */
public final class LevelCatalog {
    private static final Array<LevelDefinition> LEVELS = new Array<>();

    static {
        LEVELS.add(new LevelDefinition("demo_room_start", "Demo Room Start", "maps/demo_room_start.tmx"));
        LEVELS.add(new LevelDefinition("demo_room", "Demo Room", "maps/demo_room.tmx"));
        LEVELS.add(new LevelDefinition("demo_room_final", "Demo Room Final", "maps/demo_room_final.tmx"));
        LEVELS.add(new LevelDefinition("sample_room", "Sample Room", "maps/sample_room.tmx"));
    }

    private LevelCatalog() {
    }

    public static Array<LevelDefinition> levels() {
        return LEVELS;
    }
}
