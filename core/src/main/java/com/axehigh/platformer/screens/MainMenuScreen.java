package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.FeatureFlags;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Scaling;

import static com.axehigh.platformer.GameConstants.UI_PADDING;

/**
 * Main menu over a full-bleed {@code startup-menu.jpg} backdrop (uniform cover scaling, edges
 * cropped). The title stays screen-centered near the top; Preferences is pinned top-right; New
 * Game and Continue stack centered at the bottom; Select Level (gated behind
 * {@link FeatureFlags#isSelectLevelEnabled()}) sits in the bottom-right corner.
 */
public class MainMenuScreen extends MenuScreen {

    private final Texture backgroundTexture;

    public MainMenuScreen(Game game) {
        super(game);
        backgroundTexture = new Texture(Gdx.files.internal("splash/startup-menu.jpg"));
    }

    @Override
    public void show() {
        super.show();
        AudioManager.get().playMenuMusic();

        addBackground();

        Table content = new Table();
        content.setFillParent(true);
        stage.addActor(content);

        Table titleTable = new Table();
        titleTable.setFillParent(true);
        titleTable.top();
        titleTable.add(createMenuTitle("Origin")).padTop(3 * UI_PADDING).row();
        stage.addActor(titleTable);

        TextButton preferencesButton = createMenuButton("Preferences", () -> changeScreen(new PreferencesScreen(game)));
        Table cornerTopRight = new Table();
        cornerTopRight.setFillParent(true);
        cornerTopRight.top().right();
        cornerTopRight.add(preferencesButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT)
            .pad(UI_PADDING).row();
        stage.addActor(cornerTopRight);

        TextButton selectLevelButton = createMenuButton("Select Level", () -> changeScreen(new LevelSelectScreen(game)));
        if (FeatureFlags.isSelectLevelEnabled()) {
            Table cornerBottomRight = new Table();
            cornerBottomRight.setFillParent(true);
            cornerBottomRight.bottom().right();
            cornerBottomRight.add(selectLevelButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT)
                .pad(UI_PADDING).row();
            stage.addActor(cornerBottomRight);
        }

        Table bottomCenter = new Table();
        bottomCenter.setFillParent(true);
        bottomCenter.bottom();

        TextButton newGameButton = createMenuButton("New Game", this::newGame);
        bottomCenter.add(newGameButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT)
            .padBottom(2 * UI_PADDING).row();

        TextButton continueButton = createMenuButton("Continue", () -> changeScreen(new GameScreen(game, SaveManager.load())));
        if (!SaveManager.hasSave()) {
            continueButton.setTouchable(Touchable.disabled);
            continueButton.setColor(Color.GRAY);
        }
        bottomCenter.add(continueButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT)
            .padBottom(2 * UI_PADDING).row();

        stage.addActor(bottomCenter);
    }

    /** Adds the full-screen cover-scaled backdrop as the bottom-most actor. */
    private void addBackground() {
        Image background = new Image(backgroundTexture);
        background.setScaling(Scaling.fill);
        background.setFillParent(true);
        background.setTouchable(Touchable.disabled);
        stage.addActor(background);
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

    @Override
    public void dispose() {
        super.dispose();
        backgroundTexture.dispose();
    }
}
