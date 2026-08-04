package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Drives the player's squash-and-stretch visual pulse: a landing squashes the sprite flatter/wider,
 * then the deviation exponentially decays back to the resting scale. (Jump stretch was removed for
 * feel; the stretch path remains for future use.) The trigger point lives in {@code
 * MovementSystem.onLanding(...)}, shared with {@code MovingPlatformSystem} so every landing surface
 * squashes identically. **Disabled by default** — the trigger is gated on {@code
 * FeatureFlags.isSquashEnabled()} (default {@code false}) until the look is finalized; flip the
 * persisted pref or call {@code FeatureFlags.setSquashEnabled(true)} to re-enable. Runs just before
 * rendering (after {@code AnimationSystem}) so the pulse scales the current animation frame.
 */
public class SquashSystem extends IteratingSystem {
    /** Peak deviation (0..1) at the moment a pulse starts: e.g. 0.25 = 25% taller/flatter. */
    private static final float SQUASH_AMOUNT = 0.25f;
    /** Exponential decay rate (1/s): higher = quicker snap back to resting scale. */
    private static final float SQUASH_DECAY = 10f;
    /** Below this deviation the pulse is visually over; snap exactly to the resting scale. */
    private static final float SQUASH_EPSILON = 0.01f;

    public SquashSystem() {
        this(0);
    }

    public SquashSystem(int priority) {
        super(Family.all(PlayerComponent.class, TransformComponent.class).get(), priority);
    }

    /**
     * Starts a squash-and-stretch pulse on the player. {@code stretch = true} (jump) makes the
     * sprite taller/thinner; {@code false} (landing) makes it flatter/wider. Captures the current
     * scale as the resting base so the pulse restores exactly to it.
     */
    public static void trigger(PlayerComponent player, TransformComponent transform, boolean stretch) {
        player.squashBaseX = Math.abs(transform.scale.x);
        player.squashBaseY = Math.abs(transform.scale.y);
        player.squashAmount = SQUASH_AMOUNT;
        player.squashIsStretch = stretch;
        player.squashActive = true;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerComponent player = PLAYER.get(entity);
        if (!player.squashActive) {
            return;
        }

        player.squashAmount *= MathUtils.clamp((float) Math.exp(-SQUASH_DECAY * deltaTime), 0f, 1f);

        TransformComponent transform = TRANSFORM.get(entity);
        if (player.squashAmount < SQUASH_EPSILON) {
            player.squashActive = false;
            player.squashAmount = 0f;
            transform.scale.x = Math.signum(transform.scale.x) * player.squashBaseX;
            transform.scale.y = player.squashBaseY;
            return;
        }

        float sign = player.squashIsStretch ? 1f : -1f;
        transform.scale.y = player.squashBaseY * (1f + sign * player.squashAmount);
        transform.scale.x = Math.signum(transform.scale.x) * player.squashBaseX * (1f - sign * player.squashAmount * 0.5f);
    }
}
