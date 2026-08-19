package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TrapComponent;
import com.axehigh.platformer.ecs.components.TrapComponent.TrapType;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRAP;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Resolves trap-vs-player contact damage: for each acid drop or flame entity, checks AABB overlap
 * against the player. On overlap, applies one point of damage through
 * {@code PlayerDamageResolver#applyHitWithoutKnockback} — no knockback for environmental hazards.
 * The shared invulnerability grace period turns a sustained overlap into one hit per grace window.
 */
public class TrapContactSystem extends IteratingSystem {
    private ImmutableArray<Entity> players;
    private final RoomState roomState;

    public TrapContactSystem(RoomState roomState, int priority) {
        super(Family.all(TrapComponent.class, TransformComponent.class, CollisionComponent.class).get(), priority);
        this.roomState = roomState;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class).get());
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
            PlayerDamageResolver.applyHitWithoutKnockback(playerEntity, player);
        }
    }
}
