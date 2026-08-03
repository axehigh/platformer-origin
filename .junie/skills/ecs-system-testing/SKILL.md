---
name: ecs-system-testing
description: Use when writing or running headless unit tests for Ashley ECS systems in this libgdx platformer (e.g. MovementSystem, CameraSystem, MovingPlatformSystem) — following the established SystemTestBase + JUnit 4 + Mockito pattern, or when adding a test for a newly created or changed system. Also use when a system test fails and you need the conventions for building entities/fixtures.
---

# ECS System Testing (Headless Ashley Unit Tests)

The gameplay systems in `core/src/main/java/com/axehigh/platformer/ecs/systems/` are tested
**headless** — no GL/display backend, no `Gdx` statics — by manually wiring an Ashley `Engine`
and `Component`s. Follow the established pattern in
`core/src/test/java/com/axehigh/platformer/ecs/systems/` (see `SystemTestBase.java`,
`MovingPlatformSystemTest.java`); a system may be covered by its own `*Test.java` in the same
folder.

## Run

```powershell
./gradlew core:test
```

Run a single class: `./gradlew core:test --tests "com.axehigh.platformer.ecs.systems.MovingPlatformSystemTest"`.

## Stack & Versions

- JUnit 4.13.2 (`org.junit.Test`, `org.junit.Before`, static `assertEquals`/`assertTrue`/…).
- Mockito 5.14.2 (`org.mockito.Mockito`) — only for dependencies a system needs that are too
  heavy to fake by hand (e.g. `SpriteBatch`); prefer real lightweight objects where possible.
- Ashley `Engine`/`Entity`/`ComponentMapper` — no libGDX backend.

## The Base Class

Extend `SystemTestBase` (`com.axehigh.platformer.ecs.systems.SystemTestBase`). It provides:

- `static { GdxNativesLoader.load(); }` — loads native libs so `MathUtils` etc. work headless.
- `DT = 1f/60f` (one 60fps frame) and `EPSILON = 0.001f` for float asserts.
- `newEngine()` → plain `new Engine()`.
- `entity(Component...)` → builds an `Entity` with the given components.
- `transform(x, y)`, `movement()`, `player()`, `collision(offsetX, offsetY, w, h)` — component
  factories; `collision` also precomputes `bounds`/`worldBounds` at the offset.
- `place(transform, collision, x, y)` — moves an entity and re-derives `worldBounds` via
  `collision.updateWorldBounds(...)`. **Always use `place` when repositioning** — systems read
  the precomputed `worldBounds` rect, not `transform.position` alone, so a manual `.set()` on
  the transform desyncs the collision box.

## Conventions

- `@Before setUp()` creates the system, `engine = newEngine()`, and `engine.addSystem(system)`;
  the system under test is the only system in the engine (no cross-system interference).
- Build entities through tiny private helpers (`player(x, y)`, `platform(...)`) so each test is
  a readable one-to-three-line scenario, not component plumbing.
- Fetch components via the static mappers in `com.axehigh.platformer.ecs.components.Mappers`
  (`TRANSFORM.get(entity)`, `MOVEMENT.get(entity)`, `PLAYER.get(entity)`, …) imported with
  `import static`.
- Advance time with `engine.update(DT)` and assert **per-frame** positions with `EPSILON`
  (assert the exact `sin(DT)`, `velocity * DT` step, etc., not a coarse "moved" check).
- For multi-frame behaviour (carrying, cycles, room activation) loop `engine.update(DT)` N
  times and assert inside the loop with a message tag (`assertEquals("frame " + i, …)`).
- Systems that filter by room activation need a `RoomState` populated with
  `roomState.rooms.add(new Room(0f, 0f, w, h))` + `roomState.activeRoomIndex`.
- Systems that consult static map geometry take an `Array<Rectangle> collisionRects` — add
  `new Rectangle(x, y, w, h)` to it in the test.
- Use a `PooledEngine` in tests that spawn entities (particles/effects) and assert on the
  resulting entities (count, `ParticleComponent.scale`, …) by scanning `engine.getEntities()`.
- Some systems may need `GameConstants` values; use the real constants, not magic numbers.

## Template

```java
package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.Room;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MySystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private RoomState roomState;
    private MySystem system;
    private Engine engine;

    @Before
    public void setUp() {
        roomState = new RoomState();
        system = new MySystem(collisionRects, roomState);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 20f, 20f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), movement(), collision);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void doesTheThing() {
        Entity playerEntity = player(100f, 50f);
        MOVEMENT.get(playerEntity).velocity.x = 100f;

        engine.update(DT);

        assertEquals(100f + 100f * DT, TRANSFORM.get(playerEntity).position.x, EPSILON);
        assertTrue(MOVEMENT.get(playerEntity).grounded);
    }
}
```

## Notes

- A system may run in multiple engines across tests (`MovingPlatformSystemTest` creates a
  `PooledEngine` per particle test) — never share an `Engine` between tests; rebuild in `@Before`
  or per-test.
- Systems that touch `Gdx.graphics.getDeltaTime()` must not be unit-tested directly — wrap the
  frame-stepping (or pass `DT`) so tests stay headless; if a system calls `Gdx.*` at all, flag it
  and prefer refactoring it to take the value as an argument.
