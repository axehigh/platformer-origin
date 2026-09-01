package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.ecs.components.TrapComponent.TrapType;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;
import static com.axehigh.platformer.assets.SpriteConstants.AcidDropScale;
import static com.axehigh.platformer.ecs.components.AnimationComponent.State.*;
import static com.axehigh.platformer.ecs.components.Mappers.*;
import static com.axehigh.platformer.ecs.components.TrapComponent.TrapType.ACID_DROP;

/**
 * Drives trap entity lifecycle: acid/lava drop spawner timers, falling drop movement and
 * wall-collision removal, and flame trap pulsing (scale oscillation with cooldown cycling).
 * All trap types are room-gated — they freeze while the player is in a different room.
 */
public class TrapSystem extends IteratingSystem {
    private static final float TRAP_Z = 7f;

    /** Seconds after spawn during which an acid drop ignores wall culling, so it can clear the wall
     *  cell its spawn point sits in instead of being removed on its first frame. */
    private static final float ACID_DROP_SPAWN_GRACE = 0.12f;

    /** Seconds a freshly-spawned acid drop hangs at the spawn point (dripping/bulging) before
     *  releasing and falling. */
    private static final float ACID_DROP_DRIP_BUILD = 0.15f;

    /** Downward acceleration (world-units/s^2) applied to a falling acid drop so it speeds up like a
     *  real heavy droplet instead of falling at a constant clip. */
    private static final float ACID_DROP_GRAVITY = 800f;

    /** Frame duration (s) of the acid tube discharging animation (acid_tube1..4). */
    private static final float ACID_TUBE_FRAME_DURATION = 0.1f;

    /** Frame duration (s) of the pool's landing splash clip (acid_blob1..7, played once). */
    private static final float ACID_POOL_FRAME_DURATION = 0.05f;

    /** Pool footprint, as a fraction of the 128px pool sprite's damager box: width = this many drop
     *  widths, height = this fraction of a drop width. Yields a flat puddle ~1 tile wide. */
    private static final float ACID_POOL_WIDTH_FACTOR = 2f;
    private static final float ACID_POOL_HEIGHT_FACTOR = 0.5f;
    private static final float ACID_POOL_SOURCE_PX = 128f;

    private final Array<Rectangle> collisionRects;
    private final RoomState roomState;
    private final TextureAtlas atlas;
    private PooledEngine engine;
    private float unitScale = 1f;

    public TrapSystem(Array<Rectangle> collisionRects, RoomState roomState, AssetManager assetManager, int priority) {
        super(Family.all(TrapComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.collisionRects = collisionRects;
        this.roomState = roomState;
        this.atlas = assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class);
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = (PooledEngine) engine;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TrapComponent trap = TRAP.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);

        boolean roomActive = trap.roomIndex < 0 || trap.roomIndex == roomState.activeRoomIndex;

        switch (trap.type) {
            case ACID_DROP_SPAWNER:
                if (roomActive) {
                    updateSpawner(entity, trap, transform, deltaTime);
                }
                break;
            case ACID_DROP:
                updateDrop(entity, trap, transform, deltaTime);
                break;
            case ACID_POOL:
                updatePool(entity, trap, deltaTime);
                break;
            case FLAME:
                if (roomActive) {
                    updateFlame(entity, trap, deltaTime);
                }
                break;
        }
    }

    private void updateSpawner(Entity entity, TrapComponent trap, TransformComponent transform, float deltaTime) {
        AnimationComponent anim = ANIMATION.get(entity);
        if (trap.tubeAnimating) {
            // Mid-discharge: play the tube animation to completion, then release the drop.
            trap.tubeWindUpTimer.update(deltaTime);
            if (anim != null) {
                anim.currentState = WALKING;
            }
            if (trap.tubeWindUpTimer.isDone()) {
                trap.tubeAnimating = false;
                spawnDrop(transform, trap);
                trap.spawnTimer.start(trap.spawnInterval);
            }
            return;
        }

        if (anim != null && anim.currentState != IDLE) {
            anim.currentState = IDLE;
            anim.stateTime = 0f;
        }
        trap.spawnTimer.update(deltaTime);
        if (trap.spawnTimer.isDone()) {
            // Begin discharging: play the tube animation, the drop releases at the end.
            trap.tubeAnimating = true;
            trap.tubeWindUpTimer.start(trap.tubeWindUp);
            if (anim != null) {
                anim.currentState = WALKING;
                anim.stateTime = 0f;
            }
        }
    }

    private void spawnDrop(TransformComponent spawnerTransform, TrapComponent spawnerTrap) {
        Entity drop = engine.createEntity();

        // The drop is sized from the map unit scale (not the spawner's tube scale, which includes
        // the 2x tube fill factor) so a drop stays a ~half-tile droplet regardless of tube size.
        float dropW = 8f * unitScale;
        float dropH = 12f * unitScale;

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        // Drops spawn centered on the acid tube: the tube is a full tile anchored to the spawner
        // marker (spawner collision box is one tile), so the tube's center is half a tile up/right
        // from the corner. The transform is shifted back by half the drop's collision box so the
        // point is the CENTER of the drop (sprite + hitbox), not its bottom-left corner.
        float tubeCenterX = spawnerTransform.position.x + 8f * unitScale;
        float tubeCenterY = spawnerTransform.position.y + 8f * unitScale;
        transform.position.set(tubeCenterX - dropW / 2f, tubeCenterY - dropH / 2f);
        // The 32px acid_drop sprite is scaled by the shared drop-scale factor (same as the tube's).
        transform.scale.set(AcidDropScale * unitScale, AcidDropScale * unitScale);
        transform.z = TRAP_Z;
        drop.add(transform);

        TextureComponent texture = engine.createComponent(TextureComponent.class);
        TextureRegion dropRegion = atlas.findRegion(SpriteConstants.ACID_DROP_REGION);
        if (dropRegion != null) {
            texture.region = dropRegion;
        }
        drop.add(texture);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(dropW, dropH);
        drop.add(collision);

        TrapComponent trap = engine.createComponent(TrapComponent.class);
        trap.type = ACID_DROP;
        trap.spawnDirection = spawnerTrap.spawnDirection;
        trap.dropDamage = spawnerTrap.damage;
        trap.roomIndex = spawnerTrap.roomIndex;
        trap.projectileSpeed = spawnerTrap.projectileSpeed;
        trap.lifetime = 5.0f;
        trap.lifetimeTimer.start(trap.lifetime);
        // Hangs briefly at the spawn point (drip build) before releasing; the wall-cull grace is
        // started when the drip releases (see updateDrop) so it can clear the spawn wall.
        trap.dripBuild = ACID_DROP_DRIP_BUILD;
        trap.dripBuildTimer.start(trap.dripBuild);
        switch (trap.spawnDirection) {
            case DOWN: trap.dropVelocityY = -spawnerTrap.projectileSpeed; break;
            case UP: trap.dropVelocityY = spawnerTrap.projectileSpeed; break;
            case LEFT: trap.dropVelocityX = -spawnerTrap.projectileSpeed; break;
            case RIGHT: trap.dropVelocityX = spawnerTrap.projectileSpeed; break;
        }
        trap.dropAccel = ACID_DROP_GRAVITY;
        drop.add(trap);

        engine.addEntity(drop);
    }

    private void updateDrop(Entity entity, TrapComponent trap, TransformComponent transform, float deltaTime) {
        trap.lifetimeTimer.update(deltaTime);
        if (trap.lifetimeTimer.isDone()) {
            getEngine().removeEntity(entity);
            return;
        }

        // Drip build: hang at the spawn point, bulging, before releasing. The wall-cull grace is
        // started the moment the drop releases so it can clear the wall it spawned on.
        if (trap.dripBuildTimer.isActive()) {
            trap.dripBuildTimer.update(deltaTime);
            if (trap.dripBuildTimer.isDone()) {
                trap.spawnGrace.start(ACID_DROP_SPAWN_GRACE);
            }
            return;
        }

        // Released: move along the travel direction — a falling (DOWN) drop accelerates under
        // gravity like a real heavy droplet; other directions keep constant speed.
        if (trap.spawnDirection == TrapComponent.TrapDirection.DOWN) {
            trap.dropVelocityY -= trap.dropAccel * deltaTime;
            transform.position.y += trap.dropVelocityY * deltaTime;
        } else {
            transform.position.x += trap.dropVelocityX * deltaTime;
            transform.position.y += trap.dropVelocityY * deltaTime;
        }

        CollisionComponent collision = COLLISION.get(entity);
        collision.updateWorldBounds(transform.position);

        if (trap.spawnGrace.isActive()) {
            trap.spawnGrace.update(deltaTime);
            return;
        }

        if (hitsWall(collision.worldBounds)) {
            if (trap.spawnDirection == TrapComponent.TrapDirection.DOWN) {
                spawnPool(entity, trap, transform);
            }
            getEngine().removeEntity(entity);
        }
    }

    /**
     * Turns a downward-falling drop into a lingering acid pool on the ground at the impact point.
     * The pool sits for {@link #ACID_POOL_LIFETIME} seconds, deals damage via {@code TrapContactSystem},
     * then disappears.
     */
    private void spawnPool(Entity dropEntity, TrapComponent dropTrap, TransformComponent dropTransform) {
        float poolW = ACID_POOL_WIDTH_FACTOR * 8f * unitScale;
        float poolH = ACID_POOL_HEIGHT_FACTOR * 8f * unitScale;
        float poolScaleX = poolW / ACID_POOL_SOURCE_PX;
        float poolScaleY = poolH / ACID_POOL_SOURCE_PX;

        Entity pool = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        CollisionComponent dropCollision = COLLISION.get(dropEntity);
        float centerX = dropTransform.position.x + dropCollision.bounds.x + dropCollision.bounds.width / 2f;
        float groundY = dropTransform.position.y + dropCollision.bounds.y;
        transform.position.set(centerX - poolW / 2f, groundY);
        transform.scale.set(poolScaleX, poolScaleY);
        transform.z = TRAP_Z;
        pool.add(transform);

        TextureComponent texture = engine.createComponent(TextureComponent.class);
        // Degrade gracefully if the frames or the drop texture are missing from the atlas; the
        // splash animation below (if present) overwrites this region every frame via AnimationSystem.
        TextureRegion poolRegion = atlas.findRegion(SpriteConstants.ACID_POOL_REGION + "1");
        if (poolRegion == null) {
            poolRegion = atlas.findRegion(SpriteConstants.ACID_DROP_REGION);
        }
        texture.region = poolRegion;
        pool.add(texture);

        // One-shot landing splash (acid_blob1..7) on the SPLASHING state: plays a single round
        // when the drop lands, then rests on the final splat frame for the remaining poolDuration.
        // Falls back to the static single frame above if any clip frame is missing from the atlas.
        Array<TextureRegion> splashFrames = new Array<>();
        for (int i = 1; i <= 7; i++) {
            TextureRegion frame = atlas.findRegion(SpriteConstants.ACID_POOL_REGION + i);
            if (frame == null) break;
            splashFrames.add(frame);
        }
        if (splashFrames.size > 0) {
            AnimationComponent anim = engine.createComponent(AnimationComponent.class);
            anim.animations.put(SPLASHING,
                new Animation<>(ACID_POOL_FRAME_DURATION, splashFrames, Animation.PlayMode.NORMAL));
            anim.currentState = SPLASHING;
            pool.add(anim);
        }

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(poolW, poolH);
        pool.add(collision);

        TrapComponent trap = engine.createComponent(TrapComponent.class);
        trap.type = TrapType.ACID_POOL;
        trap.roomIndex = dropTrap.roomIndex;
        trap.dropDamage = dropTrap.dropDamage;
        trap.poolDuration = SpriteConstants.ACID_POOL_LIFETIME;
        trap.poolTimer.start(trap.poolDuration);
        pool.add(trap);

        engine.addEntity(pool);
    }

    private void updatePool(Entity entity, TrapComponent trap, float deltaTime) {
        trap.poolTimer.update(deltaTime);
        if (trap.poolTimer.isDone()) {
            getEngine().removeEntity(entity);
        }
    }

    private boolean hitsWall(Rectangle bounds) {
        for (Rectangle rect : collisionRects) {
            if (bounds.overlaps(rect)) {
                return true;
            }
        }
        return false;
    }

    private void updateFlame(Entity entity, TrapComponent trap, float deltaTime) {
        if (trap.isFlaming) {
            trap.flameTimer.update(deltaTime);
            float t = 1f - (trap.flameTimer.getRemaining() / trap.flameDuration);
            trap.currentScale = MathUtils.lerp(trap.minScale, trap.maxScale, t);

            if (trap.flameTimer.isDone()) {
                trap.isFlaming = false;
                trap.cooldownTimer.start(trap.cooldownDuration);
            }
        } else {
            trap.cooldownTimer.update(deltaTime);
            trap.currentScale = trap.minScale;

            if (trap.cooldownTimer.isDone()) {
                trap.isFlaming = true;
                trap.flameTimer.start(trap.flameDuration);
            }
        }

        updateFlameCollision(entity, trap);
    }

    private void updateFlameCollision(Entity entity, TrapComponent trap) {
        CollisionComponent collision = COLLISION.get(entity);

        float scaledHeight = trap.flameHeight * trap.currentScale;
        float scaledWidth = trap.flameWidth * trap.currentScale;

        switch (trap.flameDirection) {
            case DOWN:
                collision.bounds.width = scaledWidth;
                collision.bounds.height = scaledHeight;
                collision.bounds.x = -scaledWidth / 2f;
                collision.bounds.y = 0f;
                break;
            case UP:
                collision.bounds.width = scaledWidth;
                collision.bounds.height = scaledHeight;
                collision.bounds.x = -scaledWidth / 2f;
                collision.bounds.y = -scaledHeight;
                break;
            case RIGHT:
                collision.bounds.width = scaledHeight;
                collision.bounds.height = scaledWidth;
                collision.bounds.x = 0f;
                collision.bounds.y = -scaledWidth / 2f;
                break;
            case LEFT:
                collision.bounds.width = scaledHeight;
                collision.bounds.height = scaledWidth;
                collision.bounds.x = -scaledHeight;
                collision.bounds.y = -scaledWidth / 2f;
                break;
        }
    }
}
