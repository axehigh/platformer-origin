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

    /**
     * Horizontal reach (world units, 1 unit == 1 pixel, before {@code unitScale}) beyond the
     * collision box's leading edge, indexed by {@code ATTACKING} animation frame. {@code 0} means
     * the frame cannot hit. Tuned against the sword's position in the hero {@code attack} sprite.
     *
     * <p>Per-sprite convention: each sprite that can attack defines its own {@code *_ATTACK_REACH}
     * table here (mirroring the {@code Player*}/{@code Enemy*} collision-box grouping below), so a
     * future sprite with a different attack animation gets its own reach without touching
     * {@code MeleeAttackSystem}, which reads the table matching the attacking sprite.</p>
     */
    public static final float[] PLAYER_ATTACK_REACH = {0f, 12f, 16f, 24f, 0f};
    public static final float PLAYER_MAX_ATTACK_REACH = max(PLAYER_ATTACK_REACH);

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

    private static float max(float[] values) {
        float max = 0f;
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }
}
