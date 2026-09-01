package com.axehigh.platformer.assets;

/**
 * Adjustments to Sprites
 */
public class SpriteConstants {

    //Player Collision Box
    public static float PlayerScale = .3f; // Knight2.png
    public static float PlayerCollisionWidth = 30f;
    public static float PlayerCollisionHeight = 40f;
    public static float PlayerOffsetRight = -16f;
    public static float PlayerOffsetLeft = -16f;
    public static float PlayerOffsetY = -24f;

    //Chest atlas regions (gfx/origin-game.atlas): closed sprite on spawn, swapped to the
    //matching open sprite when a chest is melee-struck (see MeleeAttackSystem).
    public static final String CHEST_CLOSED_REGION = "Chest_01_Locked";
    public static final String CHEST_OPEN_REGION = "Chest_01_Unlocked";
    public static final String CHEST_CLOSED_REGION_ELITE = "Chest_02_Locked";
    public static final String CHEST_OPEN_REGION_ELITE = "Chest_02_Unlocked";

    //Enemy Collision Box (base units, scaled by ENEMY_SCALE)

    public static String EnemyWalkerSprite = "goblin";
    public static float EnemyWalkerScale = 0.3f;
    public static float EnemyWalkerCollisionWidth = 80f * EnemyWalkerScale;
    public static float EnemyWalkerCollisionHeight = 140f * EnemyWalkerScale;
    public static float EnemyWalkerOffsetY = 0f;

    public static String EnemyFlyerSprite = "mosquito";
    public static float EnemyFlyerScale = 0.20f;
    public static float EnemyFlyerCollisionWidth = 130f * EnemyFlyerScale;
    public static float EnemyFlyerCollisionHeight = 130f * EnemyFlyerScale;
    public static float EnemyFlyerOffsetY = 0f;

    public static String EnemyShooterSprite = "spider";
    public static float EnemyShooterScale = 0.2f;
    public static float EnemyShooterCollisionWidth = 166f * EnemyShooterScale;
    public static float EnemyShooterCollisionHeight = 166f * EnemyShooterScale;
    public static float EnemyShooterOffsetY = 4f;

    public static String EnemyKnightSprite = "goblin";
    public static float EnemyKnightScale = 0.40f;
    public static float EnemyKnightCollisionWidth = 80f * EnemyKnightScale;
    public static float EnemyKnightCollisionHeight = 140 * EnemyKnightScale;
    public static float EnemyKnightOffsetY = 10f * EnemyKnightScale;

    //Trap scales and collision boxes
    public static float AcidDropScale = 0.15f;
    public static float AcidDropCollisionWidth = 8f;
    public static float AcidDropCollisionHeight = 12f;

    public static float FlameTrapScale = 0.15f;
    public static float FlameTrapCollisionWidth = 24f;
    public static float FlameTrapCollisionHeight = 48f;
}
