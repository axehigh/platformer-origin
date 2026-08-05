package com.axehigh.platformer.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * {@link ImageButton} for the mobile touch overlay. Uses the "gameplay" skin style with a per-button
 * drawable, scales down while pressed, and delegates press/release to a {@link Handler}.
 */
public class TouchButton extends ImageButton {

    /** Callbacks for the button's press and release states. */
    public interface Handler {
        void onPress();

        default void onRelease() {
        }
    }

    public TouchButton(Skin skin, String drawableName, float pressedScale, float scaleDuration, Handler handler) {
        super(new ImageButtonStyle(skin.get("gameplay", ImageButtonStyle.class)));
        ImageButtonStyle style = getStyle();
        style.imageUp = skin.getDrawable(drawableName);
        style.imageDown = skin.getDrawable(drawableName);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.getListenerActor().addAction(Actions.scaleTo(pressedScale, pressedScale, scaleDuration));
                handler.onPress();
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                event.getListenerActor().addAction(Actions.scaleTo(1f, 1f, scaleDuration));
                handler.onRelease();
            }
        });
    }
}
