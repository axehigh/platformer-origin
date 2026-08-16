package com.axehigh.platformer.screens;

import com.axehigh.platformer.assets.GameAssetRegistry;
import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.common.BaseScreen;import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.systems.*;
import com.axehigh.platformer.map.*;
import com.axehigh.platformer.particles.ParticleHelper;
import com.axehigh.platformer.ui.HudStage;
import com.axehigh.platformer.ui.DeviceClass;
import com.axehigh.platformer.ui.LayoutMode;
import com.axehigh.platformer.ui.LayoutPrefs;
import com.axehigh.platformer.ui.TouchControlsStage;
import com.axehigh.platformer.util.FeatureFlags;
import com.axehigh.platformer.util.SaveManager;
import com.axehigh.platformer.viewport.OffsetFitViewport;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.GameConstants.*;
import static com.axehigh.platformer.assets.GameAssetRegistry.HERO_ASSET;
import static com.axehigh.platformer.ecs.components.AnimationComponent.State.*;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL;

/** Owns the Ashley Engine, the fixed-resolution viewport/camera, and drives the game loop. */
public class GameScreen extends BaseScreen {
    private static final int PRIORITY_INPUT = 0;
    private static final int PRIORITY_MUSIC = 1;
    private static final int PRIORITY_ENEMY = 4;
    private static final int PRIORITY_MOVEMENT = 5;
    private static final int PRIORITY_BOUNDS = 6;
    private static final int PRIORITY_MOVING_PLATFORM = 6;
    private static final int PRIORITY_COLLISION = 7;
    private static final int PRIORITY_MELEE = 8;
    private static final int PRIORITY_SFX = 8;
    private static final int PRIORITY_PICKUP = 8;
    private static final int PRIORITY_CHEST = 8;
    private static final int PRIORITY_ENEMY_CONTACT = 8;
    private static final int PRIORITY_LEVEL_EXIT = 8;
    private static final int PRIORITY_PLAYER_DEATH = 8;
    private static final int PRIORITY_CAMERA = 9;
    private static final int PRIORITY_ANIMATION = 10;
    private static final int PRIORITY_SQUASH = 25;
    private static final int PRIORITY_MAP_RENDER = 20;
    private static final int PRIORITY_ENTITY_RENDER = 30;
    private static final int PRIORITY_PARTICLE_RENDER = 35;
    private static final int PRIORITY_LIGHT_RENDER = 36;
    private static final int PRIORITY_DEBUG_RENDER = 40;

    private static final float DIALOG_PANEL_SCALE = 0.7f;
    private static final float DIALOG_PANEL_MARGIN = 40f;

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

    private TiledMapRenderSystem tiledMapRenderSystem;
    private DebugRenderSystem debugRenderSystem;
    private LightRenderSystem lightRenderSystem;
    private LevelManager levelManager;
    private PlayerComponent playerComponent;
    private Entity playerEntity;
    private HudStage hudStage;
    private TouchControlsStage touchControlsStage;
    private boolean gameOverActive = false;
    private boolean gamePaused = false;
    private boolean debugTouchLogging = false;

    /** Defaults to the catalog's first level. */
    public GameScreen(Game game) {
        this(game, LevelCatalog.levels().first().tmxPath);
    }

    public GameScreen(Game game, String levelPath) {
        super(game);
        this.levelPath = levelPath;
        this.saveData = null;
    }

    /** Resumes from a save: loads {@code saveData.levelPath} and applies its stats onto the new player. */
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

        PlayerInputSystem playerInputSystem = new PlayerInputSystem(assetManager, PRIORITY_INPUT);
        playerInputSystem.setUnitScale(scale);
        tiledMapRenderSystem = new TiledMapRenderSystem(mapLoader.getMap(), camera, PRIORITY_MAP_RENDER);
        engine.addSystem(playerInputSystem);

        EnemySystem enemySystem =         new EnemySystem(entityFactory, mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), mapLoader.getHazardRects(), roomState, PRIORITY_ENEMY);
        enemySystem.setUnitScale(scale);
        engine.addSystem(enemySystem);

        EnemyShootSystem shootSystem = new EnemyShootSystem(assetManager, roomState, PRIORITY_ENEMY);
        shootSystem.setUnitScale(scale);
        engine.addSystem(shootSystem);

        MovementSystem movementSystem = new MovementSystem(mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), PRIORITY_MOVEMENT);
        movementSystem.setUnitScale(scale);
        engine.addSystem(movementSystem);

        CollisionSystem collisionSystem = new CollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION);
        collisionSystem.setUnitScale(scale);
        engine.addSystem(collisionSystem);

        EnemyBulletCollisionSystem enemyBulletSystem = new EnemyBulletCollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION);
        enemyBulletSystem.setUnitScale(scale);
        engine.addSystem(enemyBulletSystem);
        engine.addSystem(new CollisionBoundsSystem(PRIORITY_BOUNDS));
        MovingPlatformSystem movingPlatformSystem = new MovingPlatformSystem(mapLoader.getCollisionRects(), roomState, PRIORITY_MOVING_PLATFORM);
        movingPlatformSystem.setUnitScale(scale);
        engine.addSystem(movingPlatformSystem);

        engine.addSystem(new MusicSystem(AudioManager.get(), PRIORITY_MUSIC));
        SfxSystem sfxSystem = new SfxSystem(AudioManager.get(), PRIORITY_SFX);
        engine.addSystem(sfxSystem);

        MeleeAttackSystem meleeSystem = new MeleeAttackSystem(assetManager, mapLoader.getSecretRects(),
            mapLoader.getCollisionRects(), mapLoader.getCollisionLayer(), sfxSystem, secretRoomRevealer, PRIORITY_MELEE);
        meleeSystem.setUnitScale(scale);
        engine.addSystem(meleeSystem);

        engine.addSystem(new PickupSystem(sfxSystem, PRIORITY_PICKUP));

        ChestSystem chestSystem = new ChestSystem(entityFactory, PRIORITY_CHEST);
        chestSystem.setUnitScale(scale);
        engine.addSystem(chestSystem);

        EnemyContactSystem enemyContactSystem = new EnemyContactSystem(PRIORITY_ENEMY_CONTACT);
        enemyContactSystem.setUnitScale(scale);
        engine.addSystem(enemyContactSystem);
        engine.addSystem(new HazardSystem(mapLoader.getHazardRects(), PRIORITY_ENEMY_CONTACT));
        engine.addSystem(new CameraSystem(camera, roomState, PRIORITY_CAMERA));
        engine.addSystem(new AnimationSystem(PRIORITY_ANIMATION));
        engine.addSystem(new SquashSystem(PRIORITY_SQUASH));
        engine.addSystem(tiledMapRenderSystem);
        engine.addSystem(new RenderSystem(batch, camera, PRIORITY_ENTITY_RENDER));
        engine.addSystem(new ParticleSystem(batch, camera, PRIORITY_PARTICLE_RENDER));
        lightRenderSystem = new LightRenderSystem(batch, camera, PRIORITY_LIGHT_RENDER);
        engine.addSystem(lightRenderSystem);
        debugRenderSystem = new DebugRenderSystem(camera, mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), mapLoader.getHazardRects(), roomState, PRIORITY_DEBUG_RENDER);
        engine.addSystem(debugRenderSystem);

        levelManager = new LevelManager(engine, entityFactory, viewport, tiledMapRenderSystem, mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), mapLoader.getHazardRects(), mapLoader.getSecretRects(), roomState, secretRoomRevealer, mapLoader);

        LevelExitSystem exitSystem = new LevelExitSystem(levelManager, PRIORITY_LEVEL_EXIT);
        exitSystem.setUnitScale(scale);
        engine.addSystem(exitSystem);

        engine.addSystem(new PlayerDeathSystem(this::onPlayerDeath, PRIORITY_PLAYER_DEATH));

        Vector2 playerStart = mapLoader.findPlayerStart();
        Entity player = entityFactory.createPlayer(playerStart.x, playerStart.y);
        playerEntity = player;
        attachPlayerAnimations(player);
        engine.addEntity(player);

        if (saveData != null) {
            applySaveData(player, saveData);
        }

        entityFactory.spawnObjects(engine, mapLoader.getObjectLayer(), roomState);
        entityFactory.spawnObjects(engine, mapLoader.getEnemiesLayer(), roomState);
        CameraSystem.snapToRoom(camera, roomState, playerStart.x, playerStart.y);

        playerComponent = PLAYER.get(player);
        Viewport hudViewport = new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT);
        Viewport touchViewport = new ExtendViewport(SCREEN_WIDTH, SCREEN_HEIGHT);
        hudStage = new HudStage(hudViewport, skin, assetManager, playerComponent);
        hudStage.getPauseButton().addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                togglePause();
            }
        });
        touchControlsStage = new TouchControlsStage(touchViewport, skin, playerInputSystem);

        DeviceClass savedDevice = LayoutPrefs.savedDevice();
        if (savedDevice != null) {
            DeviceClass.setSimulated(savedDevice);
            deviceClass = savedDevice;
            savedDevice.applyWindowSize();
        }
        layoutMode = LayoutPrefs.savedLayout() != null ? LayoutPrefs.savedLayout() : LayoutMode.defaultForDevice();
        touchControlsEnabled = LayoutMode.isTouchDevice();
        applyLayoutMode();
        CameraSystem.snapToRoom(camera, roomState, playerStart.x, playerStart.y);

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
        inputMultiplexer.addProcessor(touchControlsStage);
        inputMultiplexer.addProcessor(hudStage);
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    togglePause();
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

    private void showPauseDialog() {
        Dialog dialog = new Dialog("Paused", skin) {
            @Override
            protected void result(Object object) {
                gamePaused = false;
            }
        };
        dialog.getTitleLabel().setFontScale(FontScale);
        dialog.getContentTable().defaults().pad(UI_PADDING);
        dialog.getButtonTable().defaults().pad(UI_PADDING);

        TextButton resumeButton = new TextButton("Resume", skin);
        resumeButton.getLabel().setFontScale(FontScale);
        resumeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                dialog.hide();
                gamePaused = false;
            }
        });
        dialog.button(resumeButton);

        final TextButton musicButton = new TextButton("Music: " + (AudioManager.get().isMusicEnabled() ? "ON" : "OFF"), skin);
        musicButton.getLabel().setFontScale(FontScale);
        musicButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                AudioManager.get().setMusicEnabled(!AudioManager.get().isMusicEnabled());
                musicButton.setText("Music: " + (AudioManager.get().isMusicEnabled() ? "ON" : "OFF"));
            }
        });
        dialog.getContentTable().add(musicButton).minWidth(240f).pad(UI_PADDING).row();

        final TextButton sfxButton = new TextButton("Sound Effects: " + (AudioManager.get().isSfxEnabled() ? "ON" : "OFF"), skin);
        sfxButton.getLabel().setFontScale(FontScale);
        sfxButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                AudioManager.get().setSfxEnabled(!AudioManager.get().isSfxEnabled());
                sfxButton.setText("Sound Effects: " + (AudioManager.get().isSfxEnabled() ? "ON" : "OFF"));
            }
        });
        dialog.getContentTable().add(sfxButton).minWidth(240f).pad(UI_PADDING).row();

        final TextButton collisionDebugButton = new TextButton("Collision Debug: " + (DebugRenderSystem.isDebugEnabled() ? "ON" : "OFF"), skin);
        collisionDebugButton.getLabel().setFontScale(FontScale);
        collisionDebugButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                DebugRenderSystem.setDebugEnabled(!DebugRenderSystem.isDebugEnabled());
                collisionDebugButton.setText("Collision Debug: " + (DebugRenderSystem.isDebugEnabled() ? "ON" : "OFF"));
            }
        });

        final TextButton touchDebugButton = new TextButton("Touch Debug: " + (debugTouchLogging ? "ON" : "OFF"), skin);
        touchDebugButton.getLabel().setFontScale(FontScale);
        touchDebugButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                debugTouchLogging = !debugTouchLogging;
                touchDebugButton.setText("Touch Debug: " + (debugTouchLogging ? "ON" : "OFF"));
            }
        });

        final TextButton wallClimbButton = new TextButton("Wall Climb: " + (FeatureFlags.isWallClimbingEnabled() ? "ON" : "OFF"), skin);
        wallClimbButton.getLabel().setFontScale(FontScale);
        wallClimbButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                FeatureFlags.setWallClimbingEnabled(!FeatureFlags.isWallClimbingEnabled());
                wallClimbButton.setText("Wall Climb: " + (FeatureFlags.isWallClimbingEnabled() ? "ON" : "OFF"));
            }
        });

        Table debugRow = new Table();
        debugRow.add(collisionDebugButton).minWidth(240f).padRight(UI_PADDING);
        debugRow.add(touchDebugButton).minWidth(240f);
        dialog.getContentTable().add(debugRow).pad(UI_PADDING).row();

        final TextButton deviceButton = new TextButton(
            "Device: " + (DeviceClass.isSimulating() ? deviceClass : "Auto"), skin);
        deviceButton.getLabel().setFontScale(FontScale);
        deviceButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
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
                CameraSystem.snapToRoom(camera, roomState,
                    TRANSFORM.get(playerEntity).position.x, TRANSFORM.get(playerEntity).position.y);
                deviceButton.setText("Device: " + (deviceClass != null ? deviceClass : "Auto"));
            }
        });

        final TextButton layoutButton = new TextButton("Mobile Layout: " + layoutMode, skin);
        layoutButton.getLabel().setFontScale(FontScale);
        layoutButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                layoutMode = layoutMode.next();
                if (!LayoutMode.isTouchDevice()) {
                    deviceClass = DeviceClass.PHONE;
                    DeviceClass.setSimulated(DeviceClass.PHONE);
                    deviceClass.applyWindowSize();
                    deviceButton.setText("Device: " + deviceClass);
                }
                LayoutPrefs.save(deviceClass, layoutMode);
                applyLayoutMode();
                CameraSystem.snapToRoom(camera, roomState,
                    TRANSFORM.get(playerEntity).position.x, TRANSFORM.get(playerEntity).position.y);
                layoutButton.setText("Mobile Layout: " + layoutMode);
            }
        });

        Table featureRow = new Table();
        featureRow.add(wallClimbButton).minWidth(240f).padRight(UI_PADDING);
        featureRow.add(deviceButton).minWidth(240f).padRight(UI_PADDING);
        featureRow.add(layoutButton).minWidth(240f);
        dialog.getContentTable().add(featureRow).pad(UI_PADDING).row();

        TextButton exitButton = new TextButton("Exit", skin);
        exitButton.getLabel().setFontScale(FontScale);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                changeScreen(new MainMenuScreen(game));
            }
        });

        dialog.button(exitButton);

        dialog.show(stage);
        fitDialogToPanel(dialog);
    }

    private void showGameOverDialog() {
        SaveData currentSave = SaveManager.hasSave() ? SaveManager.load() : new SaveData();

        Dialog dialog = new Dialog("Game Over", skin) {
            @Override
            protected void result(Object object) {
            }
        };
        dialog.getTitleLabel().setFontScale(FontScale);
        Label deathLabel = new Label("You died!", skin);
        deathLabel.setFontScale(FontScale);
        dialog.text(deathLabel);

        if (currentSave.triesRemaining > 0) {
            TextButton continueButton = new TextButton("Continue (uses 1 try)", skin);
            continueButton.getLabel().setFontScale(FontScale);
            continueButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    AudioManager.get().playClick();
                    currentSave.triesRemaining--;
                    SaveManager.save(currentSave);
                    levelManager.loadLevel(levelManager.getCurrentLevelPath(), playerEntity);
                    playerComponent.health = playerComponent.maxHealth;
                    playerComponent.isDead = false;
                    dialog.hide();
                    gameOverActive = false;
                }
            });
            dialog.button(continueButton);
        }

        TextButton exitButton = new TextButton("Exit to Main Menu", skin);
        exitButton.getLabel().setFontScale(FontScale);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                AudioManager.get().playClick();
                changeScreen(new MainMenuScreen(game));
            }
        });
        dialog.button(exitButton);

        dialog.show(stage);
        sizeDialogToPanel(dialog);
    }

    private void sizeDialogToPanel(Dialog dialog) {
        TextureRegionDrawable panel = (TextureRegionDrawable) skin.getDrawable("table");
        float width = panel.getRegion().getRegionWidth() * DIALOG_PANEL_SCALE;
        float height = panel.getRegion().getRegionHeight() * DIALOG_PANEL_SCALE;
        dialog.setSize(width, height);
        dialog.setPosition(Math.round((stage.getWidth() - width) / 2f), Math.round((stage.getHeight() - height) / 2f));
    }

    private void fitDialogToPanel(Dialog dialog) {
        TextureRegionDrawable panel = (TextureRegionDrawable) skin.getDrawable("table");
        float panelW = panel.getRegion().getRegionWidth();
        float panelH = panel.getRegion().getRegionHeight();
        float scale = Math.max(dialog.getPrefWidth() / panelW, (dialog.getPrefHeight() + DIALOG_PANEL_MARGIN) / panelH);
        float maxScale = Math.min((stage.getWidth() * 0.95f) / panelW, (stage.getHeight() * 0.95f) / panelH);
        scale = Math.min(scale, maxScale);
        float width = panelW * scale;
        float height = panelH * scale;
        dialog.setSize(width, height);
        dialog.setPosition(Math.round((stage.getWidth() - width) / 2f), Math.round((stage.getHeight() - height) / 2f));
    }

    private void applySaveData(Entity player, SaveData saveData) {
        PlayerComponent playerComponent = PLAYER.get(player);
        playerComponent.health = saveData.health;
        playerComponent.maxHealth = saveData.maxHealth;
        playerComponent.coins = saveData.coins;
        playerComponent.items = saveData.items;
        playerComponent.swordDamage = saveData.swordDamage;
        playerComponent.sharpEdgePurchased = saveData.sharpEdgePurchased;
        playerComponent.daggerBandolierPurchased = saveData.daggerBandolierPurchased;
        playerComponent.ironHeartCount = saveData.ironHeartCount;
    }

    private void attachPlayerAnimations(Entity player) {
        TextureAtlas heroAtlas = assetManager.get(HERO_ASSET, TextureAtlas.class);

        AnimationComponent animationComponent = new AnimationComponent();
        animationComponent.animations.put(IDLE, new Animation<>(0.15f, heroAtlas.findRegions("idle"), LOOP));
        animationComponent.animations.put(WALKING, new Animation<>(0.1f, heroAtlas.findRegions("walk"), LOOP));
        animationComponent.animations.put(RUNNING, new Animation<>(0.1f, heroAtlas.findRegions("run"), LOOP));
        animationComponent.animations.put(JUMPING, new Animation<>(0.1f, heroAtlas.findRegions("jump"), NORMAL));
        animationComponent.animations.put(DOUBLE_JUMPING, new Animation<>(0.1f, heroAtlas.findRegions("high_jump"), NORMAL));
        animationComponent.animations.put(WALL_CLIMBING, new Animation<>(0.1f, heroAtlas.findRegions("climb"), LOOP));
        animationComponent.animations.put(ATTACKING, new Animation<>(0.066f, heroAtlas.findRegions("attack"), NORMAL));
        animationComponent.animations.put(DEATH, new Animation<>(0.1f, heroAtlas.findRegions("death"), NORMAL));
        animationComponent.animations.put(HURT, new Animation<>(0.1f, heroAtlas.findRegions("hurt"), NORMAL));

        player.add(animationComponent);
    }

    @Override
    public void render(float delta) {
        applyLayoutMode();
        syncViewports();

        super.render(delta);

        viewport.apply();

        if (!gameOverActive && !gamePaused) {
            engine.update(Gdx.graphics.getDeltaTime());
        }

        touchControlsStage.setInteractVisible(playerComponent.nearExit);
        touchControlsStage.setDropVisible(playerComponent.onDropTile);

        hudStage.getViewport().apply();
        hudStage.act(delta);
        hudStage.draw();
        if (touchControlsEnabled) {
            touchControlsStage.getViewport().apply();
            touchControlsStage.act(delta);
            touchControlsStage.draw();
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

    @Override
    public void dispose() {
        super.dispose();
        AudioManager.get().stopMusic();
        ParticleHelper.dispose();
        batch.dispose();
        assetManager.dispose();
        if (tiledMapRenderSystem != null) {
            tiledMapRenderSystem.dispose();
        }
        if (debugRenderSystem != null) {
            debugRenderSystem.dispose();
        }
        if (lightRenderSystem != null) {
            lightRenderSystem.dispose();
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
//        if (skin != null) {
//            skin.dispose();
//        }
    }
}
