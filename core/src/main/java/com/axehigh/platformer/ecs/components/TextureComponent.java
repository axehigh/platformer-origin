package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Holds the currently visible texture region for an entity. */
public class TextureComponent implements Component, Poolable {
    public TextureRegion region;

    @Override
    public void reset() {
        region = null;
    }
}
