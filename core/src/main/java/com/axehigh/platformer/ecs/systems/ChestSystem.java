package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Owns the opened-chest loot pop: once an opened chest's timer reaches 0 (and it hasn't already
 * dropped), loot pops out of its position. If the chest has a {@code potionType}, a single potion
 * pickup is spawned; otherwise, a random number of coin pickups pop out, each with a random upward +
 * horizontal launch velocity, before gravity/collision (via {@code MovementSystem}) pulls them back
 * down to rest nearby. Also reconciles chest solidity every frame against the shared static
 * collision list: a closed chest is solid — its AABB blocks the player and enemies like a static
 * wall (too tall to jump over) — and becomes fully passable the moment it is opened. The chest
 * entity itself stays in the world with its open sprite as decoration.
 */
public class ChestSystem extends IteratingSystem {
    private static final int MIN_COIN_DROPS = 2;
    private static final int MAX_COIN_DROPS = 6;

    private final EntityFactory entityFactory;
    private Array<Rectangle> collisionRects;
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

    public void setCollisionRects(Array<Rectangle> collisionRects) {
        this.collisionRects = collisionRects;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ChestComponent chest = CHEST.get(entity);
        CollisionComponent collision = COLLISION.get(entity);

        // Solid-while-closed: a closed chest blocks every mover (player and enemies alike) like a
        // static wall; an open chest is fully passable. Reconcile against the shared static
        // collisionRects list every frame — idempotent, and self-heals across level reloads and
        // entity removal.
        if (collisionRects != null && collision != null) {
            if (chest.opened) {
                collisionRects.removeValue(collision.worldBounds, true);
            } else if (!collisionRects.contains(collision.worldBounds, true)) {
                collisionRects.add(collision.worldBounds);
            }
        }

        if (!chest.opened) {
            return;
        }

        chest.disappearTimer.update(deltaTime);
        if (chest.disappearTimer.isActive() || chest.coinsDropped) {
            return;
        }

        TransformComponent transform = TRANSFORM.get(entity);
        float centerX = transform.position.x;
        float centerY = transform.position.y;
        if (collision != null) {
            centerX = collision.worldBounds.x + collision.worldBounds.width / 2f;
            centerY = collision.worldBounds.y + collision.worldBounds.height / 2f;
        }

        if (chest.potionType != null) {
            entityFactory.popPotion(getEngine(), centerX, centerY, chest.potionType.name(), unitScale, collisionRects);
        } else {
            int coinCount = MathUtils.random(MIN_COIN_DROPS, MAX_COIN_DROPS);
            entityFactory.popCoins(getEngine(), centerX, centerY, coinCount, unitScale, collisionRects);
        }

        chest.coinsDropped = true;
    }
}
