package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/**
 * Timed consumable buffs on the player (from Strength / Speed / Invulnerability potions). Each buff
 * is a {@link Timer} that {@code BuffSystem} ticks; {@code start...()} methods refresh the full
 * duration on reuse (no stacking). The speed buff also scales {@code MovementComponent.maxSpeedX}
 * (applied/reverted by {@code BuffSystem}); the strength and invulnerability buffs are read directly
 * by {@code MeleeAttackSystem} / {@code PlayerDamageResolver}.
 */
public class BuffComponent implements Component {
    /** Strength buff: adds {@link GameConstants#STRENGTH_DAMAGE_BONUS} melee damage while active. */
    public final Timer strength = new Timer();
    /** Speed buff: multiplies horizontal move speed while active. */
    public final Timer speed = new Timer();
    /** Invulnerability buff: the player takes no damage while active. */
    public final Timer invulnerability = new Timer();

    /** True while the speed multiplier is applied to {@code MovementComponent.maxSpeedX}. */
    public boolean speedApplied = false;
    /** The pre-buff horizontal max speed, captured when the speed buff starts. */
    public float speedBaseMaxSpeedX = GameConstants.MaxSpeedX;

    public boolean isStrengthActive() {
        return strength.isActive();
    }

    public boolean isSpeedActive() {
        return speed.isActive();
    }

    public boolean isInvulnerabilityActive() {
        return invulnerability.isActive();
    }

    /** Refreshes the strength buff to its full duration. */
    public void startStrength() {
        strength.start(GameConstants.STRENGTH_BUFF_DURATION);
    }

    /** Refreshes the speed buff to its full duration. */
    public void startSpeed() {
        speed.start(GameConstants.SPEED_BUFF_DURATION);
    }

    /** Refreshes the invulnerability buff to its full duration. */
    public void startInvulnerability() {
        invulnerability.start(GameConstants.INVULNERABILITY_DURATION);
    }
}
