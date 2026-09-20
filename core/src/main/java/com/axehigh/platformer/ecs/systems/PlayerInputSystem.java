package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.particles.ParticleHelper;
import com.axehigh.platformer.util.FeatureFlags;
import com.axehigh.platformer.util.PotionEffects;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.PlayerConfig.*;
import static com.axehigh.platformer.assets.GameAssetRegistry.*;
import static com.axehigh.platformer.ecs.components.Mappers.*;
import static com.badlogic.gdx.Gdx.input;
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

    private static final float DROP_WINDOW_DURATION = 0.25f;

    private final AssetManager assetManager;
    private final SfxSystem sfxSystem;

    /** Lazily wrapped {@code gfx/slash_arc.png} texture region, or {@code null} until loaded. */
    private TextureRegion slashArcRegion;

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
        this(assetManager, null, 0);
    }

    public PlayerInputSystem(AssetManager assetManager, int priority) {
        this(assetManager, null, priority);
    }

    public PlayerInputSystem(AssetManager assetManager, SfxSystem sfxSystem, int priority) {
        super(Family.all(PlayerComponent.class, MovementComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
        this.sfxSystem = sfxSystem;
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

        boolean left = input.isKeyPressed(Input.Keys.A) || input.isKeyPressed(Input.Keys.LEFT) || touchLeft;
        boolean right = input.isKeyPressed(Input.Keys.D) || input.isKeyPressed(Input.Keys.RIGHT) || touchRight;

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
            // Ground friction (FeatureFlags.isSoftStopEnabled(), default ON): don't snap velocity to
            // 0 (that made the sprite jump straight from RUNNING to IDLE in one frame). Decelerate
            // exponentially instead, so the run clip eases through slow-run into WALK below the
            // AnimationSystem 50 u/s threshold; the last bit snaps to 0 (PLAYER_STOP_EPSILON) to
            // avoid sub-pixel creep. Duration tuned by PlayerConfig.PLAYER_STOP_DECEL. When the flag
            // is OFF, restore the pre-easing behavior: velocity snaps to 0 the frame release.
            if (FeatureFlags.isSoftStopEnabled()) {
                movement.velocity.x *= (float) Math.exp(-PLAYER_STOP_DECEL * deltaTime);
                if (Math.abs(movement.velocity.x) < PLAYER_STOP_EPSILON) {
                    movement.velocity.x = 0f;
                }
            } else {
                movement.velocity.x = 0f;
            }
        }

        boolean jumpPressed = input.isKeyJustPressed(W) || input.isKeyJustPressed(UP) || touchJumpRequested;

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
        boolean potionCyclePressed = input.isKeyJustPressed(Z);
        if (!player.isDead && potionCyclePressed) {
            player.cyclePotion();
        }

        boolean potionUsePressed = input.isKeyJustPressed(C);
        if (!player.isDead && potionUsePressed && player.potionCooldown.isDone() && player.consumeSelectedPotion()) {
            PotionEffects.apply(entity, player, player.selectedPotion);
            player.potionCooldown.start(GameConstants.POTION_USE_COOLDOWN);
            if (sfxSystem != null) {
                sfxSystem.playPotionDrink();
            }
        }

        boolean meleePressed = input.isKeyJustPressed(Input.Keys.J) || input.isKeyJustPressed(Input.Keys.SPACE) || touchMeleeRequested;
        if (!locked && meleePressed && player.meleeCooldown.isDone()) {
            float attackDuration = findAttackDuration(entity);
            player.meleeAttack.start(attackDuration);
            player.meleeHasHit = false;
            player.meleeHitEnemies.clear();
            // Cooldown must be at least as long as the animation to allow it to finish
            player.meleeCooldown.start(Math.max(MELEE_COOLDOWN, attackDuration));
            spawnSlashArc(entity, transform, collision, player);
            if (sfxSystem != null) {
                sfxSystem.playSwordSwing();
            }
        }

        boolean shootPressed = input.isKeyJustPressed(Input.Keys.K) || input.isKeyJustPressed(Input.Keys.Y) || touchShootRequested;
        if (!locked && shootPressed && player.shootCooldown.isDone() && player.ammo > 0) {
            spawnBullet(entity, transform, collision, player);
            player.ammo--;
            player.shootCooldown.start(SHOOT_COOLDOWN);
            if (sfxSystem != null) {
                sfxSystem.playShoot();
            }
        }

        player.interactPressed = input.isKeyJustPressed(Input.Keys.E) || touchInteractRequested;

        boolean dropPressed = input.isKeyJustPressed(Input.Keys.S)
            || input.isKeyJustPressed(Input.Keys.DOWN)
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

    private void spawnBullet(Entity playerEntity, TransformComponent playerTransform, CollisionComponent playerCollision, PlayerComponent player) {
        Entity bullet = engine.createEntity();

        boolean fireBreath = BUFF.has(playerEntity) && BUFF.get(playerEntity).isFireBreathActive();
        String regionName = fireBreath ? "fire1" : PLAYER_BULLET_REGION;
        TextureRegion region = findRegion(assetManager, regionName);
        float scaleMult = fireBreath ? 0.3f : PLAYER_BULLET_SCALE;
        float bulletScale = unitScale * scaleMult;
        // The sprite's on-screen scale drives the render; the collision box is authored per-sprite
        // (BULLET_COLLISION_WIDTH/HEIGHT, scaled by PLAYER_BULLET_SCALE) since the visible blade can
        // be smaller than the full atlas frame. Offsets position the box within the frame.
        float bulletWidth = fireBreath ? (64f * bulletScale) : (BULLET_COLLISION_WIDTH * bulletScale);
        float bulletHeight = fireBreath ? (64f * bulletScale) : (BULLET_COLLISION_HEIGHT * bulletScale);
        float bulletOffsetX = fireBreath ? (region.getRegionWidth() * bulletScale - bulletWidth) / 2f : (BULLET_OFFSET_X * bulletScale);
        float bulletOffsetY = fireBreath ? (region.getRegionHeight() * bulletScale - bulletHeight) / 2f : (BULLET_OFFSET_Y * bulletScale);
        float frameWidth = region.getRegionWidth() * bulletScale;
        // Center the sprite's vertical extent on the player's collision center. The blade sits at
        // a fixed offset within the (tall) atlas frame, so centering the frame keeps the visible
        // blade level with the player; the authored BULLET_OFFSET_Y then places the hitbox over it.
        float frameHeight = region.getRegionHeight() * bulletScale;
        float playerCenterY = playerTransform.position.y + playerCollision.bounds.y
            + playerCollision.bounds.height / 2f;
        float centerY = playerCenterY - frameHeight / 2f;

        // Collision box offset within the frame: firing left flips the sprite horizontally, which
        // mirrors the blade to the opposite side of the frame (RenderSystem draws the flipped
        // region about the position). Mirror the hitbox x-offset so it stays over the flipped blade.
        float collisionOffsetX = player.facingDirection > 0
            ? bulletOffsetX
            : frameWidth - bulletOffsetX - bulletWidth;

        // Launch from the player's front collision edge, not the body center: the blade exits the
        // body in the facing direction, so it never starts overlapping a wall the player is
        // pressed against. When facing left, the sprite is rendered flipped (scale.x < 0) around
        // its origin (bottom-left of the local unscaled region). SpriteBatch.draw with a negative width
        // draws the texture region leftward from drawX (i.e., from drawX - width to drawX).
        // Therefore, to have the right edge of the sprite (which is the local left edge before flipping)
        // align with the player's left collision edge, drawX needs to be at the player's left edge,
        // which means spawnX is simply playerCenterX - playerCollision.bounds.width / 2f (plus bulletOffsetX
        // adjusted for the flip).
        // Specifically, for facingDirection < 0, the hitbox is mirrored via collisionOffsetX = frameWidth - bulletOffsetX - bulletWidth.
        // The sprite visual frame needs to be positioned such that its right edge is at player's left edge, so drawX = playerLeftEdge.
        // Since SpriteBatch.draw draws from drawX leftwards by frameWidth when width is negative, setting drawX = playerLeftEdge
        // places the sprite from (playerLeftEdge - frameWidth) to playerLeftEdge. But wait, if drawX = playerLeftEdge,
        // the sprite occupies [playerLeftEdge - frameWidth, playerLeftEdge], whereas the player is at [playerLeftEdge, playerRightEdge].
        // The current formula was: playerCenterX - playerCollision.bounds.width / 2f - frameWidth + bulletOffsetX,
        // which is playerLeftEdge - frameWidth + bulletOffsetX. This placed the sprite 1 tile/frameWidth to the left!
        // Adjusting it by removing - frameWidth (or correcting it to align with the player's left edge).
        float playerCenterX = playerTransform.position.x + playerCollision.bounds.x
            + playerCollision.bounds.width / 2f;
        float playerLeftEdge = playerCenterX - playerCollision.bounds.width / 2f;
        float playerRightEdge = playerCenterX + playerCollision.bounds.width / 2f;

        float spawnX = player.facingDirection > 0
            ? playerRightEdge - bulletOffsetX
            : playerLeftEdge;
        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(spawnX, centerY);
        // Negative scale.x when facing left flips the blade horizontally so it points backwards.
        transform.scale.set(bulletScale * player.facingDirection, bulletScale);
        //transform.rotation = -90f;
        transform.z = BULLET_Z;
        bullet.add(transform);

        TextureComponent textureComponent = engine.createComponent(TextureComponent.class);

        textureComponent.region = region;
        bullet.add(textureComponent);

        MovementComponent movement = engine.createComponent(MovementComponent.class);
        float speed = BULLET_SPEED * unitScale;
        movement.velocity.set(speed * player.facingDirection, 0f);
        movement.maxSpeedX = speed;
        movement.maxSpeedY = 0f;
        bullet.add(movement);

        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.set(collisionOffsetX, bulletOffsetY, bulletWidth, bulletHeight);
        collision.updateWorldBounds(transform.position);
        bullet.add(collision);

        BulletComponent bulletComponent = engine.createComponent(BulletComponent.class);
        bulletComponent.damage = fireBreath ? BULLET_DAMAGE * 2f : BULLET_DAMAGE;
        bulletComponent.lifetime = BULLET_LIFETIME;
        bullet.add(bulletComponent);
        engine.addEntity(bullet);
    }

    /**
     * Spawns the one-shot cosmetic slash-arc VFX, once per swing (called at the end of the melee
     * start block). Cosmetic only — Transform + Texture + SlashArcComponent, no CollisionComponent,
     * so RenderSystem's fallback anchor path draws it (negative scale.x flips it for left-facing).
     */
    private void spawnSlashArc(Entity playerEntity, TransformComponent playerTransform, CollisionComponent playerCollision, PlayerComponent player) {
        if (!FeatureFlags.isSlashArcEnabled()) {
            return;
        }
        if (slashArcRegion == null) {
            if (!assetManager.isLoaded(SLASH_ARC_TEXTURE)) {
                if (Gdx.app != null) {
                    Gdx.app.log("SlashArc", "Texture not loaded; skipping arc spawn");
                }
                return;
            }
            slashArcRegion = new TextureRegion(assetManager.get(SLASH_ARC_TEXTURE, Texture.class));
        }

        Entity arc = engine.createEntity();

        float arcScale = SLASH_ARC_SCALE * unitScale;
        float arcWidth = slashArcRegion.getRegionWidth() * arcScale;
        float arcHeight = slashArcRegion.getRegionHeight() * arcScale;

        // Anchor on the player's collision center (transform.position is the rect's lower-left),
        // shifted slightly forward in the facing direction and up toward the torso.
        float anchorX = playerTransform.position.x + playerCollision.bounds.x + playerCollision.bounds.width / 2f;
        float anchorY = playerTransform.position.y + playerCollision.bounds.y + playerCollision.bounds.height / 2f;
        float centerX = anchorX + player.facingDirection * SLASH_ARC_OFFSET_X * unitScale;
        float centerY = anchorY + SLASH_ARC_OFFSET_Y * unitScale;

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        // Centered on the anchor in both directions: RenderSystem's fallback path (drawX -= min(0,
        // width)) keeps the visual center at position + |width|/2 regardless of the scale.x sign.
        transform.position.set(centerX - arcWidth / 2f, centerY - arcHeight / 2f);
        transform.scale.set(arcScale * player.facingDirection, arcScale);
        transform.z = SLASH_ARC_Z;
        arc.add(transform);

        TextureComponent textureComponent = engine.createComponent(TextureComponent.class);
        textureComponent.region = slashArcRegion;
        arc.add(textureComponent);

        SlashArcComponent arcComponent = engine.createComponent(SlashArcComponent.class);
        arcComponent.lifeTime = SLASH_ARC_LIFETIME;
        arc.add(arcComponent);

        engine.addEntity(arc);
    }

    private static TextureAtlas.AtlasRegion findRegion(AssetManager assetManager, String regionName) {
        TextureAtlas atlas = assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class);
        TextureAtlas.AtlasRegion region = atlas.findRegion(regionName);
        if (region == null) {
            TextureAtlas uiAtlas = assetManager.get(ORIGIN_UI_GFX, TextureAtlas.class);
            region = uiAtlas.findRegion(regionName);
        }
        return region;
    }
}
