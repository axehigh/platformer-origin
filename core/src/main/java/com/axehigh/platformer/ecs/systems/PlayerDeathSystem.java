package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.ecs.components.Mappers.ANIMATION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;

/**
 * Detects the player's health reaching 0 and fires a one-time callback into GameScreen — but only
 * after the DEATH animation has had time to play out, so the player visibly dies (frozen, no
 * input) for a beat before the Game Over dialog appears. On the first frame {@code health <= 0} it
 * marks the player dead, zeroes its velocity (full freeze: the lethal hit's knockback doesn't slide
 * the corpse), and starts a countdown of {@code max(DEATH animation duration, MIN) + BEAT} seconds;
 * while the countdown runs the engine keeps updating so {@code AnimationSystem} renders the death
 * clip, and when it elapses the callback fires exactly once (the {@code triggered} guard). If the
 * player is revived (Continue reloads the level and restores health), {@code triggered} is reset so
 * a subsequent death still fires the callback.
 */
public class PlayerDeathSystem extends IteratingSystem {
    /** Shortest possible death-wait, even if no DEATH animation is registered. */
    private static final float DEATH_MIN_DURATION = 0.8f;
    /** Extra pause on the final death pose after the animation clip has finished. */
    private static final float DEATH_EXTRA_BEAT = 0.5f;

    private final Runnable onDeath;
    private boolean triggered = false;
    private final Timer deathDelay = new Timer();

    public PlayerDeathSystem(Runnable onDeath, int priority) {
        super(Family.all(PlayerComponent.class).get(), priority);
        this.onDeath = onDeath;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerComponent player = PLAYER.get(entity);

        if (player.health > 0) {
            // Revived (e.g. Continue from the Game Over dialog): fully re-arm so a later death
            // plays the death animation and fires the callback again.
            triggered = false;
            player.isDead = false;
            deathDelay.reset();
            return;
        }
        if (triggered) {
            return;
        }

        if (!player.isDead) {
            player.isDead = true;
            MovementComponent movement = MOVEMENT.get(entity);
            if (movement != null) {
                movement.velocity.set(0f, 0f);
            }
            deathDelay.start(computeDeathDelay(entity));
        }

        deathDelay.update(deltaTime);
        if (deathDelay.isDone()) {
            triggered = true;
            onDeath.run();
        }
    }

    private static float computeDeathDelay(Entity entity) {
        float duration = DEATH_MIN_DURATION;
        AnimationComponent anim = ANIMATION.get(entity);
        if (anim != null) {
            Animation<TextureRegion> deathAnim = anim.animations.get(AnimationComponent.State.DEATH);
            if (deathAnim != null) {
                duration = Math.max(DEATH_MIN_DURATION, deathAnim.getAnimationDuration());
            }
        }
        return duration + DEATH_EXTRA_BEAT;
    }
}
