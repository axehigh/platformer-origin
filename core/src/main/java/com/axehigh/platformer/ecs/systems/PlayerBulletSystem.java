package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.PlayerConfig;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.particles.GlobalParticles;
import com.axehigh.platformer.particles.ParticleHelper;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Owns bullet movement integration and collision resolution: removes bullets on wall impact,
 * applies damage (and a hit-stun/knockback via {@code EnemyDamageResolver}) and removes bullets
 * on enemy impact, and despawns bullets whose lifetime expires.
 */
public class PlayerBulletSystem extends IteratingSystem {
    /** Maximum world units a bullet moves per integration sub-step. Well under the smallest bullet
     *  hitbox dimension and tile thickness, so a wall can never be crossed unnoticed even at low
     *  frame rates. No spawn-grace window: bullets that overlap a wall are blocked shots. */
    private static final float MAX_SUBSTEP = 2f;

    private final Array<Rectangle> collisionRects;
    private final Vector2 stepVelocity = new Vector2();
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

        // Integrate in sub-steps, checking wall + enemy overlap after every sub-step. No spawn
        // grace: bullets spawn at the player's front edge (PlayerInputSystem.spawnBullet), so a
        // bullet that spawns overlapping a wall is a blocked shot and is removed on first contact.
        float distanceRemaining = movement.velocity.len() * deltaTime;
        do {
            float step = Math.min(distanceRemaining, MAX_SUBSTEP);
            if (step > 0f) {
                stepVelocity.set(movement.velocity).nor().scl(step);
                transform.position.add(stepVelocity);
            }
            collision.updateWorldBounds(transform.position);

            if (hitsWall(collision.worldBounds)) {
                spawnImpactSpark(collision.worldBounds);
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
                return;
            }

            distanceRemaining -= step;
        } while (distanceRemaining > 0f);

        bullet.trailTimer -= deltaTime;
        if (bullet.trailTimer <= 0f) {
            bullet.trailTimer = PlayerConfig.BULLET_TRAIL_INTERVAL;
            if (FeatureFlags.isSlashArcEnabled()) {
                TrailSystem.spawnTrail(getEngine(), transform, TEXTURE.get(bulletEntity));
            }
        }
    }

    /** Spawns a spark burst at the bullet impact point (used for non-enemy/wall despawns). */
    private void spawnImpactSpark(Rectangle bounds) {
        if (engine == null) {
            return;
        }
        ParticleHelper.spawnParticle(engine, GlobalParticles.SPARKS,
            bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f,
            0f, EnemyDamageResolver.HIT_SPARK_SCALE, EnemyDamageResolver.HIT_SPARK_MAX_LIFETIME);
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
