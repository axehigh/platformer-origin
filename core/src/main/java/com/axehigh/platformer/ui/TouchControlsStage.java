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
    private final TouchButton leftButton;
    private final TouchButton rightButton;
    private final TouchButton interactButton;
    private final TouchButton dropButton;
    private final TouchButton inventoryButton;
    private final TouchButton shootButton;
    private final TouchButton aButton;
    private final TouchButton bButton;
    private final TouchButton yButton;

    /**
     * The overlay alpha set by the current {@code LayoutMode} (transparent overlay vs solid band).
     * The tutorial highlight temporarily overrides it with {@link GameConstants#UI_BUTTON_ALPHA_SOLID}
     * while active, and this field restores it when cleared.
     */
    private float baseAlpha = UI_BUTTON_ALPHA;

    public TouchControlsStage(Viewport viewport, Skin skin, PlayerInputSystem inputSystem,
                              Drawable inventoryIcon, Runnable onInventoryToggle) {
        super(viewport);

        root = new Table();
        root.setFillParent(true);
        root.bottom();
        root.getColor().a = UI_BUTTON_ALPHA;
        addActor(root);

        Table dpad = new Table();
        leftButton = new TouchButton(skin, "left", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
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
        rightButton = new TouchButton(skin, "right", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
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
        inventoryButton = new TouchButton(skin, "potion", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                new TouchButton.Handler() {
                    @Override
                    public void onPress() {
                        onInventoryToggle.run();
                    }
                });
        inventoryButton.setDrawable(inventoryIcon);

        dpad.add(leftButton).size(UI_Button_Move_Size, UI_Button_Move_Size).padRight(UI_Button_Move_Size * 0.5f);
        dpad.add(rightButton).size(UI_Button_Move_Size, UI_Button_Move_Size).padRight(UI_PADDING_TOUCH * 2f);
        dpad.add(inventoryButton).size(UI_Button_Contextual_Size, UI_Button_Contextual_Size);

        yButton = new TouchButton(skin, "daggers", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchShoot());
        shootButton = yButton;
        yButton.setVisible(USE_BULLET);
        bButton = new TouchButton(skin, "sword", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchMelee());
        aButton = new TouchButton(skin, "jump", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchJump());

        interactButton = new TouchButton(skin, "door", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchInteract());
        interactButton.setVisible(false);

        dropButton = new TouchButton(skin, "down", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                () -> inputSystem.requestTouchDrop());
        dropButton.setVisible(false);

        Table actions = new Table();
        actions.defaults().bottom();
        actions.add(dropButton).size(UI_Button_Contextual_Size, UI_Button_Contextual_Size).padLeft(UI_PADDING_TOUCH * 2f).padRight(UI_PADDING_TOUCH * 2f);
        actions.add(yButton).size(UI_Button_Action_Size, UI_Button_Action_Size).padLeft(UI_PADDING_TOUCH * 2f).padRight(UI_PADDING_TOUCH * 2f);
        actions.add(bButton).size(UI_Button_Action_Size, UI_Button_Action_Size).padLeft(UI_PADDING_TOUCH * 2f).padRight(UI_PADDING_TOUCH * 2f);
        
        Table jumpCol = new Table();
        jumpCol.defaults().spaceBottom(UI_PADDING_TOUCH);
        jumpCol.add(interactButton).size(UI_Button_Contextual_Size, UI_Button_Contextual_Size).row();
        jumpCol.add(aButton).size(UI_Button_Jump_Size, UI_Button_Jump_Size);
        
        actions.add(jumpCol).padLeft(UI_PADDING_TOUCH * 2f).padRight(UI_PADDING_TOUCH * 2f);

        root.add(dpad).expandX().left().bottom().padLeft(UI_BOTTOM_PAD * 4f).pad(UI_BOTTOM_PAD);
        root.add(actions).expandX().right().bottom().padRight(UI_BOTTOM_PAD * 4f).pad(UI_BOTTOM_PAD);
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
        this.baseAlpha = alpha;
        root.getColor().a = alpha;
    }

    /**
     * Returns the current cluster opacity — either the {@link #setAlpha(float)} base or the
     * highlight-boosted {@link GameConstants#UI_BUTTON_ALPHA_SOLID} value while active.
     */
    public float getAlpha() {
        return root.getColor().a;
    }

    /**
     * Shows/hides the inventory (bag) button, e.g. while the inventory is open.
     */
    public void setInventoryVisible(boolean visible) {
        inventoryButton.setVisible(visible);
    }

    /** Fades the inventory (bag) button when the player has no potions. */
    public void setInventoryAlpha(float alpha) {
        inventoryButton.getColor().a = alpha;
    }

    /** Fades the shoot (daggers/Y) button when the player has no ammo. */
    public void setShootAlpha(float alpha) {
        shootButton.getColor().a = alpha;
    }

    /**
     * Maps a tutorial highlight keyword (e.g. "jump", "a", "sword") to the matching touch-button
     * skin drawable name (e.g. "jump", "sword", "daggers"). Returns {@code null} for unknown
     * keywords; used by both this class and {@link TutorialSystem} for the inline tooltip icon.
     */
    public static String iconNameFor(String target) {
        if (target == null) return null;
        String t = target.toLowerCase().trim();
        if (t.equals("jump") || t.equals("a") || t.equals("j")) return "jump";
        if (t.equals("attack") || t.equals("sword") || t.equals("b") || t.equals("melee")) return "sword";
        if (t.equals("special") || t.equals("ranged") || t.equals("y") || t.equals("dagger") || t.equals("throw")) return "daggers";
        if (t.equals("inventory") || t.equals("bag") || t.equals("potion")) return "potion";
        if (t.equals("left")) return "left";
        if (t.equals("right")) return "right";
        return null;
    }

    private TouchButton buttonForIcon(String icon) {
        switch (icon) {
            case "jump":    return aButton;
            case "sword":   return bButton;
            case "daggers": return yButton;
            case "potion":  return inventoryButton;
            case "left":    return leftButton;
            case "right":   return rightButton;
            default:        return null;
        }
    }

    /** Highlights or resets touch control buttons based on tutorial/prompt state with pulsing glow. */
    public void setHighlightedControl(String target, float timer) {
        aButton.clearHighlightColor();
        bButton.clearHighlightColor();
        yButton.clearHighlightColor();
        inventoryButton.clearHighlightColor();
        leftButton.clearHighlightColor();
        rightButton.clearHighlightColor();

        String icon = iconNameFor(target);
        if (icon != null) {
            TouchButton highlight = buttonForIcon(icon);
            if (highlight != null) {
                float pulse = 0.75f + 0.25f * (float) Math.sin(timer * 8.0);
                highlight.setHighlightColor(pulse, pulse, 0.2f);
            }
        }

        // Boost overlay alpha while a highlight is active so the pulsing icon is visible at a
        // glance; restore the mode-set base alpha when no highlight is active.
        root.getColor().a = (icon != null) ? UI_BUTTON_ALPHA_SOLID : baseAlpha;
    }
}
