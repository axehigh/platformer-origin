package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.assets.SpriteConstants.PLAYER_ATTACK_REACH;
import static com.axehigh.platformer.assets.SpriteConstants.PLAYER_MAX_ATTACK_REACH;
import static com.axehigh.platformer.ecs.components.Mappers.ANIMATION;
import static com.axehigh.platformer.ecs.components.Mappers.CHEST;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.FLYING;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TEXTURE;

/**
 * Resolves the close-combat strike attack: while the player's {@code meleeAttack} timer is active,
 * builds a frame-indexed hitbox rectangle offset from the player's collision bounds (in the
 * direction the player is facing) and checks it against enemies and chests. The hitbox is only
 * "live" while the sword is actually extended: each frame of the {@code ATTACKING} animation maps
 * to a reach in the sprite's reach table, {@link SpriteConstants#PLAYER_ATTACK_REACH} (0 =
 * windup/recovery frames don't hit at all, so a swing never registers before the blade reaches out
 * or after it pulls back). Damage is applied (or a chest opened) at most once per swing via {@code
 * meleeHasHit}, using {@code EnemyDamageResolver} for enemy hit-stun/knockback. The current frame's
 * box is always exposed via {@link #getActiveStrikeBounds()} so {@code DebugRenderSystem} can
 * visualize it under SHIFT+D.
 */
public class MeleeAttackSystem extends IteratingSystem {

    private static final float CHEST_DISAPPEAR_DELAY = 0.3f;

    private final AssetManager assetManager;
    private float unitScale = 1f;

    private final Rectangle strikeBounds = new Rectangle();
    private Rectangle activeStrikeBounds;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> chests;

    public MeleeAttackSystem(AssetManager assetManager) {
        this(assetManager, 0);
    }

    public MeleeAttackSystem(AssetManager assetManager, int priority) {
        super(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    /** The current strike hitbox (last frame's), or {@code null} while not attacking / on non-hitting frames. */
    public Rectangle getActiveStrikeBounds() {
        return activeStrikeBounds;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        enemies = engine.getEntitiesFor(Family.all(EnemyComponent.class, TransformComponent.class, CollisionComponent.class).get());
        chests = engine.getEntitiesFor(Family.all(ChestComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerComponent player = PLAYER.get(entity);
        if (player.meleeAttack.isDone()) {
            activeStrikeBounds = null;
            return;
        }

        float reach = currentReach(entity, player) * unitScale;
        CollisionComponent collision = COLLISION.get(entity);

        float strikeX = player.facingDirection > 0
            ? collision.worldBounds.x + collision.worldBounds.width
            : collision.worldBounds.x - reach;
        strikeBounds.set(strikeX, collision.worldBounds.y, reach, collision.worldBounds.height);
        activeStrikeBounds = reach > 0f ? strikeBounds : null;

        if (!player.meleeHasHit && reach > 0f) {
            Entity hitEnemy = findHit(strikeBounds, enemies);
            if (hitEnemy != null) {
                EnemyComponent enemy = ENEMY.get(hitEnemy);
                MovementComponent enemyMovement = MOVEMENT.get(hitEnemy);
                boolean isFlying = FLYING.get(hitEnemy) != null;
                EnemyDamageResolver.applyHit(hitEnemy, enemy, enemyMovement, player.swordDamage, player.facingDirection, isFlying, unitScale);
                player.meleeHasHit = true;
            } else {
                Entity hitChest = findHit(strikeBounds, chests);
                if (hitChest != null) {
                    ChestComponent chest = CHEST.get(hitChest);
                    if (!chest.opened) {
                        chest.opened = true;
                        chest.disappearTimer.start(CHEST_DISAPPEAR_DELAY);
                        TextureComponent texture = TEXTURE.get(hitChest);
                        texture.region = new TextureRegion(assetManager.get("gfx/old/chest_open.png", Texture.class));
                    }
                    player.meleeHasHit = true;
                }
            }
        }

        player.meleeAttack.update(deltaTime);
    }

    /**
     * The frame's reach for the current point in the {@code ATTACKING} animation, derived from the
     * attack timer's elapsed time (in sync with the swing start) so the windup/recovery windows land
     * on the right frames even though {@code MeleeAttackSystem} runs before {@code AnimationSystem}.
     * Falls back to the largest reach if no {@code ATTACKING} animation is registered.
     */
    private static float currentReach(Entity entity, PlayerComponent player) {
        AnimationComponent anim = ANIMATION.get(entity);
        if (anim != null) {
            Animation<TextureRegion> attack = anim.animations.get(AnimationComponent.State.ATTACKING);
            if (attack != null && attack.getKeyFrames().length > 0) {
                float frameDuration = attack.getAnimationDuration() / attack.getKeyFrames().length;
                float elapsed = Math.max(0f, attack.getAnimationDuration() - player.meleeAttack.getRemaining());
                int frame = Math.min((int) (elapsed / frameDuration), PLAYER_ATTACK_REACH.length - 1);
                return PLAYER_ATTACK_REACH[frame];
            }
        }
        return PLAYER_MAX_ATTACK_REACH;
    }

    private Entity findHit(Rectangle bounds, ImmutableArray<Entity> targets) {
        for (Entity target : targets) {
            CollisionComponent targetCollision = COLLISION.get(target);
            if (bounds.overlaps(targetCollision.worldBounds)) {
                return target;
            }
        }
        return null;
    }
}
