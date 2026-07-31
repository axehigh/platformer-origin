package com.axehigh.platformer.ui;

import com.axehigh.platformer.assets.GameAssetRegistry;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.assets.GameAssetRegistry.HERO_ASSET;

/**
 * Top HUD overlay: avatar + heart icons (top-left), coin counter (top-center), item tracker +
 * pause button (top-right) — reflecting the live values on {@link PlayerComponent}.
 */
public class HudStage extends Stage {
    private final PlayerComponent playerComponent;
    private final Image[] heartImages;
    private final Label coinLabel;
    private final Label itemLabel;
    private final TextButton pauseButton;

    public HudStage(Viewport viewport, Skin skin, AssetManager assetManager, PlayerComponent playerComponent) {
        super(viewport);
        this.playerComponent = playerComponent;

        Table root = new Table();
        root.setFillParent(true);
        root.top();
        addActor(root);

        Table leftGroup = new Table();
        TextureAtlas heroAtlas = assetManager.get(HERO_ASSET, TextureAtlas.class);
        Image avatar = new Image(heroAtlas.findRegion("idle"));
        leftGroup.add(avatar).size(66f, 66f).padRight(17f);

        Table heartsTable = new Table();
        Texture heartTexture = assetManager.get("gfx/old/heart.png", Texture.class);
        heartImages = new Image[playerComponent.maxHealth];
        for (int i = 0; i < heartImages.length; i++) {
            heartImages[i] = new Image(new TextureRegion(heartTexture));
            heartsTable.add(heartImages[i]).size(34f, 34f).padRight(8f);
        }
        leftGroup.add(heartsTable);

        Table centerGroup = new Table();
        Image coinIcon = new Image(new TextureRegion(assetManager.get("gfx/old/coin.png", Texture.class)));
        centerGroup.add(coinIcon).size(34f, 34f).padRight(17f);
        coinLabel = new Label("", skin);
        centerGroup.add(coinLabel);

        Table rightGroup = new Table();
        Image itemIcon = new Image(new TextureRegion(assetManager.get("gfx/old/dagger.png", Texture.class)));
        rightGroup.add(itemIcon).size(66f, 66f).padRight(17f);
        itemLabel = new Label("", skin);
        rightGroup.add(itemLabel).padRight(33f);
        pauseButton = new TextButton("||", skin);
        rightGroup.add(pauseButton).size(83f, 66f);

        root.add(leftGroup).expandX().left().pad(25f);
        root.add(centerGroup).expandX().center().pad(25f);
        root.add(rightGroup).expandX().right().pad(25f);

        refresh();
    }

    @Override
    public void act(float delta) {
        refresh();
        super.act(delta);
    }

    private void refresh() {
        for (int i = 0; i < heartImages.length; i++) {
            heartImages[i].setColor(i < playerComponent.health ? Color.RED : Color.DARK_GRAY);
        }
        coinLabel.setText(String.format("x %04d", playerComponent.coins));
        itemLabel.setText(String.format("x %02d/%02d", playerComponent.items, playerComponent.maxItems));
    }

    public TextButton getPauseButton() {
        return pauseButton;
    }
}
