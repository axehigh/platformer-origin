package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Full-screen Game Over view displayed when the player dies and has no tries remaining,
 * or chooses to exit. Renders over the menu backdrop with Ken Burns zoom/embers, displays
 * run statistics (coins, items, sword damage, remaining tries, potions), and provides
 * Continue (if tries remain) or Exit to Main Menu buttons.
 */
public class GameOverScreen extends MenuScreen {

    public interface Listener {
        void onContinue();
        void onExit();
    }

    private final Listener listener;

    public GameOverScreen(Game game, Listener listener) {
        super(game);
        this.listener = listener;
    }

    @Override
    public void show() {
        super.show();
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
            TextButton continueButton = createMenuButton("Continue (uses 1 try)", () -> {
                currentSave.triesRemaining--;
                SaveManager.save(currentSave);
                listener.onContinue();
            });
            buttonTable.add(continueButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padBottom(20f).row();
        }

        TextButton exitButton = createMenuButton("Exit to Main Menu", () -> {
            listener.onExit();
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
}
