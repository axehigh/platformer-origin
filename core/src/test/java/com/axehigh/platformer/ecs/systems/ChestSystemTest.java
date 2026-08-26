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
        // Stub createPotionPickup to return a real entity so engine.addEntity() doesn't NPE
        when(entityFactory.createPotionPickup(anyFloat(), anyFloat(), anyString())).thenAnswer(invocation -> {
            Entity e = new Entity();
            e.add(new com.axehigh.platformer.ecs.components.TransformComponent());
            return e;
        });
        system = new ChestSystem(entityFactory);
        system.setCollisionRects(collisionRects);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity chest(float x, float y, PotionType potionType) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 128f, 128f);
        place(transform, collision, x, y);

        ChestComponent chest = new ChestComponent();
        chest.potionType = potionType;
        chest.opened = true;
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
        verify(entityFactory, never()).createPotionPickup(anyFloat(), anyFloat(), anyString());

        // Advance past the 0.3s timer
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        verify(entityFactory).popCoins(eq(engine), anyFloat(), anyFloat(), anyInt(), anyFloat(), eq(collisionRects));
        verify(entityFactory, never()).createPotionPickup(anyFloat(), anyFloat(), anyString());
    }

    @Test
    public void potionChestSpawnsPotionInsteadOfCoins() {
        Entity e = chest(100f, 50f, PotionType.HEALING);

        // Timer still active — nothing should happen
        engine.update(DT);
        verify(entityFactory, never()).createPotionPickup(anyFloat(), anyFloat(), anyString());
        verify(entityFactory, never()).popCoins(any(), anyFloat(), anyFloat(), anyInt(), anyFloat(), any());

        // Advance past the 0.3s timer
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        verify(entityFactory).createPotionPickup(anyFloat(), anyFloat(), eq("HEALING"));
        verify(entityFactory, never()).popCoins(any(), anyFloat(), anyFloat(), anyInt(), anyFloat(), any());
    }

    @Test
    public void potionChestSpawnsCorrectType() {
        chest(100f, 50f, PotionType.STRENGTH);

        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        verify(entityFactory).createPotionPickup(anyFloat(), anyFloat(), eq("STRENGTH"));
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
        verify(entityFactory, never()).createPotionPickup(anyFloat(), anyFloat(), anyString());
    }
}
