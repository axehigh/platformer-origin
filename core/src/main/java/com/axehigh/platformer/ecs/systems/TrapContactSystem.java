package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.ecs.components.TrapComponent.TrapType;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Resolves trap-vs-player contact damage: for each acid drop, flame, or spinning blade entity,
 * checks AABB overlap against the player. On overlap, applies one point of damage through
 * {@code PlayerDamageResolver} — spinning blades apply directional knockback (like enemies), while
 * acid/flame traps apply no knockback. The shared invulnerability grace period turns a sustained
 * overlap into one hit per grace window.
 */
public class TrapContactSystem extends IteratingSystem {
    private ImmutableArray<Entity> players;
    private final RoomState roomState;
    private float unitScale = 1f;

    public TrapContactSystem(RoomState roomState, int priority) {
        super(Family.all(TrapComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.roomState = roomState;
    }

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() > 0) {
            PlayerComponent player = PLAYER.get(players.first());
            player.hitInvulnerability.update(deltaTime);
            player.hurtTimer.update(deltaTime);
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity trapEntity, float deltaTime) {
        TrapComponent trap = TRAP.get(trapEntity);
        if (trap.type == null || trap.type == TrapType.ACID_DROP_SPAWNER) {
            return;
        }

        boolean roomActive = trap.roomIndex < 0 || trap.roomIndex == roomState.activeRoomIndex;
        if (!roomActive) {
            return;
        }

        if (trap.type == TrapType.FLAME && !trap.isFlaming) {
            return;
        }

        if (players.size() == 0) {
            return;
        }

        Entity playerEntity = players.first();
        PlayerComponent player = PLAYER.get(playerEntity);
        if (player.isDead) {
            return;
        }

        CollisionComponent trapCollision = COLLISION.get(trapEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);

        if (playerCollision.worldBounds.overlaps(trapCollision.worldBounds)) {
            if (trap.type == TrapType.BLADE) {
                TransformComponent trapTransform = TRANSFORM.get(trapEntity);
                TransformComponent playerTransform = TRANSFORM.get(playerEntity);
                float playerCenterX = playerTransform.position.x + playerCollision.bounds.x + playerCollision.bounds.width / 2f;
                float trapCenterX = trapTransform.position.x + trapCollision.bounds.x + trapCollision.bounds.width / 2f;
                int knockbackDirection = playerCenterX >= trapCenterX ? 1 : -1;
                MovementComponent playerMovement = MOVEMENT.get(playerEntity);
                PlayerDamageResolver.applyHit(playerEntity, player, playerMovement, knockbackDirection, unitScale);
            } else {
                PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);
            }
        }
    }
}
