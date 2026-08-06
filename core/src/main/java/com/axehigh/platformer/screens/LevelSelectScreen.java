package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.LevelDefinition;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton.ImageTextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Lists every level in {@link LevelCatalog} in a scrollable 3-column grid and launches
 * {@link GameScreen} for the chosen one. Supports keyboard navigation (arrows/WASD + Enter).
 */
public class LevelSelectScreen extends MenuScreen {

    private static final int COLUMNS = 3;
    private static final float BUTTON_WIDTH = 430f;
    private static final float BUTTON_HEIGHT = 90f;
    private static final float GRID_WIDTH = 1350f;
    private static final float GRID_HEIGHT = 660f;

    private final Array<LevelDefinition> levels = LevelCatalog.levels();
    private final Array<ImageTextButton> levelButtons = new Array<>();
    private ScrollPane scrollPane;
    private int selectedIndex = -1;

    public LevelSelectScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Array<String> completedLevelIds = SaveManager.hasSave() ? SaveManager.load().completedLevelIds : new Array<>();

        addMenuPanel();

        Table content = new Table();
        content.setFillParent(true);
        stage.addActor(content);

        content.add(createMenuTitle("Select Level")).padBottom(6f).row();

        Label progress = new Label(progressText(completedLevelIds), skin);
        progress.setFontScale(FontScale);
        content.add(progress).padBottom(14f).row();

        Table grid = new Table();
        for (int i = 0; i < levels.size; i++) {
            LevelDefinition level = levels.get(i);
            boolean completed = completedLevelIds.contains(level.id, false);
            ImageTextButton levelButton = createLevelButton(level, completed);
            levelButtons.add(levelButton);
            grid.add(levelButton).size(BUTTON_WIDTH, BUTTON_HEIGHT).pad(10f);
            if (i % COLUMNS == COLUMNS - 1) {
                grid.row();
            }
        }
        for (int i = levels.size % COLUMNS; i < COLUMNS; i++) {
            grid.add().size(BUTTON_WIDTH, BUTTON_HEIGHT).pad(10f);
        }

        scrollPane = new ScrollPane(grid, skin);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        content.add(scrollPane).size(GRID_WIDTH, GRID_HEIGHT).row();

        TextButton backButton = createMenuButton("Back", () -> changeScreen(new MainMenuScreen(game)));
        content.add(backButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padTop(16f).row();

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (selectedIndex < 0) {
                    select(0);
                    return true;
                }
                switch (keycode) {
                    case Keys.LEFT:
                    case Keys.A:
                        select(selectedIndex - 1);
                        return true;
                    case Keys.RIGHT:
                    case Keys.D:
                        select(selectedIndex + 1);
                        return true;
                    case Keys.UP:
                    case Keys.W:
                        select(selectedIndex - COLUMNS);
                        return true;
                    case Keys.DOWN:
                    case Keys.S:
                        select(selectedIndex + COLUMNS);
                        return true;
                    case Keys.ENTER:
                    case Keys.SPACE:
                        startLevel(selectedIndex);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private ImageTextButton createLevelButton(final LevelDefinition level, boolean completed) {
        String text = completed ? level.displayName : level.displayName;
        ImageTextButton button;
        if (completed) {
            ImageTextButtonStyle style = new ImageTextButtonStyle(skin.get(ImageTextButtonStyle.class));
            style.imageUp = skin.getDrawable("star");
            style.imageOver = skin.getDrawable("star");
            style.imageDown = skin.getDrawable("star");
            button = new ImageTextButton(text, style);
            button.getImage().setScaling(Scaling.fit);
            button.getImageCell().size(56f, 56f).padRight(10f);
        } else {
            button = new ImageTextButton(text, skin);
        }
        button.getLabel().setFontScale(FontScale);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                changeScreen(new GameScreen(game, level.tmxPath));
            }
        });
        return button;
    }

    private void select(int newIndex) {
        int clamped = Math.max(0, Math.min(levelButtons.size - 1, newIndex));
        if (clamped == selectedIndex) {
            return;
        }
        if (selectedIndex >= 0) {
            levelButtons.get(selectedIndex).setColor(Color.WHITE);
        }
        selectedIndex = clamped;
        ImageTextButton selectedButton = levelButtons.get(selectedIndex);
        selectedButton.setColor(Color.GOLD);
        scrollPane.validate();
        scrollPane.scrollTo(selectedButton.getX(), selectedButton.getY(), selectedButton.getWidth(), selectedButton.getHeight());
    }

    private void startLevel(int index) {
        if (index < 0 || index >= levels.size) {
            return;
        }
        AudioManager.get().playClick();
        changeScreen(new GameScreen(game, levels.get(index).tmxPath));
    }

    private static String progressText(Array<String> completedLevelIds) {
        int completed = 0;
        for (LevelDefinition level : LevelCatalog.levels()) {
            if (completedLevelIds.contains(level.id, false)) {
                completed++;
            }
        }
        return "Completed " + completed + "/" + LevelCatalog.levels().size;
    }

}
