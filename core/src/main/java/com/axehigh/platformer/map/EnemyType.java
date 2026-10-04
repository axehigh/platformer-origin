package com.axehigh.platformer.map;

import com.axehigh.platformer.assets.SpriteConstants;

import static com.axehigh.platformer.GameConstants.EnemyBaseHealth;
import static com.axehigh.platformer.assets.SpriteConstants.*;

/**
 * The enemy archetypes placeable via the {@code enemyType} Tiled custom property. Each constant
 * owns its sprite prefix, walk-clip region name, collision geometry, and scale (all sourced from
 * {@link SpriteConstants}); adding a stats/sprite-only variant means adding a constant here.
 */
public enum EnemyType {
    WALKER(EnemyWalkerSprite, EnemyBaseHealth * 2, "walk", EnemyWalkerScale, EnemyWalkerCollisionWidth, EnemyWalkerCollisionHeight, EnemyWalkerOffsetY),
    FLYER(EnemyFlyerSprite, EnemyBaseHealth, "flight", EnemyFlyerScale, EnemyFlyerCollisionWidth, EnemyFlyerCollisionHeight, EnemyFlyerOffsetY),
    SHOOTER(EnemyShooterSprite, EnemyBaseHealth * 2, "walk", EnemyShooterScale, EnemyShooterCollisionWidth, EnemyShooterCollisionHeight, EnemyShooterOffsetY),
    GOBLIN_BRUTE(EnemyGoblinBruteSprite, EnemyBaseHealth * 3, "walk", EnemyGoblinBruteScale, EnemyGoblinBruteCollisionWidth, EnemyGoblinBruteCollisionHeight, EnemyGoblinBruteOffsetY),
    SKELETON(EnemySkeletonSprite, EnemyBaseHealth * 4, "walk", EnemySkeletonScale, EnemySkeletonCollisionWidth, EnemySkeletonCollisionHeight, EnemySkeletonOffsetY),
    GHOST(EnemyGhostSprite, EnemyBaseHealth * 2, "walk", EnemyGhostScale, EnemyGhostCollisionWidth, EnemyGhostCollisionHeight, EnemyGhostOffsetY);

    final String atlasPrefix;
    final String walkRegionName;
    final float collisionWidth;
    final float collisionHeight;
    final float collisionOffsetY;
    final float scale;
    final float maxHealth;

    EnemyType(String atlasPrefix, float maxHealth, String walkRegionName,
              float scale, float collisionWidth, float collisionHeight, float collisionOffsetY) {
        this.atlasPrefix = atlasPrefix;
        this.walkRegionName = walkRegionName;
        this.collisionWidth = collisionWidth;
        this.collisionHeight = collisionHeight;
        this.collisionOffsetY = collisionOffsetY;
        this.scale = scale;
        this.maxHealth = maxHealth;
    }

    /**
     * Resolves the raw {@code enemyType} Tiled property value; unknown values fall back to WALKER.
     */
    public static EnemyType fromTiledValue(String value) {
        if (value == null) {
            return WALKER;
        }
        switch (value) {
            case "flyer":
                return FLYER;
            case "shooter":
                return SHOOTER;
            case "goblin_brute":
                return GOBLIN_BRUTE;
            case "skeleton":
                return SKELETON;
            case "ghost":
                return GHOST;
            default:
                return WALKER;
        }
    }
}
