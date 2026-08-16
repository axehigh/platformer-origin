package com.axehigh.platformer.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import static com.axehigh.platformer.GameConstants.UI_TOUCH_HIT_PAD;

/**
 * {@link ImageButton} for the mobile touch overlay. Uses the "gameplay" skin style with a per-button
 * drawable, scales down while pressed, and delegates press/release to a {@link Handler}. The hit
 * area extends {@code UI_TOUCH_HIT_PAD} beyond the drawn bounds so the touch target is fatter than
 * the visible button, without any visual or reserved-band change.
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

    /**
     * Swaps the button's image drawable (up and down states) at runtime, e.g. to reflect the
     * currently selected potion type.
     */
    public void setDrawable(Drawable drawable) {
        ImageButtonStyle style = getStyle();
        style.imageUp = drawable;
        style.imageDown = drawable;
    }

    /**
     * Treats the whole button (plus {@code UI_TOUCH_HIT_PAD} on each side) as a hit target, so
     * taps just outside the drawn button still register.
     */
    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (touchable && getTouchable() != Touchable.enabled) {
            return null;
        }
        float pad = UI_TOUCH_HIT_PAD;
        if (x >= -pad && x <= getWidth() + pad && y >= -pad && y <= getHeight() + pad) {
            return this;
        }
        return null;
    }
}
