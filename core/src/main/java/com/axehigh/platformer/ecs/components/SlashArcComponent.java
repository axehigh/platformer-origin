package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/**
 * Marks a short-lived visual-only melee slash-arc entity: a bright crescent drawn over the player
 * at swing start. Cosmetic only — no collision, no damage. The owning entity is self-removed by
 * {@code SlashArcSystem} once {@link #age} reaches {@link #lifeTime}.
 */
public class SlashArcComponent implements Component, Poolable {
    /** Total life of the arc in seconds. */
    public float lifeTime = 0.2f;
    /** Seconds since spawn (accumulated by SlashArcSystem). */
    public float age = 0f;
    /** Fraction of {@link #lifeTime} spent fading in from alpha 0 → 1; the remainder fades 1 → 0. */
    public float fadeInRatio = 0.2f;

    @Override
    public void reset() {
        lifeTime = 0.2f;
        age = 0f;
        fadeInRatio = 0.2f;
    }
}