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
import com.badlogic.gdx.scenes.scene2d.Actor;
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

import static com.axehigh.platformer.GameConstants.FontScale;

public class MainMenuScreen implements Screen {
    private final Game game;
    private Skin skin;
    private Stage stage;

    public MainMenuScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        skin = SkinFactory.getSkin();
        Viewport viewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT);
        stage = new Stage(viewport);

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        Label title = new Label("Origin", skin);
        title.setFontScale(FontScale);
        table.add(title).padBottom(8f).row();

        TextButton newGameButton = new TextButton("New Game", skin);
        newGameButton.getLabel().setFontScale(FontScale);
        newGameButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                newGame();
            }
        });
        table.add(newGameButton).width(100f).padBottom(4f).row();

        TextButton continueButton = new TextButton("Continue", skin);
        continueButton.getLabel().setFontScale(FontScale);
        if (SaveManager.hasSave()) {
            continueButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    game.setScreen(new GameScreen(game, SaveManager.load()));
                }
            });
        } else {
            continueButton.setTouchable(Touchable.disabled);
            continueButton.setColor(Color.GRAY);
        }
        table.add(continueButton).width(100f).padBottom(4f).row();

        TextButton selectLevelButton = new TextButton("Select Level", skin);
        selectLevelButton.getLabel().setFontScale(FontScale);
        selectLevelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new LevelSelectScreen(game));
            }
        });
        table.add(selectLevelButton).width(100f).padBottom(4f).row();

        TextButton preferencesButton = new TextButton("Preferences", skin);
        preferencesButton.getLabel().setFontScale(FontScale);
        preferencesButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new PreferencesScreen(game));
            }
        });
        table.add(preferencesButton).width(100f).row();

        Gdx.input.setInputProcessor(stage);
    }

    private void newGame() {
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

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
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
