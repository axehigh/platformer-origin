package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.common.BaseScreen;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Base class for the game's menu screens. Shares the visual chrome used on every menu: a uniformly
 * scaled {@code table} panel background, a styled title label, and click-sound-wrapped buttons.
 */
public abstract class MenuScreen extends BaseScreen {

    protected static final float MENU_PANEL_WIDTH = 1517f;
    protected static final float MENU_PANEL_HEIGHT = 1040f;
    protected static final float MENU_BUTTON_WIDTH = 430f;
    protected static final float MENU_BUTTON_HEIGHT = 90f;
    protected static final float MENU_TITLE_SCALE = 1.4f;

    public MenuScreen(Game game) {
        super(game);
    }

    /** Draws the {@code table} panel, scaled uniformly and centered, behind the screen's content. */
    protected Image addMenuPanel() {
        Image panel = new Image(skin.getDrawable("table"));
        panel.setScaling(Scaling.fit);
        panel.setSize(MENU_PANEL_WIDTH, MENU_PANEL_HEIGHT);
        panel.setPosition((stage.getWidth() - MENU_PANEL_WIDTH) / 2f, (stage.getHeight() - MENU_PANEL_HEIGHT) / 2f);
        stage.addActor(panel);
        return panel;
    }

    /** Creates a title label styled for a menu screen. The caller adds it to its table layout. */
    protected Label createMenuTitle(String text) {
        Label title = new Label(text, skin);
        title.setFontScale(FontScale * MENU_TITLE_SCALE);
        return title;
    }

    /**
     * Creates a menu {@link TextButton} that plays the click sound before running {@code onClick}.
     * The caller adds it to its table layout, typically with uniform {@code MENU_BUTTON_*} sizing.
     */
    protected TextButton createMenuButton(String text, final Runnable onClick) {
        TextButton button = new TextButton(text, skin);
        button.getLabel().setFontScale(FontScale);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                onClick.run();
            }
        });
        return button;
    }
}
