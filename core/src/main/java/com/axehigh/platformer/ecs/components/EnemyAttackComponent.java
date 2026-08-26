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

    /** Horizontal reach of the melee hitbox (in front of the enemy, before unitScale). */
    public float meleeRange = 24f;

    /** Damage dealt to the player per attack (default 1, same as contact). */
    public float attackDamage = 1f;
}
