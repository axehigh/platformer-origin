package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.util.GamePreferences;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/**
 * Settings screen: music/SFX volume sliders and a debug-mode checkbox, bound to {@link GamePreferences}.
 */
public class PreferencesScreen extends MenuScreen {

    public PreferencesScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();
        GamePreferences preferences = new GamePreferences();
        AudioManager audio = AudioManager.get();

        Table content = new Table();
        content.setFillParent(true);
        stage.addActor(content);

        content.add(menuEffects.createGlowBehind(createMenuTitle("Preferences"))).colspan(2).padBottom(12f).row();

        CheckBox musicCheckBox = new CheckBox(" Music", skin);
        musicCheckBox.getLabel().setFontScale(GameConstants.FontScale);
        musicCheckBox.setChecked(audio.isMusicEnabled());
        musicCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.playClick();
                audio.setMusicEnabled(musicCheckBox.isChecked());
            }
        });
        content.add(musicCheckBox).colspan(2).padBottom(6f).row();

        Label musicLabel = new Label("Music Volume", skin);
        musicLabel.setFontScale(GameConstants.FontScale);
        Slider musicSlider = new Slider(0f, 100f, 1f, false, skin);
        musicSlider.setValue(preferences.getMusicVolume());
        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setMusicVolume(musicSlider.getValue());
            }
        });
        content.add(musicLabel).padRight(12f);
        content.add(musicSlider).padBottom(6f).row();

        CheckBox sfxCheckBox = new CheckBox(" Sound Effects", skin);
        sfxCheckBox.getLabel().setFontScale(GameConstants.FontScale);
        sfxCheckBox.setChecked(audio.isSfxEnabled());
        sfxCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.playClick();
                audio.setSfxEnabled(sfxCheckBox.isChecked());
            }
        });
        content.add(sfxCheckBox).colspan(2).padBottom(6f).row();

        Label sfxLabel = new Label("SFX Volume", skin);
        sfxLabel.setFontScale(GameConstants.FontScale);
        Slider sfxSlider = new Slider(0f, 100f, 1f, false, skin);
        sfxSlider.setValue(preferences.getSfxVolume());
        sfxSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setSfxVolume(sfxSlider.getValue());
            }
        });
        content.add(sfxLabel).padRight(12f);
        content.add(sfxSlider).padBottom(6f).row();

        CheckBox debugCheckBox = new CheckBox(" Debug Mode", skin);
        debugCheckBox.getLabel().setFontScale(GameConstants.FontScale);
        debugCheckBox.setChecked(preferences.isDebugMode());
        debugCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                preferences.setDebugMode(debugCheckBox.isChecked());
            }
        });
        content.add(debugCheckBox).colspan(2).padBottom(10f).row();

        content.add(createMenuButton("Back", () -> changeScreen(new MainMenuScreen(game))))
            .colspan(2).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).row();
    }

}
