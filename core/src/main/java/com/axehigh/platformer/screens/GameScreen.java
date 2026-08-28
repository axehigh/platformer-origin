package com.axehigh.platformer.screens;

import com.axehigh.platformer.assets.GameAssetRegistry;
import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.common.BaseScreen;
import com.axehigh.platformer.ecs.GameSystems;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.axehigh.platformer.ecs.systems.CameraSystem;
import com.axehigh.platformer.map.*;
import com.axehigh.platformer.particles.ParticleHelper;
import com.axehigh.platformer.ui.*;
import com.axehigh.platformer.util.SpawnSafety;
import com.axehigh.platformer.viewport.OffsetFitViewport;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.GameConstants.*;
import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_UI_GFX;
import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Owns the Ashley Engine, the fixed-resolution viewport/camera, and drives the game loop.
 * System construction lives in {@link GameSystems}, dialogs in {@link PauseDialog} /
 * {@link GameOverDialog}; this screen stays lifecycle, layout, and input orchestration.
 */
public class GameScreen extends BaseScreen implements PauseDialog.Listener, GameOverDialog.Listener {
    private final AssetManager assetManager = new AssetManager();
    private final PooledEngine engine = new PooledEngine();
    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final OffsetFitViewport viewport = new OffsetFitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);

    private final String levelPath;
    private final SaveData saveData;

    private RoomState roomState;
    private LayoutMode layoutMode = LayoutMode.CORNER_OVERLAY;
    private DeviceClass deviceClass = DeviceClass.simulated() != null ? DeviceClass.simulated() : DeviceClass.DESKTOP;
    private boolean touchControlsEnabled = false;

    private GameSystems systems;
    private PlayerComponent playerComponent;
    private Entity playerEntity;
    private HudStage hudStage;
    private TouchControlsStage touchControlsStage;
    private InventoryBarStage inventoryBarStage;
    private boolean gameOverActive = false;
    private boolean gamePaused = false;
    private boolean inventoryOpen = false;
    private boolean debugTouchLogging = false;

    /** Largest single-step delta allowed for the ECS simulation; prevents tunneling on Android's first-frame hitch. */
    private static final float MAX_FRAME_DELTA = 1f / 30f;

    /**
     * Defaults to the catalog's first level.
     */
    public GameScreen(Game game) {
        this(game, LevelCatalog.levels().first().tmxPath);
    }

    public GameScreen(Game game, String levelPath) {
        super(game);
        this.levelPath = levelPath;
        this.saveData = null;
    }

    /**
     * Resumes from a save: loads {@code saveData.levelPath} and applies its stats onto the new player.
     */
    public GameScreen(Game game, SaveData saveData) {
        super(game);
        this.levelPath = saveData.levelPath;
        this.saveData = saveData;
    }

    @Override
    public void show() {
        super.show();
        GameAssetRegistry.loadAssets(assetManager);
        assetManager.finishLoading();
        ParticleHelper.load(assetManager);

        MapLoader mapLoader = new MapLoader(levelPath);
        float scale = mapLoader.getTileWidth() / 16f;
        viewport.setWorldSize(VIRTUAL_WIDTH * scale, VIRTUAL_HEIGHT * scale);
        viewport.apply();

        EntityFactory entityFactory = new EntityFactory(assetManager);
        entityFactory.setUnitScale(scale);

        RoomState roomState = new RoomState();
        roomState.rooms.addAll(mapLoader.getRooms());
        this.roomState = roomState;

        SecretRoomRevealer secretRoomRevealer = new SecretRoomRevealer(engine, entityFactory, roomState);
        secretRoomRevealer.setRooms(mapLoader.getSecretRooms());
        secretRoomRevealer.setHideLayer(mapLoader.getSecretHideLayer());

        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0f);

        float killY = -mapLoader.getMapWorldHeight();
        systems = new GameSystems(engine, batch, camera, skin, assetManager, mapLoader, roomState,
            secretRoomRevealer, entityFactory, viewport, scale, this::onPlayerDeath, killY);

        Vector2 playerStart = SpawnSafety.findSafeSpawn(
            mapLoader.findPlayerStart(),
            mapLoader.getCollisionRects(),
            SpriteConstants.PlayerCollisionWidth * scale * SpriteConstants.PlayerScale,
            SpriteConstants.PlayerCollisionHeight * scale * SpriteConstants.PlayerScale);
        Entity player = entityFactory.createPlayer(playerStart.x, playerStart.y);
        playerEntity = player;
        engine.addEntity(player);
        entityFactory.installFeedbackListeners(engine);

        if (saveData != null) {
            saveData.applyTo(PLAYER.get(player));
        }

        entityFactory.spawnObjects(engine, mapLoader.getObjectLayer(), roomState);
        entityFactory.spawnObjects(engine, mapLoader.getEnemiesLayer(), roomState);
        entityFactory.spawnEffects(engine, mapLoader.getEffectSpawns(), roomState);
        CameraSystem.snapToRoom(camera, roomState, playerStart.x, playerStart.y,
            layoutMode == LayoutMode.BAND_ZOOM);

        playerComponent = PLAYER.get(player);
        Viewport hudViewport = new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT);
        Viewport touchViewport = new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT);
        hudStage = new HudStage(hudViewport, skin, assetManager, playerComponent, BUFF.get(player));
        hudStage.getPauseButton().addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                togglePause();
            }
        });
        TextureRegionDrawable bagDrawable = new TextureRegionDrawable(assetManager.get(ORIGIN_UI_GFX, TextureAtlas.class).findRegion("bag"));
        bagDrawable.setMinWidth(UI_Button_Contextual_Size / 2f);
        bagDrawable.setMinHeight(UI_Button_Contextual_Size / 2f);
        touchControlsStage = new TouchControlsStage(touchViewport, skin, systems.playerInputSystem,
                bagDrawable,
                this::toggleInventory);

        inventoryBarStage = new InventoryBarStage(new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT), skin, assetManager, playerComponent, playerEntity);
        inventoryBarStage.setOnTapOutside(this::toggleInventory);

        DeviceClass savedDevice = LayoutPrefs.savedDevice();
        if (savedDevice != null) {
            DeviceClass.setSimulated(savedDevice);
            deviceClass = savedDevice;
            savedDevice.applyWindowSize();
        }
        layoutMode = LayoutPrefs.savedLayout() != null ? LayoutPrefs.savedLayout() : LayoutMode.defaultForDevice();
        touchControlsEnabled = LayoutMode.isTouchDevice();
        applyLayoutMode();
        CameraSystem.snapToRoom(camera, roomState, playerStart.x, playerStart.y,
            layoutMode == LayoutMode.BAND_ZOOM);

        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                boolean shiftHeld = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                if (shiftHeld && keycode == Input.Keys.D) {
                    debugTouchLogging = !debugTouchLogging;
                    Gdx.app.log("TouchDebug", "logging " + (debugTouchLogging ? "ON" : "OFF"));
                    return false;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (debugTouchLogging) {
                    logTouch(screenX, screenY);
                }
                return false;
            }
        });
        inputMultiplexer.addProcessor(stage);
        inputMultiplexer.addProcessor(inventoryBarStage);
        inputMultiplexer.addProcessor(touchControlsStage);
        inputMultiplexer.addProcessor(hudStage);
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    if (inventoryOpen) {
                        toggleInventory();
                    } else {
                        togglePause();
                    }
                    return true;
                }
                if (keycode == Input.Keys.I) {
                    if (!gamePaused || inventoryOpen) {
                        toggleInventory();
                    }
                    return true;
                }
                if (keycode == Input.Keys.Q) {
                    game.setScreen(new MainMenuScreen(game));
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    private void onPlayerDeath() {
        gameOverActive = true;
        showGameOverDialog();
    }

    private void togglePause() {
        // Can't pause while the game-over dialog is up, or while the death animation is still
        // playing (the dialog appears once PlayerDeathSystem's death-wait elapses).
        if (gameOverActive || playerComponent.isDead) return;
        gamePaused = !gamePaused;
        if (gamePaused) {
            showPauseDialog();
        }
    }

    private void toggleInventory() {
        // Can't open the inventory while the game-over dialog is up or during the death animation.
        if (gameOverActive || playerComponent.isDead) return;
        inventoryOpen = !inventoryOpen;
        inventoryBarStage.setOpen(inventoryOpen);
    }

    private void showPauseDialog() {
        PauseDialog dialog = new PauseDialog(skin, this);
        dialog.show(stage);
        DialogPanelFitter.fitToPanel(skin, stage, dialog);
    }

    private void showGameOverDialog() {
        GameOverDialog dialog = new GameOverDialog(skin, this);
        dialog.show(stage);
        DialogPanelFitter.sizeToPanel(skin, stage, dialog);
    }

    @Override
    public void render(float delta) {
        applyLayoutMode();
        syncViewports();

        super.render(delta);

        viewport.apply();

        if (!gameOverActive && !gamePaused) {
            engine.update(Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_DELTA));
        }

        touchControlsStage.setInteractVisible(playerComponent.nearExit);
        touchControlsStage.setDropVisible(playerComponent.onDropTile);
        int totalPotions = 0;
        for (PotionType t : PotionType.values()) {
            totalPotions += playerComponent.countPotion(t);
        }
        touchControlsStage.setInventoryAlpha(totalPotions > 0 ? 1f : 0.4f);

        hudStage.getViewport().apply();
        hudStage.act(delta);
        hudStage.draw();
        if (touchControlsEnabled) {
            touchControlsStage.getViewport().apply();
            touchControlsStage.act(delta);
            touchControlsStage.draw();
        }
        if (inventoryOpen) {
            inventoryBarStage.getViewport().apply();
            inventoryBarStage.act(delta);
            inventoryBarStage.draw();
        }
    }

    /**
     * Applies the current {@link LayoutMode} to the game viewport (bottom band) and camera (zoom),
     * gating the touch overlay to touch devices (plus any faked {@link DeviceClass}). The control
     * band is reserved only on touch devices, but the {@link LayoutMode#BAND_ZOOM} camera zoom
     * applies everywhere, so desktop renders the zoomed big-tile view without a band. Called every
     * frame so a mid-run mode switch takes effect immediately.
     */
    private void applyLayoutMode() {
        if (systems == null) {
            return;
        }
        boolean isBandZoom = layoutMode == LayoutMode.BAND_ZOOM;
        systems.cameraSystem.setBandZoom(isBandZoom);
        systems.levelManager.setBandZoom(isBandZoom);

        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        touchControlsEnabled = LayoutMode.isTouchDevice();
        touchControlsStage.setEnabled(touchControlsEnabled);

        float zoom = 1f;
        int bandPx = 0;
        if (touchControlsEnabled) {
            float stageScale = Math.max(width / SCREEN_WIDTH, height / SCREEN_HEIGHT);
            bandPx = Math.min(
                Math.round(UI_CONTROL_BAND_HEIGHT * stageScale),
                Math.round(height * UI_BAND_SCREEN_FRACTION));
            switch (layoutMode) {
                case CORNER_OVERLAY:
                    bandPx = 0;
                    touchControlsStage.setAlpha(UI_BUTTON_ALPHA);
                    break;
                case BAND:
                    touchControlsStage.setAlpha(UI_BUTTON_ALPHA_SOLID);
                    break;
                case BAND_ZOOM:
                    zoom = MOBILE_ZOOM;
                    touchControlsStage.setAlpha(UI_BUTTON_ALPHA_SOLID);
                    break;
            }
        } else if (layoutMode == LayoutMode.BAND_ZOOM) {
            zoom = MOBILE_ZOOM;
        }
        viewport.setBottomBandPx(bandPx);
        camera.zoom = zoom;
        viewport.update(width, height);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        super.resize(width, height);
        applyLayoutMode();
        hudStage.getViewport().update(width, height, true);
        touchControlsStage.getViewport().update(width, height, true);
        inventoryBarStage.getViewport().update(width, height, true);
    }

    /**
     * Self-heals stale viewports: Android can drop or mis-order resize events
     * during heavy level transitions, leaving the Scene2D stages sized to a
     * stale surface while touch coordinates no longer line up with the buttons.
     */
    private void syncViewports() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (viewport.getScreenWidth() != width || viewport.getScreenHeight() != height) {
            viewport.update(width, height);
        }
        if (stage.getViewport().getScreenWidth() != width || stage.getViewport().getScreenHeight() != height) {
            stage.getViewport().update(width, height, true);
        }
        if (transitionStage.getViewport().getScreenWidth() != width || transitionStage.getViewport().getScreenHeight() != height) {
            transitionStage.getViewport().update(width, height, true);
        }
        if (hudStage.getViewport().getScreenWidth() != width || hudStage.getViewport().getScreenHeight() != height) {
            hudStage.getViewport().update(width, height, true);
        }
        if (touchControlsStage.getViewport().getScreenWidth() != width || touchControlsStage.getViewport().getScreenHeight() != height) {
            touchControlsStage.getViewport().update(width, height, true);
        }
        if (inventoryBarStage.getViewport().getScreenWidth() != width || inventoryBarStage.getViewport().getScreenHeight() != height) {
            inventoryBarStage.getViewport().update(width, height, true);
        }
    }

    private void logTouch(int screenX, int screenY) {
        Viewport touchViewport = touchControlsStage.getViewport();
        Vector2 stageCoords = touchViewport.unproject(new Vector2(screenX, screenY));
        Actor hit = touchControlsStage.hit(stageCoords.x, stageCoords.y, true);
        Gdx.app.log("TouchDebug",
            "surface=" + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
                + " gameVp=" + (int) viewport.getScreenWidth() + "x" + (int) viewport.getScreenHeight()
                + " baseStageVp=" + (int) stage.getViewport().getScreenWidth() + "x" + (int) stage.getViewport().getScreenHeight()
                + " touchVp=" + (int) touchViewport.getScreenWidth() + "x" + (int) touchViewport.getScreenHeight()
                + " raw=" + screenX + "," + screenY
                + " ->stage=" + Math.round(stageCoords.x) + "," + Math.round(stageCoords.y)
                + " hit=" + (hit != null ? hit.getClass().getSimpleName() : "none"));
    }

    // --- PauseDialog.Listener ---

    @Override
    public void onResume() {
        gamePaused = false;
    }

    @Override
    public boolean isTouchDebugOn() {
        return debugTouchLogging;
    }

    @Override
    public void setTouchDebugOn(boolean on) {
        debugTouchLogging = on;
    }

    @Override
    public String deviceLabel() {
        return deviceClass != null ? String.valueOf(deviceClass) : "Auto";
    }

    @Override
    public void cycleDevice() {
        deviceClass = DeviceClass.nextWithAuto(deviceClass);
        DeviceClass.setSimulated(deviceClass);
        if (deviceClass != null) {
            layoutMode = deviceClass.defaultLayout();
            deviceClass.applyWindowSize();
        } else {
            layoutMode = LayoutMode.defaultForDevice();
        }
        LayoutPrefs.save(deviceClass, layoutMode);
        applyLayoutMode();
        snapCameraToPlayerRoom();
    }

    @Override
    public String layoutLabel() {
        return layoutMode.name();
    }

    @Override
    public void cycleLayout() {
        layoutMode = layoutMode.next();
        if (!LayoutMode.isTouchDevice()) {
            deviceClass = DeviceClass.PHONE;
            DeviceClass.setSimulated(DeviceClass.PHONE);
            deviceClass.applyWindowSize();
        }
        LayoutPrefs.save(deviceClass, layoutMode);
        applyLayoutMode();
        snapCameraToPlayerRoom();
    }

    // --- GameOverDialog.Listener ---

    @Override
    public void onContinue() {
        systems.levelManager.loadLevel(systems.levelManager.getCurrentLevelPath(), playerEntity);
        playerComponent.health = playerComponent.maxHealth;
        playerComponent.isDead = false;
        gameOverActive = false;
    }

    @Override
    public void onExit() {
        changeScreen(new MainMenuScreen(game));
    }

    private void snapCameraToPlayerRoom() {
        CameraSystem.snapToRoom(camera, roomState,
            TRANSFORM.get(playerEntity).position.x, TRANSFORM.get(playerEntity).position.y,
            layoutMode == LayoutMode.BAND_ZOOM);
    }

    @Override
    public void dispose() {
        super.dispose();
        AudioManager.get().stopMusic();
        ParticleHelper.dispose();
        batch.dispose();
        assetManager.dispose();
        if (systems != null) {
            systems.tiledMapRenderSystem.dispose();
            systems.debugRenderSystem.dispose();
            systems.lightRenderSystem.dispose();
            systems.levelManager.dispose();
        }
        if (hudStage != null) {
            hudStage.dispose();
        }
        if (touchControlsStage != null) {
            touchControlsStage.dispose();
        }
        if (inventoryBarStage != null) {
            inventoryBarStage.dispose();
        }
    }
}
