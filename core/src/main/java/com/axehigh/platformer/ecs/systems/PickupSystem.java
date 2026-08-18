package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.CoinPickupComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.DaggerPickupComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionPickupComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.particles.GlobalParticles;
import com.axehigh.platformer.particles.ParticleHelper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.COIN_PICKUP;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.DAGGER_PICKUP;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.POTION_PICKUP;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Resolves pickup-vs-player overlap: looks up the single player entity once, then for each
 * dagger or coin pickup checks AABB overlap against it. On overlap, either increments the
 * player's shoot ammo (capped at {@code maxItems}) or coin count (uncapped), then removes the
 * pickup entity.
 */
public class PickupSystem extends IteratingSystem {
    private static final float COIN_SPARK_SCALE = 1.5f;
    /** Coin sparkles must not linger: forced removal once this many seconds have elapsed. */
    private static final float COIN_SPARK_MAX_LIFETIME = 1.5f;

    private final SfxSystem sfxSystem;
    private final EntityFactory entityFactory;
    private ImmutableArray<Entity> players;
    private PooledEngine engine;

    public PickupSystem() {
        this(null, null, 0);
    }

    public PickupSystem(SfxSystem sfxSystem, EntityFactory entityFactory, int priority) {
        super(Family.one(DaggerPickupComponent.class, CoinPickupComponent.class, PotionPickupComponent.class)
            .get(), priority);
        this.sfxSystem = sfxSystem;
        this.entityFactory = entityFactory;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        if (engine instanceof PooledEngine) {
            this.engine = (PooledEngine) engine;
        }
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
            PotionPickupComponent potionPickup = POTION_PICKUP.get(pickupEntity);
            if (potionPickup != null) {
                pickupPotion(player, potionPickup);
            } else {
                CoinPickupComponent coinPickup = COIN_PICKUP.get(pickupEntity);
                player.coins += coinPickup.amount;
                if (sfxSystem != null) {
                    sfxSystem.playCoin();
                }
                spawnCoinSpark(pickupCollision);
                if (entityFactory != null) {
                    entityFactory.createFloatingMessage(getEngine(),
                            "+" + coinPickup.amount, GameConstants.MESSAGE_COLOR_COINS, playerEntity);
                }
            }
        }
        getEngine().removeEntity(pickupEntity);
    }

    /** Adds a potion to the player's held count, converting the pickup to coins when at the cap. */
    private void pickupPotion(PlayerComponent player, PotionPickupComponent potionPickup) {
        if (player.countPotion(potionPickup.type) >= GameConstants.POTION_CAP) {
            player.coins += GameConstants.POTION_OVERFLOW_COINS;
            if (sfxSystem != null) {
                sfxSystem.playCoin();
            }
            if (entityFactory != null) {
                entityFactory.createFloatingMessage(getEngine(),
                        "+" + GameConstants.POTION_OVERFLOW_COINS, GameConstants.MESSAGE_COLOR_COINS, players.first());
            }
            return;
        }
        player.setPotionCount(potionPickup.type, player.countPotion(potionPickup.type) + potionPickup.amount);
    }

    /** One-shot sparkle burst at the picked-up coin; a no-op without a PooledEngine. */
    private void spawnCoinSpark(CollisionComponent pickupCollision) {
        if (engine == null) {
            return;
        }
        float centerX = pickupCollision.worldBounds.x + pickupCollision.worldBounds.width / 2f;
        float centerY = pickupCollision.worldBounds.y + pickupCollision.worldBounds.height / 2f;
        ParticleHelper.spawnParticle(engine, GlobalParticles.SPARKS, centerX, centerY, 0f, COIN_SPARK_SCALE, COIN_SPARK_MAX_LIFETIME);
    }
}
