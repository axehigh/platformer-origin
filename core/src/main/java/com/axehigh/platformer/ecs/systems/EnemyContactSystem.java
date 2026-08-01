package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Resolves enemy-vs-player contact damage: looks up the single player entity once, then for each
 * enemy checks AABB overlap against it. On overlap, while the player's {@code hitInvulnerability}
 * timer is done, applies damage through the shared {@code PlayerDamageResolver} — one point of
 * health, a brief hit-stun lock (during which the player's input is frozen so the knockback pop
 * can play out), a small knockback push away from the enemy, and a grace period so a single
 * sustained overlap (or several enemies at once) doesn't shred health in one frame.
 */
public class EnemyContactSystem extends IteratingSystem {
    private ImmutableArray<Entity> players;
    private float unitScale = 1f;

    public EnemyContactSystem() {
        this(0);
    }

    public EnemyContactSystem(int priority) {
        super(Family.all(EnemyComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
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
            player.hurtTimer.update(deltaTime);
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity enemyEntity, float deltaTime) {
        EnemyComponent enemy = ENEMY.get(enemyEntity);
        if (enemy.isDead) {
            return;
        }

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

        TransformComponent enemyTransform = TRANSFORM.get(enemyEntity);
        float playerCenterX = playerTransform.position.x + playerCollision.bounds.x + playerCollision.bounds.width / 2f;
        float enemyCenterX = enemyTransform.position.x + enemyCollision.bounds.x + enemyCollision.bounds.width / 2f;
        int knockbackDirection = playerCenterX >= enemyCenterX ? 1 : -1;
        PlayerDamageResolver.applyHit(playerEntity, player, MOVEMENT.get(playerEntity), knockbackDirection, unitScale);
    }
}
