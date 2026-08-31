package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.LevelCatalog;
import com.axehigh.platformer.map.LevelDefinition;
import com.axehigh.platformer.map.LevelManager;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Drives the exit-gate proximity/interact flow: mirrors {@code EnemyContactSystem}'s
 * single-player-resolved-once pattern. Each frame resets the player's {@code nearExit} flag, then
 * for every {@code LevelExitComponent} gate checks the player's bounds against a sensor rectangle
 * inflated a few units beyond the gate's own {@code CollisionComponent} bounds (so it feels like
 * walking up to a door rather than requiring pixel-perfect overlap). On overlap sets
 * {@code nearExit = true} and fades in an amber {@code LightComponent} (animating alpha and radius
 * over {@code FADE_DURATION}) on the gate entity so it glows via {@code LightRenderSystem}. When
 * the player moves out of proximity, the light smoothly fades out and is removed once it reaches
 * zero. If the player also pressed interact that same frame, hands off to
 * {@code LevelManager.loadLevel(...)} to perform the actual level transition.
 */
public class LevelExitSystem extends IteratingSystem {
    private static final float SENSOR_PADDING = 6f;
    private static final float DOOR_LIGHT_RADIUS = 24f;
    private static final float DOOR_LIGHT_BASE_ALPHA = 0.5f;
    private static final float FADE_DURATION = 0.3f;
    private static final Color DOOR_LIGHT_COLOR = new Color(1f, 0.85f, 0.5f, 0.5f);

    /** Performs the actual in-place level swap; decouples it from the screen's fade transition. */
    @FunctionalInterface
    public interface LevelTransition {
        void transition(String nextLevelPath, Entity playerEntity);
    }

    private final LevelManager levelManager;
    private final Rectangle sensorBounds = new Rectangle();
    private final Rectangle playerBounds = new Rectangle();
    private float unitScale = 1f;
    private ImmutableArray<Entity> players;
    private Runnable onVictory = () -> {};
    private LevelTransition onTransition;

    public LevelExitSystem(LevelManager levelManager) {
        this(levelManager, 0);
    }

    public LevelExitSystem(LevelManager levelManager, int priority) {
        super(Family.all(LevelExitComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.levelManager = levelManager;
        this.onTransition = (nextLevelPath, playerEntity) -> levelManager.loadLevel(nextLevelPath, playerEntity);
    }

    public LevelExitSystem(LevelManager levelManager, int priority, Runnable onVictory) {
        this(levelManager, priority);
        this.onVictory = onVictory;
    }

    public void setOnVictory(Runnable onVictory) {
        this.onVictory = onVictory;
    }

    public void setOnTransition(LevelTransition onTransition) {
        this.onTransition = onTransition;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() > 0) {
            PLAYER.get(players.first()).nearExit = false;
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity gateEntity, float deltaTime) {
        LevelExitComponent levelExit = LEVEL_EXIT.get(gateEntity);
        CollisionComponent gateCollision = COLLISION.get(gateEntity);

        boolean inProximity = false;
        Entity playerEntity = null;
        PlayerComponent player = null;

        if (players.size() > 0) {
            playerEntity = players.first();
            player = PLAYER.get(playerEntity);
            CollisionComponent playerCollision = COLLISION.get(playerEntity);

            float padding = SENSOR_PADDING * unitScale;
            sensorBounds.set(
                gateCollision.worldBounds.x - padding,
                gateCollision.worldBounds.y - padding,
                gateCollision.worldBounds.width + padding * 2f,
                gateCollision.worldBounds.height + padding * 2f);

            if (playerCollision.worldBounds.overlaps(sensorBounds)) {
                inProximity = true;
                player.nearExit = true;
            }
        }

        if (inProximity) {
            levelExit.fadeProgress = Math.min(1f, levelExit.fadeProgress + (FADE_DURATION > 0f ? deltaTime / FADE_DURATION : 1f));
        } else {
            levelExit.fadeProgress = Math.max(0f, levelExit.fadeProgress - (FADE_DURATION > 0f ? deltaTime / FADE_DURATION : 1f));
        }

        if (levelExit.fadeProgress > 0f) {
            LightComponent light = LIGHT.get(gateEntity);
            if (light == null) {
                light = new LightComponent();
                gateEntity.add(light);
            }
            light.color.set(DOOR_LIGHT_COLOR);
            light.baseAlpha = DOOR_LIGHT_BASE_ALPHA * levelExit.fadeProgress;
            light.radius = DOOR_LIGHT_RADIUS * unitScale * levelExit.fadeProgress;
            light.offset.set(
                gateCollision.bounds.x + gateCollision.bounds.width / 2f,
                gateCollision.bounds.y + gateCollision.bounds.height / 2f);
        } else {
            if (LIGHT.has(gateEntity)) {
                gateEntity.remove(LightComponent.class);
            }
        }

        if (inProximity && player != null && player.interactPressed) {
            player.interactPressed = false;
            if (levelExit.isFinalLevel) {
                SaveData save = buildSaveData(player, levelExit.nextLevelPath);
                save.completedWorldIds = buildCompletedWorldIds(save.completedLevelIds);
                SaveManager.save(save);
                onVictory.run();
            } else {
                SaveManager.save(buildSaveData(player, levelExit.nextLevelPath));
                onTransition.transition(levelExit.nextLevelPath, playerEntity);
            }
        }
    }

    private SaveData buildSaveData(PlayerComponent player, String nextLevelPath) {
        SaveData saveData = SaveData.of(player);
        saveData.levelPath = nextLevelPath;
        saveData.completedLevelIds = buildCompletedLevelIds();
        if (SaveManager.hasSave()) {
            SaveData previousSave = SaveManager.load();
            saveData.triesRemaining = previousSave.triesRemaining;
        }
        return saveData;
    }

    private Array<String> buildCompletedLevelIds() {
        Array<String> completedLevelIds = new Array<>();
        if (SaveManager.hasSave()) {
            SaveData previousSave = SaveManager.load();
            if (previousSave.completedLevelIds != null) {
                completedLevelIds.addAll(previousSave.completedLevelIds);
            }
        }
        String currentLevelPath = levelManager.getCurrentLevelPath();
        for (LevelDefinition level : LevelCatalog.levels()) {
            if (level.tmxPath.equals(currentLevelPath)) {
                if (!completedLevelIds.contains(level.id, false)) {
                    completedLevelIds.add(level.id);
                }
                break;
            }
        }
        return completedLevelIds;
    }

    private Array<String> buildCompletedWorldIds(Array<String> completedLevelIds) {
        Array<String> completedWorldIds = new Array<>();
        if (SaveManager.hasSave()) {
            SaveData previousSave = SaveManager.load();
            if (previousSave.completedWorldIds != null) {
                completedWorldIds.addAll(previousSave.completedWorldIds);
            }
        }
        String currentLevelPath = levelManager.getCurrentLevelPath();
        for (LevelDefinition level : LevelCatalog.levels()) {
            if (level.tmxPath.equals(currentLevelPath)) {
                String worldKey = "world" + level.worldId;
                if (!completedWorldIds.contains(worldKey, false)) {
                    completedWorldIds.add(worldKey);
                }
                break;
            }
        }
        return completedWorldIds;
    }
}
