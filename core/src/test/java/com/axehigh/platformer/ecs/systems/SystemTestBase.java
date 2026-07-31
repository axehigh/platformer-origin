package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Shared fixtures for headless unit tests of the gameplay systems. None of the systems under
 * test touch {@code Gdx} statics, so a plain {@code Engine} plus manual
 * {@code CollisionComponent} setup is enough (no headless GL backend required). {@code worldBounds}
 * must be kept in sync with {@code transform.position} via {@link #place(TransformComponent,
 * CollisionComponent, float, float)} whenever a test repositions an entity, since systems read the
 * precomputed world rectangle.
 */
public abstract class SystemTestBase {

    static {
        GdxNativesLoader.load();
    }

    /** One 60fps game frame, in seconds. */
    protected static final float DT = 1f / 60f;
    protected static final float EPSILON = 0.001f;

    protected Engine newEngine() {
        return new Engine();
    }

    protected Entity entity(Component... components) {
        Entity entity = new Entity();
        for (Component component : components) {
            entity.add(component);
        }
        return entity;
    }

    protected TransformComponent transform(float x, float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return transform;
    }

    protected MovementComponent movement() {
        return new MovementComponent();
    }

    protected PlayerComponent player() {
        return new PlayerComponent();
    }

    /** Collision box offset by {@code (offsetX, offsetY)} from the entity's position, size {@code (w, h)}. */
    protected CollisionComponent collision(float offsetX, float offsetY, float w, float h) {
        CollisionComponent collision = new CollisionComponent();
        collision.baseOffsetX = offsetX;
        collision.baseOffsetY = offsetY;
        collision.bounds.set(offsetX, offsetY, w, h);
        collision.worldBounds.set(offsetX, offsetY, w, h);
        return collision;
    }

    /** Repositions the entity and re-derives its {@code worldBounds} from the new position. */
    protected void place(TransformComponent transform, CollisionComponent collision, float x, float y) {
        transform.position.set(x, y);
        collision.updateWorldBounds(transform.position);
    }
}
