package com.axehigh.platformer.ui;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.ecs.systems.DebugRenderSystem;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * In-game pause menu, split into a Gameplay tab (audio toggles, wall-climb feature flag) and a
 * Debug tab (collision/touch debug toggles, device/layout simulation). Resume and Exit stay
 * outside the tabs side-by-side so they're reachable from either. Checkbox toggles flip settings
 * directly; state owned by the screen (pause flag, touch-debug logging, device/layout switching)
 * is reached through {@link Listener}.
 */
public class PauseDialog extends Dialog {

    /** Which content page the tab header shows. */
    private enum Tab {
        GAMEPLAY,
        DEBUG
    }

    /** Callbacks into the owning screen for state the dialog must read or mutate. */
    public interface Listener {
        /** Called when the dialog closes by any path (Resume button, Exit button, ESC/Enter). */
        void onResume();

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

    private static final float TAB_BUTTON_MIN_WIDTH = 130f;
    private static final float ACTION_BUTTON_MIN_WIDTH = 110f;
    private static final float SIMULATION_BUTTON_MIN_WIDTH = 130f;
    private static final float CONTENT_PAD = 8f;
    private static final float ELEMENT_PAD = 5f;
    private static final Color TAB_ACTIVE_COLOR = Color.GOLD;
    private static final Color TAB_INACTIVE_COLOR = Color.WHITE;

    private final Listener listener;
    private final Table gameplayContent = new Table();
    private final Table debugContent = new Table();
    private final Table tabContent = new Table();
    private final TextButton gameplayTabButton;
    private final TextButton debugTabButton;
    private TextButton deviceButton;
    private TextButton layoutButton;

    public PauseDialog(Skin skin, Listener listener) {
        super("Paused", skin);
        this.listener = listener;

        getTitleLabel().setFontScale(FontScale);
        getContentTable().defaults().pad(CONTENT_PAD);
        getButtonTable().defaults().pad(CONTENT_PAD);

        buildGameplayTab();
        buildDebugTab();

        gameplayTabButton = tabButton("Gameplay", Tab.GAMEPLAY);
        debugTabButton = tabButton("Debug", Tab.DEBUG);
        Table tabHeader = new Table();
        tabHeader.add(gameplayTabButton).minWidth(TAB_BUTTON_MIN_WIDTH).padRight(ELEMENT_PAD);
        tabHeader.add(debugTabButton).minWidth(TAB_BUTTON_MIN_WIDTH);
        getContentTable().add(tabHeader).row();

        getContentTable().add(tabContent).row();

        selectTab(Tab.GAMEPLAY);

        getButtonTable().defaults().pad(ELEMENT_PAD).minWidth(ACTION_BUTTON_MIN_WIDTH);
        button(actionButton("Resume", this::hideAndResume));
        button(actionButton("Exit", listener::onExit));
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
        gameplayContent.defaults().pad(ELEMENT_PAD).left();
        Label triesLabel = new Label("Tries remaining: " + listener.getTriesRemaining(), getSkin());
        triesLabel.setFontScale(FontScale);
        gameplayContent.add(triesLabel).row();

        gameplayContent.add(toggleCheckBox("Music",
            AudioManager.get()::isMusicEnabled,
            AudioManager.get()::setMusicEnabled)).row();

        gameplayContent.add(toggleCheckBox("Sound Effects",
            AudioManager.get()::isSfxEnabled,
            AudioManager.get()::setSfxEnabled)).row();

        gameplayContent.add(toggleCheckBox("Wall Climb",
            FeatureFlags::isWallClimbingEnabled,
            FeatureFlags::setWallClimbingEnabled)).row();
    }

    private void buildDebugTab() {
        debugContent.defaults().pad(ELEMENT_PAD);

        CheckBox collisionDebugBox = toggleCheckBox("Collision Debug",
            DebugRenderSystem::isDebugEnabled,
            DebugRenderSystem::setDebugEnabled);
        CheckBox touchDebugBox = toggleCheckBox("Touch Debug",
            listener::isTouchDebugOn,
            listener::setTouchDebugOn);

        Table debugRow = new Table();
        debugRow.defaults().pad(ELEMENT_PAD);
        debugRow.add(collisionDebugBox).left();
        debugRow.add(touchDebugBox).left();
        debugContent.add(debugRow).left().row();

        deviceButton = new TextButton("Device: " + listener.deviceLabel(), getSkin());
        deviceButton.getLabel().setFontScale(FontScale);
        deviceButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                listener.cycleDevice();
                refreshDeviceLayoutLabels();
            }
        });

        layoutButton = new TextButton("Mobile: " + listener.layoutLabel(), getSkin());
        layoutButton.getLabel().setFontScale(FontScale);
        layoutButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                listener.cycleLayout();
                refreshDeviceLayoutLabels();
            }
        });

        Table simulationRow = new Table();
        simulationRow.defaults().pad(ELEMENT_PAD);
        simulationRow.add(deviceButton).minWidth(SIMULATION_BUTTON_MIN_WIDTH);
        simulationRow.add(layoutButton).minWidth(SIMULATION_BUTTON_MIN_WIDTH);
        debugContent.add(simulationRow).row();
    }

    private TextButton tabButton(String label, Tab tab) {
        return actionButton(label, () -> selectTab(tab));
    }

    private void selectTab(Tab tab) {
        tabContent.clearChildren();
        tabContent.add(tab == Tab.GAMEPLAY ? gameplayContent : debugContent);
        gameplayTabButton.getLabel().setColor(tab == Tab.GAMEPLAY ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
        debugTabButton.getLabel().setColor(tab == Tab.DEBUG ? TAB_ACTIVE_COLOR : TAB_INACTIVE_COLOR);
    }

    private void refreshDeviceLayoutLabels() {
        deviceButton.setText("Device: " + listener.deviceLabel());
        layoutButton.setText("Mobile: " + listener.layoutLabel());
    }

    private TextButton actionButton(String text, Runnable onClick) {
        TextButton button = new TextButton(text, getSkin());
        button.getLabel().setFontScale(FontScale);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                onClick.run();
            }
        });
        return button;
    }

    private CheckBox toggleCheckBox(String labelText, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        CheckBox checkBox = new CheckBox(" " + labelText, getSkin());
        checkBox.getLabel().setFontScale(FontScale);
        checkBox.setChecked(getter.get());
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                setter.accept(checkBox.isChecked());
            }
        });
        return checkBox;
    }
}
