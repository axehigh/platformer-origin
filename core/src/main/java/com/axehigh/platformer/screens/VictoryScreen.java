package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.SaveData;
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
 * Full-screen World-complete view displayed when the player finishes the last level of a world.
 * Renders over the victory-screen backdrop with Ken Burns zoom/embers and displays run statistics.
 * For any world completion the button returns to Level Select. When the **final** world in the
 * catalog is beaten, the screen instead declares "YOU WON THE GAME" with a developer-text
 * placeholder ("You have defeated all the monsters, returned to the surface...") — the definitive
 * end-of-game state — and still provides a path back to Level Select.
 */
public class VictoryScreen extends MenuScreen {

    private static final String GAME_WON_TEXT =
        "You have defeated all the monsters, returned to the surface.\nWhat will be your next adventure.";

    private final int worldId;
    private final boolean lastWorld;
    private final Texture victoryBackgroundTexture;

    public VictoryScreen(Game game, int worldId) {
        super(game);
        this.worldId = worldId;
        this.lastWorld = LevelCatalog.isLastWorld(worldId);
        // The definitive "you won the game" backdrop — used ONLY when the final world is beaten.
        // Non-final world completions use the generic victory-screen.jpg.
        this.victoryBackgroundTexture = new Texture(Gdx.files.internal(
            lastWorld ? "splash/win-game.screen.jpeg" : "splash/victory-screen.jpg"));
    }

    @Override
    public void show() {
        super.show();
        // Override background image with victory-screen.jpg
        stage.getActors().first().remove(); // remove background image added by super
        Image bg = new Image(victoryBackgroundTexture);
        bg.setScaling(Scaling.fill);
        bg.setTouchable(Touchable.disabled);
        stage.getRoot().addActorAt(0, bg);

        menuEffects.applyKenBurns(stage, bg);

        AudioManager.get().playMenuMusic();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(50f);
        stage.addActor(root);

        if (lastWorld) {
            // Title
            Label titleLabel = createMenuTitle("YOU WON THE GAME");
            titleLabel.setAlignment(Align.center);
            root.add(titleLabel).padBottom(20f).row();

            // Developer placeholder text
            Label wonLabel = new Label(GAME_WON_TEXT, skin);
            wonLabel.setFontScale(FontScale * 1.1f);
            wonLabel.setColor(Color.LIGHT_GRAY);
            wonLabel.setAlignment(Align.center);
            root.add(wonLabel).padBottom(40f).row();
        } else {
            // Title
            Label titleLabel = createMenuTitle("WORLD " + worldId + " COMPLETE!");
            titleLabel.setAlignment(Align.center);
            root.add(titleLabel).padBottom(20f).row();

            Label subtitleLabel = new Label("You have conquered the dungeons of " + LevelCatalog.worldName(worldId) + ".", skin);
            subtitleLabel.setFontScale(FontScale * 1.1f);
            subtitleLabel.setColor(Color.LIGHT_GRAY);
            root.add(subtitleLabel).padBottom(40f).row();
        }

        // Stats Panel
        Table statsTable = new Table(skin);
        statsTable.background(skin.getDrawable("table"));
        statsTable.pad(30f);

        SaveData currentSave = SaveManager.hasSave() ? SaveManager.load() : new SaveData();

        addStatRow(statsTable, "Coins Collected:", String.valueOf(currentSave.coins));
        addStatRow(statsTable, "Items Found:", String.valueOf(currentSave.items));
        addStatRow(statsTable, "Enemies Killed:", String.valueOf(currentSave.enemiesKilled));
        addStatRow(statsTable, "Sword Damage:", String.valueOf(currentSave.swordDamage));

        root.add(statsTable).width(500f).padBottom(40f).row();

        // Buttons — always return to Level Select
        Table buttonTable = new Table();
        TextButton menuButton = createMenuButton("Select Level", () -> {
            changeScreen(new LevelSelectScreen(game));
        });
        buttonTable.add(menuButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).row();

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
        victoryBackgroundTexture.dispose();
    }
}
