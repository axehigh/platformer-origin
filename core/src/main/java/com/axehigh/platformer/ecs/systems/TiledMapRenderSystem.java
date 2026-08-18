package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Disposable;

/** Renders the Tiled map's background/collision layers behind entities. */
public class TiledMapRenderSystem extends EntitySystem implements Disposable {
    private final OrthographicCamera camera;
    private PaddedTiledMapRenderer renderer;

    public TiledMapRenderSystem(TiledMap map, OrthographicCamera camera, int priority) {
        super(priority);
        this.renderer = new PaddedTiledMapRenderer(map);
        this.camera = camera;
    }

    /** Swaps the wrapped map: disposes the current renderer and builds a new one around the new map. */
    public void setMap(TiledMap map) {
        renderer.dispose();
        renderer = new PaddedTiledMapRenderer(map);
    }

    @Override
    public void update(float deltaTime) {
        renderer.setView(camera);
        renderer.render();
    }

    @Override
    public void dispose() {
        renderer.dispose();
    }

    /**
     * Subclass that expands the culling view bounds by {@link GameConstants#TILE_MAX_OVERSHOOT}
     * on every edge so oversized tiles (up to 256 px in a 16 px grid) stay visible until they
     * are fully off-screen rather than clipping when their grid cell exits the viewport.
     */
    private static class PaddedTiledMapRenderer extends OrthogonalTiledMapRenderer {
        PaddedTiledMapRenderer(TiledMap map) {
            super(map);
        }

        @Override
        public void setView(OrthographicCamera camera) {
            super.setView(camera);
            float pad = GameConstants.TILE_MAX_OVERSHOOT;
            viewBounds.x -= pad;
            viewBounds.y -= pad;
            viewBounds.width += pad * 2f;
            viewBounds.height += pad * 2f;
        }
    }
}
