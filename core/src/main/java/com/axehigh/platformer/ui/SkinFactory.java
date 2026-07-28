package com.axehigh.platformer.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public final class SkinFactory {

    private static Skin uiSkin;

    private static Skin createSkin() {
        return new Skin(Gdx.files.internal("ui/uiskin.json"));
    }

    public static Skin getSkin() {
        if (uiSkin == null) {
            uiSkin = createSkin();
        }
        return uiSkin;
    }
}

