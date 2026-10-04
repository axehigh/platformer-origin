package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.util.FeatureFlags;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Headless tests for the potion-count bookkeeping on {@link PlayerComponent}. */
public class PlayerComponentTest {

    private com.axehigh.platformer.ecs.components.PlayerComponent player;

    @Before
    public void setUp() {
        player = new com.axehigh.platformer.ecs.components.PlayerComponent();
    }

    @After
    public void tearDown() {
        FeatureFlags.setGodModeEnabled(false);
    }

    @Test
    public void consumePotionDecrementsCount() {
        player.setPotionCount(com.axehigh.platformer.ecs.components.PotionType.HEALING, 3);
        player.health = player.maxHealth - 1;

        assertTrue(player.consumePotion(com.axehigh.platformer.ecs.components.PotionType.HEALING));

        assertEquals(2, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.HEALING));
    }

    @Test
    public void consumePotionAtZeroDoesNotConsume() {
        assertFalse(player.consumePotion(com.axehigh.platformer.ecs.components.PotionType.SPEED));

        assertEquals(0, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.SPEED));
    }

    @Test
    public void consumeSelectedPotionTargetsSelectedType() {
        player.setPotionCount(com.axehigh.platformer.ecs.components.PotionType.STRENGTH, 2);
        player.selectedPotion = com.axehigh.platformer.ecs.components.PotionType.STRENGTH;

        assertTrue(player.consumeSelectedPotion());

        assertEquals(1, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.STRENGTH));
        assertEquals(0, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.HEALING));
    }

    @Test
    public void setPotionCountClampsToCapAndFloor() {
        player.setPotionCount(com.axehigh.platformer.ecs.components.PotionType.INVULNERABILITY, GameConstants.POTION_CAP + 10);

        assertEquals(GameConstants.POTION_CAP, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.INVULNERABILITY));

        player.setPotionCount(com.axehigh.platformer.ecs.components.PotionType.INVULNERABILITY, -5);

        assertEquals(0, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.INVULNERABILITY));
    }

    @Test
    public void countPotionWhenGodModeEnabledReturnsAtLeastOne() {
        FeatureFlags.setGodModeEnabled(true);
        assertEquals(0, player.countPotionRaw(com.axehigh.platformer.ecs.components.PotionType.SPEED));
        assertEquals(1, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.SPEED));

        player.setPotionCount(com.axehigh.platformer.ecs.components.PotionType.SPEED, 3);
        assertEquals(3, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.SPEED));
    }

    @Test
    public void consumePotionWhenGodModeEnabledDoesNotConsume() {
        FeatureFlags.setGodModeEnabled(true);
        player.setPotionCount(com.axehigh.platformer.ecs.components.PotionType.HEALING, 0);
        player.health = player.maxHealth - 1;

        assertTrue(player.consumePotion(com.axehigh.platformer.ecs.components.PotionType.HEALING));
        assertEquals(1, player.countPotion(com.axehigh.platformer.ecs.components.PotionType.HEALING));
    }
}
