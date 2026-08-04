package com.axehigh.platformer.map;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.ecs.systems.CameraSystem;
import com.axehigh.platformer.ecs.systems.ChestSystem;
import com.axehigh.platformer.ecs.systems.CollisionSystem;
import com.axehigh.platformer.ecs.systems.EnemyBulletCollisionSystem;
import com.axehigh.platformer.ecs.systems.EnemyContactSystem;
import com.axehigh.platformer.ecs.systems.EnemyShootSystem;
import com.axehigh.platformer.ecs.systems.EnemySystem;
import com.axehigh.platformer.ecs.systems.LevelExitSystem;
import com.axehigh.platformer.ecs.systems.MeleeAttackSystem;
import com.axehigh.platformer.ecs.systems.MovementSystem;
import com.axehigh.platformer.ecs.systems.MovingPlatformSystem;
import com.axehigh.platformer.ecs.systems.TiledMapRenderSystem;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Owns in-place level transitions: tears down the current map's entities/map data and loads a new
 * .tmx, without ever destroying the Ashley engine or the player entity, so every persistent
 * {@code PlayerComponent} stat field (health, coins, items, ...) survives a level swap untouched
 * since it's literally the same component instance. The same {@code TiledMapRenderSystem} and
 * shared {@code collisionRects}/{@code RoomState.rooms} (read by {@code MovementSystem}, {@code
 * EnemySystem}, {@code CollisionSystem}, {@code DebugRenderSystem}, {@code CameraSystem}, ...)
 * are fed the new level's data in place, so no other system ever needs to be rebuilt or re-wired.
 */
public class LevelManager implements Disposable {
    private final PooledEngine engine;
    private final EntityFactory entityFactory;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final TiledMapRenderSystem tiledMapRenderSystem;
    private final Array<Rectangle> collisionRects;
    private final Array<Rectangle> oneWayRects;
    private final Array<Rectangle> hazardRects;
    private final RoomState roomState;

    private MapLoader mapLoader;

    public LevelManager(PooledEngine engine, EntityFactory entityFactory, Viewport viewport,
                         TiledMapRenderSystem tiledMapRenderSystem, Array<Rectangle> collisionRects,
                         Array<Rectangle> oneWayRects, Array<Rectangle> hazardRects,
                         RoomState roomState, MapLoader initialMapLoader) {
        this.engine = engine;
        this.entityFactory = entityFactory;
        this.viewport = viewport;
        this.camera = (OrthographicCamera) viewport.getCamera();
        this.tiledMapRenderSystem = tiledMapRenderSystem;
        this.collisionRects = collisionRects;
        this.oneWayRects = oneWayRects;
        this.hazardRects = hazardRects;
        this.roomState = roomState;
        this.mapLoader = initialMapLoader;
    }

    /** Swaps the active map: repositions the (persisted) player, keeps its stats, respawns objects. */
    public void loadLevel(String tmxPath, Entity player) {
        MapLoader newMapLoader = new MapLoader(tmxPath);
        float newScale = newMapLoader.getTileWidth() / 16f;
        entityFactory.setUnitScale(newScale);

        viewport.setWorldSize(GameConstants.VIRTUAL_WIDTH * newScale, GameConstants.VIRTUAL_HEIGHT * newScale);
        viewport.apply();

        MovementSystem movementSystem = engine.getSystem(MovementSystem.class);
        if (movementSystem != null) {
            movementSystem.setUnitScale(newScale);
        }
        EnemySystem enemySystem = engine.getSystem(EnemySystem.class);
        if (enemySystem != null) {
            enemySystem.setUnitScale(newScale);
        }
        EnemyShootSystem shootSystem = engine.getSystem(EnemyShootSystem.class);
        if (shootSystem != null) {
            shootSystem.setUnitScale(newScale);
        }
        CollisionSystem collisionSystem = engine.getSystem(CollisionSystem.class);
        if (collisionSystem != null) {
            collisionSystem.setUnitScale(newScale);
        }
        EnemyBulletCollisionSystem enemyBulletSystem = engine.getSystem(EnemyBulletCollisionSystem.class);
        if (enemyBulletSystem != null) {
            enemyBulletSystem.setUnitScale(newScale);
        }
        EnemyContactSystem enemyContactSystem = engine.getSystem(EnemyContactSystem.class);
        if (enemyContactSystem != null) {
            enemyContactSystem.setUnitScale(newScale);
        }
        MeleeAttackSystem meleeSystem = engine.getSystem(MeleeAttackSystem.class);
        if (meleeSystem != null) {
            meleeSystem.setUnitScale(newScale);
        }
        ChestSystem chestSystem = engine.getSystem(ChestSystem.class);
        if (chestSystem != null) {
            chestSystem.setUnitScale(newScale);
        }
        LevelExitSystem exitSystem = engine.getSystem(LevelExitSystem.class);
        if (exitSystem != null) {
            exitSystem.setUnitScale(newScale);
        }
        MovingPlatformSystem movingPlatformSystem = engine.getSystem(MovingPlatformSystem.class);
        if (movingPlatformSystem != null) {
            movingPlatformSystem.setUnitScale(newScale);
        }

        tiledMapRenderSystem.setMap(newMapLoader.getMap());

        collisionRects.clear();
        collisionRects.addAll(newMapLoader.getCollisionRects());
        oneWayRects.clear();
        oneWayRects.addAll(newMapLoader.getOneWayRects());
        hazardRects.clear();
        hazardRects.addAll(newMapLoader.getHazardRects());

        roomState.rooms.clear();
        roomState.rooms.addAll(newMapLoader.getRooms());
        roomState.activeRoomIndex = -1;

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

        entityFactory.spawnObjects(engine, mapLoader.getObjectLayer(), roomState);
        entityFactory.spawnObjects(engine, mapLoader.getEnemiesLayer(), roomState);

        Vector2 playerStart = mapLoader.findPlayerStart();
        TransformComponent transform = TRANSFORM.get(player);
        transform.position.set(playerStart.x, playerStart.y);
        float playerFinalScale = newScale * SpriteConstants.PlayerScale;
        transform.scale.set(playerFinalScale, playerFinalScale);

        CollisionComponent playerCollision = COLLISION.get(player);
        if (playerCollision != null) {
            entityFactory.resetPlayerCollision(playerCollision, playerFinalScale);
        }

        MovementComponent movement = MOVEMENT.get(player);
        if (movement != null) {
            movement.velocity.setZero();
            movement.grounded = false;
            movement.maxSpeedX = GameConstants.MaxSpeedX * newScale;
            movement.maxSpeedY = GameConstants.MaxSpeedY * newScale;
        }

        PlayerComponent playerComponent = PLAYER.get(player);
        if (playerComponent != null) {
            playerComponent.jumpCount = 0;
            playerComponent.isWallClimbing = false;
            playerComponent.interactPressed = false;
            playerComponent.nearExit = false;
            playerComponent.dropRequested = false;
            playerComponent.onDropTile = false;
            playerComponent.dropWindow.reset();
            playerComponent.squashActive = false;
            playerComponent.squashAmount = 0f;
            playerComponent.inAir = false;
            playerComponent.maxAirHeight = 0f;
        }

        CameraSystem.snapToRoom(camera, roomState, playerStart.x, playerStart.y);
    }

    /** Returns the .tmx path of the currently active level. */
    public String getCurrentLevelPath() {
        return mapLoader.getTmxPath();
    }

    /** Disposes whichever MapLoader is currently active. */
    @Override
    public void dispose() {
        mapLoader.dispose();
    }
}
