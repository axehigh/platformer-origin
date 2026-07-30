package com.axehigh.platformer.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public final class GameAssetRegistry {

    public static final String HERO_ASSET = "gfx/hero/knight2.atlas";
    public static final String ORIGIN_GAME_GFX = "gfx/origin-game.atlas";
    public static final String ORIGIN_UI_JSON = "ui/uiskin.json";
    public static final String ORIGIN_UI_GFX = "ui/uiskin.atlas";

    private GameAssetRegistry() {
    }

    public static void loadAssets(AssetManager assetManager) {
        assetManager.load(HERO_ASSET, TextureAtlas.class);
        assetManager.load(ORIGIN_GAME_GFX, TextureAtlas.class);
        assetManager.load(ORIGIN_UI_GFX, TextureAtlas.class);

        //TODO This will be outdated after the atlas'es have been loaded.
        assetManager.load("gfx/old/player.png", Texture.class);
        assetManager.load("gfx/old/coin.png", Texture.class);
        assetManager.load("gfx/old/chest.png", Texture.class);
        assetManager.load("gfx/old/torch.png", Texture.class);
        assetManager.load("gfx/old/exit_gate.png", Texture.class);
        assetManager.load("gfx/old/heart.png", Texture.class);
        assetManager.load("gfx/old/bullet.png", Texture.class);
        assetManager.load("gfx/old/dagger.png", Texture.class);
        assetManager.load("gfx/old/chest_open.png", Texture.class);
    }
}
