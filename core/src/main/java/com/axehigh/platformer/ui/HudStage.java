package com.axehigh.platformer.ui;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.axehigh.platformer.util.Timer;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_UI_GFX;

/**
 * Top HUD overlay: avatar + heart icons + coin counter (top-left), buff countdown row
 * (top-center), item tracker + pause button (top-right) — reflecting the live values on
 * {@link PlayerComponent} and the running buffs on {@link BuffComponent}. The coin/item
 * text uses a dedicated drop-shadowed clone of the skin font so it stays legible against
 * bright background tiles.
 */
public class HudStage extends Stage {
    private static final float HEART_SIZE = 28f;
    private static final float HEART_PAD = 6f;
    /** Buff types with a timed effect, in HUD countdown-row order. */
    private static final PotionType[] BUFF_ORDER = {
        PotionType.STRENGTH, PotionType.SPEED, PotionType.INVULNERABILITY
    };
    private static final float BUFF_ICON_SIZE = 26f;

    private final PlayerComponent playerComponent;
    private final BuffComponent buffComponent;
    private final Image[] heartImages;
    private final Label coinLabel;
    private Label bulletLabel;
    private final TextButton pauseButton;
    private final ObjectMap<PotionType, TextureRegionDrawable> potionDrawables = new ObjectMap<>();
    private final Image[] buffImages;
    private final Label[] buffLabels;
    /** Stage-delta accumulator driving the ~5Hz expiry blink of the buff icons. */
    private float buffBlinkClock;

    public HudStage(Viewport viewport, Skin skin, AssetManager assetManager,
                    PlayerComponent playerComponent, BuffComponent buffComponent) {
        super(viewport);
        this.playerComponent = playerComponent;
        this.buffComponent = buffComponent;

        LabelStyle counterStyle = skin.get(LabelStyle.class);

        Table root = new Table();
        root.setFillParent(true);
        root.top();
        addActor(root);

        Table leftGroup = new Table();
     //   TextureAtlas heroAtlas = assetManager.get(HERO_ASSET, TextureAtlas.class);
       // Image avatar = new Image(heroAtlas.findRegion("idle"));
        //leftGroup.add(avatar).size(66f, 66f).padRight(17f);

        TextureAtlas uiAtlas = assetManager.get(ORIGIN_UI_GFX, TextureAtlas.class);
        for (PotionType type : PotionType.values()) {
            TextureAtlas.AtlasRegion region = uiAtlas.findRegion(type.regionName());
            potionDrawables.put(type, new TextureRegionDrawable(region));
        }

        Table heartsTable = new Table();
        Texture heartTexture = assetManager.get("gfx/old/heart.png", Texture.class);
        heartImages = new Image[playerComponent.maxHealth];
        for (int i = 0; i < heartImages.length; i++) {
            heartImages[i] = new Image(new TextureRegion(heartTexture));
            heartsTable.add(heartImages[i]).size(HEART_SIZE, HEART_SIZE).padRight(HEART_PAD);
        }
        leftGroup.add(heartsTable).padRight(24f);

        Image coinIcon = new Image(new TextureRegion(assetManager.get("gfx/old/coin.png", Texture.class)));
        leftGroup.add(coinIcon).size(34f, 34f).padRight(10f);
        coinLabel = new ShadowLabel("", counterStyle);
        leftGroup.add(coinLabel).padRight(24f);

        if (GameConstants.USE_BULLET) {
            Image bulletIcon = new Image(uiAtlas.findRegion("daggers"));
            leftGroup.add(bulletIcon).size(30f, 30f).padRight(8f);
            bulletLabel = new ShadowLabel("", counterStyle);
            leftGroup.add(bulletLabel);
        }

        Table centerGroup = new Table();

        Table buffRow = new Table();
        buffImages = new Image[BUFF_ORDER.length];
        buffLabels = new Label[BUFF_ORDER.length];
        for (int i = 0; i < BUFF_ORDER.length; i++) {
            buffImages[i] = new Image(potionDrawables.get(BUFF_ORDER[i]));
            buffLabels[i] = new ShadowLabel("", counterStyle);
            buffRow.add(buffImages[i]).size(BUFF_ICON_SIZE, BUFF_ICON_SIZE);
            buffRow.add(buffLabels[i]).padRight(17f);
        }
        centerGroup.row();
        centerGroup.add(buffRow).colspan(2).center().padTop(10f);

        Table rightGroup = new Table();
        pauseButton = new TextButton("||", skin);
        rightGroup.add(pauseButton).size(83f, 66f);

        root.add(leftGroup).expandX().left().pad(25f);
        root.add(centerGroup).expandX().center().pad(25f);
        root.add(rightGroup).expandX().right().pad(25f);

        refresh();
    }

    @Override
    public void act(float delta) {
        buffBlinkClock += delta;
        refresh();
        super.act(delta);
    }

    private void refresh() {
        for (int i = 0; i < heartImages.length; i++) {
            heartImages[i].setColor(i < playerComponent.health ? Color.RED : Color.DARK_GRAY);
        }
        coinLabel.setText(String.format("x %04d", playerComponent.coins));
        if (GameConstants.USE_BULLET && bulletLabel != null) {
            bulletLabel.setText(String.format("x %02d", playerComponent.ammo));
        }
        refreshBuffRow();
    }

    /**
     * Buff countdown row: one icon + remaining-seconds entry per active buff, hidden while its
     * buff is inactive. Icons blink (~5Hz) during the final {@link GameConstants#BUFF_BLINK_THRESHOLD}
     * seconds; labels always show the remaining whole seconds.
     */
    private void refreshBuffRow() {
        boolean blinkPhase = (int) (buffBlinkClock / GameConstants.BUFF_BLINK_INTERVAL) % 2 == 0;
        for (int i = 0; i < BUFF_ORDER.length; i++) {
            Timer timer = buffTimer(BUFF_ORDER[i]);
            boolean active = timer != null && timer.isActive();
            float remaining = active ? timer.getRemaining() : 0f;
            boolean blinkHidden = active
                && remaining < GameConstants.BUFF_BLINK_THRESHOLD
                && !blinkPhase;
            buffImages[i].setVisible(active && !blinkHidden);
            buffLabels[i].setVisible(active);
            if (active) {
                buffLabels[i].setText(String.format("%d", (int) Math.ceil(remaining)));
            }
        }
    }

    /** The {@link BuffComponent} timer backing a buff-capable {@link PotionType}. */
    private Timer buffTimer(PotionType type) {
        switch (type) {
            case STRENGTH:
                return buffComponent.strength;
            case SPEED:
                return buffComponent.speed;
            case INVULNERABILITY:
                return buffComponent.invulnerability;
            default:
                return null;
        }
    }

    public TextButton getPauseButton() {
        return pauseButton;
    }
}
