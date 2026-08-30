package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/**
 * Marks an entity as a trap: acid/lava drop spawner, falling drop, or flame hazard.
 * Different fields are used depending on {@link TrapType}.
 */
public class TrapComponent implements Component, Poolable {

    public enum TrapType { ACID_DROP_SPAWNER, ACID_DROP, FLAME }

    public enum TrapDirection { UP, DOWN, LEFT, RIGHT }

    public TrapType type;
    public int roomIndex = -1;

    // === Acid drop spawner fields ===
    public TrapDirection spawnDirection = TrapDirection.DOWN;
    public float spawnInterval = 2.0f;
    public float projectileSpeed = 200f;
    public float damage = 1f;
    /** World-space offset (from the spawner's tile corner) where each drop originates — set from
     *  the designer's collision-editor point on the acid tile when one is present. */
    public float spawnOffsetX = 0f;
    public float spawnOffsetY = 0f;
    public Timer spawnTimer = new Timer();

    // === Acid drop fields ===
    public float dropDamage = 1f;
    public float lifetime = 3.0f;
    public Timer lifetimeTimer = new Timer();

    // === Flame fields ===
    public TrapDirection flameDirection = TrapDirection.DOWN;
    public float minScale = 0.2f;
    public float maxScale = 1.0f;
    public float pulseSpeed = 2.0f;
    public float currentScale = 0.2f;
    public float flameHeight = 48f;
    public float flameWidth = 24f;
    public boolean isFlaming = false;
    public Timer flameTimer = new Timer();
    public Timer cooldownTimer = new Timer();
    public float flameDuration = 2.0f;
    public float cooldownDuration = 1.5f;

    @Override
    public void reset() {
        type = null;
        roomIndex = -1;
        spawnDirection = TrapDirection.DOWN;
        spawnInterval = 2.0f;
        projectileSpeed = 200f;
        damage = 1f;
        spawnOffsetX = 0f;
        spawnOffsetY = 0f;
        spawnTimer.reset();
        dropDamage = 1f;
        lifetime = 3.0f;
        lifetimeTimer.reset();
        flameDirection = TrapDirection.DOWN;
        minScale = 0.2f;
        maxScale = 1.0f;
        pulseSpeed = 2.0f;
        currentScale = 0.2f;
        flameHeight = 48f;
        flameWidth = 24f;
        isFlaming = false;
        flameTimer.reset();
        cooldownTimer.reset();
        flameDuration = 2.0f;
        cooldownDuration = 1.5f;
    }
}
