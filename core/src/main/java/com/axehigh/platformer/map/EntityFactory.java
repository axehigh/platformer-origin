package com.axehigh.platformer.map;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.assets.GameAssetRegistry.HERO_ASSET;
import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;
import static com.axehigh.platformer.assets.GameAssetRegistry.PLATFORM_ASSET;
import static com.axehigh.platformer.ecs.components.AnimationComponent.State.*;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL;

/**
 * Builds Ashley entities for the player and for object-layer markers (coin, chest, torch, exit gate).
 */
public class EntityFactory {
    private static final float DECOR_Z = 5f;
    private static final float PLAYER_Z = 10f;
    /** Moving platforms draw above decorations but below the player. */
    private static final float PLATFORM_Z = 6f;
    /** Default oscillation angular frequency (rad/s) when a platform omits the {@code speed} property. */
    private static final float DEFAULT_PLATFORM_SPEED = 1f;
    /** Default glow halo radius (world units) for a torch light. */
    private static final float DEFAULT_TORCH_LIGHT_RADIUS = 48f;
    /** Flame offset within the 16x16 torch sprite (sprite-relative, scaled by {@code unitScale}). */
    private static final float TORCH_FLAME_X = 8f;
    private static final float TORCH_FLAME_Y = 13f;

    private final AssetManager assetManager;
    private final TextureAtlas originAtlas;
    private float unitScale = 1f;

    public EntityFactory(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.originAtlas = assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class);
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    public Entity createPlayer(float x, float y) {
        TextureAtlas heroAtlas = assetManager.get(HERO_ASSET, TextureAtlas.class);
        TextureRegion region = heroAtlas.findRegion("idle");

        float scaleFactor = SpriteConstants.PlayerScale; // Scaling factor for the new larger graphics
        float finalScale = unitScale * scaleFactor;

        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(finalScale, finalScale);
        transform.z = PLAYER_Z;
        player.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        player.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.maxSpeedX *= unitScale;
        movementComponent.maxSpeedY *= unitScale;
        player.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        float collisionWidth = SpriteConstants.PlayerCollisionWidth;
        float collisionHeight = SpriteConstants.PlayerCollisionHeight;
        collisionComponent.bounds.setSize(collisionWidth * finalScale, collisionHeight * finalScale);

        if (region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) region;
            collisionComponent.baseOffsetX = (atlasRegion.offsetX + (atlasRegion.getRegionWidth() - collisionWidth) / 2f) * finalScale;
            collisionComponent.baseOffsetY = (atlasRegion.offsetY + (atlasRegion.getRegionHeight() - collisionHeight) / 2f) * finalScale;
        } else {
            collisionComponent.baseOffsetX = (region.getRegionWidth() - collisionWidth) / 2f * finalScale;
            collisionComponent.baseOffsetY = (region.getRegionHeight() - collisionHeight) / 2f * finalScale;
        }
        collisionComponent.currentOffsetX = SpriteConstants.PlayerOffsetRight * finalScale;
        collisionComponent.currentOffsetY = SpriteConstants.PlayerOffsetY * finalScale;
        collisionComponent.bounds.setX(collisionComponent.baseOffsetX + collisionComponent.currentOffsetX);
        collisionComponent.bounds.setY(collisionComponent.baseOffsetY + collisionComponent.currentOffsetY);
        player.add(collisionComponent);

        player.add(new PlayerComponent());

        return player;
    }

    /**
     * Recomputes a (persisted) player's collision offsets from the canonical idle frame, so a
     * level swap never inherits offsets computed from whatever animation frame the player happened
     * to be showing at that moment (e.g. the death frame, whose region is much larger), which would
     * float the collision box far above the sprite. Mirrors the math in {@link #createPlayer(float, float)}.
     */
    public void resetPlayerCollision(CollisionComponent collisionComponent, float finalScale) {
        TextureAtlas heroAtlas = assetManager.get(HERO_ASSET, TextureAtlas.class);
        TextureRegion region = heroAtlas.findRegion("idle");
        float collisionWidth = SpriteConstants.PlayerCollisionWidth;
        float collisionHeight = SpriteConstants.PlayerCollisionHeight;
        collisionComponent.bounds.setSize(collisionWidth * finalScale, collisionHeight * finalScale);
        if (region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) region;
            collisionComponent.baseOffsetX = (atlasRegion.offsetX + (atlasRegion.getRegionWidth() - collisionWidth) / 2f) * finalScale;
            collisionComponent.baseOffsetY = (atlasRegion.offsetY + (atlasRegion.getRegionHeight() - collisionHeight) / 2f) * finalScale;
        } else {
            collisionComponent.baseOffsetX = (region.getRegionWidth() - collisionWidth) / 2f * finalScale;
            collisionComponent.baseOffsetY = (region.getRegionHeight() - collisionHeight) / 2f * finalScale;
        }
        collisionComponent.currentOffsetX = SpriteConstants.PlayerOffsetRight * finalScale;
        collisionComponent.currentOffsetY = SpriteConstants.PlayerOffsetY * finalScale;
        collisionComponent.bounds.setX(collisionComponent.baseOffsetX + collisionComponent.currentOffsetX);
        collisionComponent.bounds.setY(collisionComponent.baseOffsetY + collisionComponent.currentOffsetY);
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

            float spawnX, spawnY;
            float objectWidth = 0f;
            float objectHeight = 0f;
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                spawnX = rect.x;
                spawnY = rect.y;
                centerX = rect.x + rect.width / 2f;
                centerY = rect.y + rect.height / 2f;
                objectWidth = rect.width;
                objectHeight = rect.height;
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
                spawnX = tileObj.getX();
                spawnY = tileObj.getY();
                centerX = spawnX + width / 2f;
                centerY = spawnY + height / 2f;
                objectWidth = width;
                objectHeight = height;
            } else {
                continue;
            }

            String type = getProperty(object, tile, "type", null);
            if (type == null) {
                continue;
            }

            switch (type) {
                case "coin":
                    engine.addEntity(tile != null ? createCoinPickup(spawnX, spawnY, tile) : createCoinPickup(spawnX, spawnY));
                    spawned = true;
                    break;
                case "chest":
                    engine.addEntity(tile != null ? createChest(spawnX, spawnY, tile) : createChest(spawnX, spawnY));
                    spawned = true;
                    break;
                case "torch":
                    engine.addEntity(createTorch(spawnX, spawnY, object, tile));
                    spawned = true;
                    break;
                case "exitGate":
                    String nextLevelPath = getProperty(object, tile, "nextLevel", null);
                    engine.addEntity(createExitGate(spawnX, spawnY, nextLevelPath));
                    spawned = true;
                    break;
                case "dagger":
                    engine.addEntity(createDaggerPickup(spawnX, spawnY));
                    spawned = true;
                    break;
                case "enemy":
                    String enemyType = getProperty(object, tile, "enemyType", "walker");
                    int roomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    engine.addEntity(createEnemy(spawnX, spawnY, enemyType, roomIndex));
                    spawned = true;
                    break;
                case "platform":
                    int platformRoomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    engine.addEntity(createPlatform(spawnX, spawnY, objectWidth, objectHeight, tile, object, platformRoomIndex));
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

    /** Reads a numeric custom property from the object (or its tile), tolerating int/float/string encodings. */
    private float getFloatProperty(MapObject object, TiledMapTile tile, String key, float defaultValue) {
        Object value = object.getProperties().get(key);
        if (value == null && tile != null) {
            value = tile.getProperties().get(key);
        }
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Builds a scripted moving-platform entity from a Tiled object of type {@code platform}. The
     * object rectangle (or the tile drawn in it, stretched to its width/height) defines both the
     * sprite and the collision box; the platform oscillates around that spawn rectangle, driven by
     * {@code MovingPlatformSystem}. Custom properties: {@code amplitudeX}/{@code amplitudeY}
     * (travel distance per axis in world units), {@code speed} (rad/s, default 1), {@code phase}
     * (radians, default 0), and optionally {@code axis} ("x" / "y" / "both", a convenience that
     * zeroes the other axis). {@code roomIndex} mirrors the enemy pattern: the platform only moves
     * while its owning room is active.
     */
    private Entity createPlatform(float x, float y, float width, float height, TiledMapTile tile, MapObject object, int roomIndex) {
        TextureRegion region = tile != null ? tile.getTextureRegion() : new TextureRegion(getTexture(PLATFORM_ASSET));

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(width / region.getRegionWidth(), height / region.getRegionHeight());
        transform.z = PLATFORM_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(width, height);
        entity.add(collisionComponent);

        MovingPlatformComponent platform = new MovingPlatformComponent();
        platform.baseX = x;
        platform.baseY = y;
        String axis = getProperty(object, tile, "axis", null);
        if ("x".equalsIgnoreCase(axis)) {
            platform.amplitudeY = 0f;
        } else if ("y".equalsIgnoreCase(axis)) {
            platform.amplitudeX = 0f;
        }
        platform.amplitudeX = getFloatProperty(object, tile, "amplitudeX", platform.amplitudeX);
        platform.amplitudeY = getFloatProperty(object, tile, "amplitudeY", platform.amplitudeY);
        platform.speed = getFloatProperty(object, tile, "speed", DEFAULT_PLATFORM_SPEED);
        platform.phase = getFloatProperty(object, tile, "phase", 0f);
        platform.roomIndex = roomIndex;
        entity.add(platform);

        return entity;
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
     * Builds a torch decoration (same sprite as {@code createDecoration}) plus a flickering
     * {@code LightComponent} halo centered on the flame at the sprite's top. An optional
     * {@code lightRadius} Tiled custom property overrides the halo radius in world units.
     */
    private Entity createTorch(float x, float y, MapObject object, TiledMapTile tile) {
        Entity entity = createDecoration(x, y, "gfx/old/torch.png");

        LightComponent light = new LightComponent();
        light.radius = getFloatProperty(object, tile, "lightRadius", DEFAULT_TORCH_LIGHT_RADIUS);
        light.offset.set(TORCH_FLAME_X * unitScale, TORCH_FLAME_Y * unitScale);
        light.phase = MathUtils.random(MathUtils.PI2);
        entity.add(light);

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
        Texture texture = getTexture("gfx/old/exit_gate.png");

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
        Texture texture = getTexture("gfx/old/chest.png");

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

    /**
     * Builds a chest entity from a Tiled map tile.
     */
    public Entity createChest(float x, float y, TiledMapTile tile) {
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

        entity.add(new ChestComponent());

        return entity;
    }

    /**
     * Builds a static, standalone coin pickup entity (used for map object markers).
     */
    public Entity createCoinPickup(float x, float y) {
        Texture texture = getTexture("gfx/old/coin.png");

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

    /**
     * Builds a coin pickup from a Tiled map tile, supporting animation if defined in the tileset.
     */
    public Entity createCoinPickup(float x, float y, TiledMapTile tile) {
        Entity entity = new Entity();

        TextureRegion region = tile.getTextureRegion();
        float tileWidth = region.getRegionWidth();
        float tileHeight = region.getRegionHeight();

        TransformComponent transform = new TransformComponent();
        transform.position.set(
            x + (tileWidth - region.getRegionWidth()) / 2f,
            y + (tileHeight - region.getRegionHeight()) / 2f);
        transform.scale.set(1f, 1f);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(region.getRegionWidth(), region.getRegionHeight());
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
            animComp.animations.put(IDLE, animation);
            animComp.currentState = IDLE;
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
        String atlasPrefix;
        String walkRegionName;
        float colWidth, colHeight, colOffsetY, enemyScale;
        switch (enemyType) {
            case "flyer":
                atlasPrefix = "mosquito";
                walkRegionName = "flight";
                colWidth = SpriteConstants.EnemyFlyerCollisionWidth;
                colHeight = SpriteConstants.EnemyFlyerCollisionHeight;
                colOffsetY = SpriteConstants.EnemyFlyerOffsetY;
                enemyScale = unitScale * SpriteConstants.EnemyFlyerScale;
                break;
            case "shooter":
                atlasPrefix = "spider";
                walkRegionName = "walk";
                colWidth = SpriteConstants.EnemyShooterCollisionWidth;
                colHeight = SpriteConstants.EnemyShooterCollisionHeight;
                colOffsetY = SpriteConstants.EnemyShooterOffsetY;
                enemyScale = unitScale * SpriteConstants.EnemyShooterScale;
                break;
            case "knight":
                atlasPrefix = "goblin";
                walkRegionName = "walk";
                colWidth = SpriteConstants.EnemyKnightCollisionWidth;
                colHeight = SpriteConstants.EnemyKnightCollisionHeight;
                colOffsetY = SpriteConstants.EnemyKnightOffsetY;
                enemyScale = unitScale * SpriteConstants.EnemyKnightScale;
                break;
            default:
                atlasPrefix = "goblin";
                walkRegionName = "walk";
                colWidth = SpriteConstants.EnemyWalkerCollisionWidth;
                colHeight = SpriteConstants.EnemyWalkerCollisionHeight;
                colOffsetY = SpriteConstants.EnemyWalkerOffsetY;
                enemyScale = unitScale * SpriteConstants.EnemyWalkerScale;
                break;
        }

        Entity entity = new Entity();

        // Animations
        AnimationComponent animComp = new AnimationComponent();
        float frameDuration = 0.1f;
        animComp.animations.put(IDLE, createEnemyAnimation(frameDuration, atlasPrefix + "_idle", LOOP));
        animComp.animations.put(WALKING, createEnemyAnimation(frameDuration, atlasPrefix + "_" + walkRegionName, LOOP));
        animComp.animations.put(ATTACKING, createEnemyAnimation(frameDuration, atlasPrefix + "_attack", NORMAL));
        animComp.animations.put(HURT, createEnemyAnimation(frameDuration, atlasPrefix + "_hurt", NORMAL));
        animComp.animations.put(DEATH, createEnemyAnimation(frameDuration, atlasPrefix + "_death", NORMAL));
        animComp.currentState = IDLE;
        entity.add(animComp);

        Animation<TextureRegion> idleAnim = animComp.animations.get(IDLE);
        TextureRegion initialRegion = findInitialRegion(idleAnim, atlasPrefix);


        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(enemyScale, enemyScale);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = initialRegion;
        entity.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.maxSpeedX *= unitScale;
        movementComponent.maxSpeedY *= unitScale;
        entity.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(colWidth * enemyScale, colHeight * enemyScale);
        collisionComponent.baseOffsetX = (128f * enemyScale - collisionComponent.bounds.width) / 2f;
        collisionComponent.baseOffsetY = (128f * enemyScale - collisionComponent.bounds.height) / 2f;
        collisionComponent.currentOffsetY = colOffsetY * enemyScale;

        collisionComponent.bounds.setX(collisionComponent.baseOffsetX);
        collisionComponent.bounds.setY(collisionComponent.baseOffsetY + collisionComponent.currentOffsetY);
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

    private TextureRegion findInitialRegion(Animation<TextureRegion> idleAnim, String atlasPrefix) {
        TextureRegion initialRegion = idleAnim.getKeyFrame(0f);
        if (initialRegion == null) {
            initialRegion = originAtlas.findRegion(atlasPrefix + "_idle1");
        }
        return initialRegion;
    }

    private Entity createDaggerPickup(float x, float y) {
        Texture texture = getTexture("gfx/old/dagger.png");

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

    private Animation<TextureRegion> createEnemyAnimation(float frameDuration, String regionName, Animation.PlayMode playMode) {
        Array<AtlasRegion> regions = new Array<>();
        Array<AtlasRegion> found = originAtlas.findRegions(regionName);
        if (found.size > 0) {
            regions.addAll(found);
        } else {
            // Fallback for numbered naming: name1, name2, ... or name_1, name_2, ...
            for (int i = 0; i <= 10; i++) {
                AtlasRegion region = originAtlas.findRegion(regionName + i);
                if (region != null) regions.add(region);
                region = originAtlas.findRegion(regionName + "_" + i);
                if (region != null) regions.add(region);
            }
        }

        if (regions.size == 0) {
            // Emergency fallback: just find the first region that starts with the prefix
            for (AtlasRegion region : originAtlas.getRegions()) {
                if (region.name.startsWith(regionName)) {
                    regions.add(region);
                    break;
                }
            }
        }

        // If STILL empty, use a global fallback to prevent division-by-zero crash
        if (regions.size == 0) {
            AtlasRegion fallback = originAtlas.findRegion("goblin_idle1");
            if (fallback != null) {
                regions.add(fallback);
            }
        }

        return new Animation<>(frameDuration, regions, playMode);
    }
}
