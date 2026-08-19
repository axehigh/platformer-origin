package com.axehigh.platformer.map;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
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
    private static final float DEFAULT_TORCH_LIGHT_RADIUS = 96f;
    /** Z-layer for floating messages — above entities but below particles/lights. */
    private static final float FLOATING_MESSAGE_Z = 15f;
    /** Flame offset within the 16x16 torch sprite (sprite-relative, scaled by {@code unitScale}). */
    private static final float TORCH_FLAME_X = 8f;
    private static final float TORCH_FLAME_Y = 13f;
    /** Fallback gate trigger size (world units) when the Tiled object rect has no dimensions. */
    private static final float DEFAULT_EXIT_GATE_WIDTH = 140f;
    private static final float DEFAULT_EXIT_GATE_HEIGHT = 152f;
    /** Popped-coin launch velocity ranges (world units/s, scaled by {@code unitScale} on use). */
    private static final float MIN_POP_VELOCITY_Y = 80f;
    private static final float MAX_POP_VELOCITY_Y = 140f;
    private static final float MAX_POP_VELOCITY_X = 40f;
    /** Per-frame duration of the coin spin animation (matches the 100ms Tiled animation). */
    private static final float COIN_FRAME_DURATION = 0.1f;
    /** Atlas region names of the coin spin frames, in playback order. */
    private static final String[] COIN_REGION_NAMES = {"Coin_01", "Coin_02", "Coin_03", "Coin_04", "Coin_05", "Coin_06"};
    /** The coin sprite renders at half a map tile by default (8px base tile scaled by {@code unitScale}). */
    private static final float DEFAULT_COIN_SIZE = 8f;

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
        player.add(new BuffComponent());

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
                    engine.addEntity(createCoinPickup(spawnX, spawnY, objectWidth, objectHeight));
                    spawned = true;
                    break;
                case "chest":
                    engine.addEntity(createChest(spawnX, spawnY));
                    spawned = true;
                    break;
                case "torch":
                    engine.addEntity(createTorch(spawnX, spawnY, object, tile));
                    spawned = true;
                    break;
                case "exitGate":
                    String nextLevelPath = getProperty(object, tile, "nextLevel", null);
                    engine.addEntity(createExitGate(spawnX, spawnY, objectWidth, objectHeight, nextLevelPath));
                    spawned = true;
                    break;
                case "dagger":
                    engine.addEntity(createDaggerPickup(spawnX, spawnY));
                    spawned = true;
                    break;
                case "potion":
                    String potionType = getProperty(object, tile, "potionType", "healing");
                    engine.addEntity(createPotionPickup(spawnX, spawnY, potionType));
                    spawned = true;
                    break;
                case "enemy":
                    String enemyType = getProperty(object, tile, "enemyType", "walker");
                    int roomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    engine.addEntity(createEnemy(spawnX, spawnY, enemyType, object, tile, roomIndex));
                    spawned = true;
                    break;
                case "platform":
                    int platformRoomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    engine.addEntity(createPlatform(spawnX, spawnY, objectWidth, objectHeight, tile, object, platformRoomIndex));
                    spawned = true;
                    break;
                case "trap":
                    String trapType = getProperty(object, tile, "trapType", "acidDrop");
                    int trapRoomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    if ("flame".equalsIgnoreCase(trapType)) {
                        engine.addEntity(createFlameTrap(spawnX, spawnY, object, tile, trapRoomIndex));
                    } else {
                        engine.addEntity(createAcidDropSpawner(spawnX, spawnY, object, tile, trapRoomIndex));
                    }
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

    /**
     * Builds an acid/lava drop spawner entity. The spawner is invisible (no texture) and sits at
     * the Tiled marker position, periodically spawning projectile entities that fall/rise in the
     * configured direction. Properties: {@code direction} (up/down/left/right, default down),
     * {@code interval} (seconds between spawns, default 2.0), {@code speed} (projectile velocity,
     * default 200), {@code damage} (default 1).
     */
    private Entity createAcidDropSpawner(float x, float y, MapObject object, TiledMapTile tile, int roomIndex) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(unitScale, unitScale);
        transform.z = DECOR_Z;
        entity.add(transform);

        CollisionComponent collision = new CollisionComponent();
        collision.bounds.setSize(4f * unitScale, 4f * unitScale);
        entity.add(collision);

        TrapComponent trap = new TrapComponent();
        trap.type = TrapComponent.TrapType.ACID_DROP_SPAWNER;
        trap.roomIndex = roomIndex;
        trap.spawnInterval = getFloatProperty(object, tile, "interval", 2.0f);
        trap.projectileSpeed = getFloatProperty(object, tile, "speed", 200f) * unitScale;
        trap.damage = getFloatProperty(object, tile, "damage", 1f);
        trap.spawnTimer.start(trap.spawnInterval);

        String dir = getProperty(object, tile, "direction", "down");
        trap.spawnDirection = parseDirection(dir);
        entity.add(trap);

        return entity;
    }

    /**
     * Builds a flame trap entity with animated pulsing. The flame grows/shrinks cyclically and
     * damages the player during the flame-on phase. Properties: {@code direction} (up/down/left/right,
     * default down), {@code duration} (flame-on seconds, default 2.0), {@code cooldown} (off seconds,
     * default 1.5), {@code pulseSpeed} (oscillation speed, default 2.0).
     */
    private Entity createFlameTrap(float x, float y, MapObject object, TiledMapTile tile, int roomIndex) {
        TextureAtlas.AtlasRegion region = originAtlas.findRegion("fire1");
        if (region == null) {
            region = originAtlas.findRegion("goblin_idle1");
        }

        float flameScale = unitScale * SpriteConstants.FlameTrapScale;

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(flameScale, flameScale);
        transform.z = DECOR_Z;
        String dir = getProperty(object, tile, "direction", "down");
        TrapComponent.TrapDirection flameDir = parseDirection(dir);
        if (flameDir == TrapComponent.TrapDirection.LEFT) {
            transform.rotation = 270f;
        } else if (flameDir == TrapComponent.TrapDirection.RIGHT) {
            transform.rotation = 90f;
        }
        entity.add(transform);

        TextureComponent texture = new TextureComponent();
        texture.region = region;
        entity.add(texture);

        CollisionComponent collision = new CollisionComponent();
        float colW = SpriteConstants.FlameTrapCollisionWidth * flameScale;
        float colH = SpriteConstants.FlameTrapCollisionHeight * flameScale;
        collision.bounds.setSize(colW, colH);
        entity.add(collision);

        TrapComponent trap = new TrapComponent();
        trap.type = TrapComponent.TrapType.FLAME;
        trap.roomIndex = roomIndex;
        trap.flameDirection = flameDir;
        trap.flameDuration = getFloatProperty(object, tile, "duration", 2.0f);
        trap.cooldownDuration = getFloatProperty(object, tile, "cooldown", 1.5f);
        trap.pulseSpeed = getFloatProperty(object, tile, "pulseSpeed", 2.0f);
        trap.flameHeight = SpriteConstants.FlameTrapCollisionHeight;
        trap.flameWidth = SpriteConstants.FlameTrapCollisionWidth;
        trap.currentScale = trap.minScale;
        trap.isFlaming = false;
        trap.cooldownTimer.start(MathUtils.random(0f, trap.cooldownDuration));
        entity.add(trap);

        AnimationComponent animComp = new AnimationComponent();
        float frameDuration = 0.1f;
        animComp.animations.put(IDLE, createEnemyAnimation(frameDuration, "fire", LOOP));
        animComp.currentState = IDLE;
        entity.add(animComp);

        return entity;
    }

    private TrapComponent.TrapDirection parseDirection(String dir) {
        if (dir == null) return TrapComponent.TrapDirection.DOWN;
        switch (dir.toLowerCase()) {
            case "up": return TrapComponent.TrapDirection.UP;
            case "left": return TrapComponent.TrapDirection.LEFT;
            case "right": return TrapComponent.TrapDirection.RIGHT;
            default: return TrapComponent.TrapDirection.DOWN;
        }
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
     * Builds an exit-gate trigger: logic-only, drawing **no sprite** — the door's decoration is
     * painted by the level designer in the map layers (see {@code map-design-for-tiled.md}). The
     * {@code CollisionComponent} is sized from the Tiled object rectangle (falling back to a
     * default when the object carries no dimensions) so {@code LevelExitSystem} has bounds to build
     * its proximity sensor from. Only gets a {@code LevelExitComponent} (and is thus an actual
     * level-transition trigger) when {@code nextLevelPath} is non-null; otherwise it's purely
     * decorative, e.g. the dead-end final level.
     */
    private Entity createExitGate(float x, float y, float width, float height, String nextLevelPath) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        float w = width > 0f ? width : DEFAULT_EXIT_GATE_WIDTH;
        float h = height > 0f ? height : DEFAULT_EXIT_GATE_HEIGHT;
        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(w, h);
        entity.add(collisionComponent);

        if (nextLevelPath != null) {
            LevelExitComponent levelExitComponent = new LevelExitComponent();
            levelExitComponent.nextLevelPath = nextLevelPath;
            entity.add(levelExitComponent);
        }

        return entity;
    }

    /**
     * Builds a chest entity from the {@code Chest_01_Locked} atlas region (never the Tiled tile
     * sprite). The region is tile-sized (128px), so it renders 1:1 at one map tile; the open
     * variant is swapped in by {@code MeleeAttackSystem} when the chest is struck.
     */
    private Entity createChest(float x, float y) {
        AtlasRegion region = originAtlas.findRegion(SpriteConstants.CHEST_CLOSED_REGION);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(1f, 1f);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(region.getRegionWidth(), region.getRegionHeight());
        entity.add(collisionComponent);

        entity.add(new ChestComponent());

        return entity;
    }

    /**
     * Builds an animated coin pickup entity sized to half a map tile (base 8px scaled by
     * {@code unitScale}), centered on {@code (x, y)}. Used for popped coins (chest/enemy drops).
     */
    public Entity createCoinPickup(float x, float y) {
        float size = DEFAULT_COIN_SIZE * unitScale;
        AtlasRegion region = originAtlas.findRegion(COIN_REGION_NAMES[0]);
        float scale = size / region.getRegionWidth();
        return buildCoin(x - size / 2f, y - size / 2f, size, size, scale);
    }

    /**
     * Builds an animated coin pickup entity from the {@code Coin_01..06} atlas regions (never the
     * Tiled tile sprite), sized to **half a map tile** ({@code DEFAULT_COIN_SIZE * unitScale}) and
     * centered on the given {@code width} x {@code height} marker rect, so the on-screen size is
     * identical to popped coins regardless of how the marker was drawn in Tiled. The marker rect is
     * purely a placement guide; {@code (x, y)} is its bottom-left corner, matching every other
     * object-layer spawn. The spin animation is driven by the generic {@code AnimationSystem}.
     */
    public Entity createCoinPickup(float x, float y, float width, float height) {
        float size = DEFAULT_COIN_SIZE * unitScale;
        AtlasRegion region = originAtlas.findRegion(COIN_REGION_NAMES[0]);
        float scale = size / region.getRegionWidth();
        return buildCoin(x + width / 2f - size / 2f, y + height / 2f - size / 2f, size, size, scale);
    }

    /**
     * Shared coin-entity builder: places a square {@code scale * regionWidth} coin centered inside
     * the {@code width} x {@code height} rect starting at {@code (x, y)} (bottom-left).
     */
    private Entity buildCoin(float x, float y, float width, float height, float scale) {
        Animation<TextureRegion> animation = createCoinAnimation();
        AtlasRegion region = originAtlas.findRegion(COIN_REGION_NAMES[0]);

        float scaledWidth = region.getRegionWidth() * scale;
        float scaledHeight = region.getRegionHeight() * scale;

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x + (width - scaledWidth) / 2f, y + (height - scaledHeight) / 2f);
        transform.scale.set(scale, scale);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(scaledWidth, scaledHeight);
        entity.add(collisionComponent);

        AnimationComponent animComp = new AnimationComponent();
        animComp.animations.put(IDLE, animation);
        animComp.currentState = IDLE;
        entity.add(animComp);

        entity.add(new CoinPickupComponent());

        return entity;
    }

    /** Builds the coin spin animation from the {@code Coin_01..06} atlas regions. */
    private Animation<TextureRegion> createCoinAnimation() {
        Array<AtlasRegion> regions = new Array<>();
        for (String name : COIN_REGION_NAMES) {
            AtlasRegion region = originAtlas.findRegion(name);
            if (region != null) {
                regions.add(region);
            }
        }
        if (regions.size == 0) {
            for (AtlasRegion region : originAtlas.getRegions()) {
                if (region.name.startsWith(COIN_REGION_NAMES[0].substring(0, 5))) {
                    regions.add(region);
                    break;
                }
            }
        }
        return new Animation<>(COIN_FRAME_DURATION, regions, LOOP);
    }

    /**
     * Builds a coin pickup entity that launches with the given initial velocity (a small upward
     * pop plus horizontal scatter) instead of sitting still, so it visibly arcs up and out before
     * gravity/collision pulls it back down to rest. Used for chest- and enemy-dropped coins.
     */
    public Entity createPoppedCoinPickup(float x, float y, float velocityX, float velocityY) {
        Entity entity = createCoinPickup(x, y);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.velocity.set(velocityX, velocityY);
        entity.add(movementComponent);

        entity.add(new PoppedItemComponent());

        return entity;
    }

    /**
     * Spawns {@code count} popped coin pickups at {@code (x, y)} into {@code engine}, each launched
     * with a random upward velocity ({@value #MIN_POP_VELOCITY_Y}-{@value #MAX_POP_VELOCITY_Y} u/s)
     * and horizontal scatter ({@value #MAX_POP_VELOCITY_X} u/s), scaled by {@code unitScale}.
     * Shared by {@code ChestSystem} and {@code EnemySystem} so chest and enemy coin drops feel identical.
     */
    public void popCoins(Engine engine, float x, float y, int count, float unitScale) {
        for (int i = 0; i < count; i++) {
            float velocityX = MathUtils.random(-MAX_POP_VELOCITY_X, MAX_POP_VELOCITY_X) * unitScale;
            float velocityY = MathUtils.random(MIN_POP_VELOCITY_Y, MAX_POP_VELOCITY_Y) * unitScale;
            engine.addEntity(createPoppedCoinPickup(x, y, velocityX, velocityY));
        }
    }

    /**
     * Creates a floating message entity at the player's current position. The message drifts upward
     * and fades out over its lifetime. Used for damage numbers, coin pickups, and potion effects.
     */
    public Entity createFloatingMessage(Engine engine, String text, float[] rgb, Entity playerEntity) {
        TransformComponent playerTransform = com.axehigh.platformer.ecs.components.Mappers.TRANSFORM.get(playerEntity);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(playerTransform.position.x, playerTransform.position.y + 24f * unitScale);
        transform.z = FLOATING_MESSAGE_Z;
        entity.add(transform);

        FloatingMessageComponent msg = new FloatingMessageComponent();
        msg.text = text;
        msg.color.set(rgb[0], rgb[1], rgb[2], 1f);
        entity.add(msg);

        engine.addEntity(entity);
        return entity;
    }

    /**
     * Builds an enemy entity from a Tiled {@code "enemy"} marker. Per-marker custom properties may
     * override the defaults: {@code enemyType} picks the sprite/stats/behavior variant,
     * {@code aiMode} ({@code "side-to-side"}/{@code "sidetoside"}, case-insensitive) switches the
     * patrol behavior to endless walking, and {@code speed}/{@code patrolRange} override the patrol
     * speed/range in world units before they're scaled by {@code unitScale}.
     */
    private Entity createEnemy(float x, float y, String enemyType, MapObject object, TiledMapTile tile, int roomIndex) {
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
                atlasPrefix = SpriteConstants.EnemyKnightSprite;
                walkRegionName = "walk";
                colWidth = SpriteConstants.EnemyKnightCollisionWidth;
                colHeight = SpriteConstants.EnemyKnightCollisionHeight;
                colOffsetY = SpriteConstants.EnemyKnightOffsetY;
                enemyScale = unitScale * SpriteConstants.EnemyKnightScale;
                break;
            default:
                atlasPrefix = SpriteConstants.EnemyWalkerSprite;
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
        String aiMode = getProperty(object, tile, "aiMode", null);
        if ("side-to-side".equalsIgnoreCase(aiMode) || "sidetoside".equalsIgnoreCase(aiMode)) {
            enemyComponent.aiMode = EnemyComponent.AiMode.SIDE_TO_SIDE;
        }
        float speedOverride = getFloatProperty(object, tile, "speed", Float.NaN);
        if (!Float.isNaN(speedOverride)) {
            enemyComponent.speed = speedOverride;
        }
        float patrolRangeOverride = getFloatProperty(object, tile, "patrolRange", Float.NaN);
        if (!Float.isNaN(patrolRangeOverride)) {
            enemyComponent.patrolRange = patrolRangeOverride;
        }
        enemyComponent.speed *= unitScale;
        enemyComponent.patrolRange *= unitScale;
        // Desync the patrol cycle so enemies in the same room don't move/pause in lockstep:
        // randomize the initial facing (all enemies otherwise spawn walking right) and jitter the
        // patrol speed ±15%, so each enemy drifts out of phase over time and never re-syncs.
        enemyComponent.direction = MathUtils.randomBoolean() ? 1 : -1;
        enemyComponent.speed *= MathUtils.random(0.85f, 1.15f);

        if ("flyer".equals(enemyType)) {
            enemyComponent.health = 5f;
            enemyComponent.maxHealth = 5f;
            FlyingEnemyComponent flying = new FlyingEnemyComponent();
            flying.bobAmplitude *= unitScale;
            // Random bob phase so flyers don't flap in unison (they all start at bobTime = 0).
            flying.bobTime = MathUtils.random(0f, MathUtils.PI2 / flying.bobFrequency);
            entity.add(flying);
        } else if ("shooter".equals(enemyType)) {
            EnemyShooterComponent shooter = new EnemyShooterComponent();
            // Stagger the first shot across the interval so shooters don't all fire the same
            // frame the player enters their room.
            shooter.shootCooldown.start(MathUtils.random(0f, shooter.shootInterval));
            entity.add(shooter);
        } else if ("knight".equals(enemyType)) {
            enemyComponent.health = 15f;
            enemyComponent.maxHealth = 15f;
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

    private Entity createPotionPickup(float x, float y, String potionType) {
        PotionType type = parsePotionType(potionType);
        TextureAtlas gameAtlas = assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class);
        TextureAtlas.AtlasRegion region = gameAtlas.findRegion(type.regionName());

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(unitScale * 0.5f, unitScale * 0.5f);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(region.getRegionWidth() * unitScale * 0.5f, region.getRegionHeight() * unitScale * 0.5f);
        entity.add(collisionComponent);

        PotionPickupComponent potionPickup = new PotionPickupComponent();
        potionPickup.type = type;
        entity.add(potionPickup);

        return entity;
    }

    /** Parses a {@code potionType} map property, defaulting to {@code HEALING} on unknown values. */
    private PotionType parsePotionType(String potionType) {
        try {
            return PotionType.valueOf(potionType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PotionType.HEALING;
        }
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
