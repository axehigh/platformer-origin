package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
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
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.CHEST;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TEXTURE;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Resolves the close-combat strike attack: while the player's {@code meleeAttackTimer} is active
 * and hasn't hit yet, checks a short-lived hitbox rectangle offset from the player's collision
 * bounds (in the direction the player is facing) against enemies and chests, applying melee
 * damage/opening a chest at most once per swing.
 */
public class MeleeAttackSystem extends IteratingSystem {
    private static final float MELEE_DAMAGE = 1f;
    private static final float STRIKE_WIDTH = 10f;
    private static final float CHEST_DISAPPEAR_DELAY = 0.3f;

    private final AssetManager assetManager;

    private final Rectangle strikeBounds = new Rectangle();
    private final Rectangle targetBounds = new Rectangle();
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> chests;

    public MeleeAttackSystem(AssetManager assetManager) {
        this(assetManager, 0);
    }

    public MeleeAttackSystem(AssetManager assetManager, int priority) {
        super(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.assetManager = assetManager;
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
        if (player.meleeAttackTimer <= 0f) {
            return;
        }

        if (!player.meleeHasHit) {
            TransformComponent transform = TRANSFORM.get(entity);
            CollisionComponent collision = COLLISION.get(entity);

            float strikeX = player.facingDirection > 0
                ? transform.position.x + collision.bounds.width
                : transform.position.x - STRIKE_WIDTH;
            strikeBounds.set(strikeX, transform.position.y, STRIKE_WIDTH, collision.bounds.height);

            Entity hitEnemy = findHit(strikeBounds, enemies);
            if (hitEnemy != null) {
                EnemyComponent enemy = ENEMY.get(hitEnemy);
                enemy.health -= MELEE_DAMAGE;
                if (enemy.health <= 0f) {
                    getEngine().removeEntity(hitEnemy);
                }
                player.meleeHasHit = true;
            } else {
                Entity hitChest = findHit(strikeBounds, chests);
                if (hitChest != null) {
                    ChestComponent chest = CHEST.get(hitChest);
                    if (!chest.opened) {
                        chest.opened = true;
                        chest.disappearTimer = CHEST_DISAPPEAR_DELAY;
                        TextureComponent texture = TEXTURE.get(hitChest);
                        texture.region = new TextureRegion(assetManager.get("gfx/chest_open.png", Texture.class));
                    }
                    player.meleeHasHit = true;
                }
            }
        }

        player.meleeAttackTimer -= deltaTime;
    }

    private Entity findHit(Rectangle bounds, ImmutableArray<Entity> targets) {
        for (Entity target : targets) {
            CollisionComponent targetCollision = COLLISION.get(target);
            TransformComponent targetTransform = TRANSFORM.get(target);
            targetBounds.set(targetTransform.position.x, targetTransform.position.y,
                targetCollision.bounds.width, targetCollision.bounds.height);
            if (bounds.overlaps(targetBounds)) {
                return target;
            }
        }
        return null;
    }
}
