package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Renders optional camera-space cinematic screen-edge vignette and player-centric torchlight/darkness
 * vignette with a dark medieval tint, controlled by feature flags.
 */
public class VignetteRenderSystem extends EntitySystem implements Disposable {
    private static final int GRADIENT_SIZE = 64;

    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private Texture playerVignetteTexture;
    private Texture cinematicVignetteTexture;
    private ImmutableArray<Entity> players;

    public VignetteRenderSystem(SpriteBatch batch, OrthographicCamera camera, int priority) {
        super(priority);
        this.batch = batch;
        this.camera = camera;
    }

    @Override
    public void addedToEngine(com.badlogic.ashley.core.Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class).get());
    }

    private void ensureTextures() {
        if (Gdx.gl == null) {
            return;
        }
        if (playerVignetteTexture == null) {
            Pixmap pixmap = new Pixmap(GRADIENT_SIZE, GRADIENT_SIZE, Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.None);
            float center = (GRADIENT_SIZE - 1) / 2f;
            for (int y = 0; y < GRADIENT_SIZE; y++) {
                for (int x = 0; x < GRADIENT_SIZE; x++) {
                    float dx = (x - center) / center;
                    float dy = (y - center) / center;
                    float r = (float) Math.sqrt(dx * dx + dy * dy);
                    float falloff = Math.min(1f, r * r * 1.3f);
                    int alpha = Math.round(falloff * 255f);
                    pixmap.drawPixel(x, y, (0xFF << 24) | (0xFF << 16) | (0xFF << 8) | alpha);
                }
            }
            playerVignetteTexture = new Texture(pixmap);
            playerVignetteTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            pixmap.dispose();
        }

        if (cinematicVignetteTexture == null) {
            Pixmap pixmap = new Pixmap(GRADIENT_SIZE, GRADIENT_SIZE, Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.None);
            float center = (GRADIENT_SIZE - 1) / 2f;
            for (int y = 0; y < GRADIENT_SIZE; y++) {
                for (int x = 0; x < GRADIENT_SIZE; x++) {
                    float dx = (x - center) / center;
                    float dy = (y - center) / center;
                    float r = (float) Math.sqrt(dx * dx + dy * dy) / 0.7071f;
                    float falloff = Math.max(0f, Math.min(1f, r * r * 1.4f - 0.2f));
                    int alpha = Math.round(falloff * 255f);
                    pixmap.drawPixel(x, y, (0xFF << 24) | (0xFF << 16) | (0xFF << 8) | alpha);
                }
            }
            cinematicVignetteTexture = new Texture(pixmap);
            cinematicVignetteTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            pixmap.dispose();
        }
    }

    @Override
    public void update(float deltaTime) {
        boolean playerVignetteOn = FeatureFlags.isVignettePlayerCentricEnabled();
        boolean cinematicVignetteOn = FeatureFlags.isVignetteCinematicEnabled();

        if (!playerVignetteOn && !cinematicVignetteOn) {
            return;
        }

        ensureTextures();
        if (playerVignetteTexture == null || cinematicVignetteTexture == null) {
            return;
        }

        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();

        Color oldColor = batch.getColor();

        // 1. Cinematic Screen-Edge Vignette (dark medieval tint, subtle)
        if (cinematicVignetteOn) {
            float cx = camera.position.x - camera.viewportWidth / 2f;
            float cy = camera.position.y - camera.viewportHeight / 2f;
            batch.setColor(0.04f, 0.04f, 0.10f, 0.55f);
            batch.draw(cinematicVignetteTexture, cx, cy, camera.viewportWidth, camera.viewportHeight);
        }

        // 2. Player-Centric Torchlight Vignette
        if (playerVignetteOn && players != null && players.size() > 0) {
            Entity player = players.first();
            TransformComponent transform = TRANSFORM.get(player);
            CollisionComponent collision = COLLISION.get(player);
            float px = transform.position.x;
            float py = transform.position.y;
            if (collision != null) {
                px += collision.bounds.x + collision.bounds.width / 2f;
                py += collision.bounds.y + collision.bounds.height / 2f;
            } else {
                px += 8f;
                py += 12f;
            }

            float size = 320f;
            batch.setColor(0.04f, 0.04f, 0.10f, 0.65f);
            batch.draw(playerVignetteTexture, px - size / 2f, py - size / 2f, size, size);
        }

        batch.setColor(oldColor);
        batch.end();
    }

    @Override
    public void dispose() {
        if (playerVignetteTexture != null) {
            playerVignetteTexture.dispose();
            playerVignetteTexture = null;
        }
        if (cinematicVignetteTexture != null) {
            cinematicVignetteTexture.dispose();
            cinematicVignetteTexture = null;
        }
    }
}
