package com.axehigh.platformer.ui;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * Sizes Scene2D {@link Dialog}s to the shared "table" window panel so the panel background is
 * always scaled uniformly (never squished), regardless of how much content the dialog holds.
 */
public final class DialogPanelFitter {
    private static final float PANEL_SCALE = 0.7f;
    private static final float FIT_MARGIN = 40f;

    private DialogPanelFitter() {
    }

    /**
     * Fixed-size fit: panel at {@code PANEL_SCALE} of its native size, centered on the stage.
     * Use for dialogs whose content is known to fit (e.g. game over).
     */
    public static void sizeToPanel(Skin skin, Stage stage, Dialog dialog) {
        TextureRegionDrawable panel = panelDrawable(skin);
        float width = panel.getRegion().getRegionWidth() * PANEL_SCALE;
        float height = panel.getRegion().getRegionHeight() * PANEL_SCALE;
        dialog.setSize(width, height);
        center(stage, dialog, width, height);
    }

    /**
     * Content-driven fit: grows the panel uniformly until it covers the dialog's preferred size
     * plus a margin, clamped to 95% of the stage so it never overflows the screen.
     */
    public static void fitToPanel(Skin skin, Stage stage, Dialog dialog) {
        TextureRegionDrawable panel = panelDrawable(skin);
        float panelW = panel.getRegion().getRegionWidth();
        float panelH = panel.getRegion().getRegionHeight();
        float scale = Math.max(dialog.getPrefWidth() / panelW, (dialog.getPrefHeight() + FIT_MARGIN) / panelH);
        float maxScale = Math.min((stage.getWidth() * 0.95f) / panelW, (stage.getHeight() * 0.95f) / panelH);
        scale = Math.min(scale, maxScale);
        float width = panelW * scale;
        float height = panelH * scale;
        dialog.setSize(width, height);
        center(stage, dialog, width, height);
    }

    private static TextureRegionDrawable panelDrawable(Skin skin) {
        return (TextureRegionDrawable) skin.getDrawable("table");
    }

    private static void center(Stage stage, Dialog dialog, float width, float height) {
        dialog.setPosition(Math.round((stage.getWidth() - width) / 2f), Math.round((stage.getHeight() - height) / 2f));
    }
}
