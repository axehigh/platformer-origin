package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;


public class PlayerComponent implements Component {
    public int health = GameConstants.MaxHealth;
    public int maxHealth = GameConstants.MaxHealth;
    public int coins = 0;
    public int items = 0;
    public int maxItems = GameConstants.MaxItems;
    /** Current melee/sword damage per hit; base 5, raised to 8 by the "Sharp Edge" shop upgrade. */
    public int swordDamage = GameConstants.SwordDamage;
    /** One-time flag: true once the "Sharp Edge" upgrade (swordDamage -> 8) has been purchased. */
    public boolean sharpEdgePurchased = false;
    /** One-time flag: true once the "Dagger Bandolier" upgrade (maxItems -> 60) has been purchased. */
    public boolean daggerBandolierPurchased = false;
    /** Number of times the repeatable "Iron Heart" upgrade (+1 maxHealth/health) has been purchased. */
    public int ironHeartCount = 0;
    /** -1 for left, 1 for right. */
    public int facingDirection = 1;

    /** Tracks current jumps executed; resets to 0 when grounded. */
    public int jumpCount = 0;
    /** Allows for double jumping. */
    public int maxJumps = 2;
    /** Flag for wall attachment. */
    public boolean isWallClimbing = false;
    /** Prevents bullet spamming. */
    public Timer shootCooldown = new Timer();

    /** Prevents melee-strike spamming. */
    public Timer meleeCooldown = new Timer();
    /** Counts down while the melee strike hitbox is active; active means the strike is in progress. */
    public Timer meleeAttack = new Timer();
    /** Ensures a single swing damages at most one enemy hit. */
    public boolean meleeHasHit = false;
    /** Grace period after being hit by an enemy, during which further enemy contact is ignored. */
    public Timer hitInvulnerability = new Timer();
    /**
     * Short hit-stun window after being hit (at least long enough to play the HURT animation once).
     * While active, the player's movement/jump/melee/shoot input is locked (a knockback pop can play
     * out) and the HURT animation state is shown; the longer {@code hitInvulnerability} window that
     * runs past it is communicated by blinking the sprite instead.
     */
    public Timer hurtTimer = new Timer();
    
    /** Flag to track death status. */
    public boolean isDead = false;

    /** One-shot: true only during the frame the interact key/touch button was pressed. */
    public boolean interactPressed = false;
    /** True while the player is inside any exit gate's proximity sensor; drives the interact UI prompt. */
    public boolean nearExit = false;
}
