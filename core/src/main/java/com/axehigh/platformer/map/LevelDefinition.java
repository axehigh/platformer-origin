package com.axehigh.platformer.map;

/** Immutable metadata describing a single selectable level: owning world id, id, display name, and .tmx path. */
public class LevelDefinition {
    public final int worldId;
    public final String id;
    public final String displayName;
    public final String tmxPath;

    public LevelDefinition(int worldId, String id, String displayName, String tmxPath) {
        this.worldId = worldId;
        this.id = id;
        this.displayName = displayName;
        this.tmxPath = tmxPath;
    }
}
