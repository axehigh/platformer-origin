package com.axehigh.platformer.map;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;

/**
 * Shared state and atlas helpers for the entity factories: the {@link AssetManager}, the game
 * atlas, and the current map {@code unitScale} (mutated once per level swap via
 * {@link #setUnitScale(float)} — every factory holds this same instance, so one write updates all).
 */
final class FactoryContext {
    /** Z-layer for decorations, pickups, enemies, traps. */
    static final float DECOR_Z = 5f;
    /** Moving platforms draw above decorations but below the player. */
    static final float PLATFORM_Z = 6f;
    static final float PLAYER_Z = 10f;

    final AssetManager assetManager;
    final TextureAtlas originAtlas;
    float unitScale = 1f;

    FactoryContext(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.originAtlas = assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class);
    }

    void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    Texture getTexture(String path) {
        Texture texture = assetManager.get(path, Texture.class);
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return texture;
    }

    /**
     * Builds an animation from the {@code regionName} prefix in the game atlas, tolerating the
     * atlas's naming variants ({@code name1..nameN}, {@code name_1..name_N}), with layered
     * fallbacks so a missing clip degrades to a single frame instead of crashing.
     */
    Animation<TextureRegion> buildAnimation(float frameDuration, String regionName, Animation.PlayMode playMode) {
        Array<AtlasRegion> regions = new Array<>();
        Array<AtlasRegion> found = originAtlas.findRegions(regionName);
        if (found.size > 0) {
            regions.addAll(found);
        } else {
            // Fallback for numbered naming: name1, name2, ... or name_1, name_2, ...
            for (int i = 0; i <= 10; i++) {
                AtlasRegion region = originAtlas.findRegion(regionName + i);
                if (region != null) regions.add(region);
                region = originAtlas.findRegion(regionName + "_" + i);
                if (region != null) regions.add(region);
            }
        }

        if (regions.size == 0) {
            // Emergency fallback: just find the first region that starts with the prefix
            for (AtlasRegion region : originAtlas.getRegions()) {
                if (region.name.startsWith(regionName)) {
                    regions.add(region);
                    break;
                }
            }
        }

        // If STILL empty, use a global fallback to prevent division-by-zero crash
        if (regions.size == 0) {
            AtlasRegion fallback = originAtlas.findRegion("goblin_idle1");
            if (fallback != null) {
                regions.add(fallback);
            }
        }

        return new Animation<>(frameDuration, regions, playMode);
    }
}
