package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Marks an entity as a tutorial sign or text trigger with a display text message. */
public class TutorialComponent implements Component, Poolable {
    public String text = "";
    public String highlight = "";
    public boolean active = false;

    @Override
    public void reset() {
        text = "";
        highlight = "";
        active = false;
    }
}
