package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/** Flag component storing player-specific data. */
public class PlayerComponent implements Component {
    public int health = 3;
    public int maxHealth = 3;
    public int coins = 0;
    public int items = 0;
    public int maxItems = 30;
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

    /** One-shot: true only during the frame the interact key/touch button was pressed. */
    public boolean interactPressed = false;
    /** True while the player is inside any exit gate's proximity sensor; drives the interact UI prompt. */
    public boolean nearExit = false;
}
