package com.axehigh.platformer.ui;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.ecs.systems.DebugRenderSystem;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.axehigh.platformer.GameConstants.FontScale;
import static com.axehigh.platformer.GameConstants.UI_PADDING;

/**
 * In-game pause menu, split into a Gameplay tab (audio toggles, wall-climb feature flag) and a
 * Debug tab (collision/touch debug toggles, device/layout simulation). Resume and Exit stay
 * outside the tabs so they're reachable from either. Global toggles (music, SFX, collision debug,
 * wall climb) are flipped directly; state owned by the screen (pause flag, touch-debug logging,
 * device/layout switching) is reached through {@link Listener}.
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
    }

    private static final float BUTTON_MIN_WIDTH = 240f;
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
        getContentTable().defaults().pad(UI_PADDING);
        getButtonTable().defaults().pad(UI_PADDING);

        button(actionButton("Resume", this::hideAndResume));

        buildGameplayTab();
        buildDebugTab();

        gameplayTabButton = tabButton("Gameplay", Tab.GAMEPLAY);
        debugTabButton = tabButton("Debug", Tab.DEBUG);
        Table tabHeader = new Table();
        tabHeader.add(gameplayTabButton).minWidth(BUTTON_MIN_WIDTH).padRight(UI_PADDING);
        tabHeader.add(debugTabButton).minWidth(BUTTON_MIN_WIDTH);
        getContentTable().add(tabHeader).row();

        getContentTable().add(tabContent).row();

        selectTab(Tab.GAMEPLAY);

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
        gameplayContent.defaults().pad(UI_PADDING);
        gameplayContent.add(toggleButton("Music: ",
            AudioManager.get()::isMusicEnabled,
            enabled -> AudioManager.get().setMusicEnabled(enabled))).minWidth(BUTTON_MIN_WIDTH).row();

        gameplayContent.add(toggleButton("Sound Effects: ",
            AudioManager.get()::isSfxEnabled,
            enabled -> AudioManager.get().setSfxEnabled(enabled))).minWidth(BUTTON_MIN_WIDTH).row();

        gameplayContent.add(toggleButton("Wall Climb: ",
            FeatureFlags::isWallClimbingEnabled,
            FeatureFlags::setWallClimbingEnabled)).minWidth(BUTTON_MIN_WIDTH).row();
    }

    private void buildDebugTab() {
        debugContent.defaults().pad(UI_PADDING);

        TextButton collisionDebugButton = toggleButton("Collision Debug: ",
            DebugRenderSystem::isDebugEnabled,
            DebugRenderSystem::setDebugEnabled);
        TextButton touchDebugButton = toggleButton("Touch Debug: ",
            listener::isTouchDebugOn,
            listener::setTouchDebugOn);

        Table debugRow = new Table();
        debugRow.defaults().pad(UI_PADDING);
        debugRow.add(collisionDebugButton).minWidth(BUTTON_MIN_WIDTH);
        debugRow.add(touchDebugButton).minWidth(BUTTON_MIN_WIDTH);
        debugContent.add(debugRow).row();

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

        layoutButton = new TextButton("Mobile Layout: " + listener.layoutLabel(), getSkin());
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
        simulationRow.defaults().pad(UI_PADDING);
        simulationRow.add(deviceButton).minWidth(BUTTON_MIN_WIDTH);
        simulationRow.add(layoutButton).minWidth(BUTTON_MIN_WIDTH);
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
        layoutButton.setText("Mobile Layout: " + listener.layoutLabel());
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

    private TextButton toggleButton(String prefix, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        TextButton button = new TextButton(prefix + onOff(getter), getSkin());
        button.getLabel().setFontScale(FontScale);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                setter.accept(!getter.get());
                button.setText(prefix + onOff(getter));
            }
        });
        return button;
    }

    private static String onOff(Supplier<Boolean> getter) {
        return getter.get() ? "ON" : "OFF";
    }
}
