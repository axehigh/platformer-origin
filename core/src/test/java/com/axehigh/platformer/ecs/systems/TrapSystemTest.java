package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.ecs.components.TrapComponent;
import com.axehigh.platformer.ecs.components.TrapComponent.TrapType;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static com.axehigh.platformer.ecs.components.Mappers.TRAP;
import static org.junit.Assert.assertEquals;

/**
 * Headless unit tests for {@code TrapSystem}: the acid-drop spawner drops projectiles from the
 * spawner's position plus the designer's collision-editor spawn offset ({@code spawnOffsetX/Y}).
 */
public class TrapSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private RoomState roomState;
    private TrapSystem system;
    private com.badlogic.ashley.core.PooledEngine engine;

    @Before
    public void setUp() {
        roomState = new RoomState();
        system = new TrapSystem(collisionRects, roomState, new AssetManager(), 0);
        engine = new com.badlogic.ashley.core.PooledEngine();
        engine.addSystem(system);
    }

    private Entity acidSpawner(float x, float y, float offsetX, float offsetY) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 4f, 4f);
        place(transform, collision, x, y);
        TrapComponent trap = new TrapComponent();
        trap.type = TrapType.ACID_DROP_SPAWNER;
        trap.spawnOffsetX = offsetX;
        trap.spawnOffsetY = offsetY;
        // Tiny first-spawn timer so the drop fires on the next update, then a long interval so it
        // won't respawn within the same test frame.
        trap.spawnTimer.start(0.001f);
        trap.spawnInterval = 10f;
        Entity entity = entity(transform, collision, trap);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void dropSpawnsCenteredOnSpawnerPositionPlusOffset() {
        acidSpawner(100f, 200f, 64f, 35f);

        engine.update(DT);

        // Spawner + one drop = 2 entities. At scale 1 the drop's collision/sprite box is 8x12, so
        // its bottom-left sits half-width/half-height below-left of the point (the point is center).
        assertEquals(2, engine.getEntities().size());
        for (Entity e : engine.getEntities()) {
            TrapComponent trap = TRAP.get(e);
            if (trap != null && trap.type == TrapType.ACID_DROP) {
                assertEquals(100f + 64f - 4f, TRANSFORM.get(e).position.x, EPSILON);
                assertEquals(200f + 35f - 6f, TRANSFORM.get(e).position.y, EPSILON);
                return;
            }
        }
        throw new AssertionError("acid drop was not spawned");
    }

    @Test
    public void dropSpawnsAtSpawnerCornerWhenNoOffset() {
        acidSpawner(100f, 200f, 0f, 0f);

        engine.update(DT);

        for (Entity e : engine.getEntities()) {
            TrapComponent trap = TRAP.get(e);
            if (trap != null && trap.type == TrapType.ACID_DROP) {
                assertEquals(100f - 4f, TRANSFORM.get(e).position.x, EPSILON);
                assertEquals(200f - 6f, TRANSFORM.get(e).position.y, EPSILON);
                return;
            }
        }
        throw new AssertionError("acid drop was not spawned");
    }
}
