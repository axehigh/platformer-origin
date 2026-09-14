package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.HitFlashComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.GameConstants.HIT_FLASH_DURATION;
import static com.axehigh.platformer.ecs.components.Mappers.HIT_FLASH;
import static org.junit.Assert.*;

/**
 * Headless tests verifying that {@code PlayerDamageResolver.applyHit} and
 * {@code applyHitWithoutKnockback} start the {@code HitFlashComponent} on the player entity,
 * gated by {@code FeatureFlags.isSlashArcEnabled()}, and that the invulnerability guard blocks
 * the hit (and thus the flash).
 */
public class PlayerDamageResolverHitFlashTest extends SystemTestBase {

    private Entity playerEntity;
    private PlayerComponent player;
    private MovementComponent movement;

    @Before
    public void setUp() {
        FeatureFlags.setSlashArcEnabled(true);
        player = new PlayerComponent();
        movement = movement();
        playerEntity = entity(player, movement, new HitFlashComponent());
    }

    @After
    public void tearDown() {
        FeatureFlags.setSlashArcEnabled(true);
    }

    @Test
    public void applyHit_startsFlash() {
        boolean applied = PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertTrue(applied);
        assertTrue("flash must be active after applyHit", HIT_FLASH.get(playerEntity).isActive());
        assertEquals(HIT_FLASH_DURATION, HIT_FLASH.get(playerEntity).flashTimer, EPSILON);
    }

    @Test
    public void applyHitWithoutKnockback_startsFlash() {
        boolean applied = PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);

        assertTrue(applied);
        assertTrue("flash must be active after applyHitWithoutKnockback", HIT_FLASH.get(playerEntity).isActive());
        assertEquals(HIT_FLASH_DURATION, HIT_FLASH.get(playerEntity).flashTimer, EPSILON);
    }

    @Test
    public void slashArcDisabled_flashNotStarted() {
        FeatureFlags.setSlashArcEnabled(false);

        boolean applied = PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertTrue("hit still applies", applied);
        assertFalse("flash must NOT start when the gate is off", HIT_FLASH.get(playerEntity).isActive());
        assertEquals(0f, HIT_FLASH.get(playerEntity).flashTimer, EPSILON);
    }

    @Test
    public void slashArcDisabled_noKnockbackFlashNotStarted() {
        FeatureFlags.setSlashArcEnabled(false);

        boolean applied = PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);

        assertTrue("hit still applies", applied);
        assertFalse("flash must NOT start when the gate is off", HIT_FLASH.get(playerEntity).isActive());
    }

    @Test
    public void hitInvulnerabilityActive_blocksHitAndFlash() {
        player.hitInvulnerability.start(2f);

        boolean applied = PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertFalse("invulnerability must block the hit", applied);
        assertFalse("no hit = no flash", HIT_FLASH.get(playerEntity).isActive());
        assertEquals("health unchanged", 3, player.health);
    }

    @Test
    public void buffInvulnerabilityActive_blocksHitAndFlash() {
        BuffComponent buff = new BuffComponent();
        buff.startInvulnerability();
        playerEntity.add(buff);

        boolean applied = PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertFalse("invulnerability buff must block the hit", applied);
        assertFalse("no hit = no flash", HIT_FLASH.get(playerEntity).isActive());
    }

    @Test
    public void deadPlayer_blocksHitAndFlash() {
        player.isDead = true;

        boolean applied = PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertFalse("dead player must not take hits", applied);
        assertFalse("no hit = no flash", HIT_FLASH.get(playerEntity).isActive());
    }

    @Test
    public void noHitFlashComponent_doesNotCrash() {
        Entity barePlayer = entity(new PlayerComponent(), movement());

        // HIT_FLASH.get returns null, resolver null-guards it — must not throw
        boolean applied = PlayerDamageResolver.applyHit(barePlayer, new PlayerComponent(), movement(), 1, 1f);
        // The bare PlayerComponent has default health=3, so the hit applies
        assertTrue(applied);
    }

    @Test
    public void applyHit_reducesHealthByOne() {
        int before = player.health;

        PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertEquals("each hit reduces health by 1", before - 1, player.health);
    }

    @Test
    public void applyHit_clampsHealthAtZero() {
        player.health = 1;

        PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertEquals("health clamped at 0, never negative", 0, player.health);
    }
}
