package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

public class MainMenuScreen extends MenuScreen {

    public MainMenuScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();
        AudioManager.get().playMenuMusic();

        addMenuPanel();

        Table content = new Table();
        content.setFillParent(true);
        stage.addActor(content);

        content.add(createMenuTitle("Origin")).padBottom(12f).row();

        TextButton newGameButton = createMenuButton("New Game", this::newGame);
        content.add(newGameButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padBottom(8f).row();

        TextButton continueButton = createMenuButton("Continue", () -> changeScreen(new GameScreen(game, SaveManager.load())));
        if (!SaveManager.hasSave()) {
            continueButton.setTouchable(Touchable.disabled);
            continueButton.setColor(Color.GRAY);
        }
        content.add(continueButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padBottom(8f).row();

        TextButton selectLevelButton = createMenuButton("Select Level", () -> changeScreen(new LevelSelectScreen(game)));
        content.add(selectLevelButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padBottom(8f).row();

        TextButton preferencesButton = createMenuButton("Preferences", () -> changeScreen(new PreferencesScreen(game)));
        content.add(preferencesButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).row();
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
