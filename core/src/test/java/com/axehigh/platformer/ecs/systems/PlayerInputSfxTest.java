package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.PlayerConfig.PLAYER_BULLET_REGION;
import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;
import static com.badlogic.gdx.Input.Keys.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Headless tests for the placeholder-SFX wiring in {@code PlayerInputSystem} (3-arg constructor with
 * a mock {@code SfxSystem}): a melee swing plays {@code playSwordSwing()}, a shot plays
 * {@code playShoot()} only when the player has ammo (and actually consumes one), and a successful
 * potion drink plays {@code playPotionDrink()} — with the negative paths (no ammo, empty inventory)
 * staying silent. The old 1-arg/2-arg constructors delegate with a null SFX system and are covered
 * by the other {@code PlayerInput*} tests.
 */
public class PlayerInputSfxTest extends SystemTestBase {

    private PooledEngine engine;
    private PlayerInputSystem system;
    private SfxSystem sfxSystem;
    private PlayerComponent player;

    @Before
    public void setUp() {
        Gdx.input = mock(Input.class);
        sfxSystem = mock(SfxSystem.class);

        TextureAtlas atlas = mock(TextureAtlas.class);
        when(atlas.findRegion(PLAYER_BULLET_REGION)).thenReturn(mock(TextureAtlas.AtlasRegion.class));
        AssetManager assetManager = mock(AssetManager.class);
        when(assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class)).thenReturn(atlas);

        system = new PlayerInputSystem(assetManager, sfxSystem, 0);
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
        engine.addEntity(entity);
    }

    @After
    public void tearDown() {
        Gdx.input = null;
    }

    @Test
    public void meleeSwingPlaysSwordSwingSfx() {
        when(Gdx.input.isKeyJustPressed(J)).thenReturn(true);

        engine.update(DT);

        verify(sfxSystem).playSwordSwing();
    }

    @Test
    public void shootWithAmmoPlaysShootSfxAndConsumesAmmo() {
        player.ammo = 1;
        when(Gdx.input.isKeyJustPressed(K)).thenReturn(true);

        engine.update(DT);

        assertEquals("one ammo consumed by the shot", 0, player.ammo);
        assertEquals("one bullet entity spawned", 2, engine.getEntities().size());
        verify(sfxSystem).playShoot();
    }

    @Test
    public void shootWithoutAmmoPlaysNoShootSfx() {
        when(Gdx.input.isKeyJustPressed(K)).thenReturn(true);

        engine.update(DT);

        assertEquals(0, player.ammo);
        assertEquals("no bullet spawned", 1, engine.getEntities().size());
        verify(sfxSystem, never()).playShoot();
    }

    @Test
    public void successfulDrinkPlaysPotionDrinkSfx() {
        player.setPotionCount(PotionType.HEALING, 1);
        player.health = player.maxHealth - 1;
        when(Gdx.input.isKeyJustPressed(C)).thenReturn(true);

        engine.update(DT);

        assertEquals("potion consumed", 0, player.countPotion(PotionType.HEALING));
        verify(sfxSystem).playPotionDrink();
    }

    @Test
    public void failedDrinkPlaysNoPotionDrinkSfx() {
        // Empty inventory -> consumeSelectedPotion() returns false -> no drink, no SFX.
        when(Gdx.input.isKeyJustPressed(C)).thenReturn(true);

        engine.update(DT);

        verify(sfxSystem, never()).playPotionDrink();
    }
}
