package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.LevelExitComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.LEVEL_EXIT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Drives the exit-gate proximity/interact flow: mirrors {@code EnemyContactSystem}'s
 * single-player-resolved-once pattern. Each frame resets the player's {@code nearExit} flag, then
 * for every {@code LevelExitComponent} gate checks the player's bounds against a sensor rectangle
 * inflated a few units beyond the gate's own {@code CollisionComponent} bounds (so it feels like
 * walking up to a door rather than requiring pixel-perfect overlap). On overlap sets
 * {@code nearExit = true}; if the player also pressed interact that same frame, hands off to
 * {@code LevelManager.loadLevel(...)} to perform the actual level transition.
 */
public class LevelExitSystem extends IteratingSystem {
    private static final float SENSOR_PADDING = 6f;

    private final LevelManager levelManager;
    private final Rectangle sensorBounds = new Rectangle();
    private final Rectangle playerBounds = new Rectangle();
    private float unitScale = 1f;
    private ImmutableArray<Entity> players;

    public LevelExitSystem(LevelManager levelManager) {
        this(levelManager, 0);
    }

    public LevelExitSystem(LevelManager levelManager, int priority) {
        super(Family.all(LevelExitComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.levelManager = levelManager;
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
        if (players.size() == 0) {
            return;
        }
        Entity playerEntity = players.first();
        PlayerComponent player = PLAYER.get(playerEntity);
        TransformComponent playerTransform = TRANSFORM.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        playerBounds.set(playerTransform.position.x, playerTransform.position.y,
            playerCollision.bounds.width, playerCollision.bounds.height);

        TransformComponent gateTransform = TRANSFORM.get(gateEntity);
        CollisionComponent gateCollision = COLLISION.get(gateEntity);
        float padding = SENSOR_PADDING * unitScale;
        sensorBounds.set(
            gateTransform.position.x - padding,
            gateTransform.position.y - padding,
            gateCollision.bounds.width + padding * 2f,
            gateCollision.bounds.height + padding * 2f);

        if (!playerBounds.overlaps(sensorBounds)) {
            return;
        }

        player.nearExit = true;

        if (player.interactPressed) {
            player.interactPressed = false;
            LevelExitComponent levelExit = LEVEL_EXIT.get(gateEntity);
            SaveManager.save(buildSaveData(player, levelExit.nextLevelPath));
            levelManager.loadLevel(levelExit.nextLevelPath, playerEntity);
        }
    }

    private SaveData buildSaveData(PlayerComponent player, String nextLevelPath) {
        SaveData saveData = new SaveData();
        saveData.levelPath = nextLevelPath;
        saveData.health = player.health;
        saveData.maxHealth = player.maxHealth;
        saveData.coins = player.coins;
        saveData.items = player.items;
        saveData.swordDamage = player.swordDamage;
        saveData.sharpEdgePurchased = player.sharpEdgePurchased;
        saveData.daggerBandolierPurchased = player.daggerBandolierPurchased;
        saveData.ironHeartCount = player.ironHeartCount;
        saveData.completedLevelIds = buildCompletedLevelIds();
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
}
