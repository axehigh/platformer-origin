package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.LightComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.LIGHT;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Draws a soft additive-blended glow halo over every entity with a {@code LightComponent}
 * (currently only torches), with a per-light flicker so lights don't pulse in sync.
 * Runs after {@code RenderSystem}/{@code ParticleSystem} so the halo sits on top of sprites
 * and particles. The glow texture is a radial gradient generated procedurally once.
 */
public class LightRenderSystem extends IteratingSystem implements com.badlogic.gdx.utils.Disposable {
    private static final int GRADIENT_SIZE = 64;

    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private float elapsed = 0f;
    private Texture glowTexture;

    public LightRenderSystem(SpriteBatch batch, OrthographicCamera camera, int priority) {
        super(Family.all(TransformComponent.class, LightComponent.class).get(), priority);
        this.batch = batch;
        this.camera = camera;
    }

    private void ensureGlowTexture() {
        if (glowTexture != null || Gdx.gl == null) {
            return;
        }
        Pixmap pixmap = new Pixmap(GRADIENT_SIZE, GRADIENT_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        float center = (GRADIENT_SIZE - 1) / 2f;
        for (int y = 0; y < GRADIENT_SIZE; y++) {
            for (int x = 0; x < GRADIENT_SIZE; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float falloff = Math.max(0f, 1f - r);
                int alpha = Math.round(falloff * falloff * 255f);
                // drawPixel expects 0xRRGGBBAA: white RGB, alpha in the low byte.
                pixmap.drawPixel(x, y, (0xFF << 24) | (0xFF << 16) | (0xFF << 8) | alpha);
            }
        }
        glowTexture = new Texture(pixmap);
        glowTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
    }

    @Override
    public void update(float deltaTime) {
        ensureGlowTexture();
        if (glowTexture == null) {
            return;
        }
        elapsed += deltaTime;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        super.update(deltaTime);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = TRANSFORM.get(entity);
        LightComponent light = LIGHT.get(entity);

        // Two detuned sine waves give an organic, non-synced flicker across torches.
        float t = elapsed * light.flickerSpeed + light.phase;
        float pulse = 1f + light.flickerAmplitude
            * (MathUtils.sin(t) * 0.6f + MathUtils.sin(t * 1.7f + 1.3f) * 0.4f);
        float alpha = light.baseAlpha * (1f + 0.1f * MathUtils.sin(t * 1.3f + 0.7f));

        float size = light.radius * 2f * pulse;
        float x = transform.position.x + light.offset.x;
        float y = transform.position.y + light.offset.y;

        batch.setColor(light.color.r, light.color.g, light.color.b, alpha);
        batch.draw(glowTexture, x - size / 2f, y - size / 2f, size, size);
    }

    @Override
    public void dispose() {
        if (glowTexture != null) {
            glowTexture.dispose();
            glowTexture = null;
        }
    }
}
