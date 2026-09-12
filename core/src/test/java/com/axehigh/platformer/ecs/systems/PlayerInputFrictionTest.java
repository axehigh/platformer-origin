package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for the player's ground-friction deceleration in {@code PlayerInputSystem}: while
 * no direction is held the player's horizontal velocity decays exponentially (PlayerConfig
 * {@code PLAYER_STOP_DECEL}) instead of snapping straight to 0 — so the sprite eases through
 * slow-run into WALK before the idle-entry hold — then snaps to 0 below {@code PLAYER_STOP_EPSILON}.
 * Holding a direction still drives full speed (friction never interferes with acceleration).
 */
public class PlayerInputFrictionTest extends SystemTestBase {
    private PooledEngine engine;
    private PlayerInputSystem system;
    private MovementComponent movement;

    @Before
    public void setUp() {
        Gdx.input = mock(Input.class);
        FeatureFlags.setSoftStopEnabled(true);
        system = new PlayerInputSystem(new AssetManager());
        engine = new PooledEngine();
        engine.addSystem(system);

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        movement = engine.createComponent(MovementComponent.class);
        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.set(-8f, -32f, 16f, 32f);
        Entity entity = engine.createEntity();
        entity.add(transform);
        entity.add(movement);
        entity.add(collision);
        entity.add(engine.createComponent(PlayerComponent.class));
        engine.addEntity(entity);
    }

    @After
    public void tearDown() {
        Gdx.input = null;
        // Restore the default so any test class running afterwards isn't affected by this one.
        FeatureFlags.setSoftStopEnabled(true);
    }

    @Test
    public void releasingDirectionDeceleratesInsteadOfSnappingToZero() {
        movement.velocity.x = 90f;

        engine.update(DT);
        float afterOne = movement.velocity.x;
        assertTrue("friction must engage when no direction is held", afterOne < 90f);
        assertTrue("but must not stop dead: the sprite needs to ease through the stop", afterOne > 0f);

        engine.update(DT);
        assertTrue("velocity must keep decaying", movement.velocity.x < afterOne);
    }

    @Test
    public void frictionEventuallyBringsVelocityToZero() {
        movement.velocity.x = 90f;

        for (int i = 0; i < 40; i++) {
            engine.update(DT);
        }

        assertEquals(0f, movement.velocity.x, EPSILON);
    }

    @Test
    public void holdingDirectionIgnoresFriction() {
        when(Gdx.input.isKeyPressed(Input.Keys.RIGHT)).thenReturn(true);
        movement.velocity.x = 0f;

        engine.update(DT);

        assertEquals(90f, movement.velocity.x, EPSILON);
    }

    @Test
    public void softStopDisabledSnapsVelocityToZero() {
        FeatureFlags.setSoftStopEnabled(false);
        movement.velocity.x = 90f;

        engine.update(DT);

        // No decel: the input system hard-zeroes velocity the frame direction is released.
        assertEquals(0f, movement.velocity.x, EPSILON);
    }
}