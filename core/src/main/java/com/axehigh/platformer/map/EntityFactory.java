package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.CoinPickupComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.DaggerPickupComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.EnemyShooterComponent;
import com.axehigh.platformer.ecs.components.FlyingEnemyComponent;
import com.axehigh.platformer.ecs.components.LevelExitComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PoppedItemComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;

/** Builds Ashley entities for the player and for object-layer markers (coin, chest, torch, exit gate). */
public class EntityFactory {
    private static final float DECOR_Z = 5f;
    private static final float PLAYER_Z = 10f;

    private final AssetManager assetManager;

    public EntityFactory(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public Entity createPlayer(float x, float y) {
        Texture texture = getTexture("gfx/player.png");

        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = PLAYER_Z;
        player.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        player.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        player.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        player.add(collisionComponent);

        player.add(new PlayerComponent());

        return player;
    }

    /** Spawns decorative entities (coin, chest, torch, exit gate) found in the object layer. */
    public void spawnObjects(Engine engine, MapObjects objects) {
        for (MapObject object : objects) {
            if (!(object instanceof RectangleMapObject)) {
                continue;
            }
            String type = object.getProperties().get("type", String.class);
            if (type == null) {
                continue;
            }

            Rectangle rect = ((RectangleMapObject) object).getRectangle();
            float centerX = rect.x + rect.width / 2f;
            float centerY = rect.y + rect.height / 2f;

            switch (type) {
                case "coin":
                    engine.addEntity(createCoinPickup(centerX, centerY));
                    break;
                case "chest":
                    engine.addEntity(createChest(centerX, centerY));
                    break;
                case "torch":
                    engine.addEntity(createDecoration(centerX, centerY, "gfx/torch.png"));
                    break;
                case "exitGate":
                    String nextLevelPath = object.getProperties().get("nextLevel", String.class);
                    engine.addEntity(createExitGate(centerX, centerY, nextLevelPath));
                    break;
                case "dagger":
                    engine.addEntity(createDaggerPickup(centerX, centerY));
                    break;
                case "enemy":
                    String enemyType = object.getProperties().get("enemyType", "walker", String.class);
                    engine.addEntity(createEnemy(centerX, centerY, enemyType));
                    break;
                default:
                    // "playerStart" and any unrecognized type: nothing to spawn here.
                    break;
            }
        }
    }

    private Entity createDecoration(float x, float y, String texturePath) {
        Texture texture = getTexture(texturePath);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        return entity;
    }

    /**
     * Builds an exit-gate entity: the decorative sprite plus a {@code CollisionComponent} (sized
     * like other pickups) so {@code LevelExitSystem} has bounds to build its proximity sensor
     * from. Only gets a {@code LevelExitComponent} (and is thus an actual level-transition
     * trigger) when {@code nextLevelPath} is non-null; otherwise it's purely decorative, e.g. the
     * dead-end final level.
     */
    private Entity createExitGate(float x, float y, String nextLevelPath) {
        Texture texture = getTexture("gfx/exit_gate.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        if (nextLevelPath != null) {
            LevelExitComponent levelExitComponent = new LevelExitComponent();
            levelExitComponent.nextLevelPath = nextLevelPath;
            entity.add(levelExitComponent);
        }

        return entity;
    }

    private Entity createChest(float x, float y) {
        Texture texture = getTexture("gfx/chest.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        entity.add(new ChestComponent());

        return entity;
    }

    /** Builds a static, standalone coin pickup entity (used for map object markers). */
    public Entity createCoinPickup(float x, float y) {
        Texture texture = getTexture("gfx/coin.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        entity.add(new CoinPickupComponent());

        return entity;
    }

    /**
     * Builds a coin pickup entity that launches with the given initial velocity (a small upward
     * pop plus horizontal scatter) instead of sitting still, so it visibly arcs up and out before
     * gravity/collision pulls it back down to rest. Used only for chest-dropped coins.
     */
    public Entity createPoppedCoinPickup(float x, float y, float velocityX, float velocityY) {
        Entity entity = createCoinPickup(x, y);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.velocity.set(velocityX, velocityY);
        entity.add(movementComponent);

        entity.add(new PoppedItemComponent());

        return entity;
    }

    private Entity createEnemy(float x, float y, String enemyType) {
        String texturePath;
        switch (enemyType) {
            case "flyer":
                texturePath = "gfx/enemy_flyer.png";
                break;
            case "shooter":
                texturePath = "gfx/enemy_shooter.png";
                break;
            default:
                texturePath = "gfx/enemy.png";
                break;
        }
        Texture texture = getTexture(texturePath);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        entity.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        EnemyComponent enemyComponent = new EnemyComponent();
        enemyComponent.originX = x;
        if ("flyer".equals(enemyType)) {
            enemyComponent.health = 5f;
            entity.add(new FlyingEnemyComponent());
        } else if ("shooter".equals(enemyType)) {
            entity.add(new EnemyShooterComponent());
        }
        entity.add(enemyComponent);

        return entity;
    }

    private Entity createDaggerPickup(float x, float y) {
        Texture texture = getTexture("gfx/dagger.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        entity.add(new DaggerPickupComponent());

        return entity;
    }

    private Texture getTexture(String path) {
        Texture texture = assetManager.get(path, Texture.class);
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return texture;
    }
}
