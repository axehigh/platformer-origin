package com.axehigh.platformer.ui;

import com.axehigh.platformer.ecs.systems.PlayerInputSystem;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.GameConstants.*;

/**
 * Mobile touch overlay: bottom-left D-pad (left/right), bottom-right A/B/Y buttons in a single
 * row (A, the jump, is largest and rightmost) with the inventory (backpack) and contextual
 * interact/drop buttons inline to their left. All buttons drive the same {@link PlayerInputSystem}
 * handlers used by the keyboard (the backpack toggles the pause-the-game potion hotbar via its
 * {@code onInventoryToggle} callback). The whole cluster is sized from {@code UI_CONTROL_BAND_HEIGHT}
 * so the reserved game-viewport band (see {@code OffsetFitViewport}) always clears it; each button's
 * touch target is fatter than its visuals via {@code UI_TOUCH_HIT_PAD} (see {@code TouchButton#hit}).
 */
public class TouchControlsStage extends Stage {
    private final Table root;
    private final TouchButton interactButton;
    private final TouchButton dropButton;

    public TouchControlsStage(Viewport viewport, Skin skin, PlayerInputSystem inputSystem,
                              Drawable inventoryIcon, Runnable onInventoryToggle) {
        super(viewport);

        root = new Table();
        root.setFillParent(true);
        root.bottom();
        root.getColor().a = UI_BUTTON_ALPHA;
        addActor(root);

        Table dpad = new Table();
        //dpad.setDebug(true);
        TouchButton leftButton = new TouchButton(skin, "flatLeft", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                new TouchButton.Handler() {
                    @Override
                    public void onPress() {
                        inputSystem.setTouchLeft(true);
                    }

                    @Override
                    public void onRelease() {
                        inputSystem.setTouchLeft(false);
                    }
                });
        TouchButton rightButton = new TouchButton(skin, "flatRight", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                new TouchButton.Handler() {
                    @Override
                    public void onPress() {
                        inputSystem.setTouchRight(true);
                    }

                    @Override
                    public void onRelease() {
                        inputSystem.setTouchRight(false);
                    }
                });
        dpad.add(leftButton).size(UI_Button_Move_Size, UI_Button_Move_Size).padRight(UI_Button_Move_Size);
        dpad.add(rightButton).size(UI_Button_Move_Size, UI_Button_Move_Size);

        TouchButton yButton = new TouchButton(skin, "flatFlame", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchShoot());
        TouchButton bButton = new TouchButton(skin, "flatAttack", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchMelee());
        TouchButton aButton = new TouchButton(skin, "flatUp", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchJump());

        interactButton = new TouchButton(skin, "flatFly", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchInteract());
        interactButton.setVisible(false);

        dropButton = new TouchButton(skin, "flatDown", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchDrop());
        dropButton.setVisible(false);

        TouchButton inventoryButton = new TouchButton(skin, "flatUp", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                new TouchButton.Handler() {
                    @Override
                    public void onPress() {
                        onInventoryToggle.run();
                    }
                });
        inventoryButton.setDrawable(inventoryIcon);

        Table actions = new Table();
        actions.add(inventoryButton).size(UI_Button_Contextual_Size, UI_Button_Contextual_Size).padLeft(UI_PADDING_TOUCH).padRight(UI_PADDING_TOUCH);
        actions.add(interactButton).size(UI_Button_Contextual_Size, UI_Button_Contextual_Size).padLeft(UI_PADDING_TOUCH).padRight(UI_PADDING_TOUCH);
        actions.add(dropButton).size(UI_Button_Contextual_Size, UI_Button_Contextual_Size).padLeft(UI_PADDING_TOUCH).padRight(UI_PADDING_TOUCH);
        actions.add(yButton).size(UI_Button_Action_Size, UI_Button_Action_Size).padLeft(UI_PADDING_TOUCH).padRight(UI_PADDING_TOUCH);
        actions.add(bButton).size(UI_Button_Action_Size, UI_Button_Action_Size).padLeft(UI_PADDING_TOUCH).padRight(UI_PADDING_TOUCH);
        actions.add(aButton).size(UI_Button_Jump_Size, UI_Button_Jump_Size).padLeft(UI_PADDING_TOUCH).padRight(UI_PADDING_TOUCH);

        root.add(dpad).expandX().left().bottom().pad(UI_BOTTOM_PAD);
        root.add(actions).expandX().right().bottom().pad(UI_BOTTOM_PAD);
    }

    /**
     * Shows/hides the contextual interact button, e.g. while the player is near an exit gate.
     */
    public void setInteractVisible(boolean visible) {
        interactButton.setVisible(visible);
    }

    /**
     * Shows/hides the contextual drop-through button, e.g. while the player is standing on a
     * drop-through platform.
     */
    public void setDropVisible(boolean visible) {
        dropButton.setVisible(visible);
    }

    /**
     * Blocks/enables touch input to the whole cluster (used to keep the overlay inert on desktop
     * when it isn't drawn).
     */
    public void setEnabled(boolean enabled) {
        root.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    }

    /** Sets the opacity of the whole cluster (per-mode: transparent overlay vs solid band). */
    public void setAlpha(float alpha) {
        root.getColor().a = alpha;
    }
}
