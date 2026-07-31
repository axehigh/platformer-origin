package com.axehigh.platformer.screens;

import com.axehigh.platformer.assets.GameAssetRegistry;
import com.axehigh.platformer.common.BaseScreen;import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.systems.*;
import com.axehigh.platformer.map.*;
import com.axehigh.platformer.ui.HudStage;
import com.axehigh.platformer.ui.TouchControlsStage;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import static com.axehigh.platformer.GameConstants.*;
import static com.axehigh.platformer.assets.GameAssetRegistry.HERO_ASSET;
import static com.axehigh.platformer.ecs.components.AnimationComponent.State.*;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL;

/** Owns the Ashley Engine, the fixed-resolution viewport/camera, and drives the game loop. */
public class GameScreen extends BaseScreen {
    private static final int PRIORITY_INPUT = 0;
    private static final int PRIORITY_ENEMY = 4;
    private static final int PRIORITY_MOVEMENT = 5;
    private static final int PRIORITY_BOUNDS = 6;
    private static final int PRIORITY_COLLISION = 7;
    private static final int PRIORITY_MELEE = 8;
    private static final int PRIORITY_PICKUP = 8;
    private static final int PRIORITY_CHEST = 8;
    private static final int PRIORITY_ENEMY_CONTACT = 8;
    private static final int PRIORITY_LEVEL_EXIT = 8;
    private static final int PRIORITY_PLAYER_DEATH = 8;
    private static final int PRIORITY_CAMERA = 9;
    private static final int PRIORITY_ANIMATION = 10;
    private static final int PRIORITY_MAP_RENDER = 20;
    private static final int PRIORITY_ENTITY_RENDER = 30;
    private static final int PRIORITY_DEBUG_RENDER = 40;

    private final AssetManager assetManager = new AssetManager();
    private final PooledEngine engine = new PooledEngine();
    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);

    private final String levelPath;
    private final SaveData saveData;

    private TiledMapRenderSystem tiledMapRenderSystem;
    private DebugRenderSystem debugRenderSystem;
    private LevelManager levelManager;
    private PlayerComponent playerComponent;
    private Entity playerEntity;
    private HudStage hudStage;
    private TouchControlsStage touchControlsStage;
    private boolean gameOverActive = false;
    private boolean gamePaused = false;
    private boolean musicEnabled = true;

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

        MapLoader mapLoader = new MapLoader(levelPath);
        float scale = mapLoader.getTileWidth() / 16f;
        viewport.setWorldSize(VIRTUAL_WIDTH * scale, VIRTUAL_HEIGHT * scale);
        viewport.apply();

        EntityFactory entityFactory = new EntityFactory(assetManager);
        entityFactory.setUnitScale(scale);

        RoomState roomState = new RoomState();
        roomState.rooms.addAll(mapLoader.getRooms());

        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0f);

        PlayerInputSystem playerInputSystem = new PlayerInputSystem(assetManager, PRIORITY_INPUT);
        playerInputSystem.setUnitScale(scale);
        tiledMapRenderSystem = new TiledMapRenderSystem(mapLoader.getMap(), camera, PRIORITY_MAP_RENDER);
        engine.addSystem(playerInputSystem);

        EnemySystem enemySystem = new EnemySystem(mapLoader.getCollisionRects(), roomState, PRIORITY_ENEMY);
        enemySystem.setUnitScale(scale);
        engine.addSystem(enemySystem);

        EnemyShootSystem shootSystem = new EnemyShootSystem(assetManager, roomState, PRIORITY_ENEMY);
        shootSystem.setUnitScale(scale);
        engine.addSystem(shootSystem);

        MovementSystem movementSystem = new MovementSystem(mapLoader.getCollisionRects(), PRIORITY_MOVEMENT);
        movementSystem.setUnitScale(scale);
        engine.addSystem(movementSystem);

        CollisionSystem collisionSystem = new CollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION);
        collisionSystem.setUnitScale(scale);
        engine.addSystem(collisionSystem);

        engine.addSystem(new EnemyBulletCollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION));
        engine.addSystem(new CollisionBoundsSystem(PRIORITY_BOUNDS));

        MeleeAttackSystem meleeSystem = new MeleeAttackSystem(assetManager, PRIORITY_MELEE);
        meleeSystem.setUnitScale(scale);
        engine.addSystem(meleeSystem);

        engine.addSystem(new PickupSystem(PRIORITY_PICKUP));

        ChestSystem chestSystem = new ChestSystem(entityFactory, PRIORITY_CHEST);
        chestSystem.setUnitScale(scale);
        engine.addSystem(chestSystem);

        engine.addSystem(new EnemyContactSystem(PRIORITY_ENEMY_CONTACT));
        engine.addSystem(new CameraSystem(camera, roomState, PRIORITY_CAMERA));
        engine.addSystem(new AnimationSystem(PRIORITY_ANIMATION));
        engine.addSystem(tiledMapRenderSystem);
        engine.addSystem(new RenderSystem(batch, camera, PRIORITY_ENTITY_RENDER));
        debugRenderSystem = new DebugRenderSystem(camera, mapLoader.getCollisionRects(), roomState, PRIORITY_DEBUG_RENDER);
        engine.addSystem(debugRenderSystem);

        levelManager = new LevelManager(engine, entityFactory, viewport, tiledMapRenderSystem, mapLoader.getCollisionRects(), roomState, mapLoader);

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
        Viewport hudViewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        Viewport touchViewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        hudStage = new HudStage(hudViewport, skin, assetManager, playerComponent);
        hudStage.getPauseButton().addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                togglePause();
            }
        });
        touchControlsStage = new TouchControlsStage(touchViewport, skin, playerInputSystem);

        InputMultiplexer inputMultiplexer = new InputMultiplexer();
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
        if (gameOverActive) return;
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
        dialog.getContentTable().defaults().pad(2f);
        dialog.getButtonTable().defaults().pad(2f);

        TextButton resumeButton = new TextButton("Resume", skin);
        resumeButton.getLabel().setFontScale(FontScale);
        resumeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                dialog.hide();
                gamePaused = false;
            }
        });
        dialog.button(resumeButton);

        final TextButton musicButton = new TextButton("Music: " + (musicEnabled ? "ON" : "OFF"), skin);
        musicButton.getLabel().setFontScale(FontScale);
        musicButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                musicEnabled = !musicEnabled;
                musicButton.setText("Music: " + (musicEnabled ? "ON" : "OFF"));
                // Placeholder for actual music toggle logic
            }
        });
        dialog.getContentTable().add(musicButton).width(80f).pad(4f).row();

        TextButton exitButton = new TextButton("Exit", skin);
        exitButton.getLabel().setFontScale(FontScale);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                game.setScreen(new MainMenuScreen(game));
            }
        });

        dialog.button(exitButton);

        dialog.show(hudStage);
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
                    currentSave.triesRemaining--;
                    SaveManager.save(currentSave);
                    levelManager.loadLevel(levelManager.getCurrentLevelPath(), playerEntity);
                    playerComponent.health = playerComponent.maxHealth;
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
                game.setScreen(new MainMenuScreen(game));
            }
        });
        dialog.button(exitButton);

        dialog.show(hudStage);
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
        animationComponent.animations.put(ATTACKING, new Animation<>(0.1f, heroAtlas.findRegions("attack"), NORMAL));
        animationComponent.animations.put(DEATH, new Animation<>(0.1f, heroAtlas.findRegions("death"), NORMAL));
        animationComponent.animations.put(HURT, new Animation<>(0.1f, heroAtlas.findRegions("hurt"), NORMAL));

        player.add(animationComponent);
    }

    @Override
    public void render(float delta) {
        super.render(delta);

        if (!gameOverActive && !gamePaused) {
            engine.update(Gdx.graphics.getDeltaTime());
        }

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
    public void dispose() {
        super.dispose();
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
//        if (skin != null) {
//            skin.dispose();
//        }
    }
}
