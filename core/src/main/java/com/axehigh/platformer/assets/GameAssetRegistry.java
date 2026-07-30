package com.axehigh.platformer.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public final class GameAssetRegistry {

    private GameAssetRegistry() {
    }

    public static void loadAssets(AssetManager assetManager) {
        assetManager.load("gfx/hero/knight2.atlas", TextureAtlas.class);
        assetManager.load("gfx/origin-game.atlas", TextureAtlas.class);
        assetManager.load("ui/uiskin.atlas", TextureAtlas.class);
        //TODO This will be outdated after the atlas'es have been loaded.
        assetManager.load("gfx/player.png", Texture.class);
        assetManager.load("gfx/coin.png", Texture.class);
        assetManager.load("gfx/chest.png", Texture.class);
        assetManager.load("gfx/torch.png", Texture.class);
        assetManager.load("gfx/exit_gate.png", Texture.class);
        assetManager.load("gfx/heart.png", Texture.class);
        assetManager.load("gfx/bullet.png", Texture.class);
        assetManager.load("gfx/dagger.png", Texture.class);
        assetManager.load("gfx/chest_open.png", Texture.class);
    }
}
