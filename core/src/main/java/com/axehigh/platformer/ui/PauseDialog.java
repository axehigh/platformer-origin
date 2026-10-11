package com.axehigh.platformer.ui;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.ecs.systems.DebugRenderSystem;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import static com.axehigh.platformer.GameConstants.SmallFontScale;

/**
 * In-game pause menu, organized into four tabs: Gameplay (audio, screenshot, tries),
 * Visual (rendering & visual toggles), Beta (feature flags, collision/touch debug),
 * and Device (device & mobile layout simulation). Resume and Menu stay outside the tabs
 * side-by-side in the footer.
 */
public class PauseDialog extends Dialog {

    /** Which content page the tab header shows. */
    private enum Tab {
        GAMEPLAY,
        VISUAL,
        BETA,
        DEVICE
    }

    /** RoboVM-safe functional interface: reads a boolean setting (no java.util.function on iOS). */
    @FunctionalInterface
    private interface BooleanGetter {
        boolean get();
    }

    /** RoboVM-safe functional interface: writes a boolean setting (no java.util.function on iOS). */
    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }

    /** Callbacks into the owning screen for state the dialog must read or mutate. */
    public interface Listener {
        /** Called when the dialog closes by any path (Resume button, Exit button, ESC/Enter). */
        void onResume();

        /**
         * Arms a screenshot of the next fully composited frame. The dialog hides itself before
         * calling this, so the capture never contains the pause menu.
         */
        void onScreenshot();

        boolean isTouchDebugOn();

        void setTouchDebugOn(boolean on);

        /** Display name for the current simulated device ("Auto" when none is simulated). */
        String deviceLabel();

        /** Cycles the simulated device, persists the choice and re-applies layout/camera. */
        void cycleDevice();

        /** Display name for the current mobile {@link LayoutMode}. */
        String layoutLabel();

        /** Cycles the mobile layout, persists the choice and re-applies layout/camera. */
        void cycleLayout();

        void onExit();

        int getTriesRemaining();
    }

    private static final float TAB_BUTTON_MIN_WIDTH = 90f;
    private static final float FOOTER_BUTTON_WIDTH = 180f;
    private static final float FOOTER_BUTTON_HEIGHT = 60f;
    private static final float SIMULATION_BUTTON_MIN_WIDTH = 140f;
    private static final float CONTENT_PAD = 8f;
    private static final float ELEMENT_PAD = 5f;
    private static final float TOP_HEADER_PAD = 16f;
    private static final float FOOTER_BUTTON_PAD = 8f;
    private static final float FOOTER_BOTTOM_PAD = 24f;
    private static final Color TAB_ACTIVE_COLOR = Color.GOLD;
    private static final Color TAB_INACTIVE_COLOR = Color.WHITE;

    private final Listener listener;
    private final Table gameplayContent = new Table();
    private final Table visualContent = new Table();
    private final Table betaContent = new Table();
    private final Table deviceContent = new Table();
    private final Table tabContent = new Table();
    private final TextButton gameplayTabButton;
    private final TextButton visualTabButton;
    private final TextButton betaTabButton;
    private final TextButton deviceTabButton;
    private TextButton deviceButton;
    private TextButton layoutButton;

    public PauseDialog(Skin skin, Listener listener) {
        super("Paused", skin);
        this.listener = listener;

        getTitleLabel().setFontScale(SmallFontScale);
        getContentTable().top().defaults().pad(CONTENT_PAD);

        buildGameplayTab();
        buildVisualTab();
        buildBetaTab();
        buildDeviceTab();

        gameplayTabButton = tabButton("Gameplay", Tab.GAMEPLAY);
        visualTabButton = tabButton("Visual", Tab.VISUAL);
        betaTabButton = tabButton("Beta", Tab.BETA);
        deviceTabButton = tabButton("Device", Tab.DEVICE);

        Table tabHeader = new Table();
        tabHeader.defaults().pad(ELEMENT_PAD).minWidth(TAB_BUTTON_MIN_WIDTH);
        tabHeader.add(gameplayTabButton);
        tabHeader.add(visualTabButton);
        tabHeader.add(betaTabButton);
        tabHeader.add(deviceTabButton);
        getContentTable().add(tabHeader).top().padTop(TOP_HEADER_PAD).padBottom(ELEMENT_PAD).row();

        tabContent.top();
        getContentTable().add(tabContent).top().expandX().fillX().row();

        selectTab(Tab.GAMEPLAY);

        getButtonTable().padBottom(FOOTER_BOTTOM_PAD);
        getButtonTable().defaults().pad(FOOTER_BUTTON_PAD).size(FOOTER_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT);
        button(actionButton("Resume", this::hideAndResume));
        button(actionButton("Menu", listener::onExit));
    }

    @Override
    protected void result(Object object) {
        // Fires for every close path (button clicks land here too via Dialog's own handling),
        // mirroring the original behavior of unpausing no matter how the dialog goes away.
        listener.onResume();
    }

    private void hideAndResume() {
        hide();
        listener.onResume();
    }

    private void buildGameplayTab() {
        gameplayContent.top().defaults().pad(ELEMENT_PAD).left();
        Label triesLabel = new Label("Tries remaining: " + listener.getTriesRemaining(), getSkin());
        triesLabel.setFontScale(SmallFontScale);
        gameplayContent.add(triesLabel).colspan(2).left().row();

        CheckBox musicBox = toggleCheckBox("Music",
            AudioManager.get()::isMusicEnabled,
            AudioManager.get()::setMusicEnabled);
        CheckBox sfxBox = toggleCheckBox("Sound Effects",
            AudioManager.get()::isSfxEnabled,
            AudioManager.get()::setSfxEnabled);

        gameplayContent.add(musicBox).left();
        gameplayContent.add(sfxBox).left().row();

        // Closing first matters: the capture is flushed at the end of this same frame, and a dialog
        // still on stage would be drawn into the shot. hideAndResume() also drops the pause flag,
        // so the shot catches the game running again rather than frozen.
        gameplayContent.add(actionButton("Screenshot", () -> {
            hideAndResume();
            listener.onScreenshot();
        })).colspan(2).left().row();
    }

    private void buildVisualTab() {
        visualContent.top().defaults().pad(ELEMENT_PAD).left();

        CheckBox vignetteBox = toggleCheckBox("Vignette",
            FeatureFlags::isVignetteEnabled,
            FeatureFlags::setVignetteEnabled);
        CheckBox combatFxBox = toggleCheckBox("Combat FX",
            FeatureFlags::isSlashArcEnabled,
            FeatureFlags::setSlashArcEnabled);
        CheckBox visualFxBox = toggleCheckBox("Visual FX",
            FeatureFlags::isVisualEffectsEnabled,
            FeatureFlags::setVisualEffectsEnabled);
        CheckBox embersBox = toggleCheckBox("Embers",
            FeatureFlags::isEmbersEnabled,
            FeatureFlags::setEmbersEnabled);
        CheckBox softStopBox = toggleCheckBox("Soft Stop",
            FeatureFlags::isSoftStopEnabled,
            FeatureFlags::setSoftStopEnabled);

        visualContent.add(vignetteBox).left();
        visualContent.add(combatFxBox).left().row();
        visualContent.add(visualFxBox).left();
        visualContent.add(embersBox).left().row();
        visualContent.add(softStopBox).left().row();
    }

    private void buildBetaTab() {
        betaContent.top().defaults().pad(ELEMENT_PAD).left();

        CheckBox godModeBox = toggleCheckBox("God Mode",
            FeatureFlags::isGodModeEnabled,
            FeatureFlags::setGodModeEnabled);
        CheckBox wallClimbBox = toggleCheckBox("Wall Climb",
            FeatureFlags::isWallClimbingEnabled,
            FeatureFlags::setWallClimbingEnabled);
        CheckBox potionStackBox = toggleCheckBox("Potion Stacking",
            FeatureFlags::isPotionStackingEnabled,
            FeatureFlags::setPotionStackingEnabled);
        CheckBox collisionDebugBox = toggleCheckBox("Collision Debug",
            DebugRenderSystem::isDebugEnabled,
            DebugRenderSystem::setDebugEnabled);
        CheckBox touchDebugBox = toggleCheckBox("Touch Debug",
            listener::isTouchDebugOn,
            listener::setTouchDebugOn);

        betaContent.add(godModeBox).left();
        betaContent.add(wallClimbBox).left().row();
        betaContent.add(potionStackBox).left();
        betaContent.add(collisionDebugBox).left().row();
        betaContent.add(touchDebugBox).left().row();
    }

    private void buildDeviceTab() {
        deviceContent.top().defaults().pad(ELEMENT_PAD);

        Label deviceTitle = new Label("Device", getSkin());
        deviceTitle.setFontScale(SmallFontScale);

        Label layoutTitle = new Label("Mobile", getSkin());
        layoutTitle.setFontScale(SmallFontScale);

        deviceButton = new TextButton(listener.deviceLabel(), getSkin());
        deviceButton.getLabel().setFontScale(SmallFontScale);
        deviceButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                listener.cycleDevice();
                refreshDeviceLayoutLabels();
            }
        });

        layoutButton = new TextButton(listener.layoutLabel(), getSkin());
        layoutButton.getLabel().setFontScale(SmallFontScale);
        layoutButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                listener.cycleLayout();
                refreshDeviceLayoutLabels();
            }
        });

        deviceContent.add(deviceTitle);
        deviceContent.add(layoutTitle).row();
        deviceContent.add(deviceButton).minWidth(SIMULATION_BUTTON_MIN_WIDTH);
        deviceContent.add(layoutButton).minWidth(SIMULATION_BUTTON_MIN_WIDTH).row();
    }

    private TextButton tabButton(String label, Tab tab) {
        return actionButton(label, () -> selectTab(tab));
    }

    private void selectTab(Tab tab) {
        tabContent.clearChildren();
        Table selectedTable;
        switch (tab) {
            case VISUAL:
                selectedTable = visualContent;
                break;
            case BETA:
                selectedTable = betaContent;
                break;
            case DEVICE:
                selectedTable = deviceContent;
                break;
            case GAMEPLAY:
            default:
                selectedTable = gameplayContent;
                break;
        }
        tabContent.add(selectedTable).top();
        gameplayTabButton.getLabel().setColor(tab == Tab.GAMEPLAY ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
        visualTabButton.getLabel().setColor(tab == Tab.VISUAL ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
        betaTabButton.getLabel().setColor(tab == Tab.BETA ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
        deviceTabButton.getLabel().setColor(tab == Tab.DEVICE ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
    }

    private void refreshDeviceLayoutLabels() {
        deviceButton.setText(listener.deviceLabel());
        layoutButton.setText(listener.layoutLabel());
    }

    private TextButton actionButton(String text, Runnable onClick) {
        TextButton button = new TextButton(text, getSkin());
        button.getLabel().setFontScale(SmallFontScale);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                onClick.run();
            }
        });
        return button;
    }

    private CheckBox toggleCheckBox(String labelText, BooleanGetter getter, BooleanSetter setter) {
        CheckBox checkBox = new CheckBox(" " + labelText, getSkin());
        checkBox.getLabel().setFontScale(SmallFontScale);
        checkBox.setChecked(getter.get());
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                setter.set(checkBox.isChecked());
            }
        });
        return checkBox;
    }
}
