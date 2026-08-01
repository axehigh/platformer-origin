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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for the hit-stun input lock in {@code PlayerInputSystem}: while
 * {@code PlayerComponent.hurtTimer} is active, held movement input must not overwrite the knockback
 * velocity and jump/melee/shoot requests are ignored; once the hurt window ends, control returns.
 */
public class PlayerHurtTest extends SystemTestBase {
    private PooledEngine engine;
    private PlayerInputSystem system;
    private MovementComponent movement;
    private PlayerComponent player;

    @Before
    public void setUp() {
        Gdx.input = mock(Input.class);
        system = new PlayerInputSystem(new AssetManager());
        engine = new PooledEngine();
        engine.addSystem(system);

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(0f, 0f);
        movement = engine.createComponent(MovementComponent.class);
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
    public void hurtLockPreservesKnockbackVelocity() {
        player.hurtTimer.start(1f);
        movement.velocity.x = 60f;
        when(Gdx.input.isKeyPressed(Input.Keys.D)).thenReturn(true);

        engine.update(DT);

        assertEquals(60f, movement.velocity.x, EPSILON);
    }

    @Test
    public void hurtLockBlocksJump() {
        player.hurtTimer.start(1f);
        system.requestTouchJump();

        engine.update(DT);

        assertEquals(0f, movement.velocity.y, EPSILON);
        assertEquals(0, player.jumpCount);
    }

    @Test
    public void hurtLockBlocksMeleeAndShoot() {
        player.hurtTimer.start(1f);
        player.items = 5;
        system.requestTouchMelee();
        system.requestTouchShoot();

        engine.update(DT);

        assertEquals(false, player.meleeAttack.isActive());
        assertEquals(5, player.items);
    }

    @Test
    public void controlReturnsAfterHurtWindowEnds() {
        player.hurtTimer.start(0.1f);
        player.hurtTimer.update(0.2f);
        when(Gdx.input.isKeyPressed(Input.Keys.D)).thenReturn(true);

        engine.update(DT);

        assertEquals(90f, movement.velocity.x, EPSILON);
    }
}
