package com.axehigh.platformer.ui;

import com.axehigh.platformer.assets.GameAssetRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_UI_JSON;

public final class SkinFactory {

    private static Skin uiSkin;

    private static Skin createSkin() {
        return new Skin(Gdx.files.internal(ORIGIN_UI_JSON));
    }

    public static Skin getSkin() {
        if (uiSkin == null) {
            uiSkin = createSkin();
        }
        return uiSkin;
    }
}

