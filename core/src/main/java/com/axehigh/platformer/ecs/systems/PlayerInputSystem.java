package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Reads keyboard and on-screen touch input and translates it into velocity/facing-direction
 * changes on the player entity. Touch state is pushed in by {@code TouchControlsStage} so both
 * input sources drive the exact same handlers.
 */
public class PlayerInputSystem extends IteratingSystem {
    private static final float MOVE_SPEED = 90f;
    private static final float JUMP_VELOCITY = 220f;

    private static final float SHOOT_COOLDOWN = 0.35f;
    private static final float BULLET_SPEED = 220f;
    private static final float BULLET_DAMAGE = 1f;
    private static final float BULLET_LIFETIME = 1.5f;
    private static final float BULLET_SIZE = 4f;
    private static final float BULLET_Z = 8f;

    private final AssetManager assetManager;

    private PooledEngine engine;
    private boolean touchLeft = false;
    private boolean touchRight = false;
    private boolean touchJumpRequested = false;
    private boolean touchAttackRequested = false;

    public PlayerInputSystem(AssetManager assetManager) {
        this(assetManager, 0);
    }

    public PlayerInputSystem(AssetManager assetManager, int priority) {
        super(Family.all(PlayerComponent.class, MovementComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
    }

    /** Called by the D-pad's left button (touch down/up). */
    public void setTouchLeft(boolean pressed) {
        touchLeft = pressed;
    }

    /** Called by the D-pad's right button (touch down/up). */
    public void setTouchRight(boolean pressed) {
        touchRight = pressed;
    }

    /** Called by the A button (jump). */
    public void requestTouchJump() {
        touchJumpRequested = true;
    }

    /** Called by the B/Y buttons (attack/special). */
    public void requestTouchAttack() {
        touchAttackRequested = true;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = (PooledEngine) engine;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        touchJumpRequested = false;
        touchAttackRequested = false;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerComponent player = PLAYER.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);

        boolean left = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT) || touchLeft;
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT) || touchRight;

        if (left && !right) {
            movement.velocity.x = -MOVE_SPEED;
            player.facingDirection = -1;
        } else if (right && !left) {
            movement.velocity.x = MOVE_SPEED;
            player.facingDirection = 1;
        } else {
            movement.velocity.x = 0f;
        }

        boolean jumpPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.W)
            || Gdx.input.isKeyJustPressed(Input.Keys.UP)
            || touchJumpRequested;

        if (jumpPressed && player.jumpCount < player.maxJumps) {
            movement.velocity.y = JUMP_VELOCITY;
            movement.grounded = false;
            player.isWallClimbing = false;
            player.jumpCount++;
        }

        if (player.shootCooldownTimer > 0f) {
            player.shootCooldownTimer -= deltaTime;
        }

        boolean attackPressed = Gdx.input.isKeyJustPressed(Input.Keys.J)
            || Gdx.input.isKeyJustPressed(Input.Keys.K)
            || touchAttackRequested;

        if (attackPressed && player.shootCooldownTimer <= 0f) {
            spawnBullet(transform, collision, player);
            player.shootCooldownTimer = SHOOT_COOLDOWN;
        }
    }

    private void spawnBullet(TransformComponent playerTransform, CollisionComponent playerCollision, PlayerComponent player) {
        Entity bullet = engine.createEntity();

        float centerY = playerTransform.position.y + (playerCollision.bounds.height - BULLET_SIZE) / 2f;
        float spawnX = player.facingDirection > 0
            ? playerTransform.position.x + playerCollision.bounds.width
            : playerTransform.position.x - BULLET_SIZE;

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(spawnX, centerY);
        transform.z = BULLET_Z;
        bullet.add(transform);

        TextureComponent textureComponent = engine.createComponent(TextureComponent.class);
        textureComponent.region = new TextureRegion(assetManager.get("gfx/bullet.png", Texture.class));
        bullet.add(textureComponent);

        MovementComponent movement = engine.createComponent(MovementComponent.class);
        movement.velocity.set(BULLET_SPEED * player.facingDirection, 0f);
        movement.maxSpeedX = BULLET_SPEED;
        movement.maxSpeedY = 0f;
        bullet.add(movement);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(BULLET_SIZE, BULLET_SIZE);
        bullet.add(collision);

        BulletComponent bulletComponent = engine.createComponent(BulletComponent.class);
        bulletComponent.damage = BULLET_DAMAGE;
        bulletComponent.lifetime = BULLET_LIFETIME;
        bullet.add(bulletComponent);

        engine.addEntity(bullet);
    }
}
