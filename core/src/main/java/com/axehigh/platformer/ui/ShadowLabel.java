package com.axehigh.platformer.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

/**
 * A {@link Label} rendered with a soft black drop shadow behind the glyphs, so HUD counters stay
 * legible over bright background tiles. The shadow is a second cache draw offset slightly down and
 * right, tinted black — no separate font or markup needed.
 */
public class ShadowLabel extends Label {
    private static final float SHADOW_OFFSET_X = 2f;
    private static final float SHADOW_OFFSET_Y = -2f;

    private final Color tempColor = new Color();
    private final Color shadowTint = new Color(0f, 0f, 0f, 0.9f);

    public ShadowLabel(CharSequence text, LabelStyle style) {
        super(text, style);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();

        Color color = tempColor.set(getColor());
        color.a *= parentAlpha;
        LabelStyle style = getStyle();
        if (style.fontColor != null) {
            color.mul(style.fontColor);
        }

        BitmapFontCache cache = getBitmapFontCache();
        cache.tint(shadowTint);
        cache.setPosition(getX() + SHADOW_OFFSET_X, getY() + SHADOW_OFFSET_Y);
        cache.draw(batch);

        cache.tint(color);
        cache.setPosition(getX(), getY());
        cache.draw(batch);
    }
}
