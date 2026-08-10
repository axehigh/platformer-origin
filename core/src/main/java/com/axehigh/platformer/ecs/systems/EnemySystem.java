package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent.AiMode;
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
 * Drives enemy patrol movement. In {@link AiMode#PATROL} (the default) the enemy walks back and
 * forth, flipping direction once it strays {@code EnemyComponent.patrolRange} from its spawn X
 * ({@code originX}), gets blocked by a wall (detected via the zeroed horizontal velocity
 * {@code MovementSystem} leaves behind after a collision), or is about to walk off the edge of its
 * current platform (a small ground-sensor probe just past its leading foot, checked against the
 * same static {@code collisionRects} used by {@code MovementSystem}). In {@link AiMode#SIDE_TO_SIDE}
 * the {@code patrolRange}/{@code originX} bound is skipped entirely — the enemy walks endlessly,
 * turning only on walls, ledges, and hazards. Every grounded enemy, regardless of mode, also refuses
 * to walk over dangerous terrain: a hazard probe just past its leading foot (from the feet up) is
 * checked against the injected {@code hazardRects}, and walking into one flips direction. Every
 * turn-around starts a brief {@code EnemyComponent.turnPause}, during which the enemy's velocity is
 * zeroed so the turnaround is visible. While an enemy's {@code hitStun} timer is active, patrol AI
 * is skipped entirely so a hit's knockback pop can play out uninterrupted via {@code MovementSystem}.
 * A {@code FlyingEnemyComponent} enemy additionally gets a time-based vertical bob wave driven into
 * {@code movement.velocity.y} (see {@code FlyingEnemyComponent}); flyers are never {@code grounded},
 * so wall/ledge/hazard probes never fire for them and a {@code SIDE_TO_SIDE} flyer flies straight.
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
    /** How far past the leading edge of the enemy's feet the hazard probe reaches. */
    private static final float HAZARD_PROBE_AHEAD = 16f;
    /** How high above the enemy's feet the hazard probe reaches. */
    private static final float HAZARD_PROBE_HEIGHT = 40f;
    /** How long an enemy stands still after turning around. */
    private static final float TURN_PAUSE_DURATION = 0.3f;
    /** Coins dropped per full {@code EnemyComponent.maxHealth} pool on death. */
    private static final float COINS_PER_HEALTH = 5f;

    private final EntityFactory entityFactory;
    private final Array<Rectangle> collisionRects;
    private final Array<Rectangle> oneWayRects;
    private final Array<Rectangle> hazardRects;
    private final RoomState roomState;
    private final Rectangle ledgeProbe = new Rectangle();
    private final Rectangle hazardProbe = new Rectangle();
    private float unitScale = 1f;

    public EnemySystem(EntityFactory entityFactory, Array<Rectangle> collisionRects, Array<Rectangle> oneWayRects, Array<Rectangle> hazardRects, RoomState roomState) {
        this(entityFactory, collisionRects, oneWayRects, hazardRects, roomState, 0);
    }

    public EnemySystem(EntityFactory entityFactory, Array<Rectangle> collisionRects, Array<Rectangle> oneWayRects, Array<Rectangle> hazardRects, RoomState roomState, int priority) {
        super(Family.all(EnemyComponent.class, MovementComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.entityFactory = entityFactory;
        this.collisionRects = collisionRects;
        this.oneWayRects = oneWayRects;
        this.hazardRects = hazardRects;
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
            if (!enemy.deathCoinsSpawned) {
                enemy.deathCoinsSpawned = true;
                dropCoins(enemy, transform, collision);
            }
            enemy.deathTimer.update(deltaTime);
            if (enemy.deathTimer.isDone()) {
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
            enemy.wasFrozen = true;
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

        boolean wasTurnPaused = enemy.turnPause.isActive();
        enemy.turnPause.update(deltaTime);

        if (enemy.turnPause.isActive()) {
            movement.velocity.x = 0;
            if (flying != null) {
                movement.velocity.y = 0;
            }
            return;
        }

        // Right after a turn pause the velocity is still zeroed from standing still; skip the
        // wall/range checks for exactly one frame so they don't re-trigger a turn (the enemy is
        // already facing away from whatever made it turn). The same applies right after the room
        // unfreezes this enemy: the freeze zeroed velocity, so the zero must not be misread as a
        // wall block (which would flip every unfrozen enemy in the room in unison).
        boolean resumedFromTurnPause = wasTurnPaused;
        boolean resumedFromFreeze = enemy.wasFrozen;
        enemy.wasFrozen = false;
        boolean blockedByWall = !resumedFromTurnPause && !resumedFromFreeze && movement.grounded && movement.velocity.x == 0f;
        boolean atLedge = movement.grounded && !hasGroundAhead(transform, collision, enemy.direction);
        boolean atHazard = movement.grounded && hazardAhead(collision, enemy.direction);

        if (blockedByWall || atLedge || atHazard) {
            turnAround(enemy);
        } else if (enemy.aiMode == AiMode.PATROL && !resumedFromTurnPause) {
            if (transform.position.x <= enemy.originX - enemy.patrolRange || transform.position.x >= enemy.originX + enemy.patrolRange) {
                turnAround(enemy);
            }
        }

        if (enemy.turnPause.isActive()) {
            movement.velocity.x = 0;
            if (flying != null) {
                movement.velocity.y = 0;
            }
        } else {
            movement.velocity.x = enemy.speed * enemy.direction;
        }

        if (flying != null) {
            flying.bobTime += deltaTime;
            movement.velocity.y = flying.bobAmplitude * flying.bobFrequency * MathUtils.cos(flying.bobTime * flying.bobFrequency);
        }
    }

    /**
     * Spawns the death coin drop (1 coin per full {@link #COINS_PER_HEALTH} max health) at the
     * enemy's center, on the first frame the death is observed — immediately on the kill, before
     * the corpse lingers through the death animation and blinks out.
     */
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

    /** Flips the enemy's travel direction and starts the brief stand-still pause at the turnaround. */
    private void turnAround(EnemyComponent enemy) {
        enemy.direction = -enemy.direction;
        enemy.turnPause.start(TURN_PAUSE_DURATION * unitScale);
    }

    /**
     * Probes a small area just past the enemy's leading foot, at foot level, for solid ground.
     * Both regular {@code collisionRects} and one-way tiles ({@code oneWayRects}) count as ground,
     * so enemies patrol on one-way tiles without jitter-turning mid-platform.
     */
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
        for (Rectangle rect : oneWayRects) {
            if (ledgeProbe.overlaps(rect)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Probes a rectangle just past the enemy's leading foot, from its feet up
     * {@code HAZARD_PROBE_HEIGHT}, ahead {@code HAZARD_PROBE_AHEAD}, for a dangerous-tile
     * {@code hazardRects} overlap (spikes/lava), so grounded enemies refuse to walk into hazards.
     */
    private boolean hazardAhead(CollisionComponent collision, int direction) {
        float probeX = direction > 0
            ? collision.worldBounds.x + collision.worldBounds.width
            : collision.worldBounds.x - HAZARD_PROBE_AHEAD * unitScale;
        float probeY = collision.worldBounds.y;
        hazardProbe.set(probeX, probeY, HAZARD_PROBE_AHEAD * unitScale, HAZARD_PROBE_HEIGHT * unitScale);

        for (Rectangle rect : hazardRects) {
            if (hazardProbe.overlaps(rect)) {
                return true;
            }
        }
        return false;
    }
}
