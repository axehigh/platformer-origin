package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ui.SkinFactory;
import com.axehigh.platformer.util.GamePreferences;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Settings screen: music/SFX volume sliders and a debug-mode checkbox, bound to {@link GamePreferences}. */
public class PreferencesScreen implements Screen {
    private final Game game;
    private final GamePreferences preferences = new GamePreferences();
    private Skin skin;
    private Stage stage;

    public PreferencesScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        skin = SkinFactory.createBasicSkin();
        Viewport viewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT);
        stage = new Stage(viewport);

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        Label title = new Label("Preferences", skin);
        table.add(title).colspan(2).padBottom(20f).row();

        Label musicLabel = new Label("Music Volume", skin);
        Slider musicSlider = new Slider(0f, 100f, 1f, false, skin);
        musicSlider.setValue(preferences.getMusicVolume());
        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                preferences.setMusicVolume(musicSlider.getValue());
            }
        });
        table.add(musicLabel).padRight(12f);
        table.add(musicSlider).width(160f).padBottom(8f).row();

        Label sfxLabel = new Label("SFX Volume", skin);
        Slider sfxSlider = new Slider(0f, 100f, 1f, false, skin);
        sfxSlider.setValue(preferences.getSfxVolume());
        sfxSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                preferences.setSfxVolume(sfxSlider.getValue());
            }
        });
        table.add(sfxLabel).padRight(12f);
        table.add(sfxSlider).width(160f).padBottom(8f).row();

        CheckBox debugCheckBox = new CheckBox(" Debug Mode", skin);
        debugCheckBox.setChecked(preferences.isDebugMode());
        debugCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                preferences.setDebugMode(debugCheckBox.isChecked());
            }
        });
        table.add(debugCheckBox).colspan(2).padBottom(20f).row();

        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new MainMenuScreen(game));
            }
        });
        table.add(backButton).colspan(2).width(160f).row();

        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}
