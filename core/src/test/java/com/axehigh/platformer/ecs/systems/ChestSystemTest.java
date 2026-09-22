package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.CHEST;
import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ChestSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private EntityFactory entityFactory;
    private ChestSystem system;
    private Engine engine;

    @Before
    public void setUp() {
        entityFactory = mock(EntityFactory.class);
        system = new ChestSystem(entityFactory);
        system.setCollisionRects(collisionRects);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity chest(float x, float y, PotionType potionType) {
        return chest(x, y, potionType, true);
    }

    private Entity chest(float x, float y, PotionType potionType, boolean opened) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 128f, 128f);
        place(transform, collision, x, y);

        ChestComponent chest = new ChestComponent();
        chest.potionType = potionType;
        chest.opened = opened;
        chest.disappearTimer.start(0.3f);

        Entity entity = entity(transform, chest, collision);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void coinChestSpawnsCoinsAfterTimer() {
        Entity e = chest(100f, 50f, null);

        // Timer still active — nothing should happen
        engine.update(DT);
        verify(entityFactory, never()).popCoins(any(), anyFloat(), anyFloat(), anyInt(), anyFloat(), any());
        verify(entityFactory, never()).popPotion(any(), anyFloat(), anyFloat(), anyString(), anyFloat(), any());

        // Advance past the 0.3s timer
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        verify(entityFactory).popCoins(eq(engine), anyFloat(), anyFloat(), anyInt(), anyFloat(), eq(collisionRects));
        verify(entityFactory, never()).popPotion(any(), anyFloat(), anyFloat(), anyString(), anyFloat(), any());
    }

    @Test
    public void potionChestSpawnsPotionInsteadOfCoins() {
        Entity e = chest(100f, 50f, PotionType.HEALING);

        // Timer still active — nothing should happen
        engine.update(DT);
        verify(entityFactory, never()).popPotion(any(), anyFloat(), anyFloat(), anyString(), anyFloat(), any());
        verify(entityFactory, never()).popCoins(any(), anyFloat(), anyFloat(), anyInt(), anyFloat(), any());

        // Advance past the 0.3s timer
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        verify(entityFactory).popPotion(eq(engine), anyFloat(), anyFloat(), eq("HEALING"), anyFloat(), eq(collisionRects));
        verify(entityFactory, never()).popCoins(any(), anyFloat(), anyFloat(), anyInt(), anyFloat(), any());
    }

    @Test
    public void potionChestSpawnsCorrectType() {
        chest(100f, 50f, PotionType.STRENGTH);

        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        verify(entityFactory).popPotion(eq(engine), anyFloat(), anyFloat(), eq("STRENGTH"), anyFloat(), eq(collisionRects));
    }

    @Test
    public void chestDoesNotDropTwice() {
        Entity e = chest(100f, 50f, null);

        // First drop
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }
        verify(entityFactory).popCoins(eq(engine), anyFloat(), anyFloat(), anyInt(), anyFloat(), eq(collisionRects));

        // Second update — should not drop again
        engine.update(DT);
        // Still only one invocation (popCoins called once total)
        verify(entityFactory).popCoins(eq(engine), anyFloat(), anyFloat(), anyInt(), anyFloat(), eq(collisionRects));
    }

    @Test
    public void unopenedChestDoesNothing() {
        TransformComponent transform = transform(100f, 50f);
        CollisionComponent collision = collision(0f, 0f, 128f, 128f);
        place(transform, collision, 100f, 50f);

        ChestComponent chest = new ChestComponent();
        // opened stays false

        Entity entity = entity(transform, chest, collision);
        engine.addEntity(entity);

        for (int i = 0; i < 30; i++) {
            engine.update(DT);
        }

        verify(entityFactory, never()).popCoins(any(), anyFloat(), anyFloat(), anyInt(), anyFloat(), any());
        verify(entityFactory, never()).popPotion(any(), anyFloat(), anyFloat(), anyString(), anyFloat(), any());
    }

    @Test
    public void closedChest_addsWorldBoundsToCollisionRects() {
        Entity e = chest(100f, 50f, null, false);

        engine.update(DT);

        assertEquals(1, collisionRects.size);
        assertSame(COLLISION.get(e).worldBounds, collisionRects.get(0));
    }

    @Test
    public void closedChest_repeatedUpdates_noDuplicateRects() {
        Entity e = chest(100f, 50f, null, false);

        for (int i = 0; i < 5; i++) {
            engine.update(DT);
        }

        assertEquals(1, collisionRects.size);
        assertSame(COLLISION.get(e).worldBounds, collisionRects.get(0));
    }

    @Test
    public void openedChest_removedFromCollisionRects() {
        Entity e = chest(100f, 50f, null, false);
        engine.update(DT);
        assertEquals(1, collisionRects.size);

        CHEST.get(e).opened = true;
        engine.update(DT);

        assertEquals(0, collisionRects.size);
    }

    @Test
    public void openedChest_staysRemovedAcrossUpdates() {
        Entity e = chest(100f, 50f, null, false);
        engine.update(DT);
        assertEquals(1, collisionRects.size);

        CHEST.get(e).opened = true;
        engine.update(DT);

        for (int i = 0; i < 5; i++) {
            engine.update(DT);
        }
        assertEquals(0, collisionRects.size);
    }

    @Test
    public void toggleClosedToOpenedAndBack_reconcilesRects() {
        Entity e = chest(100f, 50f, null, false);
        engine.update(DT);
        assertEquals(1, collisionRects.size);

        // Open → rect removed
        CHEST.get(e).opened = true;
        engine.update(DT);
        assertEquals(0, collisionRects.size);

        // Re-close (self-heal across reloads) → rect re-added
        CHEST.get(e).opened = false;
        engine.update(DT);
        assertEquals(1, collisionRects.size);
        assertSame(COLLISION.get(e).worldBounds, collisionRects.get(0));
    }

    @Test
    public void chestWithoutCollisionComponent_skipsSolidityWithoutNpe() {
        TransformComponent transform = transform(100f, 50f);
        ChestComponent chest = new ChestComponent();
        chest.opened = false;
        Entity entity = entity(transform, chest);
        engine.addEntity(entity);

        for (int i = 0; i < 3; i++) {
            engine.update(DT);
        }

        assertEquals(0, collisionRects.size);
    }

    @Test
    public void chestWithoutCollisionComponent_opened_stillDropsLoot() {
        TransformComponent transform = transform(100f, 50f);
        ChestComponent chest = new ChestComponent();
        chest.opened = true;
        chest.disappearTimer.start(0.3f);
        Entity entity = entity(transform, chest);
        engine.addEntity(entity);

        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        // No collision component → centers on transform.position
        verify(entityFactory).popCoins(eq(engine), eq(100f), eq(50f), anyInt(), anyFloat(), eq(collisionRects));
        assertEquals(0, collisionRects.size);
    }

    @Test
    public void nullCollisionRects_noNpeForOpenOrClosedChests() {
        system.setCollisionRects(null);

        chest(100f, 50f, null, false);
        chest(200f, 50f, null, true);

        for (int i = 0; i < 5; i++) {
            engine.update(DT);
        }
        // No exception thrown is the assertion.
    }

    @Test
    public void coinChest_setsCoinsDroppedAfterTimer() {
        Entity e = chest(100f, 50f, null);

        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        assertTrue(CHEST.get(e).coinsDropped);
        verify(entityFactory).popCoins(eq(engine), anyFloat(), anyFloat(), anyInt(), anyFloat(), eq(collisionRects));

        engine.update(DT);
        verify(entityFactory).popCoins(eq(engine), anyFloat(), anyFloat(), anyInt(), anyFloat(), eq(collisionRects));
    }
}
