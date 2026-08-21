package com.axehigh.platformer.map;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.assets.GameAssetRegistry.PLATFORM_ASSET;
import static com.axehigh.platformer.ecs.components.AnimationComponent.State.IDLE;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP;

/**
 * Builds the scripted hazard entities: oscillating moving platforms, acid/lava drop spawners, and
 * pulsing flame traps, with all tuning read from the marker's Tiled custom properties.
 */
class TrapFactory {
    /** Default oscillation angular frequency (rad/s) when a platform omits the {@code speed} property. */
    private static final float DEFAULT_PLATFORM_SPEED = 1f;

    private final FactoryContext context;

    TrapFactory(FactoryContext context) {
        this.context = context;
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
    public Entity createPlatform(float x, float y, float width, float height, TiledMapTile tile, MapObject object, int roomIndex) {
        TextureRegion region = tile != null ? tile.getTextureRegion() : new TextureRegion(context.getTexture(PLATFORM_ASSET));

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(width / region.getRegionWidth(), height / region.getRegionHeight());
        transform.z = FactoryContext.PLATFORM_Z;
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
        String axis = TileProps.getProperty(object, tile, "axis", null);
        if ("x".equalsIgnoreCase(axis)) {
            platform.amplitudeY = 0f;
        } else if ("y".equalsIgnoreCase(axis)) {
            platform.amplitudeX = 0f;
        }
        platform.amplitudeX = TileProps.getFloatProperty(object, tile, "amplitudeX", platform.amplitudeX);
        platform.amplitudeY = TileProps.getFloatProperty(object, tile, "amplitudeY", platform.amplitudeY);
        platform.speed = TileProps.getFloatProperty(object, tile, "speed", DEFAULT_PLATFORM_SPEED);
        platform.phase = TileProps.getFloatProperty(object, tile, "phase", 0f);
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
    public Entity createAcidDropSpawner(float x, float y, MapObject object, TiledMapTile tile, int roomIndex) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(context.unitScale, context.unitScale);
        transform.z = FactoryContext.DECOR_Z;
        entity.add(transform);

        CollisionComponent collision = new CollisionComponent();
        collision.bounds.setSize(4f * context.unitScale, 4f * context.unitScale);
        entity.add(collision);

        TrapComponent trap = new TrapComponent();
        trap.type = TrapComponent.TrapType.ACID_DROP_SPAWNER;
        trap.roomIndex = roomIndex;
        trap.spawnInterval = TileProps.getFloatProperty(object, tile, "interval", 2.0f);
        trap.projectileSpeed = TileProps.getFloatProperty(object, tile, "speed", 200f) * context.unitScale;
        trap.damage = TileProps.getFloatProperty(object, tile, "damage", 1f);
        trap.spawnTimer.start(trap.spawnInterval);

        String dir = TileProps.getProperty(object, tile, "direction", "down");
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
    public Entity createFlameTrap(float x, float y, MapObject object, TiledMapTile tile, int roomIndex) {
        AtlasRegion region = context.originAtlas.findRegion("fire1");
        if (region == null) {
            region = context.originAtlas.findRegion("goblin_idle1");
        }

        float flameScale = context.unitScale * SpriteConstants.FlameTrapScale;

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(flameScale, flameScale);
        transform.z = FactoryContext.DECOR_Z;
        String dir = TileProps.getProperty(object, tile, "direction", "down");
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
        trap.flameDuration = TileProps.getFloatProperty(object, tile, "duration", 2.0f);
        trap.cooldownDuration = TileProps.getFloatProperty(object, tile, "cooldown", 1.5f);
        trap.pulseSpeed = TileProps.getFloatProperty(object, tile, "pulseSpeed", 2.0f);
        trap.flameHeight = SpriteConstants.FlameTrapCollisionHeight;
        trap.flameWidth = SpriteConstants.FlameTrapCollisionWidth;
        trap.currentScale = trap.minScale;
        trap.isFlaming = false;
        trap.cooldownTimer.start(MathUtils.random(0f, trap.cooldownDuration));
        entity.add(trap);

        AnimationComponent animComp = new AnimationComponent();
        animComp.animations.put(IDLE, context.buildAnimation(0.1f, "fire", LOOP));
        animComp.currentState = IDLE;
        entity.add(animComp);

        return entity;
    }

    private static TrapComponent.TrapDirection parseDirection(String dir) {
        if (dir == null) return TrapComponent.TrapDirection.DOWN;
        switch (dir.toLowerCase()) {
            case "up": return TrapComponent.TrapDirection.UP;
            case "left": return TrapComponent.TrapDirection.LEFT;
            case "right": return TrapComponent.TrapDirection.RIGHT;
            default: return TrapComponent.TrapDirection.DOWN;
        }
    }
}
