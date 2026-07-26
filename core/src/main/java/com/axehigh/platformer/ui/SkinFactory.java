package com.axehigh.platformer.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;

/**
 * Builds a minimal, programmer-art Skin (font + flat-color button drawables) shared by both UI stages.
 */
public final class SkinFactory {

    private SkinFactory() {
    }

    public static Skin createSkin(com.badlogic.gdx.graphics.g2d.TextureAtlas atlas) {
        Skin skin = new Skin(atlas);

        BitmapFont font = new BitmapFont();
        skin.add("default-font", font, BitmapFont.class);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = skin.getDrawable("button");
        buttonStyle.down = skin.getDrawable("button");
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        skin.add("default", buttonStyle);

        // Add other styles using atlas drawables if possible

        return skin;
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

        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOff = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.25f, 0.75f));
        checkBoxStyle.checkboxOn = skin.newDrawable("white", new Color(0.55f, 0.55f, 0.6f, 0.9f));
        checkBoxStyle.font = font;
        checkBoxStyle.fontColor = Color.WHITE;
        skin.add("default", checkBoxStyle);

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.25f, 0.75f));
        sliderStyle.background.setMinHeight(6f);
        sliderStyle.knob = skin.newDrawable("white", new Color(0.9f, 0.9f, 0.9f, 1f));
        sliderStyle.knob.setMinWidth(12f);
        sliderStyle.knob.setMinHeight(12f);
        skin.add("default-horizontal", sliderStyle);

        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.background = skin.newDrawable("white", new Color(0.15f, 0.15f, 0.2f, 0.95f));
        windowStyle.titleFont = font;
        windowStyle.titleFontColor = Color.WHITE;
        skin.add("default", windowStyle);

        return skin;
    }
}

