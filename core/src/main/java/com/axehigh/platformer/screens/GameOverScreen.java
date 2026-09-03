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

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Full-screen Game Over view displayed when the player dies and has no tries remaining,
 * or chooses to exit. Renders over the gameover-screen backdrop with Ken Burns zoom/embers, displays
 * run statistics (coins, items, enemies killed, sword damage, remaining tries), and provides
 * Continue (if tries remain) or Exit to Main Menu buttons.
 */
public class GameOverScreen extends MenuScreen {

    public interface Listener {
        /** Returns the screen to transition to when the player continues (e.g. a fresh {@code GameScreen}). */
        Screen onContinue();
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
        titleLabel.setAlignment(Align.center);
        root.add(titleLabel).padBottom(20f).row();

        Label subtitleLabel = new Label("You have perished in the dungeons.", skin);
        subtitleLabel.setFontScale(FontScale * 1.1f);
        subtitleLabel.setColor(Color.LIGHT_GRAY);
        root.add(subtitleLabel).padBottom(40f).row();

        // Stats Panel
        Table statsTable = new Table(skin);
        statsTable.background(skin.getDrawable("table"));
        statsTable.pad(30f);

        SaveData currentSave = SaveManager.hasSave() ? SaveManager.load() : new SaveData();

        addStatRow(statsTable, "Tries Remaining:", String.valueOf(currentSave.triesRemaining));
        addStatRow(statsTable, "Coins Collected:", String.valueOf(currentSave.coins));
        addStatRow(statsTable, "Items Found:", String.valueOf(currentSave.items));
        addStatRow(statsTable, "Enemies Killed:", String.valueOf(currentSave.enemiesKilled));
        addStatRow(statsTable, "Sword Damage:", String.valueOf(currentSave.swordDamage));

        root.add(statsTable).width(500f).padBottom(40f).row();

        // Buttons
        Table buttonTable = new Table();

        if (currentSave.triesRemaining > 0) {
            TextButton continueButton = createMenuButton("Continue", () -> {
                currentSave.triesRemaining--;
                SaveManager.save(currentSave);
                // Transition through THIS screen so the fade action runs and game.setScreen actually
                // fires (the GameScreen whose listener we implement is no longer rendering its fade stage).
                changeScreen(listener.onContinue());
            });
            buttonTable.add(continueButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padBottom(20f).row();
        }

        TextButton exitButton = createMenuButton("Exit to Main Menu", () -> {
            changeScreen(new MainMenuScreen(game));
        });
        buttonTable.add(exitButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).row();

        root.add(buttonTable);
    }

    private void addStatRow(Table table, String labelText, String valueText) {
        Label lbl = new Label(labelText, skin);
        lbl.setFontScale(FontScale);
        lbl.setColor(Color.WHITE);

        Label val = new Label(valueText, skin);
        val.setFontScale(FontScale);
        val.setColor(Color.YELLOW);
        val.setAlignment(Align.right);

        table.add(lbl).left().padRight(50f).padBottom(10f);
        table.add(val).right().padBottom(10f).row();
    }

    @Override
    public void dispose() {
        super.dispose();
        gameOverBackgroundTexture.dispose();
    }
}
