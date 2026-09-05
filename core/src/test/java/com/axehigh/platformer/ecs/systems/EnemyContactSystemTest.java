package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;

/**
 * Headless unit tests for {@code EnemyContactSystem}: overlap-based contact damage routed through
 * {@code PlayerDamageResolver} — the one-second hit-invulnerability grace period (no repeated
 * damage/knockback while it's active, damage resumes once it expires), the short hit-stun window
 * that outlives only part of the invulnerability, the knockback push away from the enemy, and
 * that dead enemies deal no contact damage.
 */
public class EnemyContactSystemTest extends SystemTestBase {

    private Engine engine;
    private EnemyContactSystem system;

    @Before
    public void setUp() {
        system = new EnemyContactSystem();
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
        Entity entity = entity(transform, collision, new EnemyComponent());
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void contactDamagesOnceThenGraceProtects() {
        Entity player = player(0f, 130f);
        enemy(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(0.1f);
        assertEquals(2, playerComponent.health);
        assertTrue(playerComponent.hitInvulnerability.isActive());

        engine.update(0.1f);
        assertEquals(2, playerComponent.health);
    }

    @Test
    public void contactDamagesAgainAfterGraceExpires() {
        Entity player = player(0f, 130f);
        enemy(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(0.1f);
        engine.update(0.5f);
        assertEquals(2, playerComponent.health);

        engine.update(1.6f);
        assertEquals(1, playerComponent.health);
        assertTrue(playerComponent.hitInvulnerability.isActive());
    }

    @Test
    public void deadEnemyCausesNoContactDamage() {
        Entity player = player(0f, 130f);
        Entity enemy = enemy(0f, 130f);
        ENEMY.get(enemy).isDead = true;
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(0.1f);

        assertEquals(3, playerComponent.health);
    }

    @Test
    public void hitKnocksPlayerAwayFromEnemy() {
        Entity player = player(10f, 130f);
        enemy(0f, 130f);
        MovementComponent movement = MOVEMENT.get(player);

        engine.update(DT);

        assertEquals(60f, movement.velocity.x, EPSILON);
    }

    @Test
    public void hitKnocksPlayerLeftWhenEnemyIsToTheRight() {
        Entity player = player(-10f, 130f);
        enemy(0f, 130f);
        MovementComponent movement = MOVEMENT.get(player);

        engine.update(DT);

        assertEquals(-60f, movement.velocity.x, EPSILON);
    }

    @Test
    public void hitStartsHurtWindowAlongsideInvulnerability() {
        Entity player = player(0f, 130f);
        enemy(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);

        assertTrue(playerComponent.hurtTimer.isActive());
        assertTrue(playerComponent.hitInvulnerability.isActive());
    }

    @Test
    public void hurtWindowIsShorterThanInvulnerability() {
        Entity player = player(0f, 130f);
        enemy(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);
        engine.update(0.4f);

        assertFalse(playerComponent.hurtTimer.isActive());
        assertTrue(playerComponent.hitInvulnerability.isActive());
    }

    @Test
    public void secondHitDuringGraceDoesNotReapplyKnockback() {
        Entity player = player(10f, 130f);
        enemy(0f, 130f);
        MovementComponent movement = MOVEMENT.get(player);

        engine.update(DT);
        assertEquals(60f, movement.velocity.x, EPSILON);

        movement.velocity.x = 0f;
        engine.update(DT);

        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void staggeredEnemyBodyCausesNoContactDamage() {
        Entity player = player(0f, 130f);
        Entity enemy = enemy(0f, 130f);
        ENEMY.get(enemy).hitStun.start(0.3f);
        PlayerComponent playerComponent = PLAYER.get(player);
        MovementComponent movement = MOVEMENT.get(player);

        engine.update(DT);

        assertEquals(3, playerComponent.health);
        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void postHitIdleEnemyBodyEjectsPlayerWithoutDamage() {
        Entity player = player(0f, 130f);
        Entity enemy = enemy(0f, 130f);
        ENEMY.get(enemy).postHitIdle.start(0.5f);
        PlayerComponent playerComponent = PLAYER.get(player);
        MovementComponent movement = MOVEMENT.get(player);

        engine.update(DT);

        assertEquals(3, playerComponent.health);
        // Player/enemy centers coincide (0 >= 0 crosses the >= tie-break), so knockbackDirection = +1:
        // the recovering body ejects the player rightward at EJECT_SPEED_X, like a gentle wall.
        assertEquals(110f, movement.velocity.x, EPSILON);
    }

    @Test
    public void hitStunEnemyBodyIsFreePassThrough() {
        Entity player = player(0f, 130f);
        Entity enemy = enemy(0f, 130f);
        ENEMY.get(enemy).hitStun.start(0.3f);
        PlayerComponent playerComponent = PLAYER.get(player);
        MovementComponent movement = MOVEMENT.get(player);
        movement.velocity.x = 50f;

        engine.update(DT);

        assertEquals(3, playerComponent.health);
        // Pure pass-through: neither contact knockback (60) nor the post-hit-idle eject (110) applies.
        assertEquals(50f, movement.velocity.x, EPSILON);
    }

    @Test
    public void attackingPlayerIsImmuneToHealthyEnemyBody() {
        Entity player = player(0f, 130f);
        enemy(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);
        playerComponent.meleeAttack.start(0.2f);

        engine.update(DT);

        assertEquals(3, playerComponent.health);
        assertEquals(0f, MOVEMENT.get(player).velocity.x, EPSILON);
    }

    @Test
    public void recoveredEnemyBodyDamagesPlayerAgain() {
        Entity player = player(0f, 130f);
        Entity enemy = enemy(0f, 130f);
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        enemyComponent.hitStun.start(0.3f);
        enemyComponent.hitStun.update(1f);
        enemyComponent.postHitIdle.start(0.5f);
        enemyComponent.postHitIdle.update(1f);
        assertFalse(enemyComponent.hitStun.isActive());
        assertFalse(enemyComponent.postHitIdle.isActive());
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);

        assertEquals(2, playerComponent.health);
        assertEquals(60f, MOVEMENT.get(player).velocity.x, EPSILON);
        assertTrue(playerComponent.hurtTimer.isActive());
        assertTrue(playerComponent.hitInvulnerability.isActive());
    }
}
