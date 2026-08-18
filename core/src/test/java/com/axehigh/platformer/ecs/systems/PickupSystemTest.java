package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CoinPickupComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.DaggerPickupComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Headless unit tests for {@code PickupSystem}: coin/dagger overlap resolution, the ammo cap at
 * {@code maxItems}, that a non-overlapping pickup is left untouched, and that only a coin pickup
 * triggers the coin SFX.
 */
public class PickupSystemTest extends SystemTestBase {

    private Engine engine;
    private PickupSystem system;
    private SfxSystem sfxSystem;

    @Before
    public void setUp() {
        sfxSystem = mock(SfxSystem.class);
        system = new PickupSystem(sfxSystem, mock(EntityFactory.class), 0);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    private Entity coin(float x, float y, int amount) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        CoinPickupComponent coin = new CoinPickupComponent();
        coin.amount = amount;
        Entity entity = entity(transform, collision, coin);
        engine.addEntity(entity);
        return entity;
    }

    private Entity dagger(float x, float y, int amount) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        DaggerPickupComponent dagger = new DaggerPickupComponent();
        dagger.amount = amount;
        Entity entity = entity(transform, collision, dagger);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void coinPickupPlaysCoinSfx() {
        player(0f, 130f);
        coin(0f, 130f, 1);

        engine.update(DT);

        verify(sfxSystem).playCoin();
    }

    @Test
    public void daggerPickupDoesNotPlaySfx() {
        player(0f, 130f);
        dagger(0f, 130f, 1);

        engine.update(DT);

        verifyNoInteractions(sfxSystem);
    }

    @Test
    public void coinPickupIncrementsCoinsAndIsRemoved() {
        Entity player = player(0f, 130f);
        coin(0f, 130f, 3);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);

        assertEquals(3, playerComponent.coins);
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void daggerPickupIncrementsItemsCappedAtMax() {
        Entity player = player(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);
        playerComponent.items = 9;
        dagger(0f, 130f, 5);

        engine.update(DT);

        assertEquals(playerComponent.maxItems, playerComponent.items);
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void daggerPickupAddsFullAmountWhenBelowCap() {
        Entity player = player(0f, 130f);
        dagger(0f, 130f, 5);

        engine.update(DT);

        assertEquals(5, PLAYER.get(player).items);
    }

    @Test
    public void nonOverlappingPickupIsLeftUntouched() {
        Entity player = player(0f, 130f);
        coin(500f, 500f, 1);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);

        assertEquals(0, playerComponent.coins);
        assertEquals(2, engine.getEntities().size());
    }
}
