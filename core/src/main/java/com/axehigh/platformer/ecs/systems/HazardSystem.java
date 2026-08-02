package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;

/**
 * Resolves tile-hazard damage (collision-layer tiles flagged {@code hazard=true}, e.g. spikes and
 * lava): while the single player's AABB overlaps any hazard rect, applies one point of damage
 * through {@code PlayerDamageResolver#applyHitWithoutKnockback} — no knockback, since a directional
 * push makes no sense for a static tile and could shove the player back into the hazard. The shared
 * invulnerability grace period (ticked each frame by {@code EnemyContactSystem}) turns a sustained
 * overlap into one hit per grace window instead of shredding health every frame.
 */
public class HazardSystem extends EntitySystem {
    private final Array<Rectangle> hazardRects;
    private ImmutableArray<Entity> players;

    public HazardSystem(Array<Rectangle> hazardRects) {
        this(hazardRects, 0);
    }

    public HazardSystem(Array<Rectangle> hazardRects, int priority) {
        super(priority);
        this.hazardRects = hazardRects;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() == 0) {
            return;
        }
        Entity playerEntity = players.first();
        PlayerComponent player = PLAYER.get(playerEntity);
        if (player.isDead) {
            return;
        }
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        for (Rectangle rect : hazardRects) {
            if (playerCollision.worldBounds.overlaps(rect)) {
                PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);
                break;
            }
        }
    }
}
