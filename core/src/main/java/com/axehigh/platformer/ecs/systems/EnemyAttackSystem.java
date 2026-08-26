package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Resolves enemy melee attacks: detects player proximity, triggers a wind-up telegraph,
 * then applies damage via {@code PlayerDamageResolver}. Enemies pause their patrol during
 * the attack and face the player when initiating.
 */
public class EnemyAttackSystem extends IteratingSystem {
    private ImmutableArray<Entity> players;
    private float unitScale = 1f;

    public EnemyAttackSystem() {
        this(0);
    }

    public EnemyAttackSystem(int priority) {
        super(Family.all(EnemyComponent.class, EnemyAttackComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
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
        TransformComponent playerTransform = TRANSFORM.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        TransformComponent enemyTransform = TRANSFORM.get(enemyEntity);
        CollisionComponent enemyCollision = COLLISION.get(enemyEntity);
        MovementComponent enemyMovement = MOVEMENT.get(enemyEntity);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);

        float playerCenterX = playerTransform.position.x + playerCollision.bounds.x + playerCollision.bounds.width / 2f;
        float enemyCenterX = enemyTransform.position.x + enemyCollision.bounds.x + enemyCollision.bounds.width / 2f;

        // Tick the attack cooldown every frame
        attack.attackCooldown.update(deltaTime);

        if (attack.isAttacking) {
            // Zero horizontal velocity during attack
            enemyMovement.velocity.x = 0;

            // Tick the wind-up timer
            attack.windUp.update(deltaTime);

            if (attack.windUp.isDone()) {
                // Wind-up finished — the strike lands
                float meleeWidth = attack.meleeRange * unitScale;
                float meleeHeight = enemyCollision.worldBounds.height;

                Rectangle meleeHitbox = new Rectangle();
                if (enemy.direction > 0) {
                    meleeHitbox.set(
                        enemyCollision.worldBounds.x + enemyCollision.worldBounds.width,
                        enemyCollision.worldBounds.y,
                        meleeWidth,
                        meleeHeight
                    );
                } else {
                    meleeHitbox.set(
                        enemyCollision.worldBounds.x - meleeWidth,
                        enemyCollision.worldBounds.y,
                        meleeWidth,
                        meleeHeight
                    );
                }

                if (meleeHitbox.overlaps(playerCollision.worldBounds)) {
                    int knockbackDirection = playerCenterX >= enemyCenterX ? 1 : -1;
                    PlayerDamageResolver.applyHit(playerEntity, player, MOVEMENT.get(playerEntity), knockbackDirection, unitScale);
                }

                // End the attack
                attack.isAttacking = false;
                attack.attackCooldown.start(attack.attackInterval);
            }
        } else if (attack.attackCooldown.isDone()) {
            // Not attacking and cooldown is done — check if player is in aggro range
            float aggroRangeX = attack.meleeRange * 3f * unitScale;
            float aggroRangeY = enemyCollision.worldBounds.height * 2f;
            float dx = Math.abs(playerCenterX - enemyCenterX);
            float playerCenterY = playerTransform.position.y + playerCollision.bounds.y + playerCollision.bounds.height / 2f;
            float enemyCenterY = enemyTransform.position.y + enemyCollision.bounds.y + enemyCollision.worldBounds.height / 2f;
            float dy = Math.abs(playerCenterY - enemyCenterY);

            if (dx <= aggroRangeX && dy <= aggroRangeY) {
                // Face the player
                enemy.direction = playerCenterX > enemyCenterX ? 1 : -1;
                // Start the wind-up
                attack.windUp.start(attack.windUpDuration);
                attack.isAttacking = true;
                // Stop patrol movement
                enemyMovement.velocity.x = 0;
            }
        }
    }
}
