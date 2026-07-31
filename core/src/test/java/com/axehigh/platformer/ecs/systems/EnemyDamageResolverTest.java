package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for the shared {@code EnemyDamageResolver} used by both {@code MeleeAttackSystem}
 * and {@code CollisionSystem}: damage subtraction, hit-stun immunity, lethal hits, knockback
 * direction, the flying vertical-hop opt-out, and the animation-driven stun duration.
 */
public class EnemyDamageResolverTest extends SystemTestBase {

    private Entity enemyEntity;
    private EnemyComponent enemy;
    private MovementComponent movement;

    @Before
    public void setUp() {
        enemy = new EnemyComponent();
        movement = movement();
        enemyEntity = entity(enemy, movement);
    }

    private boolean applyHit(float damage, int direction, boolean isFlying) {
        return EnemyDamageResolver.applyHit(enemyEntity, enemy, movement, damage, direction, isFlying, 1f);
    }

    @Test
    public void appliesDamageKnockbackAndStun() {
        boolean died = applyHit(5f, 1, false);

        assertFalse(died);
        assertEquals(5f, enemy.health, EPSILON);
        assertEquals(90f, movement.velocity.x, EPSILON);
        assertEquals(140f, movement.velocity.y, EPSILON);
        assertTrue(enemy.hitStun.isActive());
        assertFalse(enemy.isDead);
    }

    @Test
    public void knockbackFlipsWithDirection() {
        applyHit(5f, -1, false);

        assertEquals(-90f, movement.velocity.x, EPSILON);
    }

    @Test
    public void stunnedEnemyIgnoresFurtherHits() {
        enemy.hitStun.start(0.3f);

        boolean died = applyHit(5f, 1, false);

        assertFalse(died);
        assertEquals(10f, enemy.health, EPSILON);
        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void lethalHitKillsAndStartsDeathTimer() {
        boolean died = applyHit(10f, 1, false);

        assertTrue(died);
        assertTrue(enemy.isDead);
        assertEquals(0f, movement.velocity.x, EPSILON);
        assertEquals(0f, movement.velocity.y, EPSILON);
        assertTrue(enemy.deathTimer.isActive());
    }

    @Test
    public void deadEnemyIgnoresFurtherHits() {
        applyHit(10f, 1, false);

        boolean died = applyHit(5f, 1, false);

        assertFalse(died);
        assertEquals(0f, enemy.health, EPSILON);
    }

    @Test
    public void flyingEnemySkipsVerticalKnockback() {
        applyHit(5f, 1, true);

        assertEquals(90f, movement.velocity.x, EPSILON);
        assertEquals(0f, movement.velocity.y, EPSILON);
    }

    @Test
    public void hurtAnimationExtendsStunDuration() {
        AnimationComponent animation = new AnimationComponent();
        animation.animations.put(AnimationComponent.State.HURT,
            new Animation<TextureRegion>(0.5f, new TextureRegion()));
        enemyEntity.add(animation);

        applyHit(5f, 1, false);

        assertEquals(0.5f, enemy.hitStun.getRemaining(), EPSILON);
    }
}
