package com.axehigh.platformer.util;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.badlogic.ashley.core.Entity;

import static com.axehigh.platformer.ecs.components.Mappers.BUFF;

/** Shared potion-effect application, used by the keyboard quick-drink and the inventory bar. */
public final class PotionEffects {
    private PotionEffects() {
    }

    /**
     * Applies the effect of a drunk potion: Healing restores hearts immediately (capped at
     * {@code maxHealth}); the buff potions start their timed buff on {@link BuffComponent} (ticked
     * and applied by {@code BuffSystem}). A player entity without a {@code BuffComponent} simply
     * can't benefit from the buff potions.
     */
    public static void apply(Entity playerEntity, PlayerComponent player, PotionType type) {
        if (type == PotionType.HEALING) {
            player.health = Math.min(player.maxHealth, player.health + GameConstants.HEALING_POTION_HEAL);
            return;
        }
        BuffComponent buff = BUFF.get(playerEntity);
        if (buff == null) {
            return;
        }
        switch (type) {
            case STRENGTH:
                buff.startStrength();
                break;
            case SPEED:
                buff.startSpeed();
                break;
            case INVULNERABILITY:
                buff.startInvulnerability();
                break;
            default:
                break;
        }
    }
}
