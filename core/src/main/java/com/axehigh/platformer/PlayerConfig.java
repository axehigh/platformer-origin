package com.axehigh.platformer;

import static com.axehigh.platformer.assets.SpriteConstants.PlayerScale;

/**
 * Central tuning knobs for the player's combat abilities — the melee strike (swing timing,
 * hitbox shape, per-frame reach) and the ranged dagger shot (cooldown, bullet speed/damage/
 * lifetime, bullet hitbox geometry) — plus the player's starting combat stats (health, ammo,
 * sword damage, starting bullets). Owned jointly by {@code PlayerInputSystem} (input gating +
 * bullet spawning), {@code MeleeAttackSystem} (strike hitbox/reach), {@code PlayerFactory}
 * (starting ammo), {@code PlayerComponent} (health/ammo/sword defaults), and the HUD.
 *
 * <p>Tuning note: the actual swing length comes from the {@code ATTACKING} animation clip authored
 * in {@code PlayerFactory.attachPlayerAnimations} (0.04s/frame); {@link #MELEE_COOLDOWN} and
 * {@link #MELEE_ATTACK_DURATION} are only fallbacks when no attack animation is registered.</p>
 */
public final class PlayerConfig {

    private PlayerConfig() {
    }

    private static float maxOf(float[] values) {
        float max = 0f;
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    //------ Combat stats ------
    /** Maximum bullets the player can hold at once (ammo pool). */
    public static final int MAX_AMMO = 10;
    /** Base melee/sword damage per hit (raised to 8 by the "Sharp Edge" shop upgrade). */
    public static final int BASE_DAMAGE = 5;

    public static final float BULLET_DAMAGE = BASE_DAMAGE *.5f;
    /** Starting and maximum player health (hearts). */
    public static final int MAX_HEALTH = 3;
    /** Bullets in the inventory when the player spawns. */
    public static final int START_BULLETS = 0;

    //------ Melee strike ------
    /** Minimum delay between two melee swings (seconds). */
    public static final float MELEE_COOLDOWN = 0.12f;
    /** Fallback melee strike window (seconds) when no {@code ATTACKING} animation is registered. */
    public static final float MELEE_ATTACK_DURATION = 0.12f;
    /** Player melee strike hitbox height multiplier (extends above and below). */
    public static final float PLAYER_MELEE_HEIGHT_MULTIPLIER = 1.75f;
    /** Player melee strike hitbox vertical offset factor relative to collision height. */
    public static final float PLAYER_MELEE_Y_OFFSET_FACTOR = 0.25f;

    /**
     * Horizontal reach (world units, 1 unit == 1 pixel, before {@code unitScale}) beyond the
     * collision box's leading edge, indexed by {@code ATTACKING} animation frame. {@code 0} means
     * the frame cannot hit. Tuned against the sword's position in the hero {@code attack} sprite,
     * scaled by the hero sprite scale ({@code SpriteConstants.PlayerScale}).
     *
     * <p>Per-sprite convention: each sprite that can attack defines its own {@code *_ATTACK_REACH}
     * table in {@code SpriteConstants} (mirroring the {@code Player*}/{@code Enemy*} collision-box
     * grouping there), so a future sprite with a different attack animation gets its own reach
     * without touching {@code MeleeAttackSystem}, which reads the table matching the attacking
     * sprite.</p>
     */
    public static final float[] PLAYER_ATTACK_REACH = {
        0f * PlayerScale,
        24f * PlayerScale,
        32f * PlayerScale,
        44f * PlayerScale,
        0f * PlayerScale
    };
    /** Largest reach across all {@code ATTACKING} frames — the fallback when no attack animation
     *  is registered, so a strike still always connects. */
    public static final float PLAYER_MAX_ATTACK_REACH = maxOf(PLAYER_ATTACK_REACH);

    //------ Ranged dagger shot ------
    public static final String PLAYER_BULLET_REGION = "potion_healing";
    public static final float PLAYER_BULLET_SCALE = .2f;
    /** Bullet collision box (base units, scaled by {@link #PLAYER_BULLET_SCALE} in
     *  {@code PlayerInputSystem}). Authored per-sprite like the player/enemy collision boxes: the
     *  hitbox is NOT assumed to equal the full atlas frame (a sprite can have empty margins around
     *  the visible blade), so give it the actual in-sprite footprint plus any offset from the
     *  frame's bottom-left corner. */
    public static final float BULLET_COLLISION_WIDTH = 32f;
    public static final float BULLET_COLLISION_HEIGHT = 32f;
    public static final float BULLET_OFFSET_X = 0f;
    public static final float BULLET_OFFSET_Y = 0f;
    /** Minimum delay between two shots (seconds). */
    public static final float SHOOT_COOLDOWN = 0.35f;
    /** Bullet horizontal speed (world units/second, before {@code unitScale}). */
    public static final float BULLET_SPEED = 220f;
    /** Damage dealt by one bullet hit. */

    /** Bullet lifetime (arbitrary duration units, consumed by {@code BulletComponent}). */
    public static final float BULLET_LIFETIME = 300f;
    /** Bullet stacking layer (z), above the player. */
    public static final float BULLET_Z = 8f;
}
