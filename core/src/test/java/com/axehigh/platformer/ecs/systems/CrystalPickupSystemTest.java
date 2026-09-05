package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.map.EntityFactory;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Headless unit tests for the crystal objective in {@code PickupSystem}: that a crystal pickup
 * increments {@code PlayerComponent.crystalsCollected}, is removed, raises exactly one floating
 * {@code "+<amount> Crystal"} message (never any completion banner), leaves coins untouched (and
 * vice versa), and that the system tolerates a null {@code SfxSystem} + null {@code EntityFactory}
 * (used by tests that only assert counters).
 */
public class CrystalPickupSystemTest extends SystemTestBase {

    private Engine engine;
    private PickupSystem system;
    private SfxSystem sfxSystem;
    private EntityFactory entityFactory;

    @Before
    public void setUp() {
        sfxSystem = mock(SfxSystem.class);
        entityFactory = mock(EntityFactory.class);
        system = new PickupSystem(sfxSystem, entityFactory, 0);
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

    private Entity crystal(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, collision, new CrystalPickupComponent());
        engine.addEntity(entity);
        return entity;
    }

    private Entity coin(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, collision, new CoinPickupComponent());
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void crystalPickupIncrementsCrystalCountAndIsRemoved() {
        Entity player = player(0f, 130f);
        crystal(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);

        assertEquals(1, playerComponent.crystalsCollected);
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void crystalPickupShowsSinglePickupMessageAndNoBannerAtTarget() {
        Entity player = player(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);
        playerComponent.crystalTarget = 1;
        crystal(0f, 130f);

        engine.update(DT);

        assertEquals(1, playerComponent.crystalsCollected);
        verify(entityFactory).createFloatingMessage(eq(engine), eq("+1 Crystal"), any(), eq(player));
        verify(entityFactory, never()).createFloatingMessage(eq(engine), eq("All Crystals Found!"), any(), eq(player));
    }

    @Test
    public void crystalDoesNotTouchCoinsAndCoinDoesNotTouchCrystals() {
        Entity player = player(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);
        crystal(0f, 130f);
        coin(0f, 130f);

        engine.update(DT);

        assertEquals(1, playerComponent.crystalsCollected);
        assertEquals(1, playerComponent.coins);
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void crystalPickupWorksWithNullDependencies() {
        PickupSystem nullSystem = new PickupSystem(null, null, 0);
        Engine nullEngine = newEngine();
        nullEngine.addSystem(nullSystem);

        TransformComponent transform = transform(0f, 130f);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, 0f, 130f);
        Entity pickups = entity(transform, collision, new CrystalPickupComponent());

        TransformComponent playerTransform = transform(0f, 130f);
        CollisionComponent playerCollision = collision(-15f, -30f, 30f, 60f);
        place(playerTransform, playerCollision, 0f, 130f);
        Entity playerEntity = entity(playerTransform, playerCollision, new PlayerComponent());
        nullEngine.addEntity(playerEntity);
        nullEngine.addEntity(pickups);

        nullEngine.update(DT);

        assertEquals(1, PLAYER.get(playerEntity).crystalsCollected);
        assertEquals(1, nullEngine.getEntities().size());
    }
}
