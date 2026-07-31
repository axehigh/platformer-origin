package com.axehigh.platformer.ui;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.systems.PlayerInputSystem;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.GameConstants.*;

/**
 * Mobile touch overlay: bottom-left D-pad (left/right), bottom-right A/B/Y buttons.
 * All buttons drive the same {@link PlayerInputSystem} handlers used by the keyboard.
 */
public class TouchControlsStage extends Stage {
    private final TextButton interactButton;

    public TouchControlsStage(Viewport viewport, Skin skin, PlayerInputSystem inputSystem) {
        super(viewport);

        Table root = new Table();
        root.setFillParent(true);
        root.bottom();
        root.getColor().a = GameConstants.UI_BUTTON_ALPHA;
        addActor(root);

        Table dpad = new Table();
        TextButton leftButton = new TextButton("<", skin);
        TextButton rightButton = new TextButton(">", skin);
        leftButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.setTouchLeft(true);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.setTouchLeft(false);
            }
        });
        rightButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.setTouchRight(true);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.setTouchRight(false);
            }
        });
        dpad.add(leftButton).size(UI_Button_Move_Size, UI_Button_Move_Size).padRight(UI_Button_Move_Size);
        dpad.add(rightButton).size(UI_Button_Move_Size, UI_Button_Move_Size);

        Table actions = new Table();
        TextButton yButton = new TextButton("Y", skin);
        TextButton bButton = new TextButton("B", skin);
        TextButton aButton = new TextButton("A", skin);
        yButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.requestTouchShoot();
                return true;
            }
        });
        bButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.requestTouchMelee();
                return true;
            }
        });
        aButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.requestTouchJump();
                return true;
            }
        });
        actions.add(yButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        actions.row();
        actions.add(bButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);
        actions.add(aButton).size(UI_Button_Action_Size, UI_Button_Action_Size).pad(UI_PADDING);

        interactButton = new TextButton("^", skin);
        interactButton.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                inputSystem.requestTouchInteract();
                return true;
            }
        });
        interactButton.setVisible(false);

        root.add(interactButton).colspan(2).size(UI_Button_Action_Size, UI_Button_Action_Size).padBottom(6f);
        root.row();
        root.add(dpad).expandX().left().bottom().pad(20f);
        root.add(actions).expandX().right().pad(20f);
    }

    /**
     * Shows/hides the contextual interact button, e.g. while the player is near an exit gate.
     */
    public void setInteractVisible(boolean visible) {
        interactButton.setVisible(visible);
    }
}
