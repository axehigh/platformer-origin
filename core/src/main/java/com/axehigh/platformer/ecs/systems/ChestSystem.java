package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.CHEST;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Owns the opened-chest disappear-timer countdown: once an opened chest's timer reaches 0, it is
 * removed from the engine and a random number of coin pickups are scattered near its position.
 */
public class ChestSystem extends IteratingSystem {
    private static final int MIN_COIN_DROPS = 2;
    private static final int MAX_COIN_DROPS = 6;
    private static final float SCATTER_RANGE = 12f;

    private final EntityFactory entityFactory;

    public ChestSystem(EntityFactory entityFactory) {
        this(entityFactory, 0);
    }

    public ChestSystem(EntityFactory entityFactory, int priority) {
        super(Family.all(ChestComponent.class, TransformComponent.class).get(), priority);
        this.entityFactory = entityFactory;
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
        int coinCount = MathUtils.random(MIN_COIN_DROPS, MAX_COIN_DROPS);
        for (int i = 0; i < coinCount; i++) {
            float offsetX = MathUtils.random(-SCATTER_RANGE, SCATTER_RANGE);
            float offsetY = MathUtils.random(-SCATTER_RANGE, SCATTER_RANGE);
            getEngine().addEntity(entityFactory.createCoinPickup(transform.position.x + offsetX, transform.position.y + offsetY));
        }

        getEngine().removeEntity(entity);
    }
}
