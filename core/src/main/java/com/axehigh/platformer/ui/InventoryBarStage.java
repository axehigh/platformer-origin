package com.axehigh.platformer.ui;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.axehigh.platformer.util.PotionEffects;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.GameConstants.FontScale;
import static com.axehigh.platformer.GameConstants.UI_BOTTOM_PAD;
import static com.axehigh.platformer.GameConstants.UI_BUTTON_PRESS_SCALE;
import static com.axehigh.platformer.GameConstants.UI_BUTTON_SCALE_DURATION;
import static com.axehigh.platformer.GameConstants.UI_PADDING_TOUCH;
import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_UI_GFX;

/**
 * Pause-the-game potion hotbar: a horizontal row of one slot per {@link PotionType}, each showing
 * the potion's icon plus the held count. Tapping an enabled slot drinks one potion of that type
 * (healing restores a heart, the buffs start their timed effect). Slots are disabled while the
 * count is zero, while the drink cooldown is active, or for healing when health is full. The bar is
 * drawn on top of the touch controls and opens/closes via {@link #setOpen} — the game keeps
 * running while it is open.
 */
public class InventoryBarStage extends Stage {
    private static final float SLOT_SIZE = 130f;
    private static final float SLOT_PAD = 16f;
    private static final float COUNT_PAD = 8f;
    private static final float ICON_SIZE = 72f;

    private final PlayerComponent playerComponent;
    private final Entity playerEntity;
    private final ObjectMap<PotionType, Slot> slots = new ObjectMap<>();
    private final Table root;

    private boolean open = false;
    private Runnable onTapOutside;

    public InventoryBarStage(Viewport viewport, Skin skin, AssetManager assetManager,
                             PlayerComponent playerComponent, Entity playerEntity) {
        super(viewport);
        this.playerComponent = playerComponent;
        this.playerEntity = playerEntity;

        LabelStyle counterStyle = skin.get(LabelStyle.class);

        root = new Table();
        root.setFillParent(true);
        root.bottom();
        root.setVisible(false);
        addActor(root);

        TextureAtlas uiAtlas = assetManager.get(ORIGIN_UI_GFX, TextureAtlas.class);
        for (PotionType type : PotionType.values()) {
            TouchButton drinkButton = new TouchButton(skin, "flatUp", UI_BUTTON_PRESS_SCALE, UI_BUTTON_SCALE_DURATION,
                    () -> drink(type));
            TextureRegionDrawable icon = new TextureRegionDrawable(uiAtlas.findRegion(type.regionName()));
            icon.setMinWidth(ICON_SIZE);
            icon.setMinHeight(ICON_SIZE);
            drinkButton.setDrawable(icon);
            Label countLabel = new Label("", counterStyle);
            countLabel.setFontScale(FontScale);
            countLabel.setAlignment(Align.bottomRight);

            Table slot = new Table();
            slot.setBackground(skin.getDrawable("table"));
            slot.add(drinkButton).grow();
            slot.addActor(countLabel);

            slots.put(type, new Slot(drinkButton, countLabel));
            root.add(slot).size(SLOT_SIZE, SLOT_SIZE).padLeft(SLOT_PAD).padRight(SLOT_PAD).padBottom(UI_BOTTOM_PAD);
        }
    }

    private void drink(PotionType type) {
        if (!open || !playerComponent.potionCooldown.isDone()) {
            return;
        }
        if (!playerComponent.consumePotion(type)) {
            return;
        }
        PotionEffects.apply(playerEntity, playerComponent, type);
        playerComponent.potionCooldown.start(GameConstants.POTION_USE_COOLDOWN);
        AudioManager.get().playClick();
        refresh();
    }

    @Override
    public void act(float delta) {
        refresh();
        super.act(delta);
    }

    /**
     * While the bar is open, a tap that misses every slot dismisses it (invokes {@link #onTapOutside}),
     * so mobile players can close it again; slot taps fall through to the normal button handling.
     */
    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!open || onTapOutside == null) {
            return super.touchDown(screenX, screenY, pointer, button);
        }
        Vector2 coords = getViewport().unproject(new Vector2(screenX, screenY));
        if (hit(coords.x, coords.y, true) == null) {
            onTapOutside.run();
            return true;
        }
        return super.touchDown(screenX, screenY, pointer, button);
    }

    /** Callback invoked when the open bar is tapped outside any slot; used to close it. */
    public void setOnTapOutside(Runnable onTapOutside) {
        this.onTapOutside = onTapOutside;
    }

    private void refresh() {
        for (ObjectMap.Entry<PotionType, Slot> entry : slots.entries()) {
            PotionType type = entry.key;
            Slot slot = entry.value;
            int count = playerComponent.countPotion(type);
            slot.countLabel.setText(String.format("x %02d", count));
            slot.countLabel.pack();
            slot.countLabel.setPosition(SLOT_SIZE - slot.countLabel.getWidth() - COUNT_PAD, COUNT_PAD);
            boolean disabled = count <= 0
                || !playerComponent.potionCooldown.isDone()
                || (type == PotionType.HEALING && playerComponent.health >= playerComponent.maxHealth);
            slot.drinkButton.setTouchable(disabled ? Touchable.disabled : Touchable.enabled);
            slot.drinkButton.getColor().a = disabled ? 0.45f : 1f;
            slot.countLabel.getColor().a = disabled ? 0.45f : 1f;
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        root.setVisible(open);
    }

    private static final class Slot {
        final TouchButton drinkButton;
        final Label countLabel;

        Slot(TouchButton drinkButton, Label countLabel) {
            this.drinkButton = drinkButton;
            this.countLabel = countLabel;
        }
    }
}
