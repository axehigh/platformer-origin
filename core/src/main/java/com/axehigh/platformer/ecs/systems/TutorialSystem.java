package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
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
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import static com.axehigh.platformer.GameConstants.SmallFontScale;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TUTORIAL;

/**
 * Manages tutorial sign proximity and floating world tooltips. When the player walks into
 * the proximity sensor of a tutorial object, its text message is displayed as a floating world
 * tooltip above the object with a panel background.
 */
public class TutorialSystem extends IteratingSystem {
    private static final float SENSOR_PADDING = 12f;
    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final Rectangle sensorBounds = new Rectangle();
    private final TextureRegionDrawable panelDrawable;
    private TouchControlsStage touchControlsStage;
    private float highlightTimer = 0f;
    private float unitScale = 1f;

    public void setTouchControlsStage(TouchControlsStage touchControlsStage) {
        this.touchControlsStage = touchControlsStage;
    }
    private ImmutableArray<Entity> players;

    public TutorialSystem(SpriteBatch batch, OrthographicCamera camera, Skin skin, int priority) {
        super(Family.all(TutorialComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.batch = batch;
        this.camera = camera;
        BitmapFont f = skin.getFont("edgeofgalaxy");
        if (f == null) {
            f = skin.get("default", BitmapFont.class);
        }
        if (f == null) {
            f = new BitmapFont();
        }
        this.font = f;
        this.panelDrawable = (TextureRegionDrawable) skin.getDrawable("table");
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
                        font.getData().setScale(SmallFontScale);
                        layout.setText(font, tutorial.text);

                        float padX = 28f;
                        float padY = 20f;
                        float boxWidth = layout.width + padX * 2f;
                        float boxHeight = layout.height + padY * 2f;

                        float drawX = collision.worldBounds.x + collision.worldBounds.width / 2f;
                        float drawY = collision.worldBounds.y + collision.worldBounds.height + 20f * unitScale;

                        float panelX = drawX - boxWidth / 2f;
                        float panelY = drawY;

                        if (panelDrawable != null) {
                            batch.setColor(1f, 1f, 1f, GameConstants.UI_PANEL_ALPHA);
                            panelDrawable.draw(batch, panelX, panelY, boxWidth, boxHeight);
                            batch.setColor(Color.WHITE);
                        }

                        font.setColor(Color.WHITE);
                        font.draw(batch, tutorial.text,
                                panelX + padX,
                                panelY + padY + layout.height);
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
