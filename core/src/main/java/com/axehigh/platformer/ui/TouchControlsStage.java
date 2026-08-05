package com.axehigh.platformer.ui;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.systems.PlayerInputSystem;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.GameConstants.*;

/**
 * Mobile touch overlay: bottom-left D-pad (left/right), bottom-right A/B/Y buttons.
 * All buttons drive the same {@link PlayerInputSystem} handlers used by the keyboard.
 */
public class TouchControlsStage extends Stage {
    private final TouchButton interactButton;
    private final TouchButton dropButton;

    public TouchControlsStage(Viewport viewport, Skin skin, PlayerInputSystem inputSystem) {
        super(viewport);

        Table root = new Table();
        root.setFillParent(true);
        root.bottom();
        root.getColor().a = GameConstants.UI_BUTTON_ALPHA;
        addActor(root);

        Table dpad = new Table();
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

        Table actions = new Table();
        TouchButton yButton = new TouchButton(skin, "flatFlame", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchShoot());
        TouchButton bButton = new TouchButton(skin, "flatAttack", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchMelee());
        TouchButton aButton = new TouchButton(skin, "flatUp", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchJump());
        actions.add(yButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        actions.add(bButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        actions.row();
        actions.add().size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        actions.add(aButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);

        interactButton = new TouchButton(skin, "flatFly", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchInteract());
        interactButton.setVisible(false);

        dropButton = new TouchButton(skin, "flatDown", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchDrop());
        dropButton.setVisible(false);

        Table contextual = new Table();
        contextual.add().size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        contextual.add(interactButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        contextual.row();
        contextual.add().size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        contextual.add(dropButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        contextual.row();
        contextual.add(actions).colspan(2);

        root.add(dpad).expandX().left().bottom().pad(83f);
        root.add(contextual).expandX().right().bottom().pad(83f);
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
}
