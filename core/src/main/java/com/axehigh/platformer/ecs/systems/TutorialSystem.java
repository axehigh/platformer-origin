package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.ecs.components.TutorialComponent;
import com.axehigh.platformer.ui.TouchControlsStage;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import static com.axehigh.platformer.GameConstants.TutorialFontScale;
import static com.axehigh.platformer.GameConstants.UI_PANEL_ALPHA;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TUTORIAL;

/**
 * Manages tutorial sign proximity and floating world tooltips. When the player walks into
 * the proximity sensor of a tutorial object, its text message is displayed as a floating world
 * tooltip above the object, on a 9-patch parchment plaque (with a down-pointing tail toward the
 * sign) when the dedicated plaque/tail textures are available — otherwise the skin's classic
 * scroll/table panel drawable is used as the background.
 */
public class TutorialSystem extends IteratingSystem {
    /** Border thickness (px) of {@code gfx/tutorial_plaque.png} — the NinePatch split size. */
    private static final int PLAQUE_BORDER = 16;
    private static final float SENSOR_PADDING = 12f;
    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final Rectangle sensorBounds = new Rectangle();
    private final Drawable panelDrawable;
    private final TextureRegion tailRegion;
    private final Skin skin;
    private TouchControlsStage touchControlsStage;
    private float highlightTimer = 0f;
    private float unitScale = 1f;

    public void setTouchControlsStage(TouchControlsStage touchControlsStage) {
        this.touchControlsStage = touchControlsStage;
    }
    private ImmutableArray<Entity> players;

    public TutorialSystem(SpriteBatch batch, OrthographicCamera camera, Skin skin, Texture plaqueTexture, Texture tailTexture, int priority) {
        super(Family.all(TutorialComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.batch = batch;
        this.camera = camera;
        this.skin = skin;
        BitmapFont f = skin.getFont("edgeofgalaxy");
        if (f == null) {
            f = skin.get("default", BitmapFont.class);
        }
        if (f == null) {
            f = new BitmapFont();
        }
        this.font = f;
        Drawable drawable = null;
        TextureRegion tail = null;
        if (plaqueTexture != null && tailTexture != null) {
            // Purpose-built in-world plaque + pointer: 9-patch so it stretches to the text box
            // without distortion; tail drawn centered under the panel. Nearest filtering (pixel art).
            plaqueTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            tailTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            drawable = new NinePatchDrawable(new NinePatch(new TextureRegion(plaqueTexture), PLAQUE_BORDER, PLAQUE_BORDER, PLAQUE_BORDER, PLAQUE_BORDER));
            tail = new TextureRegion(tailTexture);
        }
        if (drawable == null) {
            try {
                Drawable d = skin.getDrawable("table_tall_border");
                if (d instanceof TextureRegionDrawable) {
                    drawable = (TextureRegionDrawable) d;
                }
            } catch (Exception ignored) {}
        }
        if (drawable == null) {
            drawable = (TextureRegionDrawable) skin.getDrawable("scroll_large");
        }
        if (drawable == null) {
            drawable = (TextureRegionDrawable) skin.getDrawable("scroll");
        }
        if (drawable == null) {
            drawable = (TextureRegionDrawable) skin.getDrawable("table");
        }
        this.panelDrawable = drawable;
        this.tailRegion = tail;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);

        String activeHighlight = "";

        // Render tooltips with panel background, collect the active highlight, then reset flags in
        // one pass so highlights (and tooltips) show on the same frame the sensor is entered.
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (Entity entity : getEntities()) {
            TutorialComponent tutorial = TUTORIAL.get(entity);
            if (tutorial == null) {
                continue;
            }
            if (tutorial.active) {
                if (!tutorial.highlight.isEmpty()) {
                    activeHighlight = tutorial.highlight;
                }
                if (!tutorial.text.isEmpty()) {
                    CollisionComponent collision = COLLISION.get(entity);
                    if (collision != null) {
                        font.getData().setScale(TutorialFontScale);
                        layout.setText(font, tutorial.text);

                        float padX = 64f;
                        float padY = 36f;

                        // Resolve inline icon from the highlight keyword: the same skin drawable the
                        // touch controller uses, so the tooltip text maps 1:1 to the button to press.
                        TextureRegionDrawable iconDrawable = null;
                        float iconSize = 0f;
                        if (!tutorial.highlight.isEmpty()) {
                            String iconName = TouchControlsStage.iconNameFor(tutorial.highlight);
                            if (iconName != null) {
                                Drawable d = skin.getDrawable(iconName);
                                if (d instanceof TextureRegionDrawable) {
                                    iconDrawable = (TextureRegionDrawable) d;
                                    iconSize = layout.height;
                                }
                            }
                        }

                        float gap = iconDrawable != null ? 8f : 0f;
                        float boxWidth = (iconDrawable != null ? iconSize + gap : 0f) + layout.width + padX * 2f;
                        float boxHeight = layout.height + padY * 2f;

                        float drawX = collision.worldBounds.x + collision.worldBounds.width / 2f;
                        float drawY = collision.worldBounds.y + collision.worldBounds.height + 20f * unitScale;

                        float panelX = drawX - boxWidth / 2f;
                        float panelY = drawY;

                        if (panelDrawable != null) {
                            batch.setColor(1f, 1f, 1f, UI_PANEL_ALPHA);
                            panelDrawable.draw(batch, panelX, panelY, boxWidth, boxHeight);
                            // Down-pointing pointer under the plaque, centered on the panel, toward the sign.
                            if (tailRegion != null) {
                                float tailW = tailRegion.getRegionWidth();
                                float tailH = tailRegion.getRegionHeight();
                                batch.draw(tailRegion, panelX + (boxWidth - tailW) / 2f, panelY - tailH, tailW, tailH);
                            }
                            batch.setColor(Color.WHITE);
                        }

                        float textX = panelX + padX;
                        if (iconDrawable != null) {
                            float iconY = panelY + padY + (layout.height - iconSize) / 2f;
                            iconDrawable.draw(batch, textX, iconY, iconSize, iconSize);
                            textX += iconSize + gap;
                        }

                        font.setColor(Color.WHITE);
                        font.draw(batch, tutorial.text, textX, panelY + padY + layout.height);
                    }
                }
            }
            tutorial.active = false;
        }
        batch.end();

        if (!activeHighlight.isEmpty()) {
            highlightTimer += deltaTime;
        } else {
            highlightTimer = 0f;
        }

        if (touchControlsStage != null) {
            touchControlsStage.setHighlightedControl(activeHighlight, highlightTimer);
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (players.size() == 0) {
            return;
        }
        TutorialComponent tutorial = TUTORIAL.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        Entity playerEntity = players.first();
        CollisionComponent playerCollision = COLLISION.get(playerEntity);

        float padding = SENSOR_PADDING * unitScale;
        sensorBounds.set(
            collision.worldBounds.x - padding,
            collision.worldBounds.y - padding,
            collision.worldBounds.width + padding * 2f,
            collision.worldBounds.height + padding * 2f);

        if (playerCollision.worldBounds.overlaps(sensorBounds)) {
            tutorial.active = true;
        }
    }
}
