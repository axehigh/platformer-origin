package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.LevelDefinition;
import com.axehigh.platformer.ui.SkinFactory;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Lists every level in {@link LevelCatalog} and launches {@link GameScreen} for the chosen one. */
public class LevelSelectScreen implements Screen {
    private final Game game;
    private Skin skin;
    private Stage stage;

    public LevelSelectScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        skin = SkinFactory.getSkin();
        Viewport viewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT);
        stage = new Stage(viewport);

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        Label title = new Label("Select Level", skin);
        title.setFontScale(GameConstants.FontScale);
        table.add(title).padBottom(20f).row();

        Array<String> completedLevelIds = SaveManager.hasSave() ? SaveManager.load().completedLevelIds : new Array<String>();

        for (LevelDefinition level : LevelCatalog.levels()) {
            String buttonText = findButtonTextFor(level, completedLevelIds);
            TextButton levelButton = new TextButton(buttonText, skin);
            levelButton.getLabel().setFontScale(GameConstants.FontScale);
            levelButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    game.setScreen(new GameScreen(game, level.tmxPath));
                }
            });
            table.add(levelButton).width(120f).padBottom(6f).row();
        }

        TextButton backButton = new TextButton("Back", skin);
        backButton.getLabel().setFontScale(GameConstants.FontScale);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new MainMenuScreen(game));
            }
        });
        table.add(backButton).width(120f).padTop(10f).row();

        Gdx.input.setInputProcessor(stage);
    }

    private static String findButtonTextFor(LevelDefinition level, Array<String> completedLevelIds) {
        return completedLevelIds.contains(level.id, false)
            ? level.displayName + " (Completed)"
            : level.displayName;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}
