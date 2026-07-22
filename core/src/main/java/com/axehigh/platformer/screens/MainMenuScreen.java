package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.ui.SkinFactory;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Entry screen: lets the player start a new game, continue (disabled), pick a level, or open preferences. */
public class MainMenuScreen implements Screen {
    private final Game game;
    private Skin skin;
    private Stage stage;

    public MainMenuScreen(Game game) {
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

        Label title = new Label("Axe High", skin);
        table.add(title).padBottom(20f).row();

        TextButton newGameButton = new TextButton("New Game", skin);
        newGameButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                SaveData freshSaveData = new SaveData();
                freshSaveData.levelPath = LevelCatalog.levels().first().tmxPath;
                freshSaveData.health = 3;
                freshSaveData.maxHealth = 3;
                freshSaveData.coins = 0;
                freshSaveData.items = 0;
                freshSaveData.swordDamage = 5;
                freshSaveData.sharpEdgePurchased = false;
                freshSaveData.daggerBandolierPurchased = false;
                freshSaveData.ironHeartCount = 0;
                freshSaveData.triesRemaining = 3;
                game.setScreen(new GameScreen(game, freshSaveData));
            }
        });
        table.add(newGameButton).width(160f).padBottom(8f).row();

        TextButton continueButton = new TextButton("Continue", skin);
        if (SaveManager.hasSave()) {
            continueButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    game.setScreen(new GameScreen(game, SaveManager.load()));
                }
            });
        } else {
            continueButton.setTouchable(Touchable.disabled);
            continueButton.setColor(Color.GRAY);
        }
        table.add(continueButton).width(160f).padBottom(8f).row();

        TextButton selectLevelButton = new TextButton("Select Level", skin);
        selectLevelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new LevelSelectScreen(game));
            }
        });
        table.add(selectLevelButton).width(160f).padBottom(8f).row();

        TextButton preferencesButton = new TextButton("Preferences", skin);
        preferencesButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new PreferencesScreen(game));
            }
        });
        table.add(preferencesButton).width(160f).row();

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
