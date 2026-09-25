package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Headless tests for {@code PlayerDamageResolver}'s placeholder hurt/hazard SFX wiring: the
 * damage choke points play {@code SfxSystem.playPlayerHurt()} (knockback hits) or
 * {@code playPlayerHazard()} (hazard hits) through the {@code PlayerDamageResolver.setSfxSystem}
 * seam, once per hit, and never while the hit-invulnerability grace period is active.
 */
public class PlayerDamageResolverTest extends SystemTestBase {

    private Entity playerEntity;
    private PlayerComponent player;
    private MovementComponent movement;
    private com.axehigh.platformer.ecs.systems.SfxSystem sfxSystem;

    @Before
    public void setUp() {
        sfxSystem = mock(com.axehigh.platformer.ecs.systems.SfxSystem.class);
        com.axehigh.platformer.ecs.systems.PlayerDamageResolver.setSfxSystem(sfxSystem);
        player = new PlayerComponent();
        movement = movement();
        playerEntity = entity(player, movement);
    }

    @After
    public void tearDown() {
        com.axehigh.platformer.ecs.systems.PlayerDamageResolver.setSfxSystem(null);
    }

    @Test
    public void applyHit_healthyAndPastInvulnerability_decrementsHealthAndPlaysHurtSfxOnce() {
        int before = player.health;
        assertFalse("precondition: grace window must be done", player.hitInvulnerability.isActive());

        boolean applied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertTrue(applied);
        assertEquals(before - 1, player.health);
        verify(sfxSystem).playPlayerHurt();
        assertTrue("grace window starts on the hit", player.hitInvulnerability.isActive());
    }

    @Test
    public void applyHit_duringInvulnerability_noHealthChangeAndNeverPlaysHurtSfx() {
        player.hitInvulnerability.start(2f);
        int before = player.health;

        boolean applied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);

        assertFalse("grace window must block the hit", applied);
        assertEquals(before, player.health);
        verify(sfxSystem, never()).playPlayerHurt();
    }

    @Test
    public void applyHitWithoutKnockback_playsHazardSfxOnceAndDecrementsHealth() {
        int before = player.health;

        boolean applied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);

        assertTrue(applied);
        assertEquals(before - 1, player.health);
        verify(sfxSystem).playPlayerHazard();
    }

    @Test
    public void applyHitWithoutKnockback_duringInvulnerability_neverPlaysHazardSfx() {
        player.hitInvulnerability.start(2f);
        int before = player.health;

        boolean applied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);

        assertFalse(applied);
        assertEquals(before, player.health);
        verify(sfxSystem, never()).playPlayerHazard();
    }

    @Test
    public void seamUnset_nullSfxSystem_noCrashAndHitStillApplies() {
        com.axehigh.platformer.ecs.systems.PlayerDamageResolver.setSfxSystem(null);
        int before = player.health;

        boolean hurtApplied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);
        // The first hit starts the 2s grace window; clear it so the second hit can land.
        player.hitInvulnerability.reset();
        boolean hazardApplied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);

        assertTrue("hit still applies without the SFX seam", hurtApplied);
        assertTrue("hazard hit still applies without the SFX seam", hazardApplied);
        assertEquals(before - 2, player.health);
    }

    @Test
    public void applyHit_whenGodModeEnabled_blocksDamageAndSfx() {
        FeatureFlags.setGodModeEnabled(true);
        try {
            int before = player.health;
            boolean applied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHit(playerEntity, player, movement, 1, 1f);
            assertFalse(applied);
            assertEquals(before, player.health);
            verify(sfxSystem, never()).playPlayerHurt();
        } finally {
            FeatureFlags.setGodModeEnabled(false);
        }
    }

    @Test
    public void applyHitWithoutKnockback_whenGodModeEnabled_blocksDamageAndSfx() {
        FeatureFlags.setGodModeEnabled(true);
        try {
            int before = player.health;
            boolean applied = com.axehigh.platformer.ecs.systems.PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);
            assertFalse(applied);
            assertEquals(before, player.health);
            verify(sfxSystem, never()).playPlayerHazard();
        } finally {
            FeatureFlags.setGodModeEnabled(false);
        }
    }
}
