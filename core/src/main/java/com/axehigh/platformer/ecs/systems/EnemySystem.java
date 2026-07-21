package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
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
 * Runs before {@code MovementSystem} so the velocity it sets is integrated the same frame.
 * Gravity and wall collision for enemies are handled for free by {@code MovementSystem}, since
 * any entity with Transform+Movement+Collision (and no BulletComponent) already matches its family.
 */
public class EnemySystem extends IteratingSystem {
    /** How far past the leading edge of the enemy's feet the ground sensor probe reaches. */
    private static final float LEDGE_PROBE_AHEAD = 4f;
    /** How far below the enemy's feet the ground sensor probe reaches. */
    private static final float LEDGE_PROBE_DEPTH = 4f;

    private final Array<Rectangle> collisionRects;
    private final Rectangle ledgeProbe = new Rectangle();

    public EnemySystem(Array<Rectangle> collisionRects) {
        this(collisionRects, 0);
    }

    public EnemySystem(Array<Rectangle> collisionRects, int priority) {
        super(Family.all(EnemyComponent.class, MovementComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.collisionRects = collisionRects;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EnemyComponent enemy = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);

        enemy.hitStun.update(deltaTime);
        if (enemy.hitStun.isActive()) {
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
    }

    /** Probes a small area just past the enemy's leading foot, at foot level, for solid ground. */
    private boolean hasGroundAhead(TransformComponent transform, CollisionComponent collision, int direction) {
        float probeX = direction > 0
            ? transform.position.x + collision.bounds.width
            : transform.position.x - LEDGE_PROBE_AHEAD;
        float probeY = transform.position.y - LEDGE_PROBE_DEPTH;
        ledgeProbe.set(probeX, probeY, LEDGE_PROBE_AHEAD, LEDGE_PROBE_DEPTH);

        for (Rectangle rect : collisionRects) {
            if (ledgeProbe.overlaps(rect)) {
                return true;
            }
        }
        return false;
    }
}
