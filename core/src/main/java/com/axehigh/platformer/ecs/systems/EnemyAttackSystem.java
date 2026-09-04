package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Resolves enemy melee attacks: live player detection (a centered box, {@code attackRange*3}
 * wide per side and {@code detectionHeight} tall total), a front commit rectangle that starts
 * a wind-up telegraph, then a short strike window during which the strike hitbox (enemy
 * collision width × height, in front of the enemy) can deal damage via {@code PlayerDamageResolver}.
 * Enemies pause their patrol during the attack and face the player when initiating; attacks are
 * room-gated like {@code EnemySystem}/{@code EnemyShootSystem} so a frozen enemy never attacks.
 * Exposure of the live strike rectangle ({@link #getActiveStrikeBounds()}) lets debug tooling
 * (and anything else) mirror the player's blade-out.
 */
public class EnemyAttackSystem extends IteratingSystem {
    private ImmutableArray<Entity> players;
    private float unitScale = 1f;
    private final RoomState roomState;

    /** Live strike rectangle; only assigned while the strike window is active. */
    private final Rectangle strikeBounds = new Rectangle();
    private boolean strikeLive = false;

    /** Reused scratch rect for the front attack-range commit check. */
    private final Rectangle rangeRect = new Rectangle();

    public EnemyAttackSystem() {
        this(null, 0);
    }

    public EnemyAttackSystem(RoomState roomState) {
        this(roomState, 0);
    }

    public EnemyAttackSystem(RoomState roomState, int priority) {
        super(Family.all(EnemyComponent.class, EnemyAttackComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.roomState = roomState;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    /**
     * The currently-live enemy strike hitbox (enemy collision width × height, adjacent to the
     * enemy's facing edge), or {@code null} unless the strike window is active right now —
     * mirrors {@code MeleeAttackSystem#getActiveStrikeBounds()} for the player.
     */
    public Rectangle getActiveStrikeBounds() {
        return strikeLive ? strikeBounds : null;
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
        if (enemy.hitStun.isActive()) {
            return;
        }

        Entity playerEntity = players.first();
        PlayerComponent player = PLAYER.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        CollisionComponent enemyCollision = COLLISION.get(enemyEntity);
        MovementComponent enemyMovement = MOVEMENT.get(enemyEntity);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);

        float playerCenterX = playerCollision.worldBounds.x + playerCollision.worldBounds.width / 2f;
        float playerCenterY = playerCollision.worldBounds.y + playerCollision.worldBounds.height / 2f;
        float enemyCenterX = enemyCollision.worldBounds.x + enemyCollision.worldBounds.width / 2f;
        float enemyCenterY = enemyCollision.worldBounds.y + enemyCollision.worldBounds.height / 2f;

        // Live detection: player CENTER inside the centered box (attackRange*3 wide per side, detectionHeight tall total).
        float detectHalfX = attack.attackRange * 3f * unitScale;
        float detectHalfY = attack.detectionHeight * unitScale / 2f;
        boolean playerInDetection = Math.abs(playerCenterX - enemyCenterX) <= detectHalfX
            && Math.abs(playerCenterY - enemyCenterY) <= detectHalfY;

        // Always tick the cooldown (mirrors EnemyShootSystem; idles at done so the enemy is armed).
        attack.attackCooldown.update(deltaTime);

        // postHitIdle blocks the attack trigger too (the enemy is recovering, mirrors EnemySystem).
        if (enemy.postHitIdle.isActive()) {
            return;
        }

        // Room gate: a room-frozen enemy never attacks (same rule as EnemySystem).
        boolean roomActive = roomState == null || enemy.roomIndex < 0 || enemy.roomIndex == roomState.activeRoomIndex;
        if (!roomActive) {
            return;
        }

        strikeLive = false;

        if (attack.isAttacking) {
            // Facing locked, patrol paused (EnemySystem holds velocity zeroed); keep zeroing here too.
            enemyMovement.velocity.x = 0;
            attack.windUp.update(deltaTime);
            if (attack.windUp.isDone()) {
                if (!attack.strike.isActive()) {
                    attack.strike.start(attack.strikeWindow);
                }
                attack.strike.update(deltaTime);
                if (attack.strike.isDone()) {
                    attack.isAttacking = false;
                    attack.recovery.start(attack.recoveryDuration);
                    attack.attackCooldown.start(attack.attackInterval);
                } else {
                    // Blade out: live strike bounds = enemy collision width x height, adjacent in front.
                    strikeLive = true;
                    if (enemy.direction > 0) {
                        strikeBounds.set(enemyCollision.worldBounds.x + enemyCollision.worldBounds.width,
                            enemyCollision.worldBounds.y,
                            enemyCollision.worldBounds.width, enemyCollision.worldBounds.height);
                    } else {
                        strikeBounds.set(enemyCollision.worldBounds.x - enemyCollision.worldBounds.width,
                            enemyCollision.worldBounds.y,
                            enemyCollision.worldBounds.width, enemyCollision.worldBounds.height);
                    }
                    if (strikeBounds.overlaps(playerCollision.worldBounds)) {
                        int knockbackDirection = playerCenterX >= enemyCenterX ? 1 : -1;
                        PlayerDamageResolver.applyHit(playerEntity, player, MOVEMENT.get(playerEntity), knockbackDirection, unitScale);
                    }
                }
            }
        } else if (attack.recovery.isActive()) {
            // Post-strike wind-down: still recovering from the last swing, no new trigger
            // (defense-in-depth — attackCooldown normally blocks this anyway).
            return;
        } else if (attack.attackCooldown.isDone() && playerInDetection && attackRangeRectOverlaps(enemyCollision, enemy.direction, attack, playerCollision.worldBounds)) {
            // Detected (chasing) player in the front commit rectangle and ready: commit —
            // snap-face the player, lock facing for the wind-up, stop patrol.
            attack.strike.reset();
            enemy.direction = playerCenterX > enemyCenterX ? 1 : -1;
            attack.windUp.start(attack.windUpDuration);
            attack.isAttacking = true;
            enemyMovement.velocity.x = 0;
        }
    }

    /**
     * The strike-commit rectangle: {@code attackRange * unitScale} wide, adjacent to the enemy's
     * facing edge, collision height tall, aligned to {@code worldBounds}. Overlap with the
     * player's bounds commits a strike (provided the player is also detected).
     */
    private boolean attackRangeRectOverlaps(CollisionComponent enemyCollision, int direction, EnemyAttackComponent attack, Rectangle playerBounds) {
        float rangeW = attack.attackRange * unitScale;
        float rx = direction > 0
            ? enemyCollision.worldBounds.x + enemyCollision.worldBounds.width
            : enemyCollision.worldBounds.x - rangeW;
        rangeRect.set(rx, enemyCollision.worldBounds.y, rangeW, enemyCollision.worldBounds.height);
        return rangeRect.overlaps(playerBounds);
    }
}
