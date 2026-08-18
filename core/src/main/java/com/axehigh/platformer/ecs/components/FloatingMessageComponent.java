package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Pool.Poolable;

/** A short-lived text label that floats upward from the player and fades out. */
public class FloatingMessageComponent implements Component, Poolable {
    public String text = "";
    public float lifetime = 1f;
    public float age = 0f;
    public float driftSpeed = 40f;
    public final Color color = new Color(Color.WHITE);
    public float fontScale = 1f;

    @Override
    public void reset() {
        text = "";
        lifetime = 1f;
        age = 0f;
        driftSpeed = 40f;
        color.set(Color.WHITE);
        fontScale = 1f;
    }
}
