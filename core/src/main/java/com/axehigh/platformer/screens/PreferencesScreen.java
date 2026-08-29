package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.util.GamePreferences;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/**
 * Settings screen: music/SFX volume sliders and a debug-mode checkbox, bound to {@link GamePreferences}.
 */
public class PreferencesScreen extends MenuScreen {

    private static final float ELEMENT_PAD = 48f;
    private static final float LABEL_PAD_RIGHT = 48f;

    public PreferencesScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();
        GamePreferences preferences = new GamePreferences();
        AudioManager audio = AudioManager.get();

        Table content = createMenuRoot();
        addMenuTitle(content, "Preferences");

        // Tabs table
        Table tabsTable = new Table();
        TextButton audioTabButton = createMenuButton("Audio & UI", () -> {});
        audioTabButton.getLabel().setFontScale(GameConstants.FontScale);
        TextButton gameplayTabButton = createMenuButton("GamePlay", () -> {});
        gameplayTabButton.getLabel().setFontScale(GameConstants.FontScale);
        TextButton debugTabButton = createMenuButton("Debug", () -> {});
        debugTabButton.getLabel().setFontScale(GameConstants.FontScale);

        Table tabContent = new Table();
        tabContent.center();
        final int[] activeTab = {0}; // 0 = Audio & UI, 1 = GamePlay, 2 = Debug

        Runnable refreshTabContent = new Runnable() {
            @Override
            public void run() {
                tabContent.clearChildren();
                audioTabButton.setColor(activeTab[0] == 0 ? Color.GOLD : Color.WHITE);
                gameplayTabButton.setColor(activeTab[0] == 1 ? Color.GOLD : Color.WHITE);
                debugTabButton.setColor(activeTab[0] == 2 ? Color.GOLD : Color.WHITE);

                if (activeTab[0] == 0) {
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
                    tabContent.add(musicCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

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
                    tabContent.add(musicLabel).padRight(LABEL_PAD_RIGHT);
                    tabContent.add(musicSlider).padBottom(ELEMENT_PAD).row();

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
                    tabContent.add(sfxCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

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
                    tabContent.add(sfxLabel).padRight(LABEL_PAD_RIGHT);
                    tabContent.add(sfxSlider).padBottom(ELEMENT_PAD).row();

                    Label scaleLabel = new Label("UI Icon Scale", skin);
                    scaleLabel.setFontScale(GameConstants.FontScale);
                    Slider scaleSlider = new Slider(0.5f, 4f, 0.1f, false, skin);
                    scaleSlider.setValue(preferences.getUiIconScale());
                    scaleSlider.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            preferences.setUiIconScale(scaleSlider.getValue());
                            GameConstants.UI_ICON_SCALE = scaleSlider.getValue();
                        }
                    });
                    tabContent.add(scaleLabel).padRight(LABEL_PAD_RIGHT);
                    tabContent.add(scaleSlider).padBottom(ELEMENT_PAD).row();
                } else if (activeTab[0] == 1) {
                    Label placeholder = new Label("Gameplay settings", skin);
                    placeholder.setFontScale(GameConstants.FontScale);
                    tabContent.add(placeholder).padBottom(ELEMENT_PAD).row();
                } else if (activeTab[0] == 2) {
                    CheckBox levelOpenCheckBox = new CheckBox(" Level Open", skin);
                    levelOpenCheckBox.getLabel().setFontScale(GameConstants.FontScale);
                    levelOpenCheckBox.setChecked(com.axehigh.platformer.util.FeatureFlags.isLevelOpen());
                    levelOpenCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            com.axehigh.platformer.util.FeatureFlags.setLevelOpen(levelOpenCheckBox.isChecked());
                        }
                    });
                    tabContent.add(levelOpenCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();
                }
            }
        };

        audioTabButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                activeTab[0] = 0;
                refreshTabContent.run();
            }
        });
        gameplayTabButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                activeTab[0] = 1;
                refreshTabContent.run();
            }
        });
        debugTabButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                activeTab[0] = 2;
                refreshTabContent.run();
            }
        });

        tabsTable.add(audioTabButton).size(180f, 50f).pad(8f);
        tabsTable.add(gameplayTabButton).size(180f, 50f).pad(8f);
        tabsTable.add(debugTabButton).size(180f, 50f).pad(8f);

        content.add(tabsTable).colspan(2).padBottom(30f).row();
        content.add(tabContent).colspan(2).expand().center().padBottom(30f).row();

        refreshTabContent.run();

        addBackButton(content, () -> changeScreen(new MainMenuScreen(game)));
    }

}
