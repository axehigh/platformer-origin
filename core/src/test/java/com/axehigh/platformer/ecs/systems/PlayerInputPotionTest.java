package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for the keyboard potion quick-drink in {@code PlayerInputSystem}: {@code Z}
 * cycles the selected potion type and {@code C} drinks it (applies the effect, decrements the
 * count, and starts the drink cooldown). Guarded against empty inventory, an active cooldown, and
 * a dead player.
 */
public class PlayerInputPotionTest extends SystemTestBase {
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
        player = engine.createComponent(PlayerComponent.class);
        Entity entity = engine.createEntity();
        entity.add(transform);
        entity.add(movement);
        entity.add(collision);
        entity.add(player);
        entity.add(engine.createComponent(BuffComponent.class));
        engine.addEntity(entity);
    }

    @After
    public void tearDown() {
        Gdx.input = null;
    }

    @Test
    public void pressingZCyclesToNextPotionType() {
        player.selectedPotion = PotionType.HEALING;

        when(Gdx.input.isKeyJustPressed(Input.Keys.Z)).thenReturn(true);

        engine.update(DT);

        assertEquals(PotionType.STRENGTH, player.selectedPotion);
    }

    @Test
    public void pressingCDrinksSelectedPotion() {
        player.setPotionCount(PotionType.HEALING, 1);
        player.health = player.maxHealth - 1;

        when(Gdx.input.isKeyJustPressed(Input.Keys.C)).thenReturn(true);

        engine.update(DT);

        assertEquals(0, player.countPotion(PotionType.HEALING));
        assertEquals(player.maxHealth, player.health);
        assertTrue(player.potionCooldown.isActive());
    }

    @Test
    public void pressingCWithEmptyInventoryDoesNothing() {
        player.health = player.maxHealth - 1;

        when(Gdx.input.isKeyJustPressed(Input.Keys.C)).thenReturn(true);

        engine.update(DT);

        assertEquals(player.maxHealth - 1, player.health);
        assertFalse(player.potionCooldown.isActive());
    }

    @Test
    public void pressingCDuringCooldownDoesNotDrink() {
        player.setPotionCount(PotionType.HEALING, 2);
        player.health = player.maxHealth - 1;

        when(Gdx.input.isKeyJustPressed(Input.Keys.C)).thenReturn(true, true);

        engine.update(DT);
        engine.update(DT);

        assertEquals(1, player.countPotion(PotionType.HEALING));
    }

    @Test
    public void deadPlayerCannotDrink() {
        player.setPotionCount(PotionType.HEALING, 1);
        player.health = 0;
        player.isDead = true;

        when(Gdx.input.isKeyJustPressed(Input.Keys.C)).thenReturn(true);

        engine.update(DT);

        assertEquals(1, player.countPotion(PotionType.HEALING));
    }
}
