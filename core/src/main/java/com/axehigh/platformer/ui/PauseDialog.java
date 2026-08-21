package com.axehigh.platformer.ui;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.ecs.systems.DebugRenderSystem;
import com.axehigh.platformer.util.FeatureFlags;
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
 * In-game pause menu: audio toggles, debug toggles, device/layout simulation, and exit.
 * Global toggles (music, SFX, collision debug, wall climb) are flipped directly; state owned
 * by the screen (pause flag, touch-debug logging, device/layout switching) is reached through
 * {@link Listener}.
 */
public class PauseDialog extends Dialog {

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

    private final Listener listener;
    private final TextButton deviceButton;
    private final TextButton layoutButton;

    public PauseDialog(Skin skin, Listener listener) {
        super("Paused", skin);
        this.listener = listener;

        getTitleLabel().setFontScale(FontScale);
        getContentTable().defaults().pad(UI_PADDING);
        getButtonTable().defaults().pad(UI_PADDING);

        button(actionButton("Resume", this::hideAndResume));

        getContentTable().add(toggleButton("Music: ",
            AudioManager.get()::isMusicEnabled,
            enabled -> AudioManager.get().setMusicEnabled(enabled))).minWidth(BUTTON_MIN_WIDTH).pad(UI_PADDING).row();

        getContentTable().add(toggleButton("Sound Effects: ",
            AudioManager.get()::isSfxEnabled,
            enabled -> AudioManager.get().setSfxEnabled(enabled))).minWidth(BUTTON_MIN_WIDTH).pad(UI_PADDING).row();

        TextButton collisionDebugButton = toggleButton("Collision Debug: ",
            DebugRenderSystem::isDebugEnabled,
            DebugRenderSystem::setDebugEnabled);
        TextButton touchDebugButton = toggleButton("Touch Debug: ",
            listener::isTouchDebugOn,
            listener::setTouchDebugOn);

        Table debugRow = new Table();
        debugRow.add(collisionDebugButton).minWidth(BUTTON_MIN_WIDTH).padRight(UI_PADDING);
        debugRow.add(touchDebugButton).minWidth(BUTTON_MIN_WIDTH);
        getContentTable().add(debugRow).pad(UI_PADDING).row();

        TextButton wallClimbButton = toggleButton("Wall Climb: ",
            FeatureFlags::isWallClimbingEnabled,
            FeatureFlags::setWallClimbingEnabled);

        deviceButton = new TextButton("Device: " + listener.deviceLabel(), skin);
        deviceButton.getLabel().setFontScale(FontScale);
        deviceButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                listener.cycleDevice();
                refreshDeviceLayoutLabels();
            }
        });

        layoutButton = new TextButton("Mobile Layout: " + listener.layoutLabel(), skin);
        layoutButton.getLabel().setFontScale(FontScale);
        layoutButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                listener.cycleLayout();
                refreshDeviceLayoutLabels();
            }
        });

        Table featureRow = new Table();
        featureRow.add(wallClimbButton).minWidth(BUTTON_MIN_WIDTH).padRight(UI_PADDING);
        featureRow.add(deviceButton).minWidth(BUTTON_MIN_WIDTH).padRight(UI_PADDING);
        featureRow.add(layoutButton).minWidth(BUTTON_MIN_WIDTH);
        getContentTable().add(featureRow).pad(UI_PADDING).row();

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
