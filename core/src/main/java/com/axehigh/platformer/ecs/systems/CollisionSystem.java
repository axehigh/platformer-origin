package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyBulletComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
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
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.FLYING;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Owns bullet movement integration and collision resolution: removes bullets on wall impact,
 * applies damage (and a hit-stun/knockback via {@code EnemyDamageResolver}) and removes bullets
 * on enemy impact, and despawns bullets whose lifetime expires.
 */
public class CollisionSystem extends IteratingSystem {
    private final Array<Rectangle> collisionRects;
    private ImmutableArray<Entity> enemies;
    private float unitScale = 1f;

    public CollisionSystem(Array<Rectangle> collisionRects) {
        this(collisionRects, 0);
    }

    public CollisionSystem(Array<Rectangle> collisionRects, int priority) {
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

        if (hitsWall(collision.worldBounds)) {
            getEngine().removeEntity(bulletEntity);
            return;
        }

        Entity hitEnemy = findEnemyHit(collision.worldBounds);
        if (hitEnemy != null) {
            EnemyComponent enemy = ENEMY.get(hitEnemy);
            MovementComponent enemyMovement = MOVEMENT.get(hitEnemy);
            int knockbackDirection = movement.velocity.x >= 0f ? 1 : -1;
            boolean isFlying = FLYING.get(hitEnemy) != null;
            EnemyDamageResolver.applyHit(hitEnemy, enemy, enemyMovement, bullet.damage, knockbackDirection, isFlying, unitScale);
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
