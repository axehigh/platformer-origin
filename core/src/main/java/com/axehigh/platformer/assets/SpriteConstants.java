package com.axehigh.platformer.assets;

/**
 * Adjustments to Sprites
 */
public class SpriteConstants {

    //Player Collision Box
    public static float PlayerScale = .5f; // Knight2.png
    public static float PlayerCollisionWidth = 30f;
    public static float PlayerCollisionHeight = 60f;
    public static float PlayerOffsetRight = -16f;
    public static float PlayerOffsetLeft = -16f;
    public static float PlayerOffsetY = -12f;

    //Enemy Collision Box (base units, scaled by ENEMY_SCALE)

    public static float EnemyWalkerScale = 0.3f;
    public static float EnemyWalkerCollisionWidth = 40f;
    public static float EnemyWalkerCollisionHeight = 50f;
    public static float EnemyWalkerOffsetY = 3f;

    public static float EnemyFlyerScale = 0.3f;
    public static float EnemyFlyerCollisionWidth = 40f;
    public static float EnemyFlyerCollisionHeight = 40f;
    public static float EnemyFlyerOffsetY = 0f;

    public static float EnemyShooterScale = 0.3f;
    public static float EnemyShooterCollisionWidth = 50f;
    public static float EnemyShooterCollisionHeight = 50f;
    public static float EnemyShooterOffsetY = 4f;

    public static float EnemyKnightScale = 0.3f;
    public static float EnemyKnightCollisionWidth = 60f;
    public static float EnemyKnightCollisionHeight = 90f;
    public static float EnemyKnightOffsetY = -10f;
}
