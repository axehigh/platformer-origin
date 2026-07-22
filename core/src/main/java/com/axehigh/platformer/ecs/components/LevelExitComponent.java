package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks an exit-gate entity as an actual level transition trigger (as opposed to a purely
 * decorative gate). Added only when the source Tiled object has a {@code nextLevel} custom
 * property; checked by {@code LevelExitSystem}, which drives the proximity-sensor/interact flow
 * and hands {@code nextLevelPath} to {@code LevelManager.loadLevel(...)}.
 */
public class LevelExitComponent implements Component {
    /** Target .tmx asset path (e.g. "maps/demo_room.tmx"), read from the object's `nextLevel` property. */
    public String nextLevelPath = "";
}
