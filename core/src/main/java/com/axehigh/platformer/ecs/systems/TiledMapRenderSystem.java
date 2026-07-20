package com.axehigh.platformer.ecs.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Disposable;

/** Renders the Tiled map's background/collision layers behind entities. */
public class TiledMapRenderSystem extends EntitySystem implements Disposable {
    private final OrthogonalTiledMapRenderer renderer;
    private final OrthographicCamera camera;

    public TiledMapRenderSystem(TiledMap map, OrthographicCamera camera, int priority) {
        super(priority);
        this.renderer = new OrthogonalTiledMapRenderer(map);
        this.camera = camera;
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
