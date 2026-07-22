package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.MathUtils;

/**
 * Marker component tagging an enemy as immune to gravity. Checked by {@code MovementSystem},
 * which skips the gravity/wall-slide velocity-Y update entirely for any entity that has it, so
 * the enemy holds its spawn height and hovers in place instead of falling. Also carries the
 * time-based vertical bob wave tuning ({@code EnemySystem} drives {@code MovementComponent.velocity.y}
 * from it every frame) so the enemy visibly flaps/hovers up and down around its spawn height while
 * patrolling, instead of flying in a perfectly flat line.
 */
public class FlyingEnemyComponent implements Component {
    /** How far above/below spawn height the bob wave swings, in world units. */
    public float bobAmplitude = 8f;
    /** Angular frequency of the bob wave, in radians/second (default: ~2 second period). */
    public float bobFrequency = MathUtils.PI;
    /** Elapsed time accumulator driving the wave's phase; frozen while the enemy is hit-stunned. */
    public float bobTime = 0f;
}
