package com.axehigh.platformer.screens;

import com.axehigh.platformer.common.BaseScreen;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Timer;

public class SplashScreen extends BaseScreen {

    private static final float SPLASH_DURATION = 3f;
    private static final float LOGO_SCALE = 1.6f;

    private final Texture splashTexture;
    private final Texture splashTextTexture;
    private final Timer.Task advanceTask;
    private boolean transitioning;

    public SplashScreen(Game game) {
        super(game);
        splashTexture = new Texture(Gdx.files.internal("splash/splash2.jpg"));
        splashTextTexture = new Texture(Gdx.files.internal("splash/splash_text.png"));

        Table table = new Table();
        table.setFillParent(true);
        table.setTouchable(Touchable.enabled);
        stage.addActor(table);

        Image logo = new Image(splashTexture);
        logo.setSize(500 * LOGO_SCALE, 500 * LOGO_SCALE);
        table.add(logo).padBottom(16f).row();

        Image text = new Image(splashTextTexture);
        text.setSize(487 * LOGO_SCALE, 79 * LOGO_SCALE);
        table.add(text);

        table.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                advanceToMenu();
            }
        });

        advanceTask = new Timer.Task() {
            @Override
            public void run() {
                advanceToMenu();
            }
        };
        Timer.instance().scheduleTask(advanceTask, SPLASH_DURATION);
    }

    private void advanceToMenu() {
        if (transitioning) {
            return;
        }
        transitioning = true;
        advanceTask.cancel();
        changeScreen(new MainMenuScreen(game));
    }

    @Override
    public void dispose() {
        advanceTask.cancel();
        splashTexture.dispose();
        splashTextTexture.dispose();
        super.dispose();
    }
}
