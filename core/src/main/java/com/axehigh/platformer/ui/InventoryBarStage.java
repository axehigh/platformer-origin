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

import static com.axehigh.platformer.GameConstants.*;
import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_UI_GFX;

/**
 * Pause-the-game inventory hotbar: displays equipment slots (Armor, Weapon, Bullet/Dagger Weapon, Key)
 * in the top row (when enabled via feature flag), and a horizontal row of one slot per {@link PotionType}
 * in the bottom row. Each potion slot features a title label above it identifying the potion (e.g. "SPEED")
 * and an icon plus the held count. Tapping an enabled potion slot drinks one potion of that type.
 * Unowned potions / empty slots are faded while owned potions retain full color. The bar opens/closes via {@link #setOpen}.
 */
public class InventoryBarStage extends Stage {
    private static final float SLOT_SIZE = 110f;
    private static final float SLOT_PAD = 12f;
    private static final float COUNT_PAD = 6f;
    private static final float ICON_SIZE = 60f;

    private final PlayerComponent playerComponent;
    private final Entity playerEntity;
    private final ObjectMap<PotionType, Slot> potionSlots = new ObjectMap<>();
    private Slot armorSlot;
    private Slot weaponSlot;
    private Slot bulletSlot;
    private Slot keySlot;
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
        root.padBottom(UI_BOTTOM_PAD + 15f);
        root.setVisible(false);
        addActor(root);

        TextureAtlas uiAtlas = assetManager.get(ORIGIN_UI_GFX, TextureAtlas.class);

        // --- Equipment Row (Armor, Weapon, Bullet/Dagger, Key) ---
        if (GameConstants.ENABLE_INVENTORY_EQUIPMENT) {
            Table eqRow = new Table();
            armorSlot = createEquipmentSlot(skin, uiAtlas, "Armor", "dagger");
            weaponSlot = createEquipmentSlot(skin, uiAtlas, "Weapon", "potion_strength");
            bulletSlot = createEquipmentSlot(skin, uiAtlas, "Ammo", "potion_speed");
            keySlot = createEquipmentSlot(skin, uiAtlas, "Key", "potion_healing");

            eqRow.add(armorSlot.container).size(SLOT_SIZE, SLOT_SIZE).pad(SLOT_PAD);
            eqRow.add(weaponSlot.container).size(SLOT_SIZE, SLOT_SIZE).pad(SLOT_PAD);
            eqRow.add(bulletSlot.container).size(SLOT_SIZE, SLOT_SIZE).pad(SLOT_PAD);
            eqRow.add(keySlot.container).size(SLOT_SIZE, SLOT_SIZE).pad(SLOT_PAD);

            root.add(eqRow).padBottom(15f);
            root.row();
        }

        // --- Potion Row ---
        Table potionRow = new Table();
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

            Label titleLabel = new Label(type.displayName().toUpperCase(), counterStyle);
            titleLabel.setFontScale(FontScale * 0.85f);
            titleLabel.setAlignment(Align.center);

            Table slotTable = new Table();
            slotTable.background(skin.getDrawable("table"));
            slotTable.add(drinkButton).grow();
            slotTable.addActor(countLabel);

            Table col = new Table();
            col.add(titleLabel).padBottom(4f);
            col.row();
            col.add(slotTable).size(SLOT_SIZE, SLOT_SIZE);

            potionSlots.put(type, new Slot(drinkButton, countLabel, slotTable));
            potionRow.add(col).padLeft(SLOT_PAD).padRight(SLOT_PAD);
        }

        root.add(potionRow);
    }

    private Slot createEquipmentSlot(Skin skin, TextureAtlas uiAtlas, String title, String defaultIconRegion) {
        LabelStyle counterStyle = skin.get(LabelStyle.class);
        TouchButton btn = new TouchButton(skin, "flatUp", 1f, 0f, () -> {});
        TextureRegion region = uiAtlas.findRegion(defaultIconRegion);
        if (region != null) {
            TextureRegionDrawable icon = new TextureRegionDrawable(region);
            icon.setMinWidth(ICON_SIZE);
            icon.setMinHeight(ICON_SIZE);
            btn.setDrawable(icon);
        }
        Label countLabel = new Label("", counterStyle);
        countLabel.setFontScale(FontScale);

        Label titleLabel = new Label(title.toUpperCase(), counterStyle);
        titleLabel.setFontScale(FontScale * 0.85f);
        titleLabel.setAlignment(Align.center);

        Table slotTable = new Table();
        slotTable.background(skin.getDrawable("table"));
        slotTable.add(btn).grow();

        Table col = new Table();
        col.add(titleLabel).padBottom(4f);
        col.row();
        col.add(slotTable).size(SLOT_SIZE, SLOT_SIZE);

        return new Slot(btn, countLabel, col);
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

    public void setOnTapOutside(Runnable onTapOutside) {
        this.onTapOutside = onTapOutside;
    }

    private void refresh() {
        // Refresh potion slots
        for (ObjectMap.Entry<PotionType, Slot> entry : potionSlots.entries()) {
            PotionType type = entry.key;
            Slot slot = entry.value;
            int count = playerComponent.countPotion(type);
            slot.countLabel.setText(String.format("x %02d", count));
            slot.countLabel.pack();
            slot.countLabel.setPosition(SLOT_SIZE - slot.countLabel.getWidth() - COUNT_PAD, COUNT_PAD);

            boolean empty = count <= 0;
            boolean disabled = empty
                || !playerComponent.potionCooldown.isDone()
                || (type == PotionType.HEALING && playerComponent.health >= playerComponent.maxHealth);

            slot.button.setTouchable(disabled ? Touchable.disabled : Touchable.enabled);
            // Owned potions have full color (1f), unowned/empty slots are faded (0.35f)
            float alpha = empty ? 0.35f : 1f;
            slot.button.getColor().a = alpha;
            slot.countLabel.getColor().a = empty ? 0.35f : 1f;
            slot.container.getColor().a = empty ? 0.6f : 1f;
        }

        // Refresh equipment slots if enabled
        if (GameConstants.ENABLE_INVENTORY_EQUIPMENT && bulletSlot != null) {
            bulletSlot.countLabel.setText(String.format("x %02d", playerComponent.items));
            bulletSlot.button.getColor().a = playerComponent.items > 0 ? 1f : 0.35f;
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
        final TouchButton button;
        final Label countLabel;
        final Table container;

        Slot(TouchButton button, Label countLabel, Table container) {
            this.button = button;
            this.countLabel = countLabel;
            this.container = container;
        }
    }
}


