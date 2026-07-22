package com.axehigh.platformer.map;

/** Immutable metadata describing a single selectable level: id, display name, and .tmx path. */
public class LevelDefinition {
    public final String id;
    public final String displayName;
    public final String tmxPath;

    public LevelDefinition(String id, String displayName, String tmxPath) {
        this.id = id;
        this.displayName = displayName;
        this.tmxPath = tmxPath;
    }
}
