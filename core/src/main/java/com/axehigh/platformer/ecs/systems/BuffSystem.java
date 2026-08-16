package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

import static com.axehigh.platformer.ecs.components.Mappers.BUFF;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;

/**
 * Ticks the player's timed potion buffs and applies the speed buff's side effect: while the speed
 * {@link BuffComponent#speed} timer is active, {@code MovementComponent.maxSpeedX} is scaled by
 * {@link GameConstants#SPEED_MULTIPLIER} (re-asserted every frame so a level swap that resets
 * maxSpeedX doesn't clobber an active buff); when it expires the pre-buff max speed is restored.
 * The strength and invulnerability buffs have no side effects here — {@code MeleeAttackSystem} and
 * {@code PlayerDamageResolver} read them directly.
 */
public class BuffSystem extends IteratingSystem {

    public BuffSystem(int priority) {
        super(Family.all(PlayerComponent.class, MovementComponent.class, BuffComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BuffComponent buff = BUFF.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);

        buff.strength.update(deltaTime);
        buff.speed.update(deltaTime);
        buff.invulnerability.update(deltaTime);

        if (buff.speed.isActive()) {
            if (!buff.speedApplied) {
                buff.speedBaseMaxSpeedX = movement.maxSpeedX;
                buff.speedApplied = true;
            }
            movement.maxSpeedX = buff.speedBaseMaxSpeedX * GameConstants.SPEED_MULTIPLIER;
        } else if (buff.speedApplied) {
            movement.maxSpeedX = buff.speedBaseMaxSpeedX;
            buff.speedApplied = false;
        }
    }
}
