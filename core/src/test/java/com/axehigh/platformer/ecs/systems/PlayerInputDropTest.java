package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for the drop-through-platform request in {@code PlayerInputSystem}: pressing
 * {@code S} or {@code DOWN} starts the {@code dropWindow} countdown and sets {@code dropRequested},
 * and — critically — the window is ticked down every frame so it expires ~0.25s later. This guards
 * a real bug where the window was started but never updated, leaving it active forever and making
 * the player unable to land on a one-way platform again after the first drop-through.
 */
public class PlayerInputDropTest extends SystemTestBase {
    private PooledEngine engine;
    private PlayerInputSystem system;
    private PlayerComponent player;

    @Before
    public void setUp() {
        Gdx.input = mock(Input.class);
        system = new PlayerInputSystem(new AssetManager());
        engine = new PooledEngine();
        engine.addSystem(system);

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(0f, 0f);
        MovementComponent movement = engine.createComponent(MovementComponent.class);
        CollisionComponent collision = engine.createComponent(CollisionComponent.class);
        collision.bounds.set(-8f, -32f, 16f, 32f);
        player = new PlayerComponent();
        Entity entity = engine.createEntity();
        entity.add(transform);
        entity.add(movement);
        entity.add(collision);
        entity.add(player);
        engine.addEntity(entity);
    }

    @After
    public void tearDown() {
        Gdx.input = null;
    }

    @Test
    public void pressingSStartsDropWindowAndRequestsDrop() {
        when(Gdx.input.isKeyJustPressed(Input.Keys.S)).thenReturn(true, false);

        engine.update(DT);

        assertTrue(player.dropRequested);
        assertTrue(player.dropWindow.isActive());
    }

    @Test
    public void pressingDownStartsDropWindow() {
        when(Gdx.input.isKeyJustPressed(Input.Keys.DOWN)).thenReturn(true, false);

        engine.update(DT);

        assertTrue(player.dropRequested);
        assertTrue(player.dropWindow.isActive());
    }

    @Test
    public void dropWindowExpiresAfterBeingTickedDown() {
        when(Gdx.input.isKeyJustPressed(Input.Keys.S)).thenReturn(true, false);

        engine.update(DT);
        assertTrue(player.dropWindow.isActive());

        for (int i = 0; i < 30; i++) {
            engine.update(DT);
        }

        assertFalse(player.dropWindow.isActive());
        assertTrue(player.dropWindow.isDone());
    }
}
