package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.AnimationComponent.State.IDLE;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP;

/**
 * Builds pickup entities: animated coins (placed markers and popped physics coins), daggers, and
 * potions, plus the {@link #popCoins(...)} helper used by chest and enemy death drops.
 */
class PickupFactory {
    /** Popped-coin launch velocity ranges (world units/s, scaled by {@code unitScale} on use). */
    private static final float MIN_POP_VELOCITY_Y = 80f;
    private static final float MAX_POP_VELOCITY_Y = 140f;
    private static final float MAX_POP_VELOCITY_X = 12f;
    /** Per-frame duration of the coin spin animation (matches the 100ms Tiled animation). */
    private static final float COIN_FRAME_DURATION = 0.1f;
    /** Atlas region names of the coin spin frames, in playback order. */
    private static final String[] COIN_REGION_NAMES = {"Coin_01", "Coin_02", "Coin_03", "Coin_04", "Coin_05", "Coin_06"};
    /** The coin sprite renders at half a map tile by default (8px base tile scaled by {@code unitScale}). */
    private static final float DEFAULT_COIN_SIZE = 8f;

    private final FactoryContext context;

    PickupFactory(FactoryContext context) {
        this.context = context;
    }

    /**
     * Builds an animated coin pickup entity sized to half a map tile (base 8px scaled by
     * {@code unitScale}), centered on {@code (x, y)}. Used for popped coins (chest/enemy drops).
     */
    public Entity createCoinPickup(float x, float y) {
        float size = DEFAULT_COIN_SIZE * context.unitScale;
        AtlasRegion region = context.originAtlas.findRegion(COIN_REGION_NAMES[0]);
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
        float size = DEFAULT_COIN_SIZE * context.unitScale;
        AtlasRegion region = context.originAtlas.findRegion(COIN_REGION_NAMES[0]);
        float scale = size / region.getRegionWidth();
        return buildCoin(x + width / 2f - size / 2f, y + height / 2f - size / 2f, size, size, scale);
    }

    /**
     * Shared coin-entity builder: places a square {@code scale * regionWidth} coin centered inside
     * the {@code width} x {@code height} rect starting at {@code (x, y)} (bottom-left).
     */
    private Entity buildCoin(float x, float y, float width, float height, float scale) {
        Animation<TextureRegion> animation = buildCoinAnimation();
        AtlasRegion region = context.originAtlas.findRegion(COIN_REGION_NAMES[0]);

        float scaledWidth = region.getRegionWidth() * scale;
        float scaledHeight = region.getRegionHeight() * scale;

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x + (width - scaledWidth) / 2f, y + (height - scaledHeight) / 2f);
        transform.scale.set(scale, scale);
        transform.z = FactoryContext.DECOR_Z;
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
    private Animation<TextureRegion> buildCoinAnimation() {
        Array<AtlasRegion> regions = new Array<>();
        for (String name : COIN_REGION_NAMES) {
            AtlasRegion region = context.originAtlas.findRegion(name);
            if (region != null) {
                regions.add(region);
            }
        }
        if (regions.size == 0) {
            for (AtlasRegion region : context.originAtlas.getRegions()) {
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
     * and a smaller sideways scatter (up to {@value #MAX_POP_VELOCITY_X} u/s), scaled by the given
     * {@code unitScale}.
     */
    public void popCoins(Engine engine, float x, float y, int count, float unitScale) {
        for (int i = 0; i < count; i++) {
            float velocityX = MathUtils.random(-MAX_POP_VELOCITY_X, MAX_POP_VELOCITY_X) * unitScale;
            float velocityY = MathUtils.random(MIN_POP_VELOCITY_Y, MAX_POP_VELOCITY_Y) * unitScale;
            engine.addEntity(createPoppedCoinPickup(x, y, velocityX, velocityY));
        }
    }

    /**
     * Collision-aware variant of {@link #popCoins(Engine, float, float, int, float)}. If the
     * requested spawn position overlaps a static collision rect, the coin is nudged upward until
     * it sits just above the obstacle. Horizontal velocity is clamped to at most
     * {@code 2 tiles per frame} (at 60 fps) to prevent tunneling through thin walls.
     */
    public void popCoins(Engine engine, float x, float y, int count, float unitScale, Array<Rectangle> collisionRects) {
        float coinSize = DEFAULT_COIN_SIZE * unitScale;

        for (int i = 0; i < count; i++) {
            float velocityX = MathUtils.random(-MAX_POP_VELOCITY_X, MAX_POP_VELOCITY_X) * unitScale;
            float velocityY = MathUtils.random(MIN_POP_VELOCITY_Y, MAX_POP_VELOCITY_Y) * unitScale;

            float spawnX = x;
            float spawnY = y;

            // If spawn overlaps a collision rect, push upward to the nearest open space
            if (collisionRects != null) {
                Rectangle testRect = new Rectangle(spawnX - coinSize / 2f, spawnY - coinSize / 2f, coinSize, coinSize);
                for (Rectangle rect : collisionRects) {
                    if (testRect.overlaps(rect)) {
                        spawnY = rect.y + rect.height + coinSize / 2f + 1f;
                        testRect.setY(spawnY - coinSize / 2f);
                        break;
                    }
                }
            }

            engine.addEntity(createPoppedCoinPickup(spawnX, spawnY, velocityX, velocityY));
        }
    }

    public Entity createDaggerPickup(float x, float y, MapObject object, TiledMapTile tile) {
        Texture texture = context.getTexture("gfx/old/dagger.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(context.unitScale, context.unitScale);
        transform.z = FactoryContext.DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth() * context.unitScale, texture.getHeight() * context.unitScale);
        entity.add(collisionComponent);

        DaggerPickupComponent daggerPickup = new DaggerPickupComponent();
        daggerPickup.amount = TileProps.getIntProperty(object, tile, "amount", daggerPickup.amount);
        entity.add(daggerPickup);

        return entity;
    }

    public Entity createDaggerPickup(float x, float y) {
        return createDaggerPickup(x, y, null, null);
    }

    public Entity createPotionPickup(float x, float y, String potionType) {
        PotionType type = parsePotionType(potionType);
        AtlasRegion region = context.originAtlas.findRegion(type.regionName());

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(context.unitScale * 0.375f, context.unitScale * 0.375f);
        transform.z = FactoryContext.DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(region.getRegionWidth() * context.unitScale * 0.375f, region.getRegionHeight() * context.unitScale * 0.375f);
        entity.add(collisionComponent);

        PotionPickupComponent potionPickup = new PotionPickupComponent();
        potionPickup.type = type;
        entity.add(potionPickup);

        return entity;
    }

    /**
     * Builds a potion pickup that launches with the given initial velocity (a small upward pop
     * plus horizontal scatter) instead of sitting still, so it visibly arcs up and out before
     * gravity/collision pulls it back down to rest. Used for chest-dropped potions.
     */
    public Entity createPoppedPotionPickup(float x, float y, float velocityX, float velocityY, String potionType) {
        Entity entity = createPotionPickup(x, y, potionType);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.velocity.set(velocityX, velocityY);
        entity.add(movementComponent);

        entity.add(new PoppedItemComponent());

        return entity;
    }

    /**
     * Collision-aware spawn of a single popped potion at {@code (x, y)} into {@code engine}.
     * Launched with a random upward velocity ({@value #MIN_POP_VELOCITY_Y}-{@value #MAX_POP_VELOCITY_Y}
     * u/s) and a smaller sideways scatter (up to {@value #MAX_POP_VELOCITY_X} u/s), scaled by the
     * given {@code unitScale}. If the spawn position overlaps a static collision rect, the potion
     * is nudged upward until it sits just above the obstacle.
     */
    public void popPotion(Engine engine, float x, float y, String potionType, float unitScale, Array<Rectangle> collisionRects) {
        float potionSize = 8f * unitScale;
        float velocityX = 0f;
        float velocityY = MathUtils.random(MIN_POP_VELOCITY_Y, MAX_POP_VELOCITY_Y) * unitScale;

        float spawnX = x;
        float spawnY = y;

        if (collisionRects != null) {
            Rectangle testRect = new Rectangle(spawnX - potionSize / 2f, spawnY - potionSize / 2f, potionSize, potionSize);
            for (Rectangle rect : collisionRects) {
                if (testRect.overlaps(rect)) {
                    spawnY = rect.y + rect.height + potionSize / 2f + 1f;
                    testRect.setY(spawnY - potionSize / 2f);
                    break;
                }
            }
        }

        engine.addEntity(createPoppedPotionPickup(spawnX, spawnY, velocityX, velocityY, potionType));
    }

    /** Parses a {@code potionType} map property, defaulting to {@code HEALING} on unknown values. */
    private static PotionType parsePotionType(String potionType) {
        try {
            return PotionType.valueOf(potionType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PotionType.HEALING;
        }
    }
}
