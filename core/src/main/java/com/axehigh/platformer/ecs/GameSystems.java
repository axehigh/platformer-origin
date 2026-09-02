package com.axehigh.platformer.ecs;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.ecs.systems.*;
import com.axehigh.platformer.map.*;
import com.axehigh.platformer.viewport.OffsetFitViewport;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import static com.axehigh.platformer.assets.GameAssetRegistry.BACKGROUND_FAR;
import static com.axehigh.platformer.assets.GameAssetRegistry.BACKGROUND_NEAR;

/**
 * Builds and wires every Ashley system for a live gameplay session, in fixed priority order
 * (see "System wiring &amp; priority order" in {@code resources/docs-ai/ashley-ecs.md}), and
 * creates the {@link LevelManager} those systems share. Exposes only the systems the owning
 * screen keeps direct handles on; everything else is reachable through the engine.
 */
public class GameSystems {
    private static final int PRIORITY_INPUT = 0;
    private static final int PRIORITY_MUSIC = 1;
    private static final int PRIORITY_ENEMY = 4;
    private static final int PRIORITY_BUFF = 4;
    private static final int PRIORITY_TRAP = 4;
    private static final int PRIORITY_MOVEMENT = 5;
    private static final int PRIORITY_BOUNDS = 6;
    private static final int PRIORITY_MOVING_PLATFORM = 6;
    private static final int PRIORITY_COLLISION = 7;
    private static final int PRIORITY_MELEE = 8;
    private static final int PRIORITY_SFX = 8;
    private static final int PRIORITY_PICKUP = 8;
    private static final int PRIORITY_CHEST = 8;
    private static final int PRIORITY_ENEMY_CONTACT = 8;
    private static final int PRIORITY_TRAP_CONTACT = 8;
    private static final int PRIORITY_LEVEL_EXIT = 8;
    private static final int PRIORITY_PLAYER_DEATH = 8;
    private static final int PRIORITY_CAMERA = 9;
    private static final int PRIORITY_ANIMATION = 10;
    private static final int PRIORITY_SQUASH = 25;
    private static final int PRIORITY_MAP_RENDER = 20;
    private static final int PRIORITY_BACKGROUND_RENDER = 19;
    private static final int PRIORITY_ENTITY_RENDER = 30;
    private static final int PRIORITY_FLOATING_MESSAGE = 31;
    private static final int PRIORITY_PARTICLE_RENDER = 35;
    private static final int PRIORITY_LIGHT_RENDER = 36;
    private static final int PRIORITY_VIGNETTE_RENDER = 37;
    private static final int PRIORITY_DEBUG_RENDER = 40;

    public final PlayerInputSystem playerInputSystem;
    public final TiledMapRenderSystem tiledMapRenderSystem;
    public final DebugRenderSystem debugRenderSystem;
    public final LightRenderSystem lightRenderSystem;
    public final VignetteRenderSystem vignetteRenderSystem;
    public final CameraSystem cameraSystem;
    public final ChestSystem chestSystem;
    public final LevelManager levelManager;

    public GameSystems(PooledEngine engine, SpriteBatch batch, OrthographicCamera camera, Skin skin,
                       AssetManager assetManager, MapLoader mapLoader, RoomState roomState,
                       SecretRoomRevealer secretRoomRevealer, EntityFactory entityFactory,
                       OffsetFitViewport viewport, float unitScale, Runnable onPlayerDeath,
                       LevelExitSystem.LevelTransition onLevelTransition, Runnable onVictory, float killY) {
        playerInputSystem = new PlayerInputSystem(assetManager, PRIORITY_INPUT);
        playerInputSystem.setUnitScale(unitScale);
        tiledMapRenderSystem = new TiledMapRenderSystem(mapLoader.getMap(), camera, PRIORITY_MAP_RENDER);
        engine.addSystem(playerInputSystem);

        EnemySystem enemySystem = new EnemySystem(entityFactory, mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), mapLoader.getHazardRects(), roomState, PRIORITY_ENEMY);
        enemySystem.setUnitScale(unitScale);
        engine.addSystem(enemySystem);

        EnemyShootSystem shootSystem = new EnemyShootSystem(assetManager, roomState, PRIORITY_ENEMY);
        shootSystem.setUnitScale(unitScale);
        engine.addSystem(shootSystem);

        engine.addSystem(new BuffSystem(PRIORITY_BUFF));

        TrapSystem trapSystem = new TrapSystem(mapLoader.getCollisionRects(), roomState, assetManager, PRIORITY_TRAP);
        trapSystem.setUnitScale(unitScale);
        engine.addSystem(trapSystem);

        MovementSystem movementSystem = new MovementSystem(mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), PRIORITY_MOVEMENT);
        movementSystem.setUnitScale(unitScale);
        engine.addSystem(movementSystem);

        PlayerBulletSystem playerBulletSystem = new PlayerBulletSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION);
        playerBulletSystem.setUnitScale(unitScale);
        engine.addSystem(playerBulletSystem);

        EnemyBulletCollisionSystem enemyBulletSystem = new EnemyBulletCollisionSystem(mapLoader.getCollisionRects(), PRIORITY_COLLISION);
        enemyBulletSystem.setUnitScale(unitScale);
        engine.addSystem(enemyBulletSystem);
        engine.addSystem(new CollisionBoundsSystem(PRIORITY_BOUNDS));
        engine.addSystem(new DespawnSystem(PRIORITY_BOUNDS, mapLoader));
        MovingPlatformSystem movingPlatformSystem = new MovingPlatformSystem(mapLoader.getCollisionRects(), roomState, PRIORITY_MOVING_PLATFORM);
        movingPlatformSystem.setUnitScale(unitScale);
        engine.addSystem(movingPlatformSystem);

        engine.addSystem(new MusicSystem(AudioManager.get(), PRIORITY_MUSIC));
        SfxSystem sfxSystem = new SfxSystem(AudioManager.get(), PRIORITY_SFX);
        engine.addSystem(sfxSystem);

        MeleeAttackSystem meleeSystem = new MeleeAttackSystem(assetManager, mapLoader.getSecretRects(),
            mapLoader.getCollisionRects(), mapLoader.getCollisionLayer(), sfxSystem, secretRoomRevealer, PRIORITY_MELEE);
        meleeSystem.setUnitScale(unitScale);
        engine.addSystem(meleeSystem);

        engine.addSystem(new PickupSystem(sfxSystem, entityFactory, PRIORITY_PICKUP));

        chestSystem = new ChestSystem(entityFactory, PRIORITY_CHEST);
        chestSystem.setUnitScale(unitScale);
        chestSystem.setCollisionRects(mapLoader.getCollisionRects());
        engine.addSystem(chestSystem);

        EnemyContactSystem enemyContactSystem = new EnemyContactSystem(PRIORITY_ENEMY_CONTACT);
        enemyContactSystem.setUnitScale(unitScale);
        engine.addSystem(enemyContactSystem);

        EnemyAttackSystem enemyAttackSystem = new EnemyAttackSystem(roomState, PRIORITY_ENEMY_CONTACT);
        enemyAttackSystem.setUnitScale(unitScale);
        engine.addSystem(enemyAttackSystem);
        engine.addSystem(new HazardSystem(mapLoader.getHazardRects(), PRIORITY_ENEMY_CONTACT));
        TrapContactSystem trapContactSystem = new TrapContactSystem(roomState, PRIORITY_TRAP_CONTACT);
        engine.addSystem(trapContactSystem);
        cameraSystem = new CameraSystem(camera, roomState, PRIORITY_CAMERA);
        engine.addSystem(cameraSystem);
        engine.addSystem(new AnimationSystem(PRIORITY_ANIMATION));
        engine.addSystem(new SquashSystem(PRIORITY_SQUASH));
        engine.addSystem(new ParallaxBackgroundSystem(batch, camera,
            assetManager.get(BACKGROUND_FAR, Texture.class),
            assetManager.get(BACKGROUND_NEAR, Texture.class),
            PRIORITY_BACKGROUND_RENDER));
        engine.addSystem(tiledMapRenderSystem);
        engine.addSystem(new RenderSystem(batch, camera, PRIORITY_ENTITY_RENDER));
        engine.addSystem(new FloatingMessageSystem(batch, camera, skin, PRIORITY_FLOATING_MESSAGE));
        engine.addSystem(new ParticleSystem(batch, camera, PRIORITY_PARTICLE_RENDER));
        lightRenderSystem = new LightRenderSystem(batch, camera, PRIORITY_LIGHT_RENDER);
        engine.addSystem(lightRenderSystem);
        vignetteRenderSystem = new VignetteRenderSystem(batch, camera, PRIORITY_VIGNETTE_RENDER);
        engine.addSystem(vignetteRenderSystem);
        debugRenderSystem = new DebugRenderSystem(camera, mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), mapLoader.getHazardRects(), roomState, PRIORITY_DEBUG_RENDER);
        debugRenderSystem.setUnitScale(unitScale);
        engine.addSystem(debugRenderSystem);

        levelManager = new LevelManager(engine, entityFactory, viewport, tiledMapRenderSystem, mapLoader.getCollisionRects(), mapLoader.getOneWayRects(), mapLoader.getHazardRects(), mapLoader.getSecretRects(), roomState, secretRoomRevealer, mapLoader);

        LevelExitSystem exitSystem = new LevelExitSystem(levelManager, PRIORITY_LEVEL_EXIT, onVictory);
        exitSystem.setUnitScale(unitScale);
        exitSystem.setOnTransition(onLevelTransition);
        engine.addSystem(exitSystem);

        engine.addSystem(new PlayerDeathSystem(onPlayerDeath, killY, PRIORITY_PLAYER_DEATH));
    }
}
