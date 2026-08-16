package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.GameConstants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Headless tests for the potion-count bookkeeping on {@link PlayerComponent}. */
public class PlayerComponentTest {

    private PlayerComponent player;

    @Before
    public void setUp() {
        player = new PlayerComponent();
    }

    @Test
    public void consumePotionDecrementsCount() {
        player.setPotionCount(PotionType.HEALING, 3);

        assertTrue(player.consumePotion(PotionType.HEALING));

        assertEquals(2, player.countPotion(PotionType.HEALING));
    }

    @Test
    public void consumePotionAtZeroDoesNotConsume() {
        assertFalse(player.consumePotion(PotionType.SPEED));

        assertEquals(0, player.countPotion(PotionType.SPEED));
    }

    @Test
    public void consumeSelectedPotionTargetsSelectedType() {
        player.setPotionCount(PotionType.STRENGTH, 2);
        player.selectedPotion = PotionType.STRENGTH;

        assertTrue(player.consumeSelectedPotion());

        assertEquals(1, player.countPotion(PotionType.STRENGTH));
        assertEquals(0, player.countPotion(PotionType.HEALING));
    }

    @Test
    public void setPotionCountClampsToCapAndFloor() {
        player.setPotionCount(PotionType.INVULNERABILITY, GameConstants.POTION_CAP + 10);

        assertEquals(GameConstants.POTION_CAP, player.countPotion(PotionType.INVULNERABILITY));

        player.setPotionCount(PotionType.INVULNERABILITY, -5);

        assertEquals(0, player.countPotion(PotionType.INVULNERABILITY));
    }
}
