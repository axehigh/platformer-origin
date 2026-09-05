package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Resolves enemy-vs-player contact damage: looks up the single player entity once, then for each
 * enemy checks AABB overlap against it. On overlap, while the player's {@code hitInvulnerability}
 * timer is done, applies damage through the shared {@code PlayerDamageResolver} — one point of
 * health, a brief hit-stun lock (during which the player's input is frozen so the knockback pop
 * can play out), a small knockback push away from the enemy, and a grace period so a single
 * sustained overlap (or several enemies at once) doesn't shred health in one frame. Contact
 * damage is suppressed entirely while the enemy is staggered or the player's own melee swing is
 * live: during the initial hit-stun (and while swinging) the enemy's body is pure pass-through,
 * while during the post-hit idle that follows it the body deals no damage but ejects the player
 * gently instead of letting them stand inside it.
 */
public class EnemyContactSystem extends IteratingSystem {
    /** Tunable: how fast a post-hit-idle enemy body pushes the player out of it (u/s). Slightly
     * above the player's 90 u/s run speed so the recovering body behaves like a gentle wall. */
    private static final float EJECT_SPEED_X = 110f;
    private ImmutableArray<Entity> players;
    private float unitScale = 1f;

    public EnemyContactSystem() {
        this(0);
    }

    public EnemyContactSystem(int priority) {
        super(Family.all(EnemyComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
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
        Entity playerEntity = players.first();
        PlayerComponent player = PLAYER.get(playerEntity);
        TransformComponent playerTransform = TRANSFORM.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        CollisionComponent enemyCollision = COLLISION.get(enemyEntity);

        if (!playerCollision.worldBounds.overlaps(enemyCollision.worldBounds)) {
            return;
        }

        TransformComponent enemyTransform = TRANSFORM.get(enemyEntity);
        float playerCenterX = playerTransform.position.x + playerCollision.bounds.x + playerCollision.bounds.width / 2f;
        float enemyCenterX = enemyTransform.position.x + enemyCollision.bounds.x + enemyCollision.bounds.width / 2f;
        int knockbackDirection = playerCenterX >= enemyCenterX ? 1 : -1;
        MovementComponent playerMovement = MOVEMENT.get(playerEntity);

        // Stagger grace: while the enemy is in its initial hit-stun — or the player's own swing is
        // live — the enemy's body is pure pass-through (walking an enemy down mid-swing, or the
        // brief stun window, never trades a life for the strike). Suppress silently; the
        // overlap/knockback math above is untouched and one damage-resolution-per-frame still holds.
        if (player.meleeAttack.isActive() || enemy.hitStun.isActive()) {
            return;
        }

        // Post-hit idle: the enemy's body still deals no damage, but it no longer passes through —
        // it ejects the player horizontally out of it like a gentle wall. Velocity-based (not
        // positional) on purpose, so the push flows through MovementSystem's normal collision
        // resolution next frame instead of teleporting the player into walls.
        if (enemy.postHitIdle.isActive()) {
            playerMovement.velocity.x = EJECT_SPEED_X * knockbackDirection * unitScale;
            return;
        }

        // Healthy enemy: normalize contact damage path as before.
        PlayerDamageResolver.applyHit(playerEntity, player, playerMovement, knockbackDirection, unitScale);
    }
}
