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
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Scaling;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Lists the levels of one world (world selectable via tabs above the grid) in a scrollable
 * 3-column grid and launches {@link GameScreen} for the chosen one. World 1 is the default.
 * Supports keyboard navigation (arrows/WASD + Enter, number keys switch world).
 */
public class LevelSelectScreen extends MenuScreen {

    private static final int COLUMNS = 3;
    private static final float BUTTON_WIDTH = 430f;
    private static final float BUTTON_HEIGHT = 90f;
    private static final float GRID_WIDTH = 1350f;
    private static final float GRID_HEIGHT = 620f;
    private static final float TAB_WIDTH = 260f;
    private static final float TAB_HEIGHT = 70f;

    private final IntArray worldIds = LevelCatalog.worldIds();
    private final Array<ImageTextButton> levelButtons = new Array<>();
    private final Array<TextButton> worldTabs = new Array<>();

    private Array<LevelDefinition> levels;
    private Array<String> completedLevelIds;
    private Label progress;
    private Table grid;
    private ScrollPane scrollPane;
    private int currentWorldIndex = 0;
    private int selectedIndex = -1;

    public LevelSelectScreen(Game game) {
        super(game);
        levels = LevelCatalog.levelsForWorld(worldIds.get(0));
    }

    @Override
    public void show() {
        super.show();

        completedLevelIds = SaveManager.hasSave() ? SaveManager.load().completedLevelIds : new Array<>();

        addMenuPanel();

        Table content = new Table();
        content.setFillParent(true);
        stage.addActor(content);

        content.add(createMenuTitle("Select Level")).padBottom(4f).row();

        content.add(createWorldTabs()).padBottom(8f).row();

        progress = new Label("", skin);
        progress.setFontScale(FontScale);
        content.add(progress).padBottom(12f).row();

        grid = new Table();
        scrollPane = new ScrollPane(grid, skin);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        content.add(scrollPane).size(GRID_WIDTH, GRID_HEIGHT).row();

        TextButton backButton = createMenuButton("Back", () -> changeScreen(new MainMenuScreen(game)));
        content.add(backButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).padTop(14f).row();

        switchWorld(currentWorldIndex);

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
                        int worldNumber = keycode - Keys.NUM_1 + 1;
                        int worldIndex = worldIds.indexOf(worldNumber);
                        if (worldIndex >= 0 && worldIndex != currentWorldIndex) {
                            switchWorld(worldIndex);
                            return true;
                        }
                        return false;
                }
            }
        });
    }

    private Table createWorldTabs() {
        Table tabs = new Table();
        for (int i = 0; i < worldIds.size; i++) {
            final int worldIndex = i;
            final int worldId = worldIds.get(i);
            TextButton tab = new TextButton(LevelCatalog.worldName(worldId), skin);
            tab.getLabel().setFontScale(FontScale);
            tab.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (worldIndex == currentWorldIndex) {
                        return;
                    }
                    AudioManager.get().playClick();
                    switchWorld(worldIndex);
                }
            });
            worldTabs.add(tab);
            tabs.add(tab).size(TAB_WIDTH, TAB_HEIGHT).pad(8f);
        }
        return tabs;
    }

    private void switchWorld(int worldIndex) {
        currentWorldIndex = worldIndex;
        levels = LevelCatalog.levelsForWorld(worldIds.get(worldIndex));

        for (int i = 0; i < worldTabs.size; i++) {
            worldTabs.get(i).setColor(i == currentWorldIndex ? Color.GOLD : Color.WHITE);
        }

        grid.clearChildren();
        levelButtons.clear();
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

        progress.setText(progressText());
        selectedIndex = -1;
    }

    private ImageTextButton createLevelButton(final LevelDefinition level, boolean completed) {
        String text = level.displayName;
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

    private String progressText() {
        int completed = 0;
        for (LevelDefinition level : levels) {
            if (completedLevelIds.contains(level.id, false)) {
                completed++;
            }
        }
        return LevelCatalog.worldName(worldIds.get(currentWorldIndex)) + ": Completed " + completed + "/" + levels.size;
    }

}
