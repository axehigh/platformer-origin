package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyBulletComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.EnemyShooterComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
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

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY_SHOOTER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Drives shooter-enemy firing: every {@code shootInterval} seconds, spawns a bullet at the
 * enemy's position traveling horizontally in its current patrol {@code direction} (no
 * player-aiming/aggro). Firing is skipped entirely while the enemy is stunned (mirrors
 * {@code EnemySystem}'s own stun-pause), so a knockback pop isn't interrupted by a shot, and also
 * skipped while the shooter's {@code roomIndex} isn't the currently active Room (see {@code
 * RoomState}/{@code CameraSystem}), so a shooter never fires a bullet the player wouldn't be able
 * to see coming from a different, currently inactive room. {@code shootCooldown} keeps
 * ticking/idling at done regardless, so the shooter fires as soon as the player re-enters its room.
 */
public class EnemyShootSystem extends IteratingSystem {
    private static final float BULLET_SPEED = 150f;
    private static final float BULLET_LIFETIME = 1.5f;
    private static final float BULLET_SIZE = 4f;
    private static final float BULLET_Z = 8f;

    private final AssetManager assetManager;
    private final RoomState roomState;
    private PooledEngine engine;
    private float unitScale = 1f;

    public EnemyShootSystem(AssetManager assetManager, RoomState roomState) {
        this(assetManager, roomState, 0);
    }

    public EnemyShootSystem(AssetManager assetManager, RoomState roomState, int priority) {
        super(Family.all(EnemyComponent.class, EnemyShooterComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
        this.roomState = roomState;
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
        EnemyComponent enemy = ENEMY.get(entity);
        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);

        if (enemy.isDead) {
            return;
        }

        shooter.shootCooldown.update(deltaTime);
        if (enemy.hitStun.isActive()) {
            return;
        }

        boolean roomActive = enemy.roomIndex < 0 || enemy.roomIndex == roomState.activeRoomIndex;
        if (shooter.shootCooldown.isDone() && roomActive) {
            spawnBullet(transform, collision, enemy.direction);
            shooter.shootCooldown.start(shooter.shootInterval);
        }
    }

    private void spawnBullet(TransformComponent enemyTransform, CollisionComponent enemyCollision, int direction) {
        Entity bullet = engine.createEntity();

        float bulletSize = BULLET_SIZE * unitScale;
        float centerY = enemyTransform.position.y + (enemyCollision.bounds.height - bulletSize) / 2f;
        float spawnX = direction > 0
            ? enemyTransform.position.x + enemyCollision.bounds.width
            : enemyTransform.position.x - bulletSize;

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(spawnX, centerY);
        transform.scale.set(unitScale, unitScale);
        transform.z = BULLET_Z;
        bullet.add(transform);

        TextureComponent textureComponent = engine.createComponent(TextureComponent.class);
        textureComponent.region = new TextureRegion(assetManager.get("gfx/old/bullet.png", Texture.class));
        bullet.add(textureComponent);

        MovementComponent movement = engine.createComponent(MovementComponent.class);
        float speed = BULLET_SPEED * unitScale;
        movement.velocity.set(speed * direction, 0f);
        movement.maxSpeedX = speed;
        movement.maxSpeedY = 0f;
        bullet.add(movement);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(bulletSize, bulletSize);
        bullet.add(collision);

        BulletComponent bulletComponent = engine.createComponent(BulletComponent.class);
        bulletComponent.damage = 0f;
        bulletComponent.lifetime = BULLET_LIFETIME;
        bullet.add(bulletComponent);

        bullet.add(engine.createComponent(EnemyBulletComponent.class));

        engine.addEntity(bullet);
    }
}
