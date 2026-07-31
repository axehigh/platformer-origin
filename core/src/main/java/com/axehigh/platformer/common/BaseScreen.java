package com.axehigh.platformer.common;

import com.axehigh.platformer.ui.SkinFactory;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.FitViewport;

import static com.axehigh.platformer.GameConstants.SCREEN_FADE_TIMER;
import static com.axehigh.platformer.GameConstants.SCREEN_HEIGHT;
import static com.axehigh.platformer.GameConstants.SCREEN_WIDTH;

public class BaseScreen implements Screen {
    public final Game game;
    public Skin skin;
    public final ScreenUtils screenUtils = new ScreenUtils();
    public Image fadeOverlay;
    public Texture fadeTexture;
    public Stage stage;
    public Stage transitionStage;

    public BaseScreen(Game game) {
        this.game = game;
        this.fadeTexture = screenUtils.getFadeTexture();

        stage = new Stage(new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT));
        transitionStage = new Stage(new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT));

        fadeOverlay = new Image(fadeTexture);
        fadeOverlay.setFillParent(true);
        fadeOverlay.setTouchable(Touchable.disabled);
        transitionStage.addActor(fadeOverlay);
        skin = SkinFactory.getSkin();
    }

    @Override
    public void show() {
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(transitionStage);
        multiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(multiplexer);

        // Start fade-in
        fadeOverlay.setTouchable(Touchable.enabled);
        fadeOverlay.getColor().a = 1;
        fadeOverlay.addAction(Actions.sequence(
            Actions.fadeOut(SCREEN_FADE_TIMER),
            Actions.touchable(Touchable.disabled)
        ));
    }

    public void changeScreen(final Screen nextScreen) {
        fadeOverlay.setTouchable(Touchable.enabled); // Block input during fade-out
        fadeOverlay.addAction(Actions.sequence(
            Actions.fadeIn(SCREEN_FADE_TIMER),
            Actions.run(new Runnable() {
                @Override
                public void run() {
                    game.setScreen(nextScreen);
                }
            })
        ));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(Math.min(delta, 1 / 30f));
        stage.getViewport().apply();
        stage.draw();

        renderTransition(delta);
    }

    protected void renderTransition(float delta) {
        transitionStage.act(delta);
        transitionStage.getViewport().apply();
        transitionStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        stage.getViewport().update(width, height, true);
        transitionStage.getViewport().update(width, height, true);
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
        transitionStage.dispose();
        stage.dispose();
        skin.dispose();
    }
}
