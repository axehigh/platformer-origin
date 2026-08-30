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

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Headless unit tests for {@code TrapSystem}: the acid-drop spawner drops projectiles from the
 * spawner's position plus the designer's collision-editor spawn offset ({@code spawnOffsetX/Y}),
 * then the drops hang briefly (drip build), fall/accelerate, and either vanish on a wall or spawn a
 * lingering acid pool when they land on the ground (down direction).
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
        return acidSpawner(x, y, offsetX, offsetY, TrapComponent.TrapDirection.DOWN);
    }

    private Entity acidSpawner(float x, float y, float offsetX, float offsetY, TrapComponent.TrapDirection direction) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 4f, 4f);
        place(transform, collision, x, y);
        TrapComponent trap = new TrapComponent();
        trap.type = TrapType.ACID_DROP_SPAWNER;
        trap.spawnDirection = direction;
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

    private Entity singleDrop() {
        for (Entity e : engine.getEntities()) {
            TrapComponent trap = TRAP.get(e);
            if (trap != null && trap.type == TrapType.ACID_DROP) {
                return e;
            }
        }
        return null;
    }

    private Entity singlePool() {
        for (Entity e : engine.getEntities()) {
            TrapComponent trap = TRAP.get(e);
            if (trap != null && trap.type == TrapType.ACID_POOL) {
                return e;
            }
        }
        return null;
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

    @Test
    public void dropOverlappingWallSurvivesSpawnGraceThenIsRemoved() {
        // Huge wall covering the drop's spawn box (spawner at corner, drop centered on it, box 8x12).
        collisionRects.add(new Rectangle(-100f, -200f, 200f, 400f));
        // Travel LEFT so an overlap is a wall-vanish (no pool); a DOWN landing would instead spawn a pool.
        acidSpawner(0f, 0f, 0f, 0f, TrapComponent.TrapDirection.LEFT);

        // On the first frame the drop spawns already overlapping the wall; the drip-build hang plus
        // the spawn-grace window must keep it alive so it can clear the wall instead of being culled.
        engine.update(DT);
        assertEquals(2, engine.getEntities().size());

        // Once it releases and the grace window elapses the drop is still inside the huge wall and
        // must be removed like any wall-hitting drop.
        for (int i = 0; i < 60; i++) {
            engine.update(DT);
        }
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void dropRemovedWhenHittingWallAfterGrace() {
        // Wall placed just to the left of the spawn so the drop travels left and hits it after
        // clearing the drip-build + spawn-grace windows.
        collisionRects.add(new Rectangle(-50f, -300f, 40f, 600f));
        acidSpawner(0f, 0f, 0f, 0f, TrapComponent.TrapDirection.LEFT);

        // Step far enough that the grace has ended and the drop has travelled left and hit the wall.
        for (int i = 0; i < 60; i++) {
            engine.update(DT);
        }

        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void dropHangsDuringDripBuildBeforeFalling() {
        // No walls: the drop must not move while the drip-build timer is active.
        acidSpawner(100f, 200f, 0f, 0f);

        engine.update(DT); // spawns the drop

        Entity drop = singleDrop();
        assertNotNull("acid drop was not spawned", drop);
        assertEquals(100f - 4f, TRANSFORM.get(drop).position.x, EPSILON);
        assertEquals(200f - 6f, TRANSFORM.get(drop).position.y, EPSILON);

        // A few frames in, still inside the drip-build hang: position must be unchanged.
        for (int i = 0; i < 5; i++) {
            engine.update(DT);
        }
        assertEquals(100f - 4f, TRANSFORM.get(drop).position.x, EPSILON);
        assertEquals(200f - 6f, TRANSFORM.get(drop).position.y, EPSILON);
    }

    @Test
    public void downDropAcceleratesUnderGravityThenSpawnsPool() {
        // Wall below the spawn so the falling (DOWN) drop lands on it and spawns an acid pool.
        collisionRects.add(new Rectangle(-100f, -300f, 200f, 140f)); // top edge at y=-160
        acidSpawner(0f, 0f, 0f, 0f, TrapComponent.TrapDirection.DOWN);

        // Run long enough for drip build + fall + landing.
        for (int i = 0; i < 60; i++) {
            engine.update(DT);
        }

        Entity pool = singlePool();
        assertNotNull("DOWN landing should spawn an acid pool", pool);
        assertNotNull("pool entity missing collision", COLLISION.get(pool));

        // The pool sits for ~1.5s, then disappears.
        for (int i = 0; i < 120; i++) {
            engine.update(DT);
        }
        assertEquals("pool should be removed after its lifetime", 1, engine.getEntities().size());
    }

    @Test
    public void downLandingSpawnsPoolButLeftLandingDoesNot() {
        // Down drop onto a floor spawns a pool.
        collisionRects.add(new Rectangle(-100f, -300f, 200f, 140f));
        acidSpawner(0f, 0f, 0f, 0f, TrapComponent.TrapDirection.DOWN);
        for (int i = 0; i < 60; i++) {
            engine.update(DT);
        }
        assertNotNull("DOWN landing should spawn a pool", singlePool());

        // Reset and confirm a LEFT drop instead just vanishes (no pool).
        engine.removeAllEntities();
        collisionRects.clear();
        collisionRects.add(new Rectangle(-100f, -300f, 40f, 600f));
        acidSpawner(0f, 0f, 0f, 0f, TrapComponent.TrapDirection.LEFT);
        for (int i = 0; i < 60; i++) {
            engine.update(DT);
        }
        assertEquals("LEFT landing must not spawn a pool", 1, engine.getEntities().size());
    }
}
