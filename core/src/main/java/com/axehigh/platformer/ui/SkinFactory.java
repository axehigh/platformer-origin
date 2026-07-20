package com.axehigh.platformer.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

/** Builds a minimal, programmer-art Skin (font + flat-color button drawables) shared by both UI stages. */
public final class SkinFactory {

    private SkinFactory() {
    }

    public static Skin createBasicSkin() {
        Skin skin = new Skin();

        BitmapFont font = new BitmapFont();
        skin.add("default-font", font, BitmapFont.class);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));
        pixmap.dispose();

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.25f, 0.75f));
        buttonStyle.down = skin.newDrawable("white", new Color(0.55f, 0.55f, 0.6f, 0.9f));
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        skin.add("default", buttonStyle);

        return skin;
    }
}
