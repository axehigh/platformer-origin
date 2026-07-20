package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Position, scale, rotation and z-layer (draw order) of an entity. */
public class TransformComponent implements Component, Poolable {
    public final Vector2 position = new Vector2();
    public final Vector2 scale = new Vector2(1f, 1f);
    public float rotation = 0f;
    /** Higher z is drawn on top. */
    public float z = 0f;

    @Override
    public void reset() {
        position.setZero();
        scale.set(1f, 1f);
        rotation = 0f;
        z = 0f;
    }
}
