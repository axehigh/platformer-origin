package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyBulletComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.EnemyShooterComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
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
 * skipped while the player is in a different flip-screen room (see {@code CameraSystem}/
 * {@code GameConstants}), so a shooter never fires a bullet the player wouldn't be able to see
 * coming from an adjacent, currently off-screen room. {@code shootCooldown} keeps ticking/idling
 * at done regardless, so the shooter fires as soon as the player re-enters its room.
 */
public class EnemyShootSystem extends IteratingSystem {
    private static final float BULLET_SPEED = 150f;
    private static final float BULLET_LIFETIME = 1.5f;
    private static final float BULLET_SIZE = 4f;
    private static final float BULLET_Z = 8f;

    private final AssetManager assetManager;
    private PooledEngine engine;
    private ImmutableArray<Entity> players;

    public EnemyShootSystem(AssetManager assetManager) {
        this(assetManager, 0);
    }

    public EnemyShootSystem(AssetManager assetManager, int priority) {
        super(Family.all(EnemyComponent.class, EnemyShooterComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = (PooledEngine) engine;
        this.players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EnemyComponent enemy = ENEMY.get(entity);
        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);

        shooter.shootCooldown.update(deltaTime);
        if (enemy.hitStun.isActive()) {
            return;
        }

        if (shooter.shootCooldown.isDone() && isPlayerInSameRoom(transform)) {
            spawnBullet(transform, collision, enemy.direction);
            shooter.shootCooldown.start(shooter.shootInterval);
        }
    }

    /** True if the single player entity's current flip-screen room matches the shooter's room. */
    private boolean isPlayerInSameRoom(TransformComponent shooterTransform) {
        if (players.size() == 0) {
            return false;
        }
        TransformComponent playerTransform = TRANSFORM.get(players.first());

        int shooterRoomX = (int) (shooterTransform.position.x / GameConstants.VIRTUAL_WIDTH);
        int shooterRoomY = (int) (shooterTransform.position.y / GameConstants.VIRTUAL_HEIGHT);
        int playerRoomX = (int) (playerTransform.position.x / GameConstants.VIRTUAL_WIDTH);
        int playerRoomY = (int) (playerTransform.position.y / GameConstants.VIRTUAL_HEIGHT);

        return shooterRoomX == playerRoomX && shooterRoomY == playerRoomY;
    }

    private void spawnBullet(TransformComponent enemyTransform, CollisionComponent enemyCollision, int direction) {
        Entity bullet = engine.createEntity();

        float centerY = enemyTransform.position.y + (enemyCollision.bounds.height - BULLET_SIZE) / 2f;
        float spawnX = direction > 0
            ? enemyTransform.position.x + enemyCollision.bounds.width
            : enemyTransform.position.x - BULLET_SIZE;

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(spawnX, centerY);
        transform.z = BULLET_Z;
        bullet.add(transform);

        TextureComponent textureComponent = engine.createComponent(TextureComponent.class);
        textureComponent.region = new TextureRegion(assetManager.get("gfx/bullet.png", Texture.class));
        bullet.add(textureComponent);

        MovementComponent movement = engine.createComponent(MovementComponent.class);
        movement.velocity.set(BULLET_SPEED * direction, 0f);
        movement.maxSpeedX = BULLET_SPEED;
        movement.maxSpeedY = 0f;
        bullet.add(movement);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(BULLET_SIZE, BULLET_SIZE);
        bullet.add(collision);

        BulletComponent bulletComponent = engine.createComponent(BulletComponent.class);
        bulletComponent.damage = 0f;
        bulletComponent.lifetime = BULLET_LIFETIME;
        bullet.add(bulletComponent);

        bullet.add(engine.createComponent(EnemyBulletComponent.class));

        engine.addEntity(bullet);
    }
}
