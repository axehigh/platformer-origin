package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Applied to spawned projectile entities. */
public class BulletComponent implements Component, Poolable {
    public float damage;
    /** Despawns the bullet after this many seconds if it doesn't hit anything. */
    public float lifetime;
    /** Time (seconds) since this bullet was spawned. Used for spawn-frame collision grace. */
    public float elapsed;

    @Override
    public void reset() {
        damage = 0f;
        lifetime = 0f;
        elapsed = 0f;
    }
}
