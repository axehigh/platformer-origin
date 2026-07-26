package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.CHEST;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Owns the opened-chest disappear-timer countdown: once an opened chest's timer reaches 0, it is
 * removed from the engine and a random number of coin pickups pop out of its position, each with
 * a random upward + horizontal launch velocity, before gravity/collision (via MovementSystem) pulls
 * them back down to rest nearby.
 */
public class ChestSystem extends IteratingSystem {
    private static final int MIN_COIN_DROPS = 2;
    private static final int MAX_COIN_DROPS = 6;
    private static final float MIN_POP_VELOCITY_Y = 80f;
    private static final float MAX_POP_VELOCITY_Y = 140f;
    private static final float MAX_POP_VELOCITY_X = 40f;

    private final EntityFactory entityFactory;
    private float unitScale = 1f;

    public ChestSystem(EntityFactory entityFactory) {
        this(entityFactory, 0);
    }

    public ChestSystem(EntityFactory entityFactory, int priority) {
        super(Family.all(ChestComponent.class, TransformComponent.class).get(), priority);
        this.entityFactory = entityFactory;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ChestComponent chest = CHEST.get(entity);
        if (!chest.opened) {
            return;
        }

        chest.disappearTimer.update(deltaTime);
        if (chest.disappearTimer.isActive()) {
            return;
        }

        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        float centerX = transform.position.x;
        float centerY = transform.position.y;
        if (collision != null) {
            centerX = collision.worldBounds.x + collision.worldBounds.width / 2f;
            centerY = collision.worldBounds.y + collision.worldBounds.height / 2f;
        }

        int coinCount = MathUtils.random(MIN_COIN_DROPS, MAX_COIN_DROPS);
        for (int i = 0; i < coinCount; i++) {
            float velocityX = MathUtils.random(-MAX_POP_VELOCITY_X, MAX_POP_VELOCITY_X) * unitScale;
            float velocityY = MathUtils.random(MIN_POP_VELOCITY_Y, MAX_POP_VELOCITY_Y) * unitScale;
            getEngine().addEntity(entityFactory.createPoppedCoinPickup(centerX, centerY, velocityX, velocityY));
        }

        getEngine().removeEntity(entity);
    }
}
