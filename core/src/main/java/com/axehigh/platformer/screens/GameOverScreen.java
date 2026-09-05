package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

import static com.axehigh.platformer.GameConstants.*;

/**
 * Full-screen Game Over view displayed when the player dies. Renders over the gameover-screen
 * backdrop with Ken Burns zoom/embers, displays run statistics (coins, items, enemies killed,
 * sword damage, remaining tries), and provides the three-way death economy:
 * Continue (consumes one of the per-world {@code triesRemaining} budget), Retry World (restarts
 * the world's first level with kit intact and the budget restored to 3), or Main Menu.
 */
public class GameOverScreen extends MenuScreen {

    public interface Listener {
        /** Returns the screen to transition to when the player continues (e.g. a fresh {@code GameScreen}). */
        Screen onContinue();
        /** Returns the screen to transition to when the player retries the whole world (tries exhausted). */
        Screen onRetryWorld();
        void onExit();
    }

    private final Listener listener;
    private final Texture gameOverBackgroundTexture;

    public GameOverScreen(Game game, Listener listener) {
        super(game);
        this.listener = listener;
        this.gameOverBackgroundTexture = new Texture(Gdx.files.internal("splash/gameover-screen.jpg"));
    }

    @Override
    public void show() {
        super.show();
        // Override background image with gameover-screen.jpg
        stage.getActors().first().remove(); // remove background image added by super
        Image bg = new Image(gameOverBackgroundTexture);
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
        Label titleLabel = createMenuTitle("GAME OVER");
        titleLabel.setFontScale(TitleFontScale);
        titleLabel.setAlignment(Align.center);
        root.add(titleLabel).padBottom(20f).row();

        Label subtitleLabel = new Label("You have perished in the dungeons.", skin);
        subtitleLabel.setFontScale(BodyFontScale);
        subtitleLabel.setColor(Color.LIGHT_GRAY);
        root.add(subtitleLabel).padBottom(40f).row();

        // Stats Panel
        Table statsTable = new Table(skin);
        statsTable.background(skin.getDrawable("table"));
        statsTable.setColor(1, 1, 1, UI_PANEL_ALPHA); // Add transparency
        statsTable.pad(15f);

        SaveData currentSave = SaveManager.hasSave() ? SaveManager.load() : new SaveData();

        // Tries Remaining at top
        addStatRow(statsTable, "Tries Remaining:", String.valueOf(currentSave.triesRemaining), true);

        // Two-column layout for remaining stats
        statsTable.row();

        // Column 1: Coins & Items
        Table col1 = new Table();
        addStatRow(col1, "Coins Collected:", String.valueOf(currentSave.coins), false);
        addStatRow(col1, "Items Found:", String.valueOf(currentSave.items), false);
        statsTable.add(col1).padRight(40f).top();

        // Column 2: Enemies & Damage
        Table col2 = new Table();
        addStatRow(col2, "Enemies Killed:", String.valueOf(currentSave.enemiesKilled), false);
        addStatRow(col2, "Sword Damage:", String.valueOf(currentSave.swordDamage), false);
        statsTable.add(col2).top();

        root.add(statsTable).width(800f).padBottom(40f).row();

        // Buttons
        Table buttonTable = new Table();

        if (currentSave.triesRemaining > 0) {
            TextButton continueButton = createMenuButton(
                "Continue (" + currentSave.triesRemaining + ")", () -> {
                    currentSave.triesRemaining--;
                    SaveManager.save(currentSave);
                    // Transition through THIS screen so the fade action runs and game.setScreen actually
                    // fires (the GameScreen whose listener we implement is no longer rendering its fade stage).
                    changeScreen(listener.onContinue());
                });
            buttonTable.add(continueButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padRight(20f);
        } else {
            TextButton retryButton = createMenuButton("Retry World", () -> {
                changeScreen(listener.onRetryWorld());
            });
            buttonTable.add(retryButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padRight(20f);
        }

        TextButton exitButton = createMenuButton("Main Menu", () -> {
            changeScreen(new MainMenuScreen(game));
        });
        buttonTable.add(exitButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);

        root.add(buttonTable);
    }

    private void addStatRow(Table table, String labelText, String valueText, boolean isFullRow) {
        Label lbl = new Label(labelText, skin);
        lbl.setFontScale(SmallFontScale);
        lbl.setColor(Color.WHITE);

        Label val = new Label(valueText, skin);
        val.setFontScale(SmallFontScale);
        val.setColor(Color.YELLOW);
        val.setAlignment(Align.right);

        table.add(lbl).left().padRight(isFullRow ? 50f : 20f).padBottom(5f);
        table.add(val).right().padBottom(5f).row();
    }

    @Override
    public void dispose() {
        super.dispose();
        gameOverBackgroundTexture.dispose();
    }
}
