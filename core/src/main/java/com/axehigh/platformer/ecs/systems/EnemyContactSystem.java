package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Resolves enemy-vs-player contact damage: looks up the single player entity once, then for each
 * enemy checks AABB overlap against it. On overlap, while the player's {@code hitInvulnerability}
 * timer is done, decrements the player's health by one and starts a grace period so a single
 * sustained overlap (or several enemies at once) doesn't shred health in one frame.
 */
public class EnemyContactSystem extends IteratingSystem {
    private static final float HIT_INVULNERABILITY_DURATION = 1.0f;

    private ImmutableArray<Entity> players;

    public EnemyContactSystem() {
        this(0);
    }

    public EnemyContactSystem(int priority) {
        super(Family.all(EnemyComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() > 0) {
            PlayerComponent player = PLAYER.get(players.first());
            player.hitInvulnerability.update(deltaTime);
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity enemyEntity, float deltaTime) {
        if (players.size() == 0) {
            return;
        }
        Entity playerEntity = players.first();
        PlayerComponent player = PLAYER.get(playerEntity);
        TransformComponent playerTransform = TRANSFORM.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        CollisionComponent enemyCollision = COLLISION.get(enemyEntity);

        if (!playerCollision.worldBounds.overlaps(enemyCollision.worldBounds)) {
            return;
        }

        if (player.hitInvulnerability.isDone()) {
            player.health = Math.max(0, player.health - 1);
            player.hitInvulnerability.start(HIT_INVULNERABILITY_DURATION);
        }
    }
}
