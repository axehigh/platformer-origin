package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/**
 * Marks an entity as a trap: acid/lava drop spawner, falling drop, or flame hazard.
 * Different fields are used depending on {@link TrapType}.
 */
public class TrapComponent implements Component, Poolable {

    public enum TrapType { ACID_DROP_SPAWNER, ACID_DROP, ACID_POOL, FLAME }

    public enum TrapDirection { UP, DOWN, LEFT, RIGHT }

    public TrapType type;
    public int roomIndex = -1;

    // === Acid drop spawner fields ===
    public TrapDirection spawnDirection = TrapDirection.DOWN;
    public float spawnInterval = 2.0f;
    public float projectileSpeed = 200f;
    public float damage = 1f;
    public Timer spawnTimer = new Timer();
    /** Duration of the tube's discharging animation played each time a drop is about to spawn.
     *  The acid_drop is only released once this animation completes. */
    public float tubeWindUp = 0.4f;
    /** Counts down the tube's discharging animation before releasing a drop. Restarted each spawn. */
    public Timer tubeWindUpTimer = new Timer();
    /** True while the tube is mid-discharge (playing its acid_tube animation). While active, no
     *  new spawn is scheduled; the drop drops when it finishes. */
    public boolean tubeAnimating = false;

    // === Acid drop fields ===
    public float dropDamage = 1f;
    public float lifetime = 3.0f;
    public Timer lifetimeTimer = new Timer();
    /** Short grace window after a drop spawns during which wall-culling is skipped, so a drop that
     *  spawns overlapping the wall cell around the spawn point can clear it instead of being removed
     *  on its first frame. Started when the drop is spawned. */
    public Timer spawnGrace = new Timer();
    /** Current xy velocity of a falling drop (starts at {@code projectileSpeed}, accelerated by
     *  {@link #dropAccel} each frame along the travel direction for a dripping feel). */
    public float dropVelocityX = 0f;
    public float dropVelocityY = 0f;
    /** Signed acceleration (world-units/s^2) applied along the drop's travel direction. */
    public float dropAccel = 0f;
    /** Brief hang at the spawn point before the drop releases and falls (so it visibly forms, then
     *  snaps loose, like a real viscous droplet). */
    public float dripBuild = 0.15f;
    public Timer dripBuildTimer = new Timer();

    // === Acid pool fields ===
    public float poolDuration = 1.5f;
    public Timer poolTimer = new Timer();

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
        spawnTimer.reset();
        tubeWindUp = 0.4f;
        tubeWindUpTimer.reset();
        tubeAnimating = false;
        dropDamage = 1f;
        lifetime = 3.0f;
        lifetimeTimer.reset();
        spawnGrace.reset();
        dropVelocityX = 0f;
        dropVelocityY = 0f;
        dropAccel = 0f;
        dripBuild = 0.15f;
        dripBuildTimer.reset();
        poolDuration = 1.5f;
        poolTimer.reset();
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
