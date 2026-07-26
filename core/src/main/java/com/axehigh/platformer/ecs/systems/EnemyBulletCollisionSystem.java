package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyBulletComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.BULLET;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Owns enemy-fired bullet movement integration and collision resolution: mirrors
 * {@code CollisionSystem}'s structure (lifetime countdown, position integration, wall-hit
 * removal), but resolves the "hit" case against the cached player entity instead of the enemy
 * family. On hitting the player, the bullet is always removed and, while the shared
 * {@code player.hitInvulnerability} grace-period timer (also used by {@code EnemyContactSystem})
 * is done, decrements {@code player.health} by one and restarts that timer — the exact same
 * damage rule as touching an enemy.
 */
public class EnemyBulletCollisionSystem extends IteratingSystem {
    private static final float HIT_INVULNERABILITY_DURATION = 1.0f;

    private final Array<Rectangle> collisionRects;
    private ImmutableArray<Entity> players;

    public EnemyBulletCollisionSystem(Array<Rectangle> collisionRects) {
        this(collisionRects, 0);
    }

    public EnemyBulletCollisionSystem(Array<Rectangle> collisionRects, int priority) {
        super(Family.all(BulletComponent.class, EnemyBulletComponent.class, TransformComponent.class, MovementComponent.class, CollisionComponent.class).get(), priority);
        this.collisionRects = collisionRects;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity bulletEntity, float deltaTime) {
        BulletComponent bullet = BULLET.get(bulletEntity);
        TransformComponent transform = TRANSFORM.get(bulletEntity);
        MovementComponent movement = MOVEMENT.get(bulletEntity);
        CollisionComponent collision = COLLISION.get(bulletEntity);

        bullet.lifetime -= deltaTime;
        if (bullet.lifetime <= 0f) {
            getEngine().removeEntity(bulletEntity);
            return;
        }

        transform.position.mulAdd(movement.velocity, deltaTime);
        collision.updateWorldBounds(transform.position);

        if (hitsWall(collision.worldBounds)) {
            getEngine().removeEntity(bulletEntity);
            return;
        }

        if (hitsPlayer(collision.worldBounds)) {
            getEngine().removeEntity(bulletEntity);
        }
    }

    private boolean hitsWall(Rectangle bounds) {
        for (Rectangle rect : collisionRects) {
            if (bounds.overlaps(rect)) {
                return true;
            }
        }
        return false;
    }

    private boolean hitsPlayer(Rectangle bounds) {
        if (players.size() == 0) {
            return false;
        }
        Entity playerEntity = players.first();
        CollisionComponent playerCollision = COLLISION.get(playerEntity);

        if (!bounds.overlaps(playerCollision.worldBounds)) {
            return false;
        }

        PlayerComponent player = PLAYER.get(playerEntity);
        if (player.hitInvulnerability.isDone()) {
            player.health = Math.max(0, player.health - 1);
            player.hitInvulnerability.start(HIT_INVULNERABILITY_DURATION);
        }
        return true;
    }
}
