package com.axehigh.platformer.ecs.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Disposable;

/** Renders the Tiled map's background/collision layers behind entities. */
public class TiledMapRenderSystem extends EntitySystem implements Disposable {
    private final OrthographicCamera camera;
    private OrthogonalTiledMapRenderer renderer;

    public TiledMapRenderSystem(TiledMap map, OrthographicCamera camera, int priority) {
        super(priority);
        this.renderer = new OrthogonalTiledMapRenderer(map);
        this.camera = camera;
    }

    /** Swaps the wrapped map: disposes the current renderer and builds a new one around the new map. */
    public void setMap(TiledMap map) {
        renderer.dispose();
        renderer = new OrthogonalTiledMapRenderer(map);
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
}
