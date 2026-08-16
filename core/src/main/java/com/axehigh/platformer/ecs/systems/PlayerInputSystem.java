package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.BulletComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.particles.ParticleHelper;
import com.axehigh.platformer.util.PotionEffects;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.ANIMATION;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static com.badlogic.gdx.Input.Keys.*;

/**
 * Reads keyboard and on-screen touch input and translates it into velocity/facing-direction
 * changes on the player entity. Touch state is pushed in by {@code TouchControlsStage} so both
 * input sources drive the exact same handlers.
 */
public class PlayerInputSystem extends IteratingSystem {
    private static final float MOVE_SPEED = 90f;
    private static final float JUMP_VELOCITY = 220f;
    private static final float DOUBLE_JUMP_FACTOR = 0.7f;

    private static final float SHOOT_COOLDOWN = 0.35f;
    private static final float BULLET_SPEED = 220f;
    private static final float BULLET_DAMAGE = 10f;
    private static final float BULLET_LIFETIME = 1.5f;
    private static final float BULLET_SIZE = 4f;
    private static final float BULLET_Z = 8f;

    private static final float MELEE_COOLDOWN = 0.2f;
    private static final float MELEE_ATTACK_DURATION = 0.2f;
    private static final float DROP_WINDOW_DURATION = 0.25f;

    private final AssetManager assetManager;

    private PooledEngine engine;
    private float unitScale = 1f;
    private boolean touchLeft = false;
    private boolean touchRight = false;
    private boolean touchJumpRequested = false;
    private boolean touchMeleeRequested = false;
    private boolean touchShootRequested = false;
    private boolean touchInteractRequested = false;
    private boolean touchDropRequested = false;

    public PlayerInputSystem(AssetManager assetManager) {
        this(assetManager, 0);
    }

    public PlayerInputSystem(AssetManager assetManager, int priority) {
        super(Family.all(PlayerComponent.class, MovementComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
    }

    /**
     * Called by the D-pad's left button (touch down/up).
     */
    public void setTouchLeft(boolean pressed) {
        touchLeft = pressed;
    }

    /**
     * Called by the D-pad's right button (touch down/up).
     */
    public void setTouchRight(boolean pressed) {
        touchRight = pressed;
    }

    /**
     * Called by the A button (jump).
     */
    public void requestTouchJump() {
        touchJumpRequested = true;
    }

    /**
     * Called by the B button (close-combat strike).
     */
    public void requestTouchMelee() {
        touchMeleeRequested = true;
    }

    /**
     * Called by the Y button (ranged dagger shoot).
     */
    public void requestTouchShoot() {
        touchShootRequested = true;
    }

    /**
     * Called by the contextual up-arrow button (interact with a nearby exit gate).
     */
    public void requestTouchInteract() {
        touchInteractRequested = true;
    }

    /**
     * Called by the contextual down-arrow button (drop through a drop-through platform).
     */
    public void requestTouchDrop() {
        touchDropRequested = true;
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
    public void update(float deltaTime) {
        super.update(deltaTime);
        touchJumpRequested = false;
        touchMeleeRequested = false;
        touchShootRequested = false;
        touchInteractRequested = false;
        touchDropRequested = false;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerComponent player = PLAYER.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);

        boolean left = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT) || touchLeft;
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT) || touchRight;

        // Hit-stun lock: while hurt, the player loses control — no horizontal input (so the
        // knockback pop set by PlayerDamageResolver isn't overwritten), no jump/melee/shoot.
        // A dead player (death animation playing before the Game Over dialog) is locked too.
        boolean hurt = player.hurtTimer.isActive();
        boolean locked = hurt || player.isDead;

        if (!locked && left && !right) {
            movement.velocity.x = -MOVE_SPEED * unitScale;
            player.facingDirection = -1;
        } else if (!locked && right && !left) {
            movement.velocity.x = MOVE_SPEED * unitScale;
            player.facingDirection = 1;
        } else if (!locked) {
            movement.velocity.x = 0f;
        }

        boolean jumpPressed = Gdx.input.isKeyJustPressed(W) || Gdx.input.isKeyJustPressed(UP) || touchJumpRequested;

        if (!locked && jumpPressed && player.jumpCount < player.maxJumps) {
            if (movement.grounded) {
                spawnJumpSmoke(transform, collision);
            }
            float jumpVelocity = JUMP_VELOCITY * unitScale;
            if (player.jumpCount > 0) {
                jumpVelocity *= DOUBLE_JUMP_FACTOR;
            }
            movement.velocity.y = jumpVelocity;
            movement.grounded = false;
            player.isWallClimbing = false;
            player.jumpCount++;
        }

        player.shootCooldown.update(deltaTime);
        player.meleeCooldown.update(deltaTime);
        player.dropWindow.update(deltaTime);
        player.potionCooldown.update(deltaTime);

        // Potion cycle/use: unlike movement/attacks, drinking stays available during the brief
        // hit-stun window (healing mid-fight is a clutch play) and is only blocked while dead.
        boolean potionCyclePressed = Gdx.input.isKeyJustPressed(Z);
        if (!player.isDead && potionCyclePressed) {
            player.cyclePotion();
        }

        boolean potionUsePressed = Gdx.input.isKeyJustPressed(C);
        if (!player.isDead && potionUsePressed && player.potionCooldown.isDone() && player.consumeSelectedPotion()) {
            PotionEffects.apply(entity, player, player.selectedPotion);
            player.potionCooldown.start(GameConstants.POTION_USE_COOLDOWN);
        }

        boolean meleePressed = Gdx.input.isKeyJustPressed(Input.Keys.J) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || touchMeleeRequested;
        if (!locked && meleePressed && player.meleeCooldown.isDone()) {
            float attackDuration = findAttackDuration(entity);
            player.meleeAttack.start(attackDuration);
            player.meleeHasHit = false;
            player.meleeHitEnemies.clear();
            // Cooldown must be at least as long as the animation to allow it to finish
            player.meleeCooldown.start(Math.max(MELEE_COOLDOWN, attackDuration));
        }

        boolean shootPressed = Gdx.input.isKeyJustPressed(Input.Keys.K) || Gdx.input.isKeyJustPressed(Input.Keys.Y) || touchShootRequested;
        if (!locked && shootPressed && player.shootCooldown.isDone() && player.items > 0) {
            spawnBullet(transform, collision, player);
            player.items--;
            player.shootCooldown.start(SHOOT_COOLDOWN);
        }

        player.interactPressed = Gdx.input.isKeyJustPressed(Input.Keys.E) || touchInteractRequested;

        boolean dropPressed = Gdx.input.isKeyJustPressed(Input.Keys.S)
            || Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
            || touchDropRequested;
        if (!locked && dropPressed) {
            player.dropWindow.start(DROP_WINDOW_DURATION);
            player.onDropTile = false;
        }
        player.dropRequested = dropPressed && !locked;
    }

    private void spawnJumpSmoke(TransformComponent transform, CollisionComponent collision) {
        float feetX = transform.position.x + collision.bounds.x + MathUtils.random(collision.bounds.width);
        float feetY = transform.position.y + collision.bounds.y;
        ParticleHelper.spawnSmallSmoke(engine, feetX, feetY);
    }

    private static float findAttackDuration(Entity entity) {
        float attackDuration = MELEE_ATTACK_DURATION;
        AnimationComponent anim = ANIMATION.get(entity);
        if (anim != null) {
            Animation<TextureRegion> attackAnim = anim.animations.get(AnimationComponent.State.ATTACKING);
            if (attackAnim != null) {
                attackDuration = attackAnim.getAnimationDuration();
            }
        }
        return attackDuration;
    }

    private void spawnBullet(TransformComponent playerTransform, CollisionComponent playerCollision, PlayerComponent player) {
        Entity bullet = engine.createEntity();

        float bulletSize = BULLET_SIZE * unitScale;
        float centerY = playerTransform.position.y + (playerCollision.bounds.height - bulletSize) / 2f;
        float spawnX = player.facingDirection > 0
            ? playerTransform.position.x + playerCollision.bounds.width
            : playerTransform.position.x - bulletSize;

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
        movement.velocity.set(speed * player.facingDirection, 0f);
        movement.maxSpeedX = speed;
        movement.maxSpeedY = 0f;
        bullet.add(movement);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.setSize(bulletSize, bulletSize);
        bullet.add(collision);

        BulletComponent bulletComponent = engine.createComponent(BulletComponent.class);
        bulletComponent.damage = BULLET_DAMAGE;
        bulletComponent.lifetime = BULLET_LIFETIME;
        bullet.add(bulletComponent);

        engine.addEntity(bullet);
    }
}
