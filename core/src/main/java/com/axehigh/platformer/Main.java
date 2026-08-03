package com.axehigh.platformer;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.screens.SplashScreen;
import com.badlogic.gdx.Game;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {
    @Override
    public void create() {
        AudioManager.get();
        setScreen(new SplashScreen(this));
    }

    @Override
    public void dispose() {
        AudioManager.get().dispose();
        super.dispose();
    }
}