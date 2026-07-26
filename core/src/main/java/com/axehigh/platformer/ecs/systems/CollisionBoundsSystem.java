package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Central system responsible for syncing each entity's world-space collision rectangle 
 * (worldBounds) with its current position (TransformComponent) and local offsets (bounds).
 * This eliminates redundant math in other systems and ensures consistent collision detection.
 */
public class CollisionBoundsSystem extends IteratingSystem {
    public CollisionBoundsSystem(int priority) {
        super(Family.all(TransformComponent.class, CollisionComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TRANSFORM.get(entity); // Ensure it exists, though family already checks
        COLLISION.get(entity).updateWorldBounds(TRANSFORM.get(entity).position);
    }
}
