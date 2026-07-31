package com.axehigh.platformer.common;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public class ScreenUtils {

    public Texture getFadeTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.BLACK);
        pixmap.fill();

        final Texture fadeTexture = new Texture(pixmap);
        pixmap.dispose();
        return fadeTexture;
    }
}
