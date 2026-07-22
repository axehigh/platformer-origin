package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/**
 * Empty marker component tagging a bullet entity as enemy-fired (as opposed to the player's own
 * bullets). Attached alongside {@code BulletComponent} by {@code EnemyShootSystem}; makes
 * {@code CollisionSystem} ignore it (it excludes this marker) while {@code EnemyBulletCollisionSystem}
 * resolves its movement/lifetime/hit-the-player logic instead.
 */
public class EnemyBulletComponent implements Component, Poolable {
    @Override
    public void reset() {
    }
}
