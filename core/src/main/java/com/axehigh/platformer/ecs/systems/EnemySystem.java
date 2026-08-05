package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.FlyingEnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.FLYING;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Drives simple back-and-forth enemy patrol movement: sets horizontal velocity from
 * {@code EnemyComponent.direction}/{@code speed}, and flips direction once the enemy strays
 * {@code patrolRange} away from its spawn X ({@code originX}), gets blocked by a wall (detected
 * via the zeroed horizontal velocity {@code MovementSystem} leaves behind after a collision), or
 * is about to walk off the edge of its current platform (a small ground-sensor probe just past
 * its leading foot, checked against the same static {@code collisionRects} used by
 * {@code MovementSystem}). While an enemy's {@code hitStun} timer is active, patrol AI is skipped
 * entirely so a hit's knockback pop can play out uninterrupted via {@code MovementSystem}.
 * A {@code FlyingEnemyComponent} enemy additionally gets a time-based vertical bob wave driven
 * into {@code movement.velocity.y} (see {@code FlyingEnemyComponent}), so it visibly flaps up and
 * down around its spawn height instead of flying in a perfectly flat line.
 * An enemy whose {@code roomIndex} doesn't match {@code RoomState.activeRoomIndex} is frozen
 * entirely (velocity zeroed, no patrol/bob) until the player re-enters its owning room, per the
 * Room-Based Entity management requirement.
 * Runs before {@code MovementSystem} so the velocity it sets is integrated the same frame.
 * Gravity and wall collision for enemies are handled for free by {@code MovementSystem}, since
 * any entity with Transform+Movement+Collision (and no BulletComponent) already matches its family.
 */
public class EnemySystem extends IteratingSystem {
    /** How far past the leading edge of the enemy's feet the ground sensor probe reaches. */
    private static final float LEDGE_PROBE_AHEAD = 4f;
    /** How far below the enemy's feet the ground sensor probe reaches. */
    private static final float LEDGE_PROBE_DEPTH = 4f;
    /** Coins dropped per full {@code EnemyComponent.maxHealth} pool on death. */
    private static final float COINS_PER_HEALTH = 5f;

    private final EntityFactory entityFactory;
    private final Array<Rectangle> collisionRects;
    private final RoomState roomState;
    private final Rectangle ledgeProbe = new Rectangle();
    private float unitScale = 1f;

    public EnemySystem(EntityFactory entityFactory, Array<Rectangle> collisionRects, RoomState roomState) {
        this(entityFactory, collisionRects, roomState, 0);
    }

    public EnemySystem(EntityFactory entityFactory, Array<Rectangle> collisionRects, RoomState roomState, int priority) {
        super(Family.all(EnemyComponent.class, MovementComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.entityFactory = entityFactory;
        this.collisionRects = collisionRects;
        this.roomState = roomState;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EnemyComponent enemy = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        FlyingEnemyComponent flying = FLYING.get(entity);

        if (enemy.isDead) {
            enemy.deathTimer.update(deltaTime);
            if (enemy.deathTimer.isDone()) {
                dropCoins(enemy, transform, collision);
                getEngine().removeEntity(entity);
            }
            return;
        }

        boolean wasHitStunActive = enemy.hitStun.isActive();
        enemy.hitStun.update(deltaTime);
        enemy.postHitIdle.update(deltaTime);

        if (wasHitStunActive && !enemy.hitStun.isActive()) {
            enemy.postHitIdle.start(EnemyDamageResolver.POST_HIT_IDLE_DURATION);
        }

        boolean roomActive = enemy.roomIndex < 0 || enemy.roomIndex == roomState.activeRoomIndex;
        if (!roomActive) {
            movement.velocity.x = 0f;
            if (flying != null) {
                movement.velocity.y = 0f;
            }
            return;
        }

        if (enemy.hitStun.isActive()) {
            return;
        }

        if (enemy.postHitIdle.isActive()) {
            movement.velocity.x = 0;
            if (flying != null) {
                movement.velocity.y = 0;
            }
            return;
        }

        boolean blockedByWall = movement.grounded && movement.velocity.x == 0f;
        boolean atLedge = movement.grounded && !hasGroundAhead(transform, collision, enemy.direction);
        if (blockedByWall || atLedge) {
            enemy.direction = -enemy.direction;
        } else if (transform.position.x <= enemy.originX - enemy.patrolRange) {
            enemy.direction = 1;
        } else if (transform.position.x >= enemy.originX + enemy.patrolRange) {
            enemy.direction = -1;
        }

        movement.velocity.x = enemy.speed * enemy.direction;

        if (flying != null) {
            flying.bobTime += deltaTime;
            movement.velocity.y = flying.bobAmplitude * flying.bobFrequency * MathUtils.cos(flying.bobTime * flying.bobFrequency);
        }
    }

    /** Spawns the death coin drop (1 coin per full {@link #COINS_PER_HEALTH} max health) at the enemy's center. */
    private void dropCoins(EnemyComponent enemy, TransformComponent transform, CollisionComponent collision) {
        int coinCount = (int) (enemy.maxHealth / COINS_PER_HEALTH);
        if (coinCount <= 0) {
            return;
        }
        float centerX = transform.position.x;
        float centerY = transform.position.y;
        if (collision != null) {
            centerX = collision.worldBounds.x + collision.worldBounds.width / 2f;
            centerY = collision.worldBounds.y + collision.worldBounds.height / 2f;
        }
        entityFactory.popCoins(getEngine(), centerX, centerY, coinCount, unitScale);
    }

    /** Probes a small area just past the enemy's leading foot, at foot level, for solid ground. */
    private boolean hasGroundAhead(TransformComponent transform, CollisionComponent collision, int direction) {
        float probeX = direction > 0
            ? collision.worldBounds.x + collision.worldBounds.width
            : collision.worldBounds.x - LEDGE_PROBE_AHEAD * unitScale;
        float probeY = collision.worldBounds.y - LEDGE_PROBE_DEPTH * unitScale;
        ledgeProbe.set(probeX, probeY, LEDGE_PROBE_AHEAD * unitScale, LEDGE_PROBE_DEPTH * unitScale);

        for (Rectangle rect : collisionRects) {
            if (ledgeProbe.overlaps(rect)) {
                return true;
            }
        }
        return false;
    }
}
