package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.common.BaseScreen;
import com.axehigh.platformer.ui.MenuEffects;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Scaling;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Base class for the game's menu screens. Shares the visual chrome used on every menu: a
 * full-bleed backdrop with ambient effects (Ken Burns zoom, drifting embers). The styled title
 * label and buttons are shared across all implementations.
 */
public abstract class MenuScreen extends BaseScreen {

    private static final int EMBER_COUNT = 28;

    protected static final float MENU_BUTTON_WIDTH = 330f;
    protected static final float MENU_BUTTON_HEIGHT = 90f;
    protected static final float MENU_TITLE_SCALE = 1.4f;

    private final Texture backgroundTexture;
    protected final MenuEffects menuEffects = new MenuEffects();
    private Image backgroundImage;

    public MenuScreen(Game game) {
        super(game);
        backgroundTexture = new Texture(Gdx.files.internal("splash/startup-menu.jpg"));
    }

    @Override
    public void show() {
        super.show();
        addBackground();
        menuEffects.applyKenBurns(stage, backgroundImage);
        menuEffects.addEmbers(stage, EMBER_COUNT);
    }

    private void addBackground() {
        backgroundImage = new Image(backgroundTexture);
        backgroundImage.setScaling(Scaling.fill);
        backgroundImage.setTouchable(Touchable.disabled);
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

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        menuEffects.resize(stage.getWidth(), stage.getHeight());
    }

    @Override
    public void dispose() {
        super.dispose();
        backgroundTexture.dispose();
        menuEffects.dispose();
    }
}
