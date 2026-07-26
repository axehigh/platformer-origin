package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.AnimationComponent;
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
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;

/** Builds Ashley entities for the player and for object-layer markers (coin, chest, torch, exit gate). */
public class EntityFactory {
    private static final float DECOR_Z = 5f;
    private static final float PLAYER_Z = 10f;

    private final AssetManager assetManager;
    private float unitScale = 1f;

    public EntityFactory(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    public Entity createPlayer(float x, float y) {
        Texture texture = getTexture("gfx/player.png");

        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(unitScale, unitScale);
        transform.z = PLAYER_Z;
        player.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        player.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.maxSpeedX *= unitScale;
        movementComponent.maxSpeedY *= unitScale;
        player.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth() * unitScale, texture.getHeight() * unitScale);
        player.add(collisionComponent);

        player.add(new PlayerComponent());

        return player;
    }

    /**
     * Spawns decorative entities (coin, chest, torch, exit gate, enemy) found in the object layer.
     * {@code roomState} is used to assign each spawned enemy to whichever Room rectangle contains
     * its spawn point (see {@code EnemyComponent.roomIndex}).
     */
    public void spawnObjects(Engine engine, MapObjects objects, RoomState roomState) {
        com.badlogic.gdx.utils.Array<MapObject> toRemove = new com.badlogic.gdx.utils.Array<>();
        for (MapObject object : objects) {
            float centerX, centerY;
            TiledMapTile tile = null;
            boolean spawned = false;

            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                centerX = rect.x + rect.width / 2f;
                centerY = rect.y + rect.height / 2f;
            } else if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObj = (TiledMapTileMapObject) object;
                tile = tileObj.getTile();
                float width = object.getProperties().get("width", 0f, Float.class);
                float height = object.getProperties().get("height", 0f, Float.class);
                // If properties missing, fall back to tile dimensions
                if ((width == 0f || height == 0f) && tile != null) {
                    width = tile.getTextureRegion().getRegionWidth();
                    height = tile.getTextureRegion().getRegionHeight();
                }
                float x = object.getProperties().get("x", 0f, Float.class);
                float y = object.getProperties().get("y", 0f, Float.class);
                centerX = x + width / 2f;
                centerY = y + height / 2f;
            } else {
                continue;
            }

            String type = getProperty(object, tile, "type", null);
            if (type == null) {
                continue;
            }

            switch (type) {
                case "coin":
                    engine.addEntity(tile != null ? createCoinPickup(centerX, centerY, tile) : createCoinPickup(centerX, centerY));
                    spawned = true;
                    break;
                case "chest":
                    engine.addEntity(createChest(centerX, centerY));
                    spawned = true;
                    break;
                case "torch":
                    engine.addEntity(createDecoration(centerX, centerY, "gfx/torch.png"));
                    spawned = true;
                    break;
                case "exitGate":
                    String nextLevelPath = getProperty(object, tile, "nextLevel", null);
                    engine.addEntity(createExitGate(centerX, centerY, nextLevelPath));
                    spawned = true;
                    break;
                case "dagger":
                    engine.addEntity(createDaggerPickup(centerX, centerY));
                    spawned = true;
                    break;
                case "enemy":
                    String enemyType = getProperty(object, tile, "enemyType", "walker");
                    int roomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    engine.addEntity(createEnemy(centerX, centerY, enemyType, roomIndex));
                    spawned = true;
                    break;
                default:
                    // "playerStart" and any unrecognized type: nothing to spawn here.
                    break;
            }
            if (spawned) {
                toRemove.add(object);
            }
        }
        for (MapObject obj : toRemove) {
            objects.remove(obj);
        }
    }

    private String getProperty(MapObject object, TiledMapTile tile, String key, String defaultValue) {
        String value = object.getProperties().get(key, String.class);
        if (value == null && tile != null) {
            value = tile.getProperties().get(key, String.class);
        }
        return value != null ? value : defaultValue;
    }

    private Entity createDecoration(float x, float y, String texturePath) {
        Texture texture = getTexture(texturePath);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(unitScale, unitScale);
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
        transform.scale.set(unitScale, unitScale);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth() * unitScale, texture.getHeight() * unitScale);
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
        transform.scale.set(unitScale, unitScale);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth() * unitScale, texture.getHeight() * unitScale);
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
        transform.scale.set(unitScale, unitScale);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth() * unitScale, texture.getHeight() * unitScale);
        entity.add(collisionComponent);

        entity.add(new CoinPickupComponent());

        return entity;
    }

    /** Builds a coin pickup from a Tiled map tile, supporting animation if defined in the tileset. */
    public Entity createCoinPickup(float x, float y, TiledMapTile tile) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(1f, 1f);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = tile.getTextureRegion();
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(tile.getTextureRegion().getRegionWidth(),
            tile.getTextureRegion().getRegionHeight());
        entity.add(collisionComponent);

        entity.add(new CoinPickupComponent());

        if (tile instanceof AnimatedTiledMapTile) {
            AnimatedTiledMapTile animatedTile = (AnimatedTiledMapTile) tile;
            StaticTiledMapTile[] frames = animatedTile.getFrameTiles();
            TextureRegion[] regions = new TextureRegion[frames.length];
            for (int i = 0; i < frames.length; i++) {
                regions[i] = frames[i].getTextureRegion();
            }
            int[] intervals = animatedTile.getAnimationIntervals();
            float frameDuration = intervals.length > 0 ? intervals[0] / 1000f : 0.1f;

            Animation<TextureRegion> animation = new Animation<>(frameDuration, regions);
            AnimationComponent animComp = new AnimationComponent();
            animComp.animations.put(AnimationComponent.State.IDLE, animation);
            animComp.currentState = AnimationComponent.State.IDLE;
            entity.add(animComp);
        }

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

    private Entity createEnemy(float x, float y, String enemyType, int roomIndex) {
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
        transform.scale.set(unitScale, unitScale);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.maxSpeedX *= unitScale;
        movementComponent.maxSpeedY *= unitScale;
        entity.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth() * unitScale, texture.getHeight() * unitScale);
        entity.add(collisionComponent);

        EnemyComponent enemyComponent = new EnemyComponent();
        enemyComponent.originX = x;
        enemyComponent.roomIndex = roomIndex;
        enemyComponent.speed *= unitScale;
        enemyComponent.patrolRange *= unitScale;

        if ("flyer".equals(enemyType)) {
            enemyComponent.health = 5f;
            FlyingEnemyComponent flying = new FlyingEnemyComponent();
            flying.bobAmplitude *= unitScale;
            entity.add(flying);
        } else if ("shooter".equals(enemyType)) {
            entity.add(new EnemyShooterComponent());
        } else if ("knight".equals(enemyType)) {
            enemyComponent.health = 15f;
        }
        entity.add(enemyComponent);

        return entity;
    }

    private Entity createDaggerPickup(float x, float y) {
        Texture texture = getTexture("gfx/dagger.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(unitScale, unitScale);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth() * unitScale, texture.getHeight() * unitScale);
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
