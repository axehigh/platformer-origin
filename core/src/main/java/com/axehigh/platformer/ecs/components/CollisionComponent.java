package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Bounding box (in world units, relative to the entity's TransformComponent position) used for AABB checks. */
public class CollisionComponent implements Component, Poolable {
    public final Rectangle bounds = new Rectangle();

    @Override
    public void reset() {
        bounds.set(0f, 0f, 0f, 0f);
    }
}
