package com.axehigh.platformer.ui;

import com.axehigh.platformer.GameConstants;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * A {@link CheckBox} whose label is laid out before the checkbox image (text, then box),
 * instead of the default image-then-label order. The checkbox image can also be enlarged
 * relative to its skin-defined natural size via {@code boxScale}.
 */
public class LabelFirstCheckBox extends CheckBox {

    private static final float DEFAULT_BOX_SCALE = GameConstants.UI_CHECKBOX_SCALE;
    private static final float BOX_PAD_LEFT = 16f;

    public LabelFirstCheckBox(String text, Skin skin) {
        this(text, skin, DEFAULT_BOX_SCALE);
    }

    public LabelFirstCheckBox(String text, Skin skin, String styleName) {
        this(text, skin, styleName, DEFAULT_BOX_SCALE);
    }

    public LabelFirstCheckBox(String text, Skin skin, float boxScale) {
        super(text, skin);
        reorderLabelBeforeImage(boxScale);
    }

    public LabelFirstCheckBox(String text, Skin skin, String styleName, float boxScale) {
        super(text, skin, styleName);
        reorderLabelBeforeImage(boxScale);
    }

    private void reorderLabelBeforeImage(float boxScale) {
        float boxWidth = getImage().getDrawable().getMinWidth() * boxScale;
        float boxHeight = getImage().getDrawable().getMinHeight() * boxScale;

        clearChildren();
        add(getLabel());
        add(getImage()).size(boxWidth, boxHeight).padLeft(BOX_PAD_LEFT);
        pack();
    }
}
