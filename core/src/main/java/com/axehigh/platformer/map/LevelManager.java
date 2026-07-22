package com.axehigh.platformer.map;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.ecs.systems.TiledMapRenderSystem;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Owns in-place level transitions: tears down the current map's entities/map data and loads a new
 * .tmx, without ever destroying the Ashley engine or the player entity, so every persistent
 * {@code PlayerComponent} stat field (health, coins, items, ...) survives a level swap untouched
 * since it's literally the same component instance. The same {@code TiledMapRenderSystem} and
 * shared {@code collisionRects} array (read by {@code MovementSystem}, {@code EnemySystem},
 * {@code CollisionSystem}, {@code DebugRenderSystem}, ...) are fed the new level's data in place,
 * so no other system ever needs to be rebuilt or re-wired.
 */
public class LevelManager implements Disposable {
    private final PooledEngine engine;
    private final EntityFactory entityFactory;
    private final OrthographicCamera camera;
    private final TiledMapRenderSystem tiledMapRenderSystem;
    private final Array<Rectangle> collisionRects;

    private MapLoader mapLoader;

    public LevelManager(PooledEngine engine, EntityFactory entityFactory, OrthographicCamera camera,
                         TiledMapRenderSystem tiledMapRenderSystem, Array<Rectangle> collisionRects,
                         MapLoader initialMapLoader) {
        this.engine = engine;
        this.entityFactory = entityFactory;
        this.camera = camera;
        this.tiledMapRenderSystem = tiledMapRenderSystem;
        this.collisionRects = collisionRects;
        this.mapLoader = initialMapLoader;
    }

    /** Swaps the active map: repositions the (persisted) player, keeps its stats, respawns objects. */
    public void loadLevel(String tmxPath, Entity player) {
        MapLoader newMapLoader = new MapLoader(tmxPath);

        tiledMapRenderSystem.setMap(newMapLoader.getMap());

        collisionRects.clear();
        collisionRects.addAll(newMapLoader.getCollisionRects());

        Array<Entity> entitiesToRemove = new Array<>();
        for (Entity entity : engine.getEntities()) {
            if (entity != player) {
                entitiesToRemove.add(entity);
            }
        }
        for (Entity entity : entitiesToRemove) {
            engine.removeEntity(entity);
        }

        MapLoader oldMapLoader = mapLoader;
        mapLoader = newMapLoader;
        oldMapLoader.dispose();

        entityFactory.spawnObjects(engine, mapLoader.getObjectLayer());

        Vector2 playerStart = mapLoader.findPlayerStart();
        TransformComponent transform = TRANSFORM.get(player);
        transform.position.set(playerStart.x, playerStart.y);

        MovementComponent movement = MOVEMENT.get(player);
        if (movement != null) {
            movement.velocity.setZero();
            movement.grounded = false;
        }

        PlayerComponent playerComponent = PLAYER.get(player);
        if (playerComponent != null) {
            playerComponent.jumpCount = 0;
            playerComponent.isWallClimbing = false;
            playerComponent.interactPressed = false;
            playerComponent.nearExit = false;
        }

        int roomX = (int) (playerStart.x / GameConstants.VIRTUAL_WIDTH);
        int roomY = (int) (playerStart.y / GameConstants.VIRTUAL_HEIGHT);
        camera.position.set(
            (roomX * GameConstants.VIRTUAL_WIDTH) + (GameConstants.VIRTUAL_WIDTH / 2f),
            (roomY * GameConstants.VIRTUAL_HEIGHT) + (GameConstants.VIRTUAL_HEIGHT / 2f),
            0f);
        camera.update();
    }

    /** Disposes whichever MapLoader is currently active. */
    @Override
    public void dispose() {
        mapLoader.dispose();
    }
}
