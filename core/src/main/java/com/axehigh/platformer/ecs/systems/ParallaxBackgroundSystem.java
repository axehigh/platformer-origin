package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Draws the multi-layer parallax skybox behind the Tiled map: a far layer ({@code Background_01},
 * slow scroll) and a near layer ({@code Background_02}, faster scroll), each scaled to cover the
 * effective (zoomed) view and repeated horizontally across it. Positions are derived from the
 * shared game camera with a per-layer parallax factor, so the layers track the hybrid flip/scroll
 * camera automatically — static while a flip room holds the camera still, drifting slower than the
 * world while a scroll room pans it. Runs just before {@code TiledMapRenderSystem}, so the map's
 * own background tile layer (brick walls, pillars, ...) still draws on top. The textures are
 * pre-rendered scenery (not pixel art), so they are set to {@code TextureFilter.Linear}; both are
 * owned by the {@code AssetManager}, so the system neither loads nor disposes them.
 */
public class ParallaxBackgroundSystem extends EntitySystem {
    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private final Layer farLayer;
    private final Layer nearLayer;

    public ParallaxBackgroundSystem(SpriteBatch batch, OrthographicCamera camera,
                                    Texture farTexture, Texture nearTexture, int priority) {
        super(priority);
        this.batch = batch;
        this.camera = camera;
        farTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        nearTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        this.farLayer = new Layer(farTexture, GameConstants.PARALLAX_BG_FAR);
        this.nearLayer = new Layer(nearTexture, GameConstants.PARALLAX_BG_NEAR);
    }

    @Override
    public void update(float deltaTime) {
        float viewW = camera.viewportWidth * camera.zoom;
        float viewH = camera.viewportHeight * camera.zoom;
        if (viewW <= 0f || viewH <= 0f) {
            return;
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawLayer(farLayer, viewW, viewH);
        drawLayer(nearLayer, viewW, viewH);
        batch.end();
    }

    private void drawLayer(Layer layer, float viewW, float viewH) {
        Texture texture = layer.texture;
        float bgW = viewW;
        float bgH = viewH;

        float factor = layer.factor;
        float cx = camera.position.x * (1f - factor);
        float cy = camera.position.y * (1f - factor);
        float left = cx - viewW / 2f;
        float bottom = cy - viewH / 2f;

        for (float x = left - bgW; x < left + viewW; x += bgW) {
            batch.draw(texture, x, bottom, bgW, bgH);
        }
    }

    /** One parallax layer: its texture plus how fast it drifts relative to the world (0 = skybox-fixed, 1 = world-locked). */
    private static final class Layer {
        final Texture texture;
        final float factor;

        Layer(Texture texture, float factor) {
            this.texture = texture;
            this.factor = factor;
        }
    }
}
