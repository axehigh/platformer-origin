package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.PoppedItemComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.MapLoader;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Removes entities that fall below the map boundaries. Prevents popped coins, daggers, and
 * potions from accumulating indefinitely when they fall through gaps or are launched off-screen.
 * The despawn threshold is one screen-height (≈200 world units) below y=0.
 */
public class DespawnSystem extends IteratingSystem {
    /** Y position below which entities are despawned. */
    private final float despawnY;

    /**
     * @param priority  execution order — should run after movement but before rendering
     * @param mapLoader used to read the map height and tile size for threshold computation
     */
    public DespawnSystem(int priority, MapLoader mapLoader) {
        super(Family.all(TransformComponent.class, PoppedItemComponent.class).get(), priority);
        this.despawnY = -(mapLoader.getTileHeight() * 12.5f); // ~200 world units below y=0
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (TRANSFORM.get(entity).position.y < despawnY) {
            getEngine().removeEntity(entity);
        }
    }
}
