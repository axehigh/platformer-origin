package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Velocity, acceleration and maximum speed limits used by the MovementSystem. */
public class MovementComponent implements Component, Poolable {
    public final Vector2 velocity = new Vector2();
    public final Vector2 acceleration = new Vector2();
    public float maxSpeedX = 100f;
    public float maxSpeedY = 400f;
    public boolean grounded = false;

    @Override
    public void reset() {
        velocity.setZero();
        acceleration.setZero();
        maxSpeedX = 100f;
        maxSpeedY = 400f;
        grounded = false;
    }
}
