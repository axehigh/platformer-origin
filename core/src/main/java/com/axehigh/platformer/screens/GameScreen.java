package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.systems.AnimationSystem;
import com.axehigh.platformer.ecs.systems.CameraSystem;
import com.axehigh.platformer.ecs.systems.ChestSystem;
import com.axehigh.platformer.ecs.systems.CollisionSystem;
import com.axehigh.platformer.ecs.systems.DebugRenderSystem;
import com.axehigh.platformer.ecs.systems.EnemyBulletCollisionSystem;
import com.axehigh.platformer.ecs.systems.EnemyContactSystem;
import com.axehigh.platformer.ecs.systems.EnemyShootSystem;
import com.axehigh.platformer.ecs.systems.EnemySystem;
import com.axehigh.platformer.ecs.systems.LevelExitSystem;
import com.axehigh.platformer.ecs.systems.MeleeAttackSystem;
import com.axehigh.platformer.ecs.systems.MovementSystem;
import com.axehigh.platformer.ecs.systems.PickupSystem;
import com.axehigh.platformer.ecs.systems.PlayerInputSystem;
import com.axehigh.platformer.ecs.systems.RenderSystem;
import com.axehigh.platformer.ecs.systems.TiledMapRenderSystem;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.LevelManager;
import com.axehigh.platformer.map.MapLoader;
import com.axehigh.platformer.map.RoomState;
import com.axehigh.platformer.ui.HudStage;
import com.axehigh.platformer.ui.SkinFactory;
import com.axehigh.platformer.ui.TouchControlsStage;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;

/** Owns the Ashley Engine, the fixed-resolution viewport/camera, and drives the game loop. */
public class GameScreen implements Screen {
    private static final int PRIORITY_INPUT = 0;
    private static final int PRIORITY_ENEMY = 4;
    private static final int PRIORITY_MOVEMENT = 5;
    private static final int PRIORITY_COLLISION = 6;
    private static final int PRIORITY_MELEE = 7;
    private static final int PRIORITY_PICKUP = 7;
    private static final int PRIORITY_CHEST = 7;
    private static final int PRIORITY_ENEMY_CONTACT = 7;
    private static final int PRIORITY_LEVEL_EXIT = 7;
    private static final int PRIORITY_CAMERA = 8;
    private static final int PRIORITY_ANIMATION = 10;
    private static final int PRIORITY_MAP_RENDER = 20;
    private static final int PRIORITY_ENTITY_RENDER = 30;
    private static final int PRIORITY_DEBUG_RENDER = 40;

    private final AssetManager assetManager = new AssetManager();
    private final PooledEngine engine = new PooledEngine();
    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT, camera);

    private final Game game;
    private final String levelPath;

    private TiledMapRenderSystem tiledMapRenderSystem;
    private DebugRenderSystem debugRenderSystem;
    private LevelManager levelManager;
    private PlayerComponent playerComponent;
    private Skin uiSkin;
    private HudStage hudStage;
    private TouchControlsStage touchControlsStage;

    /** Defaults to the catalog's first level. */
    public GameScreen(Game game) {
        this(game, LevelCatalog.levels().first().tmxPath);
    }

    public GameScreen(Game game, String levelPath) {
        this.game = game;
        this.levelPath = levelPath;
    }

    @Override
    public void show() {
        assetManager.load("gfx/player.png", Texture.class);
        assetManager.load("gfx/coin.png", Texture.class);
        assetManager.load("gfx/chest.png", Texture.class);
        assetManager.load("gfx/torch.png", Texture.class);
        assetManager.load("gfx/exit_gate.png", Texture.class);
        assetManager.load("gfx/heart.png", Texture.class);
        assetManager.load("gfx/bullet.png", Texture.class);
        assetManager.load("gfx/dagger.png", Texture.class);
        assetManager.load("gfx/chest_open.png", Texture.class);
        assetManager.load("gfx/player_attack.png", Texture.class);
        assetManager.load("gfx/enemy.png", Texture.class);
        assetManager.load("gfx/enemy_flyer.png", Texture.class);
        assetManager.load("gfx/enemy_shooter.png", Texture.class);
        assetManager.finishLoading();

        MapLoader mapLoader = new MapLoader(levelPath);
        EntityFactory entityFactory = new EntityFactory(assetManager);
        RoomState roomState = new RoomState();
        roomState.rooms.addAll(mapLoader.getRooms());

        camera.position.set(GameConstants.VIRTUAL_WIDTH / 2f, GameConstants.VIRTUAL_HEIGHT / 2f, 0f);

        PlayerInputSystem playerInputSystem = new PlayerInputSystem(assetManager, PRIORITY_INPUT);
        tiledMapRenderSystem = new TiledMapRenderSystem(mapLoader.getMap(), camera, PRIORITY_MAP_RENDER);
        engine.addSystem(playerInputSystem);
        engine.addSystem(new EnemySystem(mapLoader.getCollisionRects(), roomState, PRIORITY_ENEMY));
        engine.addSystem(new EnemyShootSystem(assetManager, roomState, PRIORITY_ENEMY));
        engine.addSystem(new MovementSystem(mapLoader.getCollisionRects(), PRIORITY_MOVEMENT));
        engine.addSystem(new CollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION));
        engine.addSystem(new EnemyBulletCollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION));
        engine.addSystem(new MeleeAttackSystem(assetManager, PRIORITY_MELEE));
        engine.addSystem(new PickupSystem(PRIORITY_PICKUP));
        engine.addSystem(new ChestSystem(entityFactory, PRIORITY_CHEST));
        engine.addSystem(new EnemyContactSystem(PRIORITY_ENEMY_CONTACT));
        engine.addSystem(new CameraSystem(camera, roomState, PRIORITY_CAMERA));
        engine.addSystem(new AnimationSystem(PRIORITY_ANIMATION));
        engine.addSystem(tiledMapRenderSystem);
        engine.addSystem(new RenderSystem(batch, camera, PRIORITY_ENTITY_RENDER));
        debugRenderSystem = new DebugRenderSystem(camera, mapLoader.getCollisionRects(), roomState, PRIORITY_DEBUG_RENDER);
        engine.addSystem(debugRenderSystem);

        levelManager = new LevelManager(engine, entityFactory, camera, tiledMapRenderSystem, mapLoader.getCollisionRects(), roomState, mapLoader);
        engine.addSystem(new LevelExitSystem(levelManager, PRIORITY_LEVEL_EXIT));

        Vector2 playerStart = mapLoader.findPlayerStart();
        Entity player = entityFactory.createPlayer(playerStart.x, playerStart.y);
        attachPlayerAnimations(player);
        engine.addEntity(player);

        entityFactory.spawnObjects(engine, mapLoader.getObjectLayer(), roomState);
        CameraSystem.snapToRoom(camera, roomState, playerStart.x, playerStart.y);

        uiSkin = SkinFactory.createBasicSkin();
        playerComponent = PLAYER.get(player);
        Viewport hudViewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT);
        Viewport touchViewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT);
        hudStage = new HudStage(hudViewport, uiSkin, assetManager, playerComponent);
        touchControlsStage = new TouchControlsStage(touchViewport, uiSkin, playerInputSystem);

        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(touchControlsStage);
        inputMultiplexer.addProcessor(hudStage);
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    game.setScreen(new MainMenuScreen(game));
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    private void attachPlayerAnimations(Entity player) {
        TextureRegion idleRegion = new TextureRegion(assetManager.get("gfx/player.png", Texture.class));
        TextureRegion attackRegion = new TextureRegion(assetManager.get("gfx/player_attack.png", Texture.class));
        AnimationComponent animationComponent = new AnimationComponent();
        animationComponent.animations.put(AnimationComponent.State.IDLE, new Animation<>(1f, idleRegion));
        animationComponent.animations.put(AnimationComponent.State.ATTACKING, new Animation<>(1f, attackRegion));
        player.add(animationComponent);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        viewport.apply();
        engine.update(Gdx.graphics.getDeltaTime());

        touchControlsStage.setInteractVisible(playerComponent.nearExit);

        hudStage.act(delta);
        hudStage.draw();
        touchControlsStage.act(delta);
        touchControlsStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        viewport.update(width, height);
        hudStage.getViewport().update(width, height, true);
        touchControlsStage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        assetManager.dispose();
        if (tiledMapRenderSystem != null) {
            tiledMapRenderSystem.dispose();
        }
        if (debugRenderSystem != null) {
            debugRenderSystem.dispose();
        }
        if (levelManager != null) {
            levelManager.dispose();
        }
        if (hudStage != null) {
            hudStage.dispose();
        }
        if (touchControlsStage != null) {
            touchControlsStage.dispose();
        }
        if (uiSkin != null) {
            uiSkin.dispose();
        }
    }
}
