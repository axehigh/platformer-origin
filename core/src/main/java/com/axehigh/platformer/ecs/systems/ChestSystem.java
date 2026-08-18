package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
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
 * Owns the opened-chest coin-pop: once an opened chest's timer reaches 0 (and it hasn't already
 * dropped), a random number of coin pickups pop out of its position, each with a random upward +
 * horizontal launch velocity, before gravity/collision (via MovementSystem) pulls them back down
 * to rest nearby. The chest entity itself stays in the world with its open sprite as decoration.
 */
public class ChestSystem extends IteratingSystem {
    private static final int MIN_COIN_DROPS = 2;
    private static final int MAX_COIN_DROPS = 6;

    private final EntityFactory entityFactory;
    private float unitScale = 1f;
    private Entity playerEntity;

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

    public void setPlayerEntity(Entity playerEntity) {
        this.playerEntity = playerEntity;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ChestComponent chest = CHEST.get(entity);
        if (!chest.opened) {
            return;
        }

        chest.disappearTimer.update(deltaTime);
        if (chest.disappearTimer.isActive() || chest.coinsDropped) {
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
        entityFactory.popCoins(getEngine(), centerX, centerY, coinCount, unitScale);

        if (playerEntity != null) {
            entityFactory.createFloatingMessage(getEngine(),
                    "+" + coinCount, GameConstants.MESSAGE_COLOR_COINS, playerEntity);
        }

        chest.coinsDropped = true;
    }
}
