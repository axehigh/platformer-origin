package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.util.Timer;
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
 *
 * Also owns the player's buff-feedback halo: while any buff is active the player carries one
 * {@link LightComponent} (rendered by the shared {@code LightRenderSystem} pass) tinted with the
 * component-wise average of the active {@link PotionType} colors; it is removed once no buff
 * remains. When the soonest-expiring timer drops under {@link GameConstants#BUFF_BLINK_THRESHOLD},
 * the halo's {@code baseAlpha} blinks between full and dim on a {@link GameConstants#BUFF_BLINK_INTERVAL}
 * toggle (driven by a {@link Timer}, matching the project timer convention).
 */
public class BuffSystem extends IteratingSystem {

    /** Radius of the blended buff halo on the player (world units). */
    private static final float BUFF_LIGHT_RADIUS = 56f;
    /** Halo alpha while a buff is steady (the {@code LightComponent} default). */
    private static final float BUFF_LIGHT_ALPHA = 0.85f;
    /** Halo alpha during the dim phase of the expiry blink. */
    private static final float BUFF_LIGHT_BLINK_ALPHA = 0.15f;

    /** Drives the bright/dim toggle of the halo while a buff is about to expire. */
    private final Timer blinkTimer = new Timer();
    /** Current phase of the expiry blink: {@code true} = bright, {@code false} = dim. */
    private boolean blinkBright = false;

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

        updateHalo(entity, buff, deltaTime);
    }

    /**
     * Get-or-adds the blended halo while any buff is active, removes it when none remain, and
     * blinks its alpha once the soonest-expiring buff is inside the threshold.
     */
    private void updateHalo(Entity entity, BuffComponent buff, float deltaTime) {
        int activeCount = 0;
        float minRemaining = Float.MAX_VALUE;
        float red = 0f;
        float green = 0f;
        float blue = 0f;
        PotionType[] contributing = {
            PotionType.STRENGTH, PotionType.SPEED, PotionType.INVULNERABILITY
        };
        for (PotionType type : contributing) {
            Timer timer = buffTimer(buff, type);
            if (!timer.isActive()) {
                continue;
            }
            activeCount++;
            float[] color = type.messageColor();
            red += color[0];
            green += color[1];
            blue += color[2];
            minRemaining = Math.min(minRemaining, timer.getRemaining());
        }

        if (activeCount == 0) {
            blinkTimer.reset();
            blinkBright = false;
            if (LIGHT.has(entity)) {
                entity.remove(LightComponent.class);
            }
            return;
        }

        LightComponent light = LIGHT.get(entity);
        if (light == null) {
            light = new LightComponent();
            entity.add(light);
        }
        light.radius = BUFF_LIGHT_RADIUS;
        light.color.set(red / activeCount, green / activeCount, blue / activeCount, 1f);
        // Center the halo on the player's body: middle of the live collision box relative to
        // the transform (same convention RenderSystem uses for its debug box center), so the
        // glow follows facing offsets and trimmed-atlas offsets instead of assuming a centered
        // sprite.
        if (COLLISION.has(entity)) {
            CollisionComponent collision = COLLISION.get(entity);
            light.offset.set(
                collision.bounds.x + collision.bounds.width / 2f,
                collision.bounds.y + collision.bounds.height / 2f);
        }

        if (minRemaining < GameConstants.BUFF_BLINK_THRESHOLD) {
            blinkTimer.update(deltaTime);
            if (blinkTimer.isDone()) {
                blinkBright = !blinkBright;
                blinkTimer.start(GameConstants.BUFF_BLINK_INTERVAL);
            }
            light.baseAlpha = blinkBright ? BUFF_LIGHT_ALPHA : BUFF_LIGHT_BLINK_ALPHA;
        } else {
            blinkTimer.reset();
            blinkBright = false;
            light.baseAlpha = BUFF_LIGHT_ALPHA;
        }
    }

    /** The {@link BuffComponent} timer backing a buff-capable {@link PotionType}. */
    private static Timer buffTimer(BuffComponent buff, PotionType type) {
        switch (type) {
            case STRENGTH:
                return buff.strength;
            case SPEED:
                return buff.speed;
            case INVULNERABILITY:
                return buff.invulnerability;
            default:
                throw new IllegalArgumentException("No buff timer for " + type);
        }
    }
}
