package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks an exit-gate entity as an actual level transition trigger (as opposed to a purely
 * decorative gate). Added only when the source Tiled object has a {@code nextLevel} custom
 * property; checked by {@code LevelExitSystem}, which drives the proximity-sensor/interact flow
 * and hands {@code nextLevelPath} to {@code LevelManager.loadLevel(...)}. When
 * {@code isFinalLevel} is true, interacting with the gate triggers the victory callback instead
 * of loading the next level.
 */
public class LevelExitComponent implements Component {
    /** Target .tmx asset path (e.g. "maps/demo_room.tmx"), read from the object's `nextLevel` property. */
    public String nextLevelPath = "";
    /** Normalized fade progress [0, 1] driving the door's proximity glow fade-in and fade-out. */
    public float fadeProgress = 0f;
    /** True when this gate is the last level in its world; triggers victory flow instead of loadLevel. */
    public boolean isFinalLevel = false;
}
