package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.HitFlashComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.GameConstants.HIT_FLASH_DURATION;
import static com.axehigh.platformer.ecs.components.Mappers.HIT_FLASH;
import static org.junit.Assert.*;

/**
 * Headless tests verifying that {@code EnemyDamageResolver.applyHit} starts the
 * {@code HitFlashComponent} on the enemy entity, gated by
 * {@code FeatureFlags.isSlashArcEnabled()}.
 */
public class EnemyDamageResolverHitFlashTest extends SystemTestBase {

    private Entity enemyEntity;
    private EnemyComponent enemy;
    private MovementComponent movement;

    @Before
    public void setUp() {
        FeatureFlags.setSlashArcEnabled(true);
        enemy = new EnemyComponent();
        movement = movement();
        enemyEntity = entity(enemy, movement, new HitFlashComponent());
    }

    @After
    public void tearDown() {
        FeatureFlags.setSlashArcEnabled(true);
    }

    private boolean applyHit(float damage, int direction, boolean isFlying) {
        return EnemyDamageResolver.applyHit(enemyEntity, enemy, movement, damage, direction, isFlying, 1f, null);
    }

    @Test
    public void survivingHit_startsFlash() {
        applyHit(5f, 1, false);

        assertTrue("flash must be active after a surviving hit", HIT_FLASH.get(enemyEntity).isActive());
        assertEquals(HIT_FLASH_DURATION, HIT_FLASH.get(enemyEntity).flashTimer, EPSILON);
    }

    @Test
    public void fatalHit_stillStartsFlash() {
        boolean died = applyHit(10f, 1, false);

        assertTrue(died);
        assertTrue(enemy.isDead);
        assertTrue("flash must start even on a lethal hit", HIT_FLASH.get(enemyEntity).isActive());
        assertEquals(HIT_FLASH_DURATION, HIT_FLASH.get(enemyEntity).flashTimer, EPSILON);
    }

    @Test
    public void slashArcDisabled_flashNotStarted() {
        FeatureFlags.setSlashArcEnabled(false);

        applyHit(5f, 1, false);

        assertFalse("flash must NOT start when the gate is off", HIT_FLASH.get(enemyEntity).isActive());
        assertEquals(0f, HIT_FLASH.get(enemyEntity).flashTimer, EPSILON);
        // The hit still applies (health reduced, knockback applied)
        assertEquals(5f, enemy.health, EPSILON);
        assertTrue(enemy.hitStun.isActive());
    }

    @Test
    public void noHitFlashComponent_doesNotCrash() {
        Entity bareEnemy = entity(new EnemyComponent(), movement());

        // Should not throw — HIT_FLASH.get returns null, resolver null-guards it
        boolean died = EnemyDamageResolver.applyHit(bareEnemy, new EnemyComponent(), movement(), 5f, 1, false, 1f, null);
        assertFalse(died);
    }

    @Test
    public void stunnedEnemy_noHitMeansNoFlash() {
        enemy.hitStun.start(0.3f);

        boolean applied = applyHit(5f, 1, false);

        assertFalse(applied);
        assertFalse("no hit = no flash start", HIT_FLASH.get(enemyEntity).isActive());
    }
}
