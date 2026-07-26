package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CoinPickupComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.DaggerPickupComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.COIN_PICKUP;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.DAGGER_PICKUP;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Resolves pickup-vs-player overlap: looks up the single player entity once, then for each
 * dagger or coin pickup checks AABB overlap against it. On overlap, either increments the
 * player's shoot ammo (capped at {@code maxItems}) or coin count (uncapped), then removes the
 * pickup entity.
 */
public class PickupSystem extends IteratingSystem {
    private ImmutableArray<Entity> players;

    public PickupSystem() {
        this(0);
    }

    public PickupSystem(int priority) {
        super(Family.one(DaggerPickupComponent.class, CoinPickupComponent.class)
            .get(), priority);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity pickupEntity, float deltaTime) {
        if (players.size() == 0) {
            return;
        }
        Entity playerEntity = players.first();
        PlayerComponent player = PLAYER.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        CollisionComponent pickupCollision = COLLISION.get(pickupEntity);

        if (!playerCollision.worldBounds.overlaps(pickupCollision.worldBounds)) {
            return;
        }

        DaggerPickupComponent daggerPickup = DAGGER_PICKUP.get(pickupEntity);
        if (daggerPickup != null) {
            player.items = Math.min(player.maxItems, player.items + daggerPickup.amount);
        } else {
            CoinPickupComponent coinPickup = COIN_PICKUP.get(pickupEntity);
            player.coins += coinPickup.amount;
        }
        getEngine().removeEntity(pickupEntity);
    }
}
