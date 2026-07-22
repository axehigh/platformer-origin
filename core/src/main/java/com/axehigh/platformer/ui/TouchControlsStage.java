package com.axehigh.platformer.ui;

import com.axehigh.platformer.ecs.systems.PlayerInputSystem;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.Viewport;

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
        dpad.add(leftButton).size(24f, 24f).padRight(6f);
        dpad.add(rightButton).size(24f, 24f);

        Table actions = new Table();
        TextButton yButton = new TextButton("Y", skin);
        TextButton bButton = new TextButton("B", skin);
        TextButton aButton = new TextButton("A", skin);
        yButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                inputSystem.requestTouchShoot();
            }
        });
        bButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                inputSystem.requestTouchMelee();
            }
        });
        aButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                inputSystem.requestTouchJump();
            }
        });
        actions.add(yButton).size(20f, 20f).pad(2f);
        actions.row();
        actions.add(bButton).size(20f, 20f).pad(2f);
        actions.add(aButton).size(20f, 20f).pad(2f);

        interactButton = new TextButton("^", skin);
        interactButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                inputSystem.requestTouchInteract();
            }
        });
        interactButton.setVisible(false);

        root.add(interactButton).colspan(2).size(20f, 20f).padBottom(6f);
        root.row();
        root.add(dpad).expandX().left().pad(10f);
        root.add(actions).expandX().right().pad(10f);
    }

    /** Shows/hides the contextual interact button, e.g. while the player is near an exit gate. */
    public void setInteractVisible(boolean visible) {
        interactButton.setVisible(visible);
    }
}
