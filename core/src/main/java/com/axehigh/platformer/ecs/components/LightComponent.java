package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Marks a glowing light source, drawn additively by {@code LightRenderSystem}. */
public class LightComponent implements Component, Poolable {
    /** Halo radius in world units (before flicker scaling). */
    public float radius = 96f;
    /** Tint of the halo (white core gradient is multiplied by this color). */
    public Color color = new Color(1f, 0.85f, 0.6f, 1f);
    /** Base alpha; flicker oscillates around it. */
    public float baseAlpha = 0.85f;
    /** Fraction of the radius the flicker can add/subtract. */
    public float flickerAmplitude = 0.15f;
    /** Oscillation speed in rad/s. */
    public float flickerSpeed = 6f;
    /** Per-light phase offset so multiple torches don't flicker in sync. */
    public float phase = 0f;
    /** Halo center relative to the entity's {@code TransformComponent.position} (world units). */
    public Vector2 offset = new Vector2();

    @Override
    public void reset() {
        radius = 96f;
        color.set(1f, 0.85f, 0.6f, 1f);
        baseAlpha = 0.85f;
        flickerAmplitude = 0.15f;
        flickerSpeed = 6f;
        phase = 0f;
        offset.setZero();
    }
}
