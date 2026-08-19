package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TrapComponent;
import com.axehigh.platformer.ecs.components.TrapComponent.TrapType;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TRAP;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Drives trap entity lifecycle: acid/lava drop spawner timers, falling drop movement and
 * wall-collision removal, and flame trap pulsing (scale oscillation with cooldown cycling).
 * All trap types are room-gated — they freeze while the player is in a different room.
 */
public class TrapSystem extends IteratingSystem {
    private static final float TRAP_Z = 7f;

    private final Array<Rectangle> collisionRects;
    private final RoomState roomState;
    private final AssetManager assetManager;
    private PooledEngine engine;

    public TrapSystem(Array<Rectangle> collisionRects, RoomState roomState, AssetManager assetManager, int priority) {
        super(Family.all(TrapComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.collisionRects = collisionRects;
        this.roomState = roomState;
        this.assetManager = assetManager;
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
            case FLAME:
                if (roomActive) {
                    updateFlame(entity, trap, deltaTime);
                }
                break;
        }
    }

    private void updateSpawner(Entity entity, TrapComponent trap, TransformComponent transform, float deltaTime) {
        trap.spawnTimer.update(deltaTime);
        if (trap.spawnTimer.isDone()) {
            spawnDrop(transform, trap);
            trap.spawnTimer.start(trap.spawnInterval);
        }
    }

    private void spawnDrop(TransformComponent spawnerTransform, TrapComponent spawnerTrap) {
        Entity drop = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(spawnerTransform.position.x, spawnerTransform.position.y);
        transform.scale.set(spawnerTransform.scale.x, spawnerTransform.scale.y);
        transform.z = TRAP_Z;
        drop.add(transform);

        TextureComponent texture = engine.createComponent(TextureComponent.class);
        try {
            Texture tex = assetManager.get("gfx/acid_drop.png", Texture.class);
            texture.region = new TextureRegion(tex);
        } catch (Exception e) {
            // Fallback: use a white pixel if texture not loaded
        }
        drop.add(texture);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(8f * transform.scale.x, 12f * transform.scale.y);
        drop.add(collision);

        TrapComponent trap = engine.createComponent(TrapComponent.class);
        trap.type = TrapType.ACID_DROP;
        trap.spawnDirection = spawnerTrap.spawnDirection;
        trap.dropDamage = spawnerTrap.damage;
        trap.roomIndex = spawnerTrap.roomIndex;
        trap.projectileSpeed = spawnerTrap.projectileSpeed;
        trap.lifetime = 5.0f;
        trap.lifetimeTimer.start(trap.lifetime);
        drop.add(trap);

        engine.addEntity(drop);
    }

    private void updateDrop(Entity entity, TrapComponent trap, TransformComponent transform, float deltaTime) {
        trap.lifetimeTimer.update(deltaTime);
        if (trap.lifetimeTimer.isDone()) {
            getEngine().removeEntity(entity);
            return;
        }

        float speed = trap.projectileSpeed;
        switch (trap.spawnDirection) {
            case DOWN:
                transform.position.y -= speed * deltaTime;
                break;
            case UP:
                transform.position.y += speed * deltaTime;
                break;
            case LEFT:
                transform.position.x -= speed * deltaTime;
                break;
            case RIGHT:
                transform.position.x += speed * deltaTime;
                break;
        }

        CollisionComponent collision = COLLISION.get(entity);
        collision.updateWorldBounds(transform.position);

        if (hitsWall(collision.worldBounds)) {
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
