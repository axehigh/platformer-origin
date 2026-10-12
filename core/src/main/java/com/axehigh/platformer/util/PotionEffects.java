package com.axehigh.platformer.util;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;

import static com.axehigh.platformer.ecs.components.Mappers.BUFF;

/** Shared potion-effect application, used by the keyboard quick-drink and the inventory bar. */
public final class PotionEffects {

    /** Callback interface for potion events — allows GameScreen to spawn floating messages. */
    public interface PotionListener {
        void onPotionApplied(Entity playerEntity, PotionType type);
    }

    private static PotionListener potionListener;

    private PotionEffects() {
    }

    public static void setPotionListener(PotionListener listener) {
        potionListener = listener;
    }

    /**
     * Returns whether the potion can be applied. If potion stacking is disallowed
     * ({@link FeatureFlags#isPotionStackingEnabled()} is false), drinking a non-healing potion
     * is disallowed if any other buff is currently active on the player.
     */
    public static boolean canApply(Entity playerEntity, PotionType type) {
        if (type == PotionType.HEALING) {
            return true;
        }
        if (!FeatureFlags.isPotionStackingEnabled()) {
            BuffComponent buff = BUFF.get(playerEntity);
            if (buff != null) {
                if (buff.isStrengthActive() || buff.isSpeedActive() || buff.isInvulnerabilityActive()
                        || buff.isInvisibilityActive() || buff.isJumpActive() || buff.isFireBreathActive()
                        || buff.isSpiderActive()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Applies the effect of a drunk potion: Healing restores hearts immediately (capped at
     * {@code maxHealth}); the buff potions start their timed buff on {@link BuffComponent} (ticked
     * and applied by {@code BuffSystem}). A player entity without a {@code BuffComponent} simply
     * can't benefit from the buff potions.
     */
    public static void apply(Entity playerEntity, PlayerComponent player, PotionType type) {
        if (Gdx.app != null) {
            Gdx.app.log("PotionEffects", "apply " + type + ", listener=" + (potionListener != null));
        }
        if (!canApply(playerEntity, type)) {
            return;
        }
        if (type == PotionType.HEALING) {
            player.health = Math.min(player.maxHealth, player.health + GameConstants.HEALING_POTION_HEAL);
            if (potionListener != null) {
                potionListener.onPotionApplied(playerEntity, type);
            }
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
            case INVISIBILITY:
                buff.startInvisibility();
                break;
            case JUMP:
                buff.startJump();
                break;
            case FIRE_BREATH:
                buff.startFireBreath();
                break;
            case SPIDER:
                buff.startSpider();
                break;
            default:
                break;
        }
        if (potionListener != null) {
            potionListener.onPotionApplied(playerEntity, type);
        }
    }
}
