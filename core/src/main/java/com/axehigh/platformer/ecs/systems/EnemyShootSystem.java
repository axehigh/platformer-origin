package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Drives shooter-enemy firing. A shot only **commits** when the cooldown is done, the shooter is
 * alive and not in its initial hit-stun, its room is the active one, and the player's collision
 * center is within the detection reach horizontally and within a {@code ±1 source-tile} band
 * vertically of the shooter's own collision center (asymmetric — the reach depends on which side
 * of the shooter's CURRENT facing the player is on: {@code detectionRange} ahead, only
 * {@code detectionRange × REAR_DETECTION_SCALE} behind; no line-of-sight). On commit the shooter snap-faces the player
 * ({@code enemy.direction = sign(centerX difference)}, like the melee commit). If that facing
 * change was needed (the player was on the side opposite the current {@code enemy.direction}),
 * the shot enters a **turn-idle**: the shooter stands still for {@code EnemySystem.TURN_PAUSE_DURATION × unitScale}
 * (the same pause a walker holds after a patrol turn-around) during which its horizontal velocity
 * is zeroed every frame, it does NOT fire, and the facing re-tracks — the player crossing to the
 * other side mid-idle flips {@code enemy.direction} again. If NO facing change was needed, the
 * wind-up starts immediately on the same frame as commit (unchanged behavior). Once the turn-idle
 * completes (or is skipped), the fire direction is captured — {@code shooter.fireDirection = enemy.direction}
 * — AND THEN {@code shooter.windUp} starts: the bullet is NOT spawned yet, and {@code fireDirection}
 * is locked for the rest of the shot, so a patrol flip mid-wind-up can't re-aim it. The wind-up
 * duration is the **effective maximum** of the authored seconds and the {@code ATTACKING} clip:
 * {@code Math.max(shooter.windUpSeconds, ATTACKING clip duration)} — the Tiled per-marker
 * {@code windUp} property (default {@code 0.5}, real seconds, matching the melee
 * {@code EnemyAttackComponent.windUpDuration} convention) wins unless the clip is longer, with the
 * existing {@link #DEFAULT_WINDUP_DURATION} fallback when no ATTACKING clip exists. During the
 * wind-up this system zeroes the shooter's horizontal velocity every frame (it plants in place for
 * the charged shot, exactly like the turn-idle stand-still) while the {@code AnimationSystem}
 * loops the {@code ATTACKING} pose (the spider visibly pulses its charge — see the
 * {@code AnimationSystem} row). The bullet spawns the frame the wind-up completes, then
 * {@code shootCooldown}
 * restarts at {@code shootInterval} (the cooldown does NOT restart on commit — a cancelled
 * shot lets the shooter re-detect and re-commit immediately). Both the turn-idle and the wind-up
 * are cancelled (timer reset, no wind-up / no bullet) if, before the phase completes: the shooter
 * dies, enters its initial hit-stun, its room becomes inactive, or the player stops meeting the
 * detection rule. Spawned bullets travel horizontally in the captured {@code fireDirection} at the
 * collision box's vertical center on the enemy's leading edge, with {@code maxTravelDistance =
 * shooter.shootRange} (world units; see {@code EnemyBulletCollisionSystem}).
 * <p>
 * Priority note: {@code EnemySystem} and this system both run at priority {@code 4}; in
 * {@code GameSystems} {@code EnemySystem} is added FIRST and Ashley's sort is stable, so it runs
 * before this system each frame. That ordering is what makes the turn-idle stand-still win:
 * {@code EnemySystem} has just written the shooter's patrol velocity when this system re-asserts
 * {@code velocity.x = 0}, and {@code MovementSystem} (priority 5) integrates that zero. (During
 * the idle {@code EnemySystem} may briefly misread the previous frame's zeroed velocity as a wall
 * block and flip direction + start {@code enemy.turnPause}; the per-frame re-track here corrects
 * the facing immediately, and the coincidental {@code turnPause} simply helps hold the shooter
 * still. This system never touches {@code EnemyComponent.turnPause} — that timer belongs to
 * {@code EnemySystem}.)
 */
public class EnemyShootSystem extends IteratingSystem {
    private static final float BULLET_SPEED = 150f;
    private static final float BULLET_LIFETIME = 1.5f;
    private static final float BULLET_SIZE = 4f;
    private static final float BULLET_Z = 8f;
    /**
     * Base vertical half-band of the shot-detection rule: the player's center must be within
     * ±1 tile of the shooter's center ("same horizontal line"). One base tile is 16 world units;
     * at runtime the band is {@code DETECTION_VERTICAL_BAND × unitScale} (16 × unitScale = the
     * map's actual tile width, e.g. ±128u on 128px maps), matching the {@code shootRange}/{@code
     * detectionRange} conversion in {@code EnemyFactory}.
     */
    static final float DETECTION_VERTICAL_BAND = 16f;
    /**
     * Fraction of {@code detectionRange} the shot-detection reach extends BEHIND the shooter's
     * current facing ({@code 0.5} = half as far behind as in front): the player sneaking up on the
     * spider's rear must get much closer before a shot can commit.
     */
    static final float REAR_DETECTION_SCALE = 0.5f;
    /** Fallback wind-up if the shooter has no ATTACKING animation clip. */
    private static final float DEFAULT_WINDUP_DURATION = 0.3f;

    private final AssetManager assetManager;
    private final RoomState roomState;
    private PooledEngine engine;
    private ImmutableArray<Entity> players;
    private float unitScale = 1f;

    public EnemyShootSystem(AssetManager assetManager, RoomState roomState) {
        this(assetManager, roomState, 0);
    }

    public EnemyShootSystem(AssetManager assetManager, RoomState roomState, int priority) {
        super(Family.all(EnemyComponent.class, EnemyShooterComponent.class, TransformComponent.class, CollisionComponent.class, AnimationComponent.class).get(), priority);
        this.assetManager = assetManager;
        this.roomState = roomState;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = (PooledEngine) engine;
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EnemyComponent enemy = ENEMY.get(entity);
        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        AnimationComponent animation = ANIMATION.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);

        if (enemy.isDead) {
            shooter.turnIdle.reset();
            shooter.windUp.reset();
            return;
        }

        if (shooter.turnIdle.isActive()) {
            // Turn-idle: the commit needed a facing change, so the shooter stands still for
            // EnemySystem.TURN_PAUSE_DURATION × unitScale before the wind-up. Cancel before
            // ticking, exactly like the wind-up below: hit-stun, room frozen, or the player no
            // longer detected abandons the shot entirely — no fireDirection lock, no wind-up, and
            // a cancelled idle never restarts the cooldown, so the shooter re-detects and
            // re-commits (turn-idle again, if the facing still differs).
            if (enemy.hitStun.isActive() || !isRoomActive(enemy) || !playerDetected(collision, enemy, shooter.detectionRange)) {
                shooter.turnIdle.reset();
                return;
            }
            // Re-track facing: if the player crossed to the other side mid-idle, snap back so the
            // shot committed toward one side can still re-aim before the wind-up locks it.
            CollisionComponent playerCollision = COLLISION.get(players.first());
            int trackedDirection = facingToward(collision, playerCollision);
            if (trackedDirection != enemy.direction) {
                enemy.direction = trackedDirection;
            }
            // Stand still (EnemySystem ran before us this frame at the same priority, so this
            // zero is what MovementSystem integrates; the fixture-less velocity is null-guarded).
            if (movement != null) {
                movement.velocity.x = 0f;
            }
            shooter.turnIdle.update(deltaTime);
            if (!shooter.turnIdle.isActive()) {
                // Idle completed: lock the fire direction to the re-tracked facing NOW (wind-up
                // start), then proceed into the existing telegraph.
                shooter.fireDirection = enemy.direction;
                shooter.windUp.start(effectiveWindUpDuration(shooter, animation));
            }
            return;
        }

        if (shooter.windUp.isActive()) {
            // Cancel before ticking: dead (checked above), hit-stun, room frozen, or the player
            // no longer detected. A cancelled wind-up never restarts the cooldown, so the shooter
            // re-detects and re-commits immediately.
            if (enemy.hitStun.isActive() || !isRoomActive(enemy) || !playerDetected(collision, enemy, shooter.detectionRange)) {
                shooter.windUp.reset();
                return;
            }
            // Stand still for the charge (mirrors the turn-idle): the shooter plants in place
            // during the whole telegraph instead of patrolling (reverses the older
            // patrolling-wind-up behavior; EnemySystem ran before us this frame at the same
            // priority, so this zero is what MovementSystem integrates).
            if (movement != null) {
                movement.velocity.x = 0f;
            }
            shooter.windUp.update(deltaTime);
            if (shooter.windUp.isDone()) {
                spawnBullet(transform, collision, shooter.fireDirection, shooter.shootRange);
                shooter.shootCooldown.start(shooter.shootInterval);
            }
            return;
        }

        shooter.shootCooldown.update(deltaTime);
        if (enemy.hitStun.isActive()) {
            return;
        }

        if (shooter.shootCooldown.isDone() && isRoomActive(enemy) && playerDetected(collision, enemy, shooter.detectionRange)) {
            // Commit: snap-face the player (mirrors the melee commit in EnemyAttackSystem). No
            // cooldown restart here. If the snap-turn actually needed to change facing, hold a
            // stationary turn-idle before the wind-up; otherwise the wind-up starts immediately
            // (the facing-change re-aim opportunity doesn't apply).
            CollisionComponent playerCollision = COLLISION.get(players.first());
            int targetDirection = facingToward(collision, playerCollision);
            if (targetDirection != enemy.direction) {
                enemy.direction = targetDirection;
                shooter.turnIdle.start(EnemySystem.TURN_PAUSE_DURATION * unitScale);
                if (movement != null) {
                    movement.velocity.x = 0f;
                }
            } else {
                shooter.fireDirection = enemy.direction;
                shooter.windUp.start(effectiveWindUpDuration(shooter, animation));
            }
        }
    }

    /** Sign of (playerCenterX − shooterCenterX): which side the player is on, {@code 1} right / {@code -1} left. */
    private int facingToward(CollisionComponent shooterCollision, CollisionComponent playerCollision) {
        float shooterCenterX = shooterCollision.worldBounds.x + shooterCollision.worldBounds.width / 2f;
        float playerCenterX = playerCollision.worldBounds.x + playerCollision.worldBounds.width / 2f;
        return playerCenterX > shooterCenterX ? 1 : -1;
    }

    private boolean isRoomActive(EnemyComponent enemy) {
        return enemy.roomIndex < 0 || enemy.roomIndex == roomState.activeRoomIndex;
    }

    /**
     * Detection gate: player center within a per-side reach horizontally and a ±1-tile band
     * vertically. The horizontal reach depends on which side of the shooter's CURRENT facing
     * ({@code enemy.direction}) the player is on: {@code detectionRange} ahead, only
     * {@code detectionRange × REAR_DETECTION_SCALE} behind. Because the shooter snap-faces the
     * player at commit and re-tracks during the turn-idle, the cancels during those phases run
     * against the player-facing direction — the player is effectively always "ahead" there, so
     * those checks use the full range.
     */
    private boolean playerDetected(CollisionComponent shooterCollision, EnemyComponent enemy, float detectionRange) {
        if (players.size() == 0) {
            return false;
        }
        CollisionComponent playerCollision = COLLISION.get(players.first());
        float shooterCenterX = shooterCollision.worldBounds.x + shooterCollision.worldBounds.width / 2f;
        float shooterCenterY = shooterCollision.worldBounds.y + shooterCollision.worldBounds.height / 2f;
        float playerCenterX = playerCollision.worldBounds.x + playerCollision.worldBounds.width / 2f;
        float playerCenterY = playerCollision.worldBounds.y + playerCollision.worldBounds.height / 2f;
        int playerSide = playerCenterX > shooterCenterX ? 1 : -1;
        float reach = playerSide == enemy.direction ? detectionRange : detectionRange * REAR_DETECTION_SCALE;
        return Math.abs(playerCenterX - shooterCenterX) <= reach
            && Math.abs(playerCenterY - shooterCenterY) <= DETECTION_VERTICAL_BAND * unitScale;
    }

    /**
     * Effective wind-up telegraph duration: {@code Math.max(shooter.windUpSeconds, ATTACKING clip
     * duration)} — the authored seconds (Tiled {@code windUp}, real time) win unless the clip is
     * longer, so a slow/long attack clip can extend the charge but never shortens it. Falls back
     * to {@link #DEFAULT_WINDUP_DURATION} when the shooter has no ATTACKING clip.
     */
    private float effectiveWindUpDuration(EnemyShooterComponent shooter, AnimationComponent animation) {
        Animation<TextureRegion> attackAnim = animation != null ? animation.animations.get(AnimationComponent.State.ATTACKING) : null;
        float clipDuration = attackAnim != null ? attackAnim.getAnimationDuration() : DEFAULT_WINDUP_DURATION;
        return Math.max(shooter.windUpSeconds, clipDuration);
    }

    private void spawnBullet(TransformComponent enemyTransform, CollisionComponent enemyCollision, int direction, float shootRange) {
        Entity bullet = engine.createEntity();

        float bulletSize = BULLET_SIZE * unitScale;
        float centerY = enemyTransform.position.y + enemyCollision.bounds.y + (enemyCollision.bounds.height - bulletSize) / 2f;
        float spawnX = direction > 0
            ? enemyTransform.position.x + enemyCollision.bounds.x + enemyCollision.bounds.width
            : enemyTransform.position.x + enemyCollision.bounds.x - bulletSize;

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(spawnX, centerY);
        transform.scale.set(unitScale, unitScale);
        transform.z = BULLET_Z;
        bullet.add(transform);

        TextureComponent textureComponent = engine.createComponent(TextureComponent.class);
        textureComponent.region = new TextureRegion(assetManager.get("gfx/old/bullet.png", Texture.class));
        bullet.add(textureComponent);

        MovementComponent movement = engine.createComponent(MovementComponent.class);
        float speed = BULLET_SPEED * unitScale;
        movement.velocity.set(speed * direction, 0f);
        movement.maxSpeedX = speed;
        movement.maxSpeedY = 0f;
        bullet.add(movement);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(bulletSize, bulletSize);
        collision.updateWorldBounds(transform.position);
        bullet.add(collision);

        BulletComponent bulletComponent = engine.createComponent(BulletComponent.class);
        bulletComponent.damage = 0f;
        bulletComponent.lifetime = BULLET_LIFETIME;
        // travel range (world units) — the bullet despawns exactly after shootRange world units.
        bulletComponent.maxTravelDistance = shootRange;
        bulletComponent.traveledDistance = 0f;
        bullet.add(bulletComponent);

        bullet.add(engine.createComponent(EnemyBulletComponent.class));

        engine.addEntity(bullet);
    }
}