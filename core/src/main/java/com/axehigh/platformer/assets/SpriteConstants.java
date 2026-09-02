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

    //Acid atlas region prefixes (gfx/origin-game.atlas)
    /** Dispatcher tube animation — 64px sprites, scaled to fill a full 128px tile. */
    public static final String ACID_TUBE_REGION = "acid_tube";
    public static final float AcidTubeScale = 0.25f;
    /** Static falling drop sprite — 32px, scaled to fill a full 128px tile. */
    public static final String ACID_DROP_REGION = "acid_drop";
    /** Render scale factor (times {@code unitScale}) for the acid drop, matching the tube's. */
    public static final float AcidDropScale = .25f;
    public static float AcidDropCollisionWidth = 4f;
    public static float AcidDropCollisionHeight = 6f;
    /** Pool/splash animation on landing — 128px sprites. */
    public static final String ACID_POOL_REGION = "acid_blob";
    /** Seconds a landed acid pool lingers before disappearing. */
    public static final float ACID_POOL_LIFETIME = 1.5f;

    public static float FlameTrapScale = 0.15f;
    public static float FlameTrapCollisionWidth = 24f;
    public static float FlameTrapCollisionHeight = 48f;
}
