package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks a damageable entity (player or enemy) for a brief white-sprite tint on every hit.
 * Visual-only — no gameplay effect. The component persists on the entity for its lifetime;
 * {@link com.axehigh.platformer.ecs.systems.HitFlashSystem} counts the timer down each frame,
 * and {@link com.axehigh.platformer.ecs.systems.RenderSystem} applies the white tint while active.
 * Gated by {@code FeatureFlags.isSlashArcEnabled()} (the "combat effects" toggle) at the
 * call sites that invoke {@link #start(float)}.
 */
public class HitFlashComponent implements Component {
    /** Remaining active flash time in seconds; > 0 means currently flashing. */
    public float flashTimer = 0f;
    /** Total duration of the current flash (used only for reference; the timer counts down). */
    public float flashDuration = 0f;

    /** Starts the flash for the given duration (seconds). */
    public void start(float duration) {
        flashTimer = duration;
        flashDuration = duration;
    }

    /** True while the flash tint is currently active. */
    public boolean isActive() {
        return flashTimer > 0f;
    }
}
