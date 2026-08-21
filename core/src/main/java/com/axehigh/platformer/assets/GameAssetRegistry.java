package com.axehigh.platformer.assets;

import com.axehigh.platformer.particles.GlobalParticles;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public final class GameAssetRegistry {

    public static final String HERO_ASSET = "gfx/hero/knight2.atlas";
    public static final String ORIGIN_GAME_GFX = "gfx/origin-game.atlas";
    public static final String ORIGIN_UI_JSON = "ui/uiskin.json";
    public static final String ORIGIN_UI_GFX = "ui/uiskin.atlas";
    public static final String BACKGROUND_FAR = "maps/gfx/background/Background_01.png";
    public static final String BACKGROUND_NEAR = "maps/gfx/background/Background_02.png";

    private GameAssetRegistry() {
    }

    public static void loadAssets(AssetManager assetManager) {
        loadGFX(assetManager);
        loadParticles(assetManager);
    }

    private static void loadGFX(AssetManager assetManager) {
        assetManager.load(HERO_ASSET, TextureAtlas.class);
        assetManager.load(ORIGIN_GAME_GFX, TextureAtlas.class);
        assetManager.load(ORIGIN_UI_GFX, TextureAtlas.class);

        assetManager.load("gfx/old/coin.png", Texture.class);
        assetManager.load("gfx/old/torch.png", Texture.class);
        assetManager.load("gfx/old/heart.png", Texture.class);
        assetManager.load("gfx/old/bullet.png", Texture.class);
        assetManager.load("gfx/old/dagger.png", Texture.class);
        assetManager.load("gfx/acid_drop.png", Texture.class);
        assetManager.load("gfx/lava_drop.png", Texture.class);
        assetManager.load(BACKGROUND_FAR, Texture.class);
        assetManager.load(BACKGROUND_NEAR, Texture.class);
    }

    public static void loadParticles(AssetManager assetManager) {
        assetManager.load(GlobalParticles.EXPLOSION, ParticleEffect.class);
        assetManager.load(GlobalParticles.GHOST, ParticleEffect.class);
        assetManager.load(GlobalParticles.SMOKE, ParticleEffect.class);
        assetManager.load(GlobalParticles.SPARKS, ParticleEffect.class);
    }
}
