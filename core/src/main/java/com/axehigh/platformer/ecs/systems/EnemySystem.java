package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.ecs.components.EnemyComponent.AiMode;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.*;

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
 * A melee-capable enemy that has detected the player (same live detection box as
 * {@code EnemyAttackSystem}: center-based, {@code attackRange*3} per side, {@code detectionHeight}
 * tall) instead chases them: it moves toward the player using normal movement but never turns away,
 * so a wall, ledge, or hazard simply holds it in place while it keeps facing the player. The chase
 * overrides patrol turn/range logic entirely for as long as the player is detected.
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
    /**
     * How long an enemy stands still after turning around. Also reused by {@code EnemyShootSystem}
     * (same package) as the shooter's pre-wind-up turn-idle: the stand-still a shooter holds after
     * snap-turning toward the player on a commit that required a facing change, so both turnaround
     * pauses feel identical.
     */
    static final float TURN_PAUSE_DURATION = 0.1f;
    /** Coins dropped per full {@code EnemyComponent.maxHealth} pool on death. */
    private static final float COINS_PER_HEALTH = 5f;

    private final EntityFactory entityFactory;
    private final Array<Rectangle> collisionRects;
    private final Array<Rectangle> oneWayRects;
    private final Array<Rectangle> hazardRects;
    private final RoomState roomState;
    private final Rectangle ledgeProbe = new Rectangle();
    private final Rectangle hazardProbe = new Rectangle();
    private ImmutableArray<Entity> players;
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
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EnemyComponent enemy = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        FlyingEnemyComponent flying = FLYING.get(entity);

        // The attack pause (wind-up + post-strike recovery stand) is captured/updated below: the
        // attack system (priority 8, this frame LATER) starts the recovery stand when a strike
        // resolves, and on the frame the recovery ends the enemy's velocity is still zeroed from
        // standing still — so that frame must not misread the zero as a wall block (which would
        // flip the enemy right after it finished its swing).
        EnemyAttackComponent attack = ENEMY_ATTACK.get(entity);

        if (enemy.isDead) {
            if (!enemy.deathCoinsSpawned) {
                enemy.deathCoinsSpawned = true;
                dropLoot(entity, enemy, transform, collision);
            }
            enemy.deathTimer.update(deltaTime);
            if (enemy.deathTimer.isDone()) {
                getEngine().removeEntity(entity);
            }
            return;
        }

        boolean wasRecovering = attack != null && attack.recovery.isActive();
        if (attack != null) {
            attack.recovery.update(deltaTime);
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

        // Wind-up attack: pause patrol entirely (stationary + facing locked) so the attack
        // animation plays cleanly — and so the zeroed attack velocity isn't misread as a
        // wall block (which would flip the enemy mid-wind-up and swing the wrong way).
        if (attack != null && attack.isAttacking) {
            movement.velocity.x = 0;
            if (flying != null) {
                movement.velocity.y = 0;
            }
            return;
        }

        // Post-strike "wind down": the enemy stands still, facing locked, for
        // EnemyAttackComponent.recoveryDuration after a strike, then resumes its patrol in
        // the same direction (normal wall/ledge/hazard/range turn checks apply from then on).
        if (attack != null && attack.recovery.isActive()) {
            movement.velocity.x = 0;
            if (flying != null) {
                movement.velocity.y = 0;
            }
            return;
        }

        // Detection: circular detection range for flyers, rectangular box for grounded enemies.
        float playerCenterX = 0f;
        float playerCenterY = 0f;
        boolean playerInDetection = false;
        if (attack != null && players.size() > 0) {
            Entity playerEntity = players.first();
            BuffComponent buff = BUFF.get(playerEntity);
            boolean playerInvisible = buff != null && buff.isInvisibilityActive();
            if (!playerInvisible) {
                CollisionComponent playerCollision = COLLISION.get(playerEntity);
                playerCenterX = playerCollision.worldBounds.x + playerCollision.worldBounds.width / 2f;
                playerCenterY = playerCollision.worldBounds.y + playerCollision.worldBounds.height / 2f;
                float ecx = collision.worldBounds.x + collision.worldBounds.width / 2f;
                float ecy = collision.worldBounds.y + collision.worldBounds.height / 2f;

                if (flying != null) {
                    float dx = playerCenterX - ecx;
                    float dy = playerCenterY - ecy;
                    float distSq = dx * dx + dy * dy;
                // 1. Increase flyer attack range by 25% -> attackRange is multiplied by 1.25. Detection range scales correspondingly.
                // 2. Add stable hysteresis to detection/retreat border so it doesn't chatter/get stuck near the threshold.
                float effectiveAttackRange = attack.attackRange * 1.25f;
                float detectRadius = effectiveAttackRange * 2.5f * unitScale;
                float retreatHysteresisRadius = detectRadius * 1.15f;
                if (flying.flightState == FlyingEnemyComponent.FlightState.ATTACK_APPROACH) {
                    playerInDetection = distSq <= retreatHysteresisRadius * retreatHysteresisRadius;
                } else {
                    playerInDetection = distSq <= detectRadius * detectRadius;
                }
                } else {
                    playerInDetection = Math.abs(playerCenterX - ecx) <= attack.attackRange * 3f * unitScale
                        && Math.abs(playerCenterY - ecy) <= attack.detectionHeight * unitScale / 2f;
                }
            }
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

        boolean resumedFromTurnPause = wasTurnPaused;
        boolean resumedFromFreeze = enemy.wasFrozen;
        enemy.wasFrozen = false;
        boolean resumedFromAttack = wasRecovering;
        boolean blockedByWall = !resumedFromTurnPause && !resumedFromFreeze && !resumedFromAttack && movement.grounded && movement.velocity.x == 0f;
        boolean atLedge = movement.grounded && !hasGroundAhead(transform, collision, enemy.direction);
        boolean atHazard = movement.grounded && hazardAhead(collision, enemy.direction);

        // Flyer specialized fly-toward-attack-then-fly-back-to-patrol behavior
        if (flying != null) {
            flying.retreatTimer.update(deltaTime);
            float ecx = collision.worldBounds.x + collision.worldBounds.width / 2f;
            float ecy = collision.worldBounds.y + collision.worldBounds.height / 2f;

            if (flying.flightState == FlyingEnemyComponent.FlightState.RETREAT) {
                // Fly back toward spawn/patrol origin
                float targetX = flying.spawnX;
                float targetY = flying.spawnY;
                float dx = targetX - ecx;
                float dy = targetY - ecy;
                float distSq = dx * dx + dy * dy;
                if (distSq < 4f * unitScale * unitScale || flying.retreatTimer.isDone()) {
                    flying.flightState = FlyingEnemyComponent.FlightState.PATROL;
                } else {
                    float angle = com.badlogic.gdx.math.MathUtils.atan2(dy, dx);
                    float spd = enemy.speed * 1.2f; // slightly quicker retreat
                    movement.velocity.x = spd * com.badlogic.gdx.math.MathUtils.cos(angle);
                    movement.velocity.y = spd * com.badlogic.gdx.math.MathUtils.sin(angle);
                    if (Math.abs(movement.velocity.x) > 0.5f) {
                        enemy.direction = movement.velocity.x > 0 ? 1 : -1;
                    }
                    flying.bobTime += deltaTime;
                    return;
                }
            }

            if (playerInDetection && attack != null) {
                flying.flightState = FlyingEnemyComponent.FlightState.ATTACK_APPROACH;
                // Check if player is beyond attack range (i.e. fly toward player)
                float dx = playerCenterX - ecx;
                float dy = playerCenterY - ecy;
                float distSq = dx * dx + dy * dy;
                // 1. Increase flyer attack range by 25% and 2. shorten standoff/hover distance so flyer closes in much closer (tight melee distance: 25% of attack range, or edge of collision bounds)
                float attackRadius = attack.attackRange * 1.25f * unitScale;
                float standoffRadius = Math.max(collision.worldBounds.width, attackRadius * 0.25f);

                if (Math.abs(playerCenterX - ecx) > 1f) {
                    enemy.direction = playerCenterX > ecx ? 1 : -1;
                }

                if (distSq > standoffRadius * standoffRadius) {
                    // Fly toward player in 2D space with obstacle avoidance ray/steering
                    float angle = com.badlogic.gdx.math.MathUtils.atan2(dy, dx);
                    float targetVx = enemy.speed * com.badlogic.gdx.math.MathUtils.cos(angle);
                    float targetVy = enemy.speed * com.badlogic.gdx.math.MathUtils.sin(angle);

                    // Cast a look-ahead ray/box based strictly on the flyer's own collision bounds
                    float probeSize = 16f * unitScale;
                    com.badlogic.gdx.math.Rectangle flyerProbe = new com.badlogic.gdx.math.Rectangle(
                        targetVx > 0
                            ? collision.worldBounds.x + collision.worldBounds.width
                            : collision.worldBounds.x - probeSize,
                        collision.worldBounds.y,
                        probeSize,
                        collision.worldBounds.height
                    );
                    boolean obstacleAhead = false;
                    for (Rectangle rect : collisionRects) {
                        if (flyerProbe.overlaps(rect)) {
                            obstacleAhead = true;
                            break;
                        }
                    }

                    if (obstacleAhead) {
                        // Steer perpendicularly (slide along obstacle vertically or horizontally)
                        targetVy += (dy >= 0 ? 1f : -1.5f) * enemy.speed;
                        targetVx *= 0.5f;
                    }

                    movement.velocity.x = targetVx;
                    movement.velocity.y = targetVy;
                } else {
                    // Within standoff range: hover/drift around or hold
                    movement.velocity.x = 0f;
                    flying.bobTime += deltaTime;
                    movement.velocity.y = flying.bobAmplitude * flying.bobFrequency * com.badlogic.gdx.math.MathUtils.cos(flying.bobTime * flying.bobFrequency);
                }

                // 3. Enforce a minimum flight altitude (at least one tile / 16px above floor/platform below it, or relative to its spawn height)
                applyMinimumAltitude(transform, collision, movement, flying);
                return;
            } else if (flying.flightState == FlyingEnemyComponent.FlightState.ATTACK_APPROACH) {
                // Player left detection range -> trigger retreat back to patrol origin
                flying.flightState = FlyingEnemyComponent.FlightState.RETREAT;
                flying.retreatTimer.start(3.0f); // max 3 seconds retreat fallback
            }

            // Normal flyer patrol behavior
            if (enemy.aiMode == AiMode.PATROL && !resumedFromTurnPause) {
                if (transform.position.x <= enemy.originX - enemy.patrolRange || transform.position.x >= enemy.originX + enemy.patrolRange) {
                    turnAround(enemy);
                }
            }
            if (enemy.turnPause.isActive()) {
                movement.velocity.x = 0;
                movement.velocity.y = 0;
            } else {
                movement.velocity.x = enemy.speed * enemy.direction;
                flying.bobTime += deltaTime;
                movement.velocity.y = flying.bobAmplitude * flying.bobFrequency * com.badlogic.gdx.math.MathUtils.cos(flying.bobTime * flying.bobFrequency);
            }
            applyMinimumAltitude(transform, collision, movement, flying);
            return;
        }

        // Chase: detected player → move toward them using normal movement, but never turn away
        // (a wall/ledge/hazard just holds the enemy in place, still facing the player).
        if (playerInDetection) {
            float enemyCenterX = collision.worldBounds.x + collision.worldBounds.width / 2f;
            if (Math.abs(playerCenterX - enemyCenterX) > 1f) {
                enemy.direction = playerCenterX > enemyCenterX ? 1 : -1;
            }
            if (blockedByWall || atLedge || atHazard) {
                movement.velocity.x = 0f;
            } else {
                movement.velocity.x = enemy.speed * enemy.direction;
            }
            return;
        }

        if (blockedByWall || atLedge || atHazard) {
            turnAround(enemy);
        } else if (enemy.aiMode == AiMode.PATROL && !resumedFromTurnPause) {
            if (transform.position.x <= enemy.originX - enemy.patrolRange || transform.position.x >= enemy.originX + enemy.patrolRange) {
                turnAround(enemy);
            }
        }

        if (enemy.turnPause.isActive()) {
            movement.velocity.x = 0;
        } else {
            movement.velocity.x = enemy.speed * enemy.direction;
        }
    }

    /**
     * Spawns loot on death, or defaults to coins if no loot is configured.
     */
    private void dropLoot(Entity entity, EnemyComponent enemy, TransformComponent transform, CollisionComponent collision) {
        LootComponent loot = LOOT.get(entity);

        float centerX = transform.position.x;
        float centerY = transform.position.y;
        if (collision != null) {
            centerX = collision.worldBounds.x + collision.worldBounds.width / 2f;
            centerY = collision.worldBounds.y + collision.worldBounds.height / 2f;
        }

        if (loot != null) {
            for (LootComponent.LootEntry drop : loot.drops) {
                switch (drop.type) {
                    case COIN:
                        entityFactory.popCoins(getEngine(), centerX, centerY, drop.amount, unitScale, collisionRects);
                        break;
                    case AMMO:
                        entityFactory.popAmmo(getEngine(), centerX, centerY, drop.amount, unitScale, collisionRects);
                        break;
                    case POTION:
                        entityFactory.popPotion(getEngine(), centerX, centerY, drop.potionType, unitScale, collisionRects);
                        break;
                }
            }
        } else {
            // Default to coins
            int coinCount = (int) (enemy.maxHealth / COINS_PER_HEALTH);
            if (coinCount > 0) {
                entityFactory.popCoins(getEngine(), centerX, centerY, coinCount, unitScale, collisionRects);
            }
        }
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

    /**
     * Enforces a minimum flight altitude for flyers: ensures the flyer's bottom is at least
     * one solid tile (16 units * unitScale) above any floor or platform directly below it,
     * maintaining solid 1-tile clearance and never pinning or sinking to the floor.
     * Operates on {@code transform.position} — NOT {@code collision.worldBounds} — because
     * {@code CollisionBoundsSystem} (priority 6) regenerates worldBounds from the transform
     * every frame; writing worldBounds directly would be wiped out immediately and the flyer
     * would sink/hover at the ground.
     */
    private void applyMinimumAltitude(TransformComponent transform, CollisionComponent collision, MovementComponent movement, FlyingEnemyComponent flying) {
        float minTileHeight = 16f * unitScale;
        float flyerBottom = transform.position.y + collision.bounds.y;

        // Ray/box probe directly beneath the flyer to find any floor or platform below
        com.badlogic.gdx.math.Rectangle floorProbe = new com.badlogic.gdx.math.Rectangle(
            transform.position.x + collision.bounds.x + 2f,
            flyerBottom - 48f * unitScale,
            Math.max(4f, collision.bounds.width - 4f),
            48f * unitScale
        );

        float highestFloorTop = Float.NEGATIVE_INFINITY;
        for (Rectangle rect : collisionRects) {
            if (floorProbe.overlaps(rect)) {
                float top = rect.y + rect.height;
                if (top > highestFloorTop) {
                    highestFloorTop = top;
                }
            }
        }
        for (Rectangle rect : oneWayRects) {
            if (floorProbe.overlaps(rect)) {
                float top = rect.y + rect.height;
                if (top > highestFloorTop) {
                    highestFloorTop = top;
                }
            }
        }

        if (highestFloorTop != Float.NEGATIVE_INFINITY) {
            float requiredBottom = highestFloorTop + minTileHeight;
            if (flyerBottom < requiredBottom) {
                // Push the flyer's transform up so the clamp persists through MovementSystem
                // integration and CollisionBoundsSystem's worldBounds recompute
                float diff = requiredBottom - flyerBottom;
                transform.position.y += diff;
                if (movement.velocity.y < 0f) {
                    movement.velocity.y = Math.abs(movement.velocity.y);
                }
            }
        }

        // Also ensure it never drifts more than a couple tiles below its spawn Y altitude
        float minSpawnAlt = flying.spawnY - 32f * unitScale;
        if (flyerBottom < minSpawnAlt) {
            transform.position.y += minSpawnAlt - flyerBottom;
            if (movement.velocity.y < 0f) {
                movement.velocity.y = 0f;
            }
        }
    }
}
