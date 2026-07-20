package com.axehigh.platformer.ecs.components;

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
    public float shootCooldownTimer = 0f;
}
