package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.LightComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

import static com.axehigh.platformer.ecs.components.Mappers.*;

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
        PlayerComponent player = PLAYER.get(entity);

        buff.strength.update(deltaTime);
        buff.speed.update(deltaTime);
        buff.invulnerability.update(deltaTime);
        buff.invisibility.update(deltaTime);
        buff.jump.update(deltaTime);
        buff.fireBreath.update(deltaTime);

        if (buff.invulnerability.isActive()) {
            LightComponent light = LIGHT.get(entity);
            if (light == null) {
                light = new LightComponent();
                light.color.set(1f, 0.85f, 0.2f, 1f);
                light.radius = 80f;
                light.baseAlpha = 0.6f;
                light.flickerSpeed = 6f;
                light.flickerAmplitude = 0.2f;
                entity.add(light);
            }
        } else {
            LightComponent light = LIGHT.get(entity);
            if (light != null) {
                entity.remove(LightComponent.class);
            }
        }

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

        if (player != null) {
            player.maxJumps = buff.jump.isActive() ? 3 : 2;

            boolean speedBuffActive = buff.speed.isActive();
            boolean jumpBuffActive = buff.jump.isActive();

            boolean spawnSpeedTrail = speedBuffActive && (Math.abs(movement.velocity.x) > 5f || !movement.grounded);
            boolean spawnJumpTrail = jumpBuffActive && !movement.grounded && player.jumpCount == 3;

            if (spawnSpeedTrail || spawnJumpTrail) {
                player.trailTimer -= deltaTime;
                if (player.trailTimer <= 0f) {
                    player.trailTimer = com.axehigh.platformer.PlayerConfig.PLAYER_TRAIL_INTERVAL;
                    boolean speedActiveForTrail = speedBuffActive && spawnSpeedTrail;
                    boolean jumpActiveForTrail = jumpBuffActive && spawnJumpTrail;
                    TrailSystem.spawnPlayerTrail(getEngine(), TRANSFORM.get(entity), TEXTURE.get(entity), COLLISION.get(entity), speedActiveForTrail, jumpActiveForTrail);
                }
            } else {
                player.trailTimer = 0f;
            }
        }
    }
}
