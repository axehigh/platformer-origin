package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.util.FeatureFlags;
import com.axehigh.platformer.util.GamePreferences;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import static com.axehigh.platformer.GameConstants.SmallFontScale;
import static com.axehigh.platformer.GameConstants.UI_ICON_SCALE;
import static com.axehigh.platformer.screens.GameConstantText.*;

/**
 * Settings screen: music/SFX volume sliders and a debug-mode checkbox, bound to {@link GamePreferences}.
 */
public class SettingsScreen extends MenuScreen {

    private static final float ELEMENT_PAD = 48f;
    private static final float LABEL_PAD_RIGHT = 48f;
    private static final float TAB_BUTTON_WIDTH = 300f;
    private static final float TAB_BUTTON_HEIGHT = 75f;

    public SettingsScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();
        GamePreferences preferences = new GamePreferences();
        AudioManager audio = AudioManager.get();

        Table content = createMenuRoot();
        addMenuTitle(content, "Settings");

        // Tabs table
        Table tabsTable = new Table();
        TextButton audioTabButton = createMenuButton("Audio & UI", () -> {});
        TextButton gameplayTabButton = createMenuButton("GamePlay", () -> {});
        TextButton debugTabButton = createMenuButton("Debug", () -> {});

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
                    musicCheckBox.getLabel().setFontScale(SmallFontScale);
                    musicCheckBox.setChecked(audio.isMusicEnabled());
                    musicCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            audio.playClick();
                            audio.setMusicEnabled(musicCheckBox.isChecked());
                        }
                    });
                    tabContent.add(musicCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

                    Label musicLabel = new Label(MUSIC_VOLUME, skin);
                    musicLabel.setFontScale(SmallFontScale);
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
                    sfxCheckBox.getLabel().setFontScale(SmallFontScale);
                    sfxCheckBox.setChecked(audio.isSfxEnabled());
                    sfxCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            audio.playClick();
                            audio.setSfxEnabled(sfxCheckBox.isChecked());
                        }
                    });
                    tabContent.add(sfxCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

                    Label sfxLabel = new Label(SFX_VOLUME, skin);
                    sfxLabel.setFontScale(SmallFontScale);
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

                    Label scaleLabel = new Label(GameConstantText.UI_ICON_SCALE, skin);
                    scaleLabel.setFontScale(SmallFontScale);
                    Slider scaleSlider = new Slider(0.5f, 4f, 0.1f, false, skin);
                    scaleSlider.setValue(preferences.getUiIconScale());
                    scaleSlider.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            preferences.setUiIconScale(scaleSlider.getValue());
                            UI_ICON_SCALE = scaleSlider.getValue();
                        }
                    });
                    tabContent.add(scaleLabel).padRight(LABEL_PAD_RIGHT);
                    tabContent.add(scaleSlider).padBottom(ELEMENT_PAD).row();
                } else if (activeTab[0] == 1) {
                    Label header = new Label(GAMEPLAY_SETTINGS, skin);
                    header.setFontScale(SmallFontScale);
                    tabContent.add(header).colspan(2).padBottom(ELEMENT_PAD).row();

                    CheckBox wallClimbCheckBox = new CheckBox(" Wall Climb", skin);
                    wallClimbCheckBox.getLabel().setFontScale(SmallFontScale);
                    wallClimbCheckBox.setChecked(FeatureFlags.isWallClimbingEnabled());
                    wallClimbCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            AudioManager.get().playClick();
                            FeatureFlags.setWallClimbingEnabled(wallClimbCheckBox.isChecked());
                        }
                    });
                    tabContent.add(wallClimbCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

                    CheckBox softStopCheckBox = new CheckBox(" Soft Stop", skin);
                    softStopCheckBox.getLabel().setFontScale(SmallFontScale);
                    softStopCheckBox.setChecked(FeatureFlags.isSoftStopEnabled());
                    softStopCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            AudioManager.get().playClick();
                            FeatureFlags.setSoftStopEnabled(softStopCheckBox.isChecked());
                        }
                    });
                    tabContent.add(softStopCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();
                } else if (activeTab[0] == 2) {
                    CheckBox levelOpenCheckBox = new CheckBox(" Level Open", skin);
                    levelOpenCheckBox.getLabel().setFontScale(SmallFontScale);
                    levelOpenCheckBox.setChecked(FeatureFlags.isLevelOpen());
                    levelOpenCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setLevelOpen(levelOpenCheckBox.isChecked());
                        }
                    });
                    tabContent.add(levelOpenCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

                    CheckBox embersCheckBox = new CheckBox(" Embers Effect", skin);
                    embersCheckBox.getLabel().setFontScale(SmallFontScale);
                    embersCheckBox.setChecked(FeatureFlags.isEmbersEnabled());
                    embersCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setEmbersEnabled(embersCheckBox.isChecked());
                        }
                    });
                    tabContent.add(embersCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

                    CheckBox vignetteCheckBox = new CheckBox(" Vignette Effect", skin);
                    vignetteCheckBox.getLabel().setFontScale(SmallFontScale);
                    vignetteCheckBox.setChecked(FeatureFlags.isVignetteEnabled());
                    vignetteCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setVignetteEnabled(vignetteCheckBox.isChecked());
                        }
                    });
                    tabContent.add(vignetteCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

                    CheckBox slashArcCheckBox = new CheckBox(" Slash Effect", skin);
                    slashArcCheckBox.getLabel().setFontScale(SmallFontScale);
                    slashArcCheckBox.setChecked(FeatureFlags.isSlashArcEnabled());
                    slashArcCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setSlashArcEnabled(slashArcCheckBox.isChecked());
                        }
                    });
                    tabContent.add(slashArcCheckBox).colspan(2).padBottom(ELEMENT_PAD).row();

                    TextButton clearPlayerButton = createMenuButton("Clear Player", () -> {
                        com.axehigh.platformer.util.SaveManager.clear();
                    });
                    tabContent.add(clearPlayerButton).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).colspan(2).padBottom(ELEMENT_PAD).row();
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

        tabsTable.add(audioTabButton).size(TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT).pad(10f);
        tabsTable.add(gameplayTabButton).size(TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT).pad(10f);
        tabsTable.add(debugTabButton).size(TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT).pad(10f);

        content.add(tabsTable).colspan(2).padBottom(30f).row();
        content.add(tabContent).colspan(2).expand().center().padBottom(30f).row();

        refreshTabContent.run();

        addBackButton(content, () -> changeScreen(new MainMenuScreen(game)));
    }

}
