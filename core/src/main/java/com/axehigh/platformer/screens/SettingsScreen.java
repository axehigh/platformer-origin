package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.ui.LabelFirstCheckBox;
import com.axehigh.platformer.util.FeatureFlags;
import com.axehigh.platformer.util.GamePreferences;
import com.axehigh.platformer.util.StylizedTransitionOverlay;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import static com.axehigh.platformer.GameConstants.BodyFontScale;
import static com.axehigh.platformer.GameConstants.UI_PANEL_ALPHA;
import static com.axehigh.platformer.screens.GameConstantText.MUSIC_VOLUME;
import static com.axehigh.platformer.screens.GameConstantText.SFX_VOLUME;
import static com.axehigh.platformer.util.FeatureFlags.isVignetteEnabled;

/**
 * Settings screen: music/SFX volume sliders and a debug-mode checkbox, bound to {@link GamePreferences}.
 */
public class SettingsScreen extends MenuScreen {

    private static final float ELEMENT_PAD = 48f;
    private static final float LABEL_PAD_RIGHT = 48f;
    private static final float TAB_BUTTON_WIDTH = 300f;
    private static final float TAB_BUTTON_HEIGHT = 75f;
    private static final float LABEL_COLUMN_WIDTH = 380f;
    private static final float CONTROL_COLUMN_WIDTH = 400f;
    private static final float TAB_CONTENT_PAD = 40f;

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

        Table tabContent = new Table(skin);
        tabContent.background(skin.getDrawable("table"));
        tabContent.setColor(1, 1, 1, UI_PANEL_ALPHA);
        tabContent.pad(TAB_CONTENT_PAD);
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
                    LabelFirstCheckBox musicCheckBox = new LabelFirstCheckBox(" Music", skin);
                    musicCheckBox.getLabel().setFontScale(BodyFontScale);
                    musicCheckBox.setChecked(audio.isMusicEnabled());
                    musicCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            audio.playClick();
                            audio.setMusicEnabled(musicCheckBox.isChecked());
                        }
                    });
                    tabContent.add(musicCheckBox).colspan(2).left().padBottom(ELEMENT_PAD).row();

                    Label musicLabel = new Label(MUSIC_VOLUME, skin);
                    musicLabel.setFontScale(BodyFontScale);
                    Slider musicSlider = new Slider(0f, 100f, 1f, false, skin);
                    musicSlider.setValue(preferences.getMusicVolume());
                    musicSlider.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            audio.setMusicVolume(musicSlider.getValue());
                        }
                    });
                    tabContent.add(musicLabel).width(LABEL_COLUMN_WIDTH).right().padRight(LABEL_PAD_RIGHT);
                    tabContent.add(musicSlider).width(CONTROL_COLUMN_WIDTH).left().padBottom(ELEMENT_PAD).row();

                    LabelFirstCheckBox sfxCheckBox = new LabelFirstCheckBox(" Sound Effects", skin);
                    sfxCheckBox.getLabel().setFontScale(BodyFontScale);
                    sfxCheckBox.setChecked(audio.isSfxEnabled());
                    sfxCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            audio.playClick();
                            audio.setSfxEnabled(sfxCheckBox.isChecked());
                        }
                    });
                    tabContent.add(sfxCheckBox).colspan(2).left().padBottom(ELEMENT_PAD).row();

                    Label sfxLabel = new Label(SFX_VOLUME, skin);
                    sfxLabel.setFontScale(BodyFontScale);
                    Slider sfxSlider = new Slider(0f, 100f, 1f, false, skin);
                    sfxSlider.setValue(preferences.getSfxVolume());
                    sfxSlider.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            audio.setSfxVolume(sfxSlider.getValue());
                        }
                    });
                    tabContent.add(sfxLabel).width(LABEL_COLUMN_WIDTH).right().padRight(LABEL_PAD_RIGHT);
                    tabContent.add(sfxSlider).width(CONTROL_COLUMN_WIDTH).left().padBottom(ELEMENT_PAD).row();

                    Label scaleLabel = new Label(GameConstantText.UI_ICON_SCALE, skin);
                    scaleLabel.setFontScale(BodyFontScale);
                    Slider scaleSlider = new Slider(0.5f, 4f, 0.1f, false, skin);
                    scaleSlider.setValue(preferences.getUiIconScale());
                    scaleSlider.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            preferences.setUiIconScale(scaleSlider.getValue());
                            GameConstants.UI_ICON_SCALE = scaleSlider.getValue();
                        }
                    });
                    tabContent.add(scaleLabel).width(LABEL_COLUMN_WIDTH).right().padRight(LABEL_PAD_RIGHT);
                    tabContent.add(scaleSlider).width(CONTROL_COLUMN_WIDTH).left().padBottom(ELEMENT_PAD).row();
                } else if (activeTab[0] == 1) {
//                    Label header = new Label(GAMEPLAY_SETTINGS, skin);
//                    header.setFontScale(BodyFontScale);
//                    tabContent.add(header).colspan(2).padBottom(ELEMENT_PAD).row();

                    LabelFirstCheckBox wallClimbCheckBox = new LabelFirstCheckBox(" Wall Climb", skin);
                    wallClimbCheckBox.getLabel().setFontScale(BodyFontScale);
                    wallClimbCheckBox.setChecked(FeatureFlags.isWallClimbingEnabled());
                    wallClimbCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            AudioManager.get().playClick();
                            FeatureFlags.setWallClimbingEnabled(wallClimbCheckBox.isChecked());
                        }
                    });
                    tabContent.add(wallClimbCheckBox).colspan(2).left().padBottom(ELEMENT_PAD).row();

                    Label transitionLabel = new Label("Transition Style:", skin);
                    transitionLabel.setFontScale(BodyFontScale);
                    final TextButton[] transitionStyleButtonHolder = new TextButton[1];
                    transitionStyleButtonHolder[0] = createMenuButton(FeatureFlags.getTransitionStyle().name(), () -> {
                        StylizedTransitionOverlay.TransitionType current = FeatureFlags.getTransitionStyle();
                        StylizedTransitionOverlay.TransitionType[] values = StylizedTransitionOverlay.TransitionType.values();
                        StylizedField: for (int i = 0; i < values.length; i++) {
                            if (values[i] == current) {
                                StylizedTransitionOverlay.TransitionType next = values[(i + 1) % values.length];
                                FeatureFlags.setTransitionStyle(next);
                                transitionStyleButtonHolder[0].setText(next.name());
                                break StylizedField;
                            }
                        }
                    });
                    tabContent.add(transitionLabel).width(LABEL_COLUMN_WIDTH).right().padRight(LABEL_PAD_RIGHT);
                    tabContent.add(transitionStyleButtonHolder[0]).size(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT).left().padBottom(ELEMENT_PAD).row();
                } else if (activeTab[0] == 2) {
                    LabelFirstCheckBox levelOpenCheckBox = new LabelFirstCheckBox(" Level Open", skin);
                    levelOpenCheckBox.getLabel().setFontScale(BodyFontScale);
                    levelOpenCheckBox.setChecked(FeatureFlags.isLevelOpen());
                    levelOpenCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setLevelOpen(levelOpenCheckBox.isChecked());
                        }
                    });
                    tabContent.add(levelOpenCheckBox).colspan(2).left().padBottom(ELEMENT_PAD).row();

                    LabelFirstCheckBox embersCheckBox = new LabelFirstCheckBox(" Embers Effect", skin);
                    embersCheckBox.getLabel().setFontScale(BodyFontScale);
                    embersCheckBox.setChecked(FeatureFlags.isEmbersEnabled());
                    embersCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setEmbersEnabled(embersCheckBox.isChecked());
                        }
                    });
                    tabContent.add(embersCheckBox).colspan(2).left().padBottom(ELEMENT_PAD).row();

                    LabelFirstCheckBox vignetteCheckBox = new LabelFirstCheckBox(" Vignette Effect", skin);
                    vignetteCheckBox.getLabel().setFontScale(BodyFontScale);
                    vignetteCheckBox.setChecked(isVignetteEnabled());
                    vignetteCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setVignetteEnabled(vignetteCheckBox.isChecked());
                        }
                    });
                    tabContent.add(vignetteCheckBox).colspan(2).left().padBottom(ELEMENT_PAD).row();

                    LabelFirstCheckBox slashArcCheckBox = new LabelFirstCheckBox(" Combat Effects", skin);
                    slashArcCheckBox.getLabel().setFontScale(BodyFontScale);
                    slashArcCheckBox.setChecked(FeatureFlags.isSlashArcEnabled());
                    slashArcCheckBox.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            FeatureFlags.setSlashArcEnabled(slashArcCheckBox.isChecked());
                        }
                    });
                    tabContent.add(slashArcCheckBox).colspan(2).left().padBottom(ELEMENT_PAD).row();

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
