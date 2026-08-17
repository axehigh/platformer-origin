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
        this.farLayer = new Layer(farTexture, GameConstants.PARALLAX_BG_FAR, 0f);
        this.nearLayer = new Layer(nearTexture, GameConstants.PARALLAX_BG_NEAR,
                GameConstants.PARALLAX_TILE_HEIGHT);
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
        float factor = layer.factor;
        float cx = camera.position.x * (1f - factor);

        float camLeft = camera.position.x - viewW / 2f;
        float camRight = camera.position.x + viewW / 2f;
        float camBottom = camera.position.y - viewH / 2f;
        float camTop = camera.position.y + viewH / 2f;

        float bgW;
        float bgH;

        if (layer.yOffset > 0f) {
            bgW = viewW;
            bgH = viewW * ((float) texture.getHeight() / texture.getWidth());
        } else {
            bgW = viewW;
            bgH = viewH;
        }

        float x = cx - bgW;
        while (x + bgW < camLeft) x += bgW;
        for (; x < camRight; x += bgW) {
            if (layer.yOffset > 0f) {
                float y = camBottom + layer.yOffset - bgH;
                while (y + bgH < camBottom) y += bgH;
                for (; y < camTop; y += bgH) {
                    batch.draw(texture, x, y, bgW, bgH);
                }
            } else {
                batch.draw(texture, x, camBottom, bgW, bgH);
            }
        }
    }

    /** One parallax layer: its texture plus how fast it drifts relative to the world (0 = skybox-fixed, 1 = world-locked). */
    private static final class Layer {
        final Texture texture;
        final float factor;
        final float yOffset;

        Layer(Texture texture, float factor, float yOffset) {
            this.texture = texture;
            this.factor = factor;
            this.yOffset = yOffset;
        }
    }
}
