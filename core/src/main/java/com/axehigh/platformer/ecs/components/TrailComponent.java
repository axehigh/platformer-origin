package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Fading afterimage sprite, cosmetic only — no collision/damage/lifecycle coupling. */
public class TrailComponent implements Component, Poolable {
    /** Total lifespan in seconds. */
    public float lifeTime = 0.12f;
    /** Seconds since this trail entity was spawned. */
    public float age = 0f;

    @Override
    public void reset() {
        lifeTime = 0.12f;
        age = 0f;
    }
}
