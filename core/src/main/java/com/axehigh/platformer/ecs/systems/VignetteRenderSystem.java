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
import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;
import static com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;

/**
 * Renders optional camera-space cinematic screen-edge vignette and player-centric torchlight/darkness
 * vignette with a dark medieval tint, controlled by feature flags.
 */
public class VignetteRenderSystem extends EntitySystem implements Disposable {
    private static final int GRADIENT_SIZE = 256;
    private static final float VIGNETTE_FALLOFF_POWER = 2.5f;

    // Cinematic Vignette Constants (tunable)
    private static final float CINEMATIC_COLOR_R = 0.04f;
    private static final float CINEMATIC_COLOR_G = 0.04f;
    private static final float CINEMATIC_COLOR_B = 0.10f;
    private static final float CINEMATIC_COLOR_A = 0.35f; // tuned down from 0.55f so it's less dark

    // Player-Centric Vignette Constants (tunable)
    private static final float PLAYER_COLOR_R = 0.04f;
    private static final float PLAYER_COLOR_G = 0.04f;
    private static final float PLAYER_COLOR_B = 0.10f;
    private static final float PLAYER_COLOR_A = 0.65f;
    private static final float PLAYER_VIGNETTE_SIZE = 320f;

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

    private Texture createVignetteTexture() {
        int size = GRADIENT_SIZE;
        Pixmap pixmap = new Pixmap(size, size, RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float centerX = size / 2f;
        float centerY = size / 2f;
        float maxDist = size / 2f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - centerX;
                float dy = y - centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = Math.min(1.0f, dist / maxDist);
                // Power function for softer center
                alpha = (float) Math.pow(alpha, VIGNETTE_FALLOFF_POWER);
                pixmap.setColor(0, 0, 0, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Linear, Linear);
        pixmap.dispose();
        return texture;
    }

    private void ensureTextures() {
        if (Gdx.gl == null) {
            return;
        }
        if (playerVignetteTexture == null) {
            playerVignetteTexture = createVignetteTexture();
        }

        if (cinematicVignetteTexture == null) {
            cinematicVignetteTexture = createVignetteTexture();
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
            float vpWidth = camera.viewportWidth * camera.zoom;
            float vpHeight = camera.viewportHeight * camera.zoom;
            float cx = camera.position.x - vpWidth * 0.5f;
            float cy = camera.position.y - vpHeight * 0.5f;
            batch.setColor(CINEMATIC_COLOR_R, CINEMATIC_COLOR_G, CINEMATIC_COLOR_B, CINEMATIC_COLOR_A);
            batch.draw(cinematicVignetteTexture, cx, cy, vpWidth, vpHeight);
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

            float size = PLAYER_VIGNETTE_SIZE;
            batch.setColor(PLAYER_COLOR_R, PLAYER_COLOR_G, PLAYER_COLOR_B, PLAYER_COLOR_A);
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
