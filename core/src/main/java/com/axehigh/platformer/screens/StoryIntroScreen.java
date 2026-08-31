package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.text.StoryText;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Full-screen story intro displayed before the first level of a new game.
 * Shows the prologue narrative over a backdrop, then starts the game on confirm.
 */
public class StoryIntroScreen extends MenuScreen {

    private final Texture introBackgroundTexture;

    public StoryIntroScreen(Game game) {
        super(game);
        this.introBackgroundTexture = new Texture(Gdx.files.internal("splash/intro-screen.jpeg"));
    }

    @Override
    public void show() {
        super.show();
        // Override background with the intro backdrop
        stage.getActors().first().remove();
        Image bg = new Image(introBackgroundTexture);
        bg.setScaling(Scaling.fill);
        bg.setTouchable(Touchable.disabled);
        stage.getRoot().addActorAt(0, bg);

        menuEffects.applyKenBurns(stage, bg);

        AudioManager.get().playMenuMusic();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(50f);
        stage.addActor(root);

        // Title
        Label titleLabel = createMenuTitle(StoryText.PROLOGUE_TITLE);
        titleLabel.setAlignment(Align.center);
        root.add(titleLabel).padBottom(20f).row();

        // Body text
        Label bodyLabel = new Label(StoryText.INTRO_BODY, skin);
        bodyLabel.setFontScale(FontScale * 1.1f);
        bodyLabel.setColor(Color.LIGHT_GRAY);
        bodyLabel.setAlignment(Align.center);
        bodyLabel.setWrap(true);
        root.add(bodyLabel).width(900f).padBottom(40f).row();

        // Enter button
        TextButton enterButton = createMenuButton(StoryText.ENTER_BUTTON, () -> newGame());
        root.add(enterButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);
    }

    private void newGame() {
        SaveManager.clear();
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
        introBackgroundTexture.dispose();
    }
}
