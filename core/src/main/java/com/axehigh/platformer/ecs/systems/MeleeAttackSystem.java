package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.SecretRoomRevealer;
import com.axehigh.platformer.particles.GlobalParticles;
import com.axehigh.platformer.particles.ParticleHelper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.GameConstants.PLAYER_MELEE_HEIGHT_MULTIPLIER;
import static com.axehigh.platformer.GameConstants.PLAYER_MELEE_Y_OFFSET_FACTOR;
import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;
import static com.axehigh.platformer.assets.SpriteConstants.PLAYER_ATTACK_REACH;
import static com.axehigh.platformer.assets.SpriteConstants.PLAYER_MAX_ATTACK_REACH;
import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Resolves the close-combat strike attack: while the player's {@code meleeAttack} timer is active,
 * builds a frame-indexed hitbox rectangle offset from the player's collision bounds (in the
 * direction the player is facing) and checks it against enemies, chests, and secret walls; a strike
 * hitting a regular solid wall spawns a sword-clank spark. The
 * hitbox is only "live" while the sword is actually extended: each frame of the {@code ATTACKING}
 * animation maps to a reach in the sprite's reach table, {@link SpriteConstants#PLAYER_ATTACK_REACH}
 * (0 = windup/recovery frames don't hit at all, so a swing never registers before the blade reaches
 * out or after it pulls back). Every overlapping enemy is damaged at most once per swing (tracked in
 * {@link PlayerComponent#meleeHitEnemies}, reset at every swing start, so a swing can still hit
 * enemies that only come into reach as the blade extends) and every overlapping unopened chest
 * opens, using {@code EnemyDamageResolver} for enemy hit-stun/knockback. The one-shot secret-wall
 * break and wall-clank spark are gated behind {@code meleeHasHit} (at most one per swing). The
 * current frame's box is always exposed via
 * {@link #getActiveStrikeBounds()} so {@code DebugRenderSystem} can visualize it under SHIFT+D.
 */
public class MeleeAttackSystem extends IteratingSystem {

    private static final float CHEST_DISAPPEAR_DELAY = 0.3f;

    private final AssetManager assetManager;
    private float unitScale = 1f;

    private PooledEngine engine;
    private final Rectangle strikeBounds = new Rectangle();
    private Rectangle activeStrikeBounds;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> chests;
    private Array<Rectangle> secretRects;
    private Array<Rectangle> collisionRects;
    private TiledMapTileLayer collisionLayer;
    private SfxSystem sfxSystem;
    private SecretRoomRevealer secretRoomRevealer;

    public MeleeAttackSystem(AssetManager assetManager) {
        this(assetManager, null, null, null, null, 0);
    }

    public MeleeAttackSystem(AssetManager assetManager, int priority) {
        this(assetManager, null, null, null, null, priority);
    }

    /** Fully wired constructor: the shared secret/collision rect arrays, the collision tile layer
     * (blanked cell = removed sprite), and the SFX system for the wall-break sound. */
    public MeleeAttackSystem(AssetManager assetManager, Array<Rectangle> secretRects,
                             Array<Rectangle> collisionRects, TiledMapTileLayer collisionLayer,
                             SfxSystem sfxSystem, int priority) {
        this(assetManager, secretRects, collisionRects, collisionLayer, sfxSystem, null, priority);
    }

    /** Fully wired constructor plus the {@link SecretRoomRevealer} that opens a secret room when its wall breaks. */
    public MeleeAttackSystem(AssetManager assetManager, Array<Rectangle> secretRects,
                             Array<Rectangle> collisionRects, TiledMapTileLayer collisionLayer,
                             SfxSystem sfxSystem, SecretRoomRevealer secretRoomRevealer, int priority) {
        super(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
        this.secretRects = secretRects;
        this.collisionRects = collisionRects;
        this.collisionLayer = collisionLayer;
        this.sfxSystem = sfxSystem;
        this.secretRoomRevealer = secretRoomRevealer;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    /** Swaps the collision tile layer (blanked cells = removed sprites) on a level change. */
    public void setCollisionLayer(TiledMapTileLayer collisionLayer) {
        this.collisionLayer = collisionLayer;
    }

    /** The current strike hitbox (last frame's), or {@code null} while not attacking / on non-hitting frames. */
    public Rectangle getActiveStrikeBounds() {
        return activeStrikeBounds;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        if (engine instanceof PooledEngine) {
            this.engine = (PooledEngine) engine;
        }
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
        float strikeHeight = collision.worldBounds.height * PLAYER_MELEE_HEIGHT_MULTIPLIER;
        float strikeY = collision.worldBounds.y - (collision.worldBounds.height * PLAYER_MELEE_Y_OFFSET_FACTOR);
        strikeBounds.set(strikeX, strikeY, reach, strikeHeight);
        activeStrikeBounds = reach > 0f ? strikeBounds : null;

        if (reach > 0f) {
            int damage = player.swordDamage * (isStrengthBuffActive(entity) ? 2 : 1);
            hitAllEnemies(strikeBounds, player, damage);
            openAllChests(strikeBounds, player);
            if (!player.meleeHasHit) {
                if (breakSecretWall(strikeBounds)) {
                    player.meleeHasHit = true;
                } else if (spawnWallClankSpark(strikeBounds)) {
                    player.meleeHasHit = true;
                }
            }
        }

        player.meleeAttack.update(deltaTime);
    }

    /** True while the player's Strength potion buff is active (no-op without a buff). */
    private boolean isStrengthBuffActive(Entity entity) {
        BuffComponent buff = BUFF.get(entity);
        return buff != null && buff.isStrengthActive();
    }

    /** Damages every enemy overlapping {@code bounds} that hasn't already been hit this swing
     * (tracked in {@link PlayerComponent#meleeHitEnemies}, reset at every swing start) via
     * {@code EnemyDamageResolver}, so a single swing can hit every enemy in reach. */
    private void hitAllEnemies(Rectangle bounds, PlayerComponent player, int damage) {
        for (Entity hitEnemy : enemies) {
            CollisionComponent enemyCollision = COLLISION.get(hitEnemy);
            if (!bounds.overlaps(enemyCollision.worldBounds) || player.meleeHitEnemies.contains(hitEnemy)) {
                continue;
            }
            EnemyComponent enemy = ENEMY.get(hitEnemy);
            MovementComponent enemyMovement = MOVEMENT.get(hitEnemy);
            boolean isFlying = FLYING.get(hitEnemy) != null;
            EnemyDamageResolver.applyHit(hitEnemy, enemy, enemyMovement, damage,
                player.facingDirection, isFlying, unitScale, engine);
            player.meleeHitEnemies.add(hitEnemy);
            player.meleeHasHit = true;
        }
    }

    /** Opens every unopened chest overlapping {@code bounds} (sprite swap to the open region,
     * disappear timer, smoke puff). Chests are independent of enemy hits, so one swing can both
     * damage an enemy and open a chest. */
    private void openAllChests(Rectangle bounds, PlayerComponent player) {
        for (Entity hitChest : chests) {
            CollisionComponent chestCollision = COLLISION.get(hitChest);
            if (!bounds.overlaps(chestCollision.worldBounds)) {
                continue;
            }
            ChestComponent chest = CHEST.get(hitChest);
            if (chest.opened) {
                continue;
            }
            chest.opened = true;
            chest.disappearTimer.start(CHEST_DISAPPEAR_DELAY);
            TextureComponent texture = TEXTURE.get(hitChest);
            texture.region = assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class)
                .findRegion(SpriteConstants.CHEST_OPEN_REGION);
            if (engine != null && chestCollision != null) {
                ParticleHelper.spawnSmallSmoke(engine,
                    chestCollision.worldBounds.x + chestCollision.worldBounds.width / 2f,
                    chestCollision.worldBounds.y + chestCollision.worldBounds.height / 2f);
            }
            player.meleeHasHit = true;
        }
    }

    /**
     * Breaks at most one secret wall overlapping the strike box: removes the wall rect from both the
     * shared {@code secretRects} and {@code collisionRects} sets (passable next frame), blanks its
     * cell in the collision layer (the tile sprite disappears), spawns the smoke puff at the tile
     * center, and plays the wall-break SFX. When the wall tile carries a {@code secretRoom}
     * property, its secret room is revealed (veil blanked, deferred loot/enemies spawned, crumble
     * smoke). Returns {@code true} when a wall was broken.
     */
    private boolean breakSecretWall(Rectangle bounds) {
        if (secretRects == null || collisionLayer == null) {
            return false;
        }
        for (int i = 0; i < secretRects.size; i++) {
            Rectangle rect = secretRects.get(i);
            if (!bounds.overlaps(rect)) {
                continue;
            }
            String roomName = secretRoomOf(rect);
            secretRects.removeIndex(i);
            if (collisionRects != null) {
                collisionRects.removeValue(rect, true);
            }
            blankSecretTile(rect);
            if (engine != null) {
                ParticleHelper.spawnSmallSmoke(engine, rect.x + rect.width / 2f, rect.y + rect.height / 2f);
            }
            if (sfxSystem != null) {
                sfxSystem.playWallBreak();
            }
            if (secretRoomRevealer != null && roomName != null) {
                secretRoomRevealer.reveal(roomName);
            }
            return true;
        }
        return false;
    }

    /** The {@code secretRoom} property of the wall's collision-layer cell tile, or null when it guards no room. */
    private String secretRoomOf(Rectangle rect) {
        int tileX = (int) (rect.x / collisionLayer.getTileWidth());
        int tileY = (int) (rect.y / collisionLayer.getTileHeight());
        TiledMapTileLayer.Cell cell = collisionLayer.getCell(tileX, tileY);
        if (cell == null) {
            return null;
        }
        return cell.getTile().getProperties().get("secretRoom", String.class);
    }

    /**
     * Spawns a sword-clank spark at the first solid wall (non-secret {@code collisionRects} member)
     * overlapping the strike box, when no enemy/chest/secret wall was hit. Returns {@code true} when
     * a wall was clanked, so the swing is consumed (one clank per swing).
     */
    private boolean spawnWallClankSpark(Rectangle bounds) {
        if (collisionRects == null || engine == null) {
            return false;
        }
        for (Rectangle rect : collisionRects) {
            if (!bounds.overlaps(rect)) {
                continue;
            }
            ParticleHelper.spawnParticle(engine, GlobalParticles.SPARKS,
                rect.x + rect.width / 2f, rect.y + rect.height / 2f,
                0f, EnemyDamageResolver.HIT_SPARK_SCALE, EnemyDamageResolver.HIT_SPARK_MAX_LIFETIME);
            return true;
        }
        return false;
    }

    /** Blanks the collision-layer cell underneath a broken secret-wall rect (removes its sprite). */    private void blankSecretTile(Rectangle rect) {
        int tileX = (int) (rect.x / collisionLayer.getTileWidth());
        int tileY = (int) (rect.y / collisionLayer.getTileHeight());
        TiledMapTileLayer.Cell cell = collisionLayer.getCell(tileX, tileY);
        if (cell != null) {
            cell.setTile(null);
        }
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
}
