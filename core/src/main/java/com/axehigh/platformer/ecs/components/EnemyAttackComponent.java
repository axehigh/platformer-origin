package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/**
 * Layered on top of {@code EnemyComponent} to give an enemy an active attack
 * (melee bite/swipe). Resolved by {@code EnemyAttackSystem}.
 */
public class EnemyAttackComponent implements Component {

    public enum AttackType {
        MELEE
    }

    /** Which attack this enemy performs. */
    public AttackType attackType = AttackType.MELEE;

    /** Cooldown between attacks, in seconds. */
    public float attackInterval = 2.0f;

    /** Ticks down between attacks; system restarts it after each attack lands. */
    public final Timer attackCooldown = new Timer();

    /** Duration of the wind-up phase before the hit registers. */
    public float windUpDuration = 0.4f;

    /** Ticks down during the wind-up; when done, the hit lands. */
    public final Timer windUp = new Timer();

    /** True while the enemy is in its attack animation (wind-up + strike). */
    public boolean isAttacking = false;

    /** Duration of the post-strike "wind down": after the strike lands (hit or whiff) the enemy
     * stands still, facing-locked, for this long before resuming its patrol in the same
     * direction — so a swing never lurches into a turn-around right after it completes. */
    public float recoveryDuration = 0.5f;

    /** Ticks down after the strike resolves; while active, {@code EnemySystem} holds the enemy
     * stationary (facing locked) and {@code EnemyAttackSystem} refuses a new attack trigger. */
    public final Timer recovery = new Timer();

    /** Horizontal reach in front of the enemy (before unitScale) at which it commits a strike. */
    public float attackRange = 24f;

    /** Total height (before unitScale) of the omni-directional detection box the enemy uses to
     *  spot the player and start chasing: 1.25 tiles (1.25 × 16px tile = 20u), centered on the
     *  enemy, so only same-floor standoffs are detected — platforms above/below don't aggro. */
    public float detectionHeight = 20f;

    /** Duration of the active "blade out" window after the wind-up, during which the strike
     *  hitbox (enemy collision width × height, in front of the enemy) can deal damage. */
    public float strikeWindow = 0.2f;

    /** Ticks down during the strike window; while active the strike is live. */
    public final Timer strike = new Timer();

    /** Damage dealt to the player per attack (default 1, same as contact). */
    public float attackDamage = 1f;
}
