package com.axehigh.platformer.screens;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.systems.AnimationSystem;
import com.axehigh.platformer.ecs.systems.CameraSystem;
import com.axehigh.platformer.ecs.systems.CollisionSystem;
import com.axehigh.platformer.ecs.systems.MovementSystem;
import com.axehigh.platformer.ecs.systems.PlayerInputSystem;
import com.axehigh.platformer.ecs.systems.RenderSystem;
import com.axehigh.platformer.ecs.systems.TiledMapRenderSystem;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.MapLoader;
import com.axehigh.platformer.ui.HudStage;
import com.axehigh.platformer.ui.SkinFactory;
import com.axehigh.platformer.ui.TouchControlsStage;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
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
    private static final int PRIORITY_MOVEMENT = 5;
    private static final int PRIORITY_COLLISION = 6;
    private static final int PRIORITY_CAMERA = 8;
    private static final int PRIORITY_ANIMATION = 10;
    private static final int PRIORITY_MAP_RENDER = 20;
    private static final int PRIORITY_ENTITY_RENDER = 30;

    private final AssetManager assetManager = new AssetManager();
    private final PooledEngine engine = new PooledEngine();
    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT, camera);

    private MapLoader mapLoader;
    private TiledMapRenderSystem tiledMapRenderSystem;
    private Skin uiSkin;
    private HudStage hudStage;
    private TouchControlsStage touchControlsStage;

    @Override
    public void show() {
        assetManager.load("gfx/player.png", Texture.class);
        assetManager.load("gfx/coin.png", Texture.class);
        assetManager.load("gfx/chest.png", Texture.class);
        assetManager.load("gfx/torch.png", Texture.class);
        assetManager.load("gfx/exit_gate.png", Texture.class);
        assetManager.load("gfx/heart.png", Texture.class);
        assetManager.load("gfx/sword.png", Texture.class);
        assetManager.load("gfx/bullet.png", Texture.class);
        assetManager.finishLoading();

        mapLoader = new MapLoader("maps/demo_room.tmx");
        EntityFactory entityFactory = new EntityFactory(assetManager);

        camera.position.set(GameConstants.VIRTUAL_WIDTH / 2f, GameConstants.VIRTUAL_HEIGHT / 2f, 0f);

        PlayerInputSystem playerInputSystem = new PlayerInputSystem(assetManager, PRIORITY_INPUT);
        tiledMapRenderSystem = new TiledMapRenderSystem(mapLoader.getMap(), camera, PRIORITY_MAP_RENDER);
        engine.addSystem(playerInputSystem);
        engine.addSystem(new MovementSystem(mapLoader.getCollisionRects(), PRIORITY_MOVEMENT));
        engine.addSystem(new CollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION));
        engine.addSystem(new CameraSystem(camera, PRIORITY_CAMERA));
        engine.addSystem(new AnimationSystem(PRIORITY_ANIMATION));
        engine.addSystem(tiledMapRenderSystem);
        engine.addSystem(new RenderSystem(batch, camera, PRIORITY_ENTITY_RENDER));

        Vector2 playerStart = mapLoader.findPlayerStart();
        Entity player = entityFactory.createPlayer(playerStart.x, playerStart.y);
        attachIdleAnimation(player);
        engine.addEntity(player);

        entityFactory.spawnObjects(engine, mapLoader.getObjectLayer());

        uiSkin = SkinFactory.createBasicSkin();
        PlayerComponent playerComponent = PLAYER.get(player);
        Viewport hudViewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT);
        Viewport touchViewport = new FitViewport(GameConstants.VIRTUAL_WIDTH, GameConstants.VIRTUAL_HEIGHT);
        hudStage = new HudStage(hudViewport, uiSkin, assetManager, playerComponent);
        touchControlsStage = new TouchControlsStage(touchViewport, uiSkin, playerInputSystem);

        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(touchControlsStage);
        inputMultiplexer.addProcessor(hudStage);
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    private void attachIdleAnimation(Entity player) {
        Texture texture = assetManager.get("gfx/player.png", Texture.class);
        TextureRegion region = new TextureRegion(texture);
        AnimationComponent animationComponent = new AnimationComponent();
        animationComponent.animations.put(AnimationComponent.State.IDLE, new Animation<>(1f, region));
        player.add(animationComponent);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        viewport.apply();
        engine.update(Gdx.graphics.getDeltaTime());

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
        if (mapLoader != null) {
            mapLoader.dispose();
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
