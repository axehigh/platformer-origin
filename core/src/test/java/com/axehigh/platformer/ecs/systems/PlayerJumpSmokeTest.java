package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Headless tests for the ground-jump smoke puff: a jump started while {@code MovementComponent.grounded}
 * spawns a {@code ParticleComponent} entity (via {@code ParticleHelper.spawnSmallSmoke}'s headless branch),
 * while a mid-air jump or no jump at all spawns nothing.
 */
public class PlayerJumpSmokeTest extends SystemTestBase {
    private PooledEngine engine;
    private PlayerInputSystem system;

    @Before
    public void setUp() {
        Gdx.input = mock(Input.class);
        system = new PlayerInputSystem(new AssetManager());
        engine = new PooledEngine();
        engine.addSystem(system);
    }

    @After
    public void tearDown() {
        Gdx.input = null;
    }

    private void player(boolean grounded) {
        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(0f, 0f);
        MovementComponent movement = engine.createComponent(MovementComponent.class);
        movement.grounded = grounded;
        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.set(-8f, -32f, 16f, 32f);
        PlayerComponent player = new PlayerComponent();
        Entity entity = engine.createEntity();
        entity.add(transform);
        entity.add(movement);
        entity.add(collision);
        entity.add(player);
        engine.addEntity(entity);
    }

    private int particleEntityCount() {
        int count = 0;
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(ParticleComponent.class) != null) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void groundJumpSpawnsSmokePuff() {
        player(true);
        system.requestTouchJump();
        engine.update(DT);
        assertEquals(1, particleEntityCount());
    }

    @Test
    public void midAirJumpSpawnsNoSmoke() {
        player(false);
        system.requestTouchJump();
        engine.update(DT);
        assertEquals(0, particleEntityCount());
    }

    @Test
    public void standingStillSpawnsNoSmoke() {
        player(true);
        engine.update(DT);
        assertEquals(0, particleEntityCount());
    }
}
