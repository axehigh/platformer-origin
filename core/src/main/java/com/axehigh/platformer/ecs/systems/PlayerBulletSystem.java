package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Owns bullet movement integration and collision resolution: removes bullets on wall impact,
 * applies damage (and a hit-stun/knockback via {@code EnemyDamageResolver}) and removes bullets
 * on enemy impact, and despawns bullets whose lifetime expires.
 */
public class PlayerBulletSystem extends IteratingSystem {
    /** Grace window (seconds) during which a freshly-spawned bullet is exempt from wall culling,
     *  so it can clear the wall it spawned against instead of being removed on its first frame. */
    private static final float SPAWN_GRACE = 0.12f;

    private final Array<Rectangle> collisionRects;
    private ImmutableArray<Entity> enemies;
    private PooledEngine engine;
    private float unitScale = 1f;

    public PlayerBulletSystem(Array<Rectangle> collisionRects) {
        this(collisionRects, 0);
    }

    public PlayerBulletSystem(Array<Rectangle> collisionRects, int priority) {
        super(Family.all(BulletComponent.class, TransformComponent.class, MovementComponent.class, CollisionComponent.class)
            .exclude(EnemyBulletComponent.class).get(), priority);
        this.collisionRects = collisionRects;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        if (engine instanceof PooledEngine) {
            this.engine = (PooledEngine) engine;
        }
        enemies = engine.getEntitiesFor(Family.all(EnemyComponent.class, TransformComponent.class, CollisionComponent.class).get());
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

        // Give a freshly-spawned bullet a short grace window before wall collision applies,
        // so it can move past terrain it happened to spawn overlapping (e.g. a wall directly
        // against the player) instead of being culled on its very first frame.
        if (bullet.elapsed < SPAWN_GRACE) {
            bullet.elapsed += deltaTime;
        } else if (hitsWall(collision.worldBounds)) {
            getEngine().removeEntity(bulletEntity);
            return;
        }

        Entity hitEnemy = findEnemyHit(collision.worldBounds);
        if (hitEnemy != null) {
            EnemyComponent enemy = ENEMY.get(hitEnemy);
            MovementComponent enemyMovement = MOVEMENT.get(hitEnemy);
            int knockbackDirection = movement.velocity.x >= 0f ? 1 : -1;
            boolean isFlying = FLYING.get(hitEnemy) != null;
            EnemyDamageResolver.applyHit(hitEnemy, enemy, enemyMovement, bullet.damage, knockbackDirection, isFlying, unitScale, engine);
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

    private Entity findEnemyHit(Rectangle bounds) {
        for (Entity enemyEntity : enemies) {
            CollisionComponent enemyCollision = COLLISION.get(enemyEntity);
            if (bounds.overlaps(enemyCollision.worldBounds)) {
                return enemyEntity;
            }
        }
        return null;
    }
}
