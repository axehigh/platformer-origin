package com.axehigh.platformer.util;

import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Headless tests for the shared {@link PotionEffects} potion-effect application. */
public class PotionEffectsTest {

    private Entity playerEntity;
    private PlayerComponent player;
    private BuffComponent buff;

    @Before
    public void setUp() {
        playerEntity = new Entity();
        player = new PlayerComponent();
        buff = new BuffComponent();
        playerEntity.add(player);
        playerEntity.add(buff);
    }

    @Test
    public void healingRestoresHealthCappedAtMaxHealth() {
        player.health = player.maxHealth - 1;

        PotionEffects.apply(playerEntity, player, PotionType.HEALING);

        assertEquals(player.maxHealth, player.health);
    }

    @Test
    public void healingAtFullHealthDoesNotOverflow() {
        player.health = player.maxHealth;

        PotionEffects.apply(playerEntity, player, PotionType.HEALING);

        assertEquals(player.maxHealth, player.health);
        assertFalse(buff.isStrengthActive());
    }

    @Test
    public void strengthStartsStrengthBuff() {
        PotionEffects.apply(playerEntity, player, PotionType.STRENGTH);

        assertTrue(buff.isStrengthActive());
        assertFalse(buff.isSpeedActive());
        assertFalse(buff.isInvulnerabilityActive());
    }

    @Test
    public void speedStartsSpeedBuff() {
        PotionEffects.apply(playerEntity, player, PotionType.SPEED);

        assertTrue(buff.isSpeedActive());
    }

    @Test
    public void invulnerabilityStartsInvulnerabilityBuff() {
        PotionEffects.apply(playerEntity, player, PotionType.INVULNERABILITY);

        assertTrue(buff.isInvulnerabilityActive());
    }

    @Test
    public void buffPotionOnEntityWithoutBuffComponentIsNoOp() {
        Entity barePlayer = new Entity();
        barePlayer.add(new PlayerComponent());

        PotionEffects.apply(barePlayer, player, PotionType.STRENGTH);

        assertFalse(buff.isStrengthActive());
    }
}
