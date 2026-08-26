package com.axehigh.platformer.map;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.ecs.systems.PlayerDamageResolver;
import com.axehigh.platformer.util.PotionEffects;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
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

import static com.axehigh.platformer.GameConstants.MESSAGE_COLOR_DAMAGE;

/**
 * Facade for building Ashley entities: dispatches object-layer markers ({@link #spawnObjects})
 * and effect spawns ({@link #spawnEffects}) to the focused factories ({@link PlayerFactory},
 * {@link EnemyFactory}, {@link PickupFactory}, {@link TrapFactory}), and directly builds the
 * map-driven decorations (torch, light effect), exit gate, chest, and floating messages.
 */
public class EntityFactory {
    /** Z-layer for floating messages — above entities but below particles/lights. */
    private static final float FLOATING_MESSAGE_Z = 15f;
    /** Flame offset within the 16x16 torch sprite (sprite-relative, scaled by {@code unitScale}). */
    private static final float TORCH_FLAME_X = 8f;
    private static final float TORCH_FLAME_Y = 13f;
    /** Default glow halo radius (world units) for a torch light. */
    private static final float DEFAULT_TORCH_LIGHT_RADIUS = 96f;
    /** Fallback gate trigger size (world units) when the Tiled object rect has no dimensions. */
    private static final float DEFAULT_EXIT_GATE_WIDTH = 140f;
    private static final float DEFAULT_EXIT_GATE_HEIGHT = 152f;

    private final FactoryContext context;
    private final PlayerFactory playerFactory;
    private final EnemyFactory enemyFactory;
    private final PickupFactory pickupFactory;
    private final TrapFactory trapFactory;

    public EntityFactory(AssetManager assetManager) {
        this.context = new FactoryContext(assetManager);
        this.playerFactory = new PlayerFactory(context);
        this.enemyFactory = new EnemyFactory(context);
        this.pickupFactory = new PickupFactory(context);
        this.trapFactory = new TrapFactory(context);
    }

    public void setUnitScale(float unitScale) {
        context.setUnitScale(unitScale);
    }

    public Entity createPlayer(float x, float y) {
        return playerFactory.createPlayer(x, y);
    }

    public void resetPlayerCollision(CollisionComponent collisionComponent, float finalScale) {
        playerFactory.resetPlayerCollision(collisionComponent, finalScale);
    }

    /**
     * Builds an animated coin pickup entity sized to half a map tile, centered on {@code (x, y)}.
     * Used for popped coins (chest/enemy drops).
     */
    public Entity createCoinPickup(float x, float y) {
        return pickupFactory.createCoinPickup(x, y);
    }

    public Entity createPoppedCoinPickup(float x, float y, float velocityX, float velocityY) {
        return pickupFactory.createPoppedCoinPickup(x, y, velocityX, velocityY);
    }

    /** Spawns {@code count} popped coin pickups at {@code (x, y)} with random launch velocities. */
    public void popCoins(Engine engine, float x, float y, int count, float unitScale) {
        pickupFactory.popCoins(engine, x, y, count, unitScale);
    }

    /** Spawns {@code count} popped coin pickups, avoiding spawn inside collision rects. */
    public void popCoins(Engine engine, float x, float y, int count, float unitScale, Array<Rectangle> collisionRects) {
        pickupFactory.popCoins(engine, x, y, count, unitScale, collisionRects);
    }

    /** Builds a potion pickup entity at the given position. */
    public Entity createPotionPickup(float x, float y, String potionType) {
        return pickupFactory.createPotionPickup(x, y, potionType);
    }

    /**
     * Spawns decorative entities (coin, chest, torch, exit gate, enemy) found in the object layer.
     * {@code roomState} is used to assign each spawned enemy to whichever Room rectangle contains
     * its spawn point (see {@code EnemyComponent.roomIndex}).
     */
    public void spawnObjects(Engine engine, MapObjects objects, RoomState roomState) {
        Array<MapObject> toRemove = new Array<>();
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

            String type = TileProps.getProperty(object, tile, "type", null);
            if (type == null) {
                continue;
            }

            switch (type) {
                case "coin":
                    engine.addEntity(pickupFactory.createCoinPickup(spawnX, spawnY, objectWidth, objectHeight));
                    spawned = true;
                    break;
                case "chest":
                    String chestPotionType = TileProps.getProperty(object, tile, "potionType", null);
                    engine.addEntity(createChest(spawnX, spawnY, chestPotionType));
                    spawned = true;
                    break;
                case "torch":
                    engine.addEntity(createTorch(spawnX, spawnY, object, tile));
                    spawned = true;
                    break;
                case "exitGate":
                    String nextLevelPath = TileProps.getProperty(object, tile, "nextLevel", null);
                    engine.addEntity(createExitGate(spawnX, spawnY, objectWidth, objectHeight, nextLevelPath));
                    spawned = true;
                    break;
                case "dagger":
                    engine.addEntity(pickupFactory.createDaggerPickup(spawnX, spawnY));
                    spawned = true;
                    break;
                case "potion":
                    String potionType = TileProps.getProperty(object, tile, "potionType", "healing");
                    engine.addEntity(pickupFactory.createPotionPickup(spawnX, spawnY, potionType));
                    spawned = true;
                    break;
                case "enemy":
                    String enemyType = TileProps.getProperty(object, tile, "enemyType", "walker");
                    int roomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    engine.addEntity(enemyFactory.createEnemy(spawnX, spawnY, enemyType, object, tile, roomIndex));
                    spawned = true;
                    break;
                case "platform":
                    int platformRoomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    engine.addEntity(trapFactory.createPlatform(spawnX, spawnY, objectWidth, objectHeight, tile, object, platformRoomIndex));
                    spawned = true;
                    break;
                case "trap":
                    String trapType = TileProps.getProperty(object, tile, "trapType", "acidDrop");
                    int trapRoomIndex = roomState.findRoomIndexContaining(centerX, centerY);
                    if ("flame".equalsIgnoreCase(trapType)) {
                        engine.addEntity(trapFactory.createFlameTrap(spawnX, spawnY, object, tile, trapRoomIndex));
                    } else {
                        engine.addEntity(trapFactory.createAcidDropSpawner(spawnX, spawnY, object, tile, trapRoomIndex));
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

    /**
     * Spawns runtime effect entities (lights, particles, sounds) at decoration-layer tiles that
     * carry an {@code effect} property. The tile sprite is rendered by the Tiled map renderer —
     * these entities carry only the effect component (e.g. {@code LightComponent}), no texture.
     */
    public void spawnEffects(Engine engine, Array<MapLoader.EffectSpawn> spawns, RoomState roomState) {
        for (MapLoader.EffectSpawn spawn : spawns) {
            switch (spawn.effectType) {
                case "light":
                    engine.addEntity(createLightEffect(spawn.x, spawn.y, spawn.tile));
                    break;
                case "particle":
                    // TODO: future — engine.addEntity(createParticleEffect(spawn.x, spawn.y, spawn.tile));
                    break;
                case "sound":
                    // TODO: future — engine.addEntity(createSoundEffect(spawn.x, spawn.y, spawn.tile));
                    break;
            }
        }
    }

    /**
     * Creates a light-only effect entity at the given world position. No texture — the tile's
     * sprite is rendered by the Tiled map renderer. The light center is read from the tile's
     * collision-editor shape (drawn in Tiled on the tile): the shape's center in tile-local
     * coordinates becomes the light offset. If no shape is drawn, the light defaults to the
     * tile center. Per-tile properties {@code lightRadius}, {@code lightColor},
     * {@code lightFlickerSpeed} override defaults.
     */
    private Entity createLightEffect(float x, float y, TiledMapTile tile) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(context.unitScale, context.unitScale);
        transform.z = FactoryContext.DECOR_Z;
        entity.add(transform);

        LightComponent light = new LightComponent();
        light.radius = TileProps.getFloatPropertyFromTile(tile, "lightRadius", DEFAULT_TORCH_LIGHT_RADIUS);
        light.phase = MathUtils.random(MathUtils.PI2);

        // Read light offset from the tile's collision-editor shape center (Tiled WYSIWYG).
        // Shape coords are in tile-local pixel space (1 world unit = 1 pixel), same as the
        // decoration-layer renderer — no unitScale multiplication needed.
        MapObjects shapes = tile.getObjects();
        if (shapes.getCount() > 0) {
            MapObject shape = shapes.get(0);
            Rectangle bounds = MapLoader.shapeBounds(shape);
            if (bounds != null) {
                light.offset.set(
                    bounds.x + bounds.width / 2f,
                    bounds.y + bounds.height / 2f
                );
            } else {
                light.offset.set(tile.getTextureRegion().getRegionWidth() / 2f,
                    tile.getTextureRegion().getRegionHeight() / 2f);
            }
        } else {
            // No shape drawn — default to tile center
            light.offset.set(tile.getTextureRegion().getRegionWidth() / 2f,
                tile.getTextureRegion().getRegionHeight() / 2f);
        }

        String colorStr = TileProps.getStringPropertyFromTile(tile, "lightColor", null);
        if (colorStr != null) {
            light.color = TileProps.parseColor(colorStr, light.color);
        }
        float flickerSpeed = TileProps.getFloatPropertyFromTile(tile, "lightFlickerSpeed", Float.NaN);
        if (!Float.isNaN(flickerSpeed)) {
            light.flickerSpeed = flickerSpeed;
        }
        entity.add(light);

        return entity;
    }

    private Entity createDecoration(float x, float y, String texturePath) {
        Texture texture = context.getTexture(texturePath);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(context.unitScale, context.unitScale);
        transform.z = FactoryContext.DECOR_Z;
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
        light.radius = TileProps.getFloatProperty(object, tile, "lightRadius", DEFAULT_TORCH_LIGHT_RADIUS);
        light.offset.set(TORCH_FLAME_X * context.unitScale, TORCH_FLAME_Y * context.unitScale);
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
        transform.z = FactoryContext.DECOR_Z;
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
     *
     * @param potionType if non-null, the chest drops a potion of this type instead of coins
     */
    private Entity createChest(float x, float y, String potionType) {
        AtlasRegion region = context.originAtlas.findRegion(SpriteConstants.CHEST_CLOSED_REGION);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(1f, 1f);
        transform.z = FactoryContext.DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(region.getRegionWidth(), region.getRegionHeight());
        entity.add(collisionComponent);

        ChestComponent chest = new ChestComponent();
        if (potionType != null) {
            try {
                chest.potionType = PotionType.valueOf(potionType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Unknown potion type — fall back to coin chest
            }
        }
        entity.add(chest);

        return entity;
    }

    /**
     * Registers the global floating-message feedback listeners: damage numbers on every player
     * hit and pickup text (with per-potion color) whenever a potion effect fires. Call once at
     * screen setup; the listeners are static and persist until replaced.
     */
    public void installFeedbackListeners(Engine engine) {
        PlayerDamageResolver.setDamageListener(damagedEntity ->
            createFloatingMessage(engine, "-1", MESSAGE_COLOR_DAMAGE, damagedEntity));
        PotionEffects.setPotionListener((potionEntity, type) ->
            createFloatingMessage(engine, type.pickupMessage(), type.messageColor(), potionEntity));
    }

    /**
     * Creates a floating message entity at the player's current position. The message drifts upward
     * and fades out over its lifetime. Used for damage numbers, coin pickups, and potion effects.
     */
    public Entity createFloatingMessage(Engine engine, String text, float[] rgb, Entity playerEntity) {
        TransformComponent playerTransform = Mappers.TRANSFORM.get(playerEntity);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(playerTransform.position.x, playerTransform.position.y + 24f * context.unitScale);
        transform.z = FLOATING_MESSAGE_Z;
        entity.add(transform);

        FloatingMessageComponent msg = new FloatingMessageComponent();
        msg.text = text;
        msg.color.set(rgb[0], rgb[1], rgb[2], 1f);
        entity.add(msg);

        engine.addEntity(entity);
        return entity;
    }
}
