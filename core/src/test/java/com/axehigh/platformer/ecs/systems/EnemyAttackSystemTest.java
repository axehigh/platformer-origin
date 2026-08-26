package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;

/**
 * Headless unit tests for {@code EnemyAttackSystem}: aggro-range detection, wind-up → melee
 * hitbox → damage, cooldown gating, dead/stunned enemy skips, hitbox direction, and
 * out-of-range melee miss.
 */
public class EnemyAttackSystemTest extends SystemTestBase {

    private Engine engine;
    private EnemyAttackSystem system;

    @Before
    public void setUp() {
        system = new EnemyAttackSystem();
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        EnemyComponent enemyComponent = new EnemyComponent();
        EnemyAttackComponent attackComponent = new EnemyAttackComponent();
        MovementComponent movement = movement();
        Entity entity = entity(transform, movement, enemyComponent, attackComponent, collision);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void enemyFacesPlayerAndStartsWindUpWhenInRange() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);

        engine.update(DT);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        EnemyComponent enemyComp = ENEMY.get(enemyEntity);

        assertTrue("attack should be active", attack.isAttacking);
        assertTrue("wind-up should be active", attack.windUp.isActive());
        assertEquals("enemy should face right toward player", 1, enemyComp.direction);
        assertEquals("enemy velocity.x should be zeroed", 0f, MOVEMENT.get(enemyEntity).velocity.x, EPSILON);
    }

    @Test
    public void windUpCompletesAndDealsDamage() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        // Manually start the attack to skip the aggro-range check
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        // Tick the wind-up past its duration
        engine.update(attack.windUpDuration + DT);

        assertEquals("player should take damage", 2, playerComp.health);
        assertFalse("attack should be finished", attack.isAttacking);
    }

    @Test
    public void attackSkippedWhenPlayerOutOfRange() {
        // Player far beyond aggro range (meleeRange*3 = 72 at unitScale=1)
        Entity playerEntity = player(200f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        engine.update(DT);

        assertFalse("attack should not start", ENEMY_ATTACK.get(enemyEntity).isAttacking);
        assertEquals("health unchanged", 3, playerComp.health);
    }

    @Test
    public void cooldownPreventsRapidReattack() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        // First attack completes
        engine.update(attack.windUpDuration + DT);
        assertEquals("first hit lands", 2, playerComp.health);
        assertTrue("cooldown should be active", attack.attackCooldown.isActive());

        // Immediately update again — cooldown should block a second attack
        engine.update(DT);
        assertFalse("second attack should not start", attack.isAttacking);
        assertEquals("health unchanged", 2, playerComp.health);
    }

    @Test
    public void deadEnemyDoesNotAttack() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        ENEMY.get(enemyEntity).isDead = true;

        engine.update(DT);

        assertFalse("dead enemy should not attack", ENEMY_ATTACK.get(enemyEntity).isAttacking);
    }

    @Test
    public void stunnedEnemyDoesNotAttack() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        ENEMY.get(enemyEntity).hitStun.start(1.0f);

        engine.update(DT);

        assertFalse("stunned enemy should not attack", ENEMY_ATTACK.get(enemyEntity).isAttacking);
    }

    @Test
    public void meleeHitboxDirectionMatchesEnemyFacing() {
        // --- Case 1: enemy to the LEFT of player → should face right (direction=1) ---
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(-2f, 0f);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        // Let the system naturally detect aggro and set direction
        engine.update(DT);
        assertTrue("attack should have started", attack.isAttacking);
        assertEquals("enemy should face right", 1, ENEMY.get(enemyEntity).direction);

        // Advance past wind-up to land the hit
        engine.update(attack.windUpDuration + DT);
        assertEquals("damage from the right side", 2, PLAYER.get(playerEntity).health);

        // --- Case 2: enemy to the RIGHT of player → should face left (direction=-1) ---
        PlayerComponent playerComp = PLAYER.get(playerEntity);
        // Clear invulnerability so the next hit can land
        playerComp.hitInvulnerability.reset();
        playerComp.hurtTimer.reset();

        // Move enemy to the right of the player
        TransformComponent eTransform = TRANSFORM.get(enemyEntity);
        CollisionComponent eCollision = eCollision(enemyEntity);
        place(eTransform, eCollision, 20f, 0f);
        ENEMY.get(enemyEntity).direction = 1; // will be overridden by system

        // Reset attack state so the system can naturally start a new attack
        attack.isAttacking = false;
        attack.attackCooldown.reset();

        engine.update(DT);
        assertTrue("attack should have started", attack.isAttacking);
        assertEquals("enemy should face left", -1, ENEMY.get(enemyEntity).direction);

        // Advance past wind-up to land the hit
        engine.update(attack.windUpDuration + DT);
        assertEquals("damage from the left side", 1, PLAYER.get(playerEntity).health);
    }

    @Test
    public void noDamageWhenMeleeMisses() {
        // Enemy at x=0, player at x=100 — within aggro range (72) but
        // melee hitbox (24 units in front) won't reach the player's worldBounds.
        Entity playerEntity = player(100f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        engine.update(attack.windUpDuration + DT);

        assertFalse("attack should be finished", attack.isAttacking);
        assertEquals("no damage on miss", 3, playerComp.health);
    }

    // --- helpers ---

    /** Returns the CollisionComponent for the given entity. */
    private CollisionComponent eCollision(Entity entity) {
        return com.axehigh.platformer.ecs.components.Mappers.COLLISION.get(entity);
    }
}
