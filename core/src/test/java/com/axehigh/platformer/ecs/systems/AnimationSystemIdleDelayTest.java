package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.PlayerConfig;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Headless tests for the player's idle-entry grace in {@code AnimationSystem}: after horizontal
 * movement stops, the player keeps the last run/walk pose for {@code PLAYER_IDLE_DELAY} seconds
 * instead of snapping to IDLE the instant velocity zeroes, then eases into IDLE once the grace
 * elapses. Re-moving during the grace aborts it.
 */
public class AnimationSystemIdleDelayTest extends SystemTestBase {
    private Engine engine;
    private AnimationComponent animation;
    private PlayerComponent player;
    private MovementComponent movement;

    @Before
    public void setUp() {
        FeatureFlags.setSoftStopEnabled(true);
        engine = newEngine();
        engine.addSystem(new AnimationSystem());

        animation = new AnimationComponent();
        animation.animations.put(AnimationComponent.State.IDLE, new Animation<>(0.2f, new TextureRegion()));
        animation.animations.put(AnimationComponent.State.WALKING, new Animation<>(0.1f, new TextureRegion()));
        animation.animations.put(AnimationComponent.State.RUNNING, new Animation<>(0.1f, new TextureRegion()));

        player = new PlayerComponent();
        movement = new MovementComponent();

        Entity entity = new Entity();
        entity.add(animation);
        entity.add(new TextureComponent());
        entity.add(player);
        entity.add(movement);
        entity.add(new TransformComponent());
        engine.addEntity(entity);
    }

    private int holdFrames() {
        // Frames that fit strictly inside the grace window (timer arms on the stop frame and ticks
        // every frame from then on), so the pose must still be held after this many frames.
        return Math.max(1, (int) (PlayerConfig.PLAYER_IDLE_DELAY / DT) - 1);
    }

    @After
    public void tearDown() {
        // Restore the default so any test class running afterwards isn't affected by this one.
        FeatureFlags.setSoftStopEnabled(true);
    }

    @Test
    public void runningPlayerHoldsRunPoseWhileStopping() {
        movement.grounded = true;
        movement.velocity.x = 90f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.RUNNING, animation.currentState);

        // Direction released: velocity snaps to 0, but the run pose must linger through the grace.
        movement.velocity.x = 0f;
        for (int i = 0; i < holdFrames(); i++) {
            engine.update(DT);
        }
        assertEquals(AnimationComponent.State.RUNNING, animation.currentState);

        // Once the grace window elapses, the idle animation takes over.
        for (int i = 0; i < (int) (PlayerConfig.PLAYER_IDLE_DELAY / DT) + 2; i++) {
            engine.update(DT);
        }
        assertEquals(AnimationComponent.State.IDLE, animation.currentState);
    }

    @Test
    public void walkingPlayerHoldsWalkPoseWhileStopping() {
        movement.grounded = true;
        movement.velocity.x = 40f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.WALKING, animation.currentState);

        movement.velocity.x = 0f;
        for (int i = 0; i < holdFrames(); i++) {
            engine.update(DT);
        }
        assertEquals(AnimationComponent.State.WALKING, animation.currentState);
    }

    @Test
    public void stationaryPlayerNeverHolds() {
        movement.grounded = true;
        movement.velocity.x = 0f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.IDLE, animation.currentState);

        // Even after the grace window would have elapsed, a never-moving player stays idle.
        int remainingFrames = (int) (PlayerConfig.PLAYER_IDLE_DELAY / DT) + 1;
        for (int i = 0; i < remainingFrames; i++) {
            engine.update(DT);
        }
        assertEquals(AnimationComponent.State.IDLE, animation.currentState);
    }

    @Test
    public void movingAgainDuringGraceAbortsIt() {
        movement.grounded = true;
        movement.velocity.x = 90f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.RUNNING, animation.currentState);

        movement.velocity.x = 0f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.RUNNING, animation.currentState);

        // Player re-runs the other way mid-grace: hold aborts, movement state resumes.
        movement.velocity.x = -90f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.RUNNING, animation.currentState);

        // Stopping again still holds (fresh grace window instead of the stale one).
        movement.velocity.x = 0f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.RUNNING, animation.currentState);
    }

    @Test
    public void softStopDisabledSkipsHoldEntirely() {
        FeatureFlags.setSoftStopEnabled(false);

        // Stationary case: straight to IDLE, no hold.
        movement.grounded = true;
        movement.velocity.x = 0f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.IDLE, animation.currentState);

        // Running case: run, then stop -> snaps straight to IDLE the same frame.
        movement.velocity.x = 90f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.RUNNING, animation.currentState);

        movement.velocity.x = 0f;
        engine.update(DT);
        assertEquals(AnimationComponent.State.IDLE, animation.currentState);
    }
}
