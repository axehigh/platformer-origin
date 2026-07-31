package com.axehigh.platformer.screens;

import com.axehigh.platformer.common.BaseScreen;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import static com.axehigh.platformer.GameConstants.FontScale;

public class MainMenuScreen extends BaseScreen {

    public MainMenuScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        Label title = new Label("Origin", skin);
        table.add(title).padBottom(8f).row();

        TextButton newGameButton = new TextButton("New Game", skin);
        newGameButton.getLabel().setFontScale(FontScale);
        newGameButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                newGame();
            }
        });
        table.add(newGameButton).padBottom(4f).row();

        TextButton continueButton = new TextButton("Continue", skin);
        continueButton.getLabel().setFontScale(FontScale);
        if (SaveManager.hasSave()) {
            continueButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    changeScreen(new GameScreen(game, SaveManager.load()));
                }
            });
        } else {
            continueButton.setTouchable(Touchable.disabled);
            continueButton.setColor(Color.GRAY);
        }
        table.add(continueButton).padBottom(4f).row();

        TextButton selectLevelButton = new TextButton("Select Level", skin);
        selectLevelButton.getLabel().setFontScale(FontScale);
        selectLevelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                changeScreen(new LevelSelectScreen(game));
            }
        });
        table.add(selectLevelButton).padBottom(4f).row();

        TextButton preferencesButton = new TextButton("Preferences", skin);
        preferencesButton.getLabel().setFontScale(FontScale);
        preferencesButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                changeScreen(new PreferencesScreen(game));
            }
        });
        table.add(preferencesButton).row();
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
        changeScreen(new GameScreen(game, freshSaveData));
    }

}

