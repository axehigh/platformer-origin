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
    public static final float[] PLAYER_ATTACK_REACH = {
        0f * PlayerScale,
        24f * PlayerScale,
        32f * PlayerScale,
        44f * PlayerScale,
        0f * PlayerScale
    };
    public static final float PLAYER_MAX_ATTACK_REACH = maxOf(PLAYER_ATTACK_REACH);

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
    public static float EnemyFlyerScale = 0.3f;
    public static float EnemyFlyerCollisionWidth = 40f;
    public static float EnemyFlyerCollisionHeight = 40f;
    public static float EnemyFlyerOffsetY = 0f;

    public static String EnemyShooterSprite = "spider";
    public static float EnemyShooterScale = 0.3f;
    public static float EnemyShooterCollisionWidth = 50f;
    public static float EnemyShooterCollisionHeight = 50f;
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
    ;

    private static float maxOf(float[] values) {
        float max = 0f;
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }
}
