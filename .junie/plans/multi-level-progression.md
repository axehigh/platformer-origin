---
sessionId: session-260722-082352-60v2
---

# Requirements

### Overview & Goals
Introduce a 3-level progression: `demo_room_start` (level 1) → `demo_room` (level 2) → `demo_room_final` (level 3). The player spawns in level 1; reaching an exit gate and interacting with it loads the next level in place, repositioning the player at that map's `playerStart` while **all player stats persist** (health, coins, items, etc.) since the same `PlayerComponent`/entity is kept alive across the swap. `demo_room_final` is a dead end (no exit gate) — there is intentionally no win/game-over screen yet, matching today's codebase.

### Scope
**In scope:**
- A new `LevelExitComponent` + data-driven `nextLevel` Tiled property per exit gate (no hardcoded level list).
- A `LevelManager` that swaps the active map/entities in place without destroying the Ashley engine or the player entity, so all `PlayerComponent` fields (health, coins, items, maxItems, cooldown timers, etc.) survive untouched.
- A new **interact** action (E key + a touch button) required to actually trigger a nearby exit gate, plus a proximity "sensor" slightly larger than the gate sprite that also drives a contextual UI prompt.
- Wiring the existing `demo_room.tmx` exit gate to point at `demo_room_final.tmx`, and adding a new exit gate to `demo_room_start.tmx` pointing at `demo_room.tmx`.
- Switching `GameScreen`'s initial map load from `demo_room.tmx` to `demo_room_start.tmx`.

**Out of scope:**
- Any win/game-over/death screen (none exists today; not requested).
- Camera slide/fade transitions between levels (camera snaps instantly, same as the existing flip-screen `CameraSystem`).
- Saving progress across app restarts (in-memory only, for the lifetime of one play session).

### User Stories
- As a player, I start the game in `demo_room_start` and can explore/collect items exactly as before.
- As a player, when I walk up to an exit gate and press the interact button/key, I'm moved into the next level at its designated start point, keeping my health/coins/items.
- As a player, reaching the exit gate in `demo_room` takes me to `demo_room_final`; reaching the end of `demo_room_final` does nothing further (no gate there).

### Functional Requirements
1. An exit gate only triggers a level change while the player is within its proximity sensor **and** presses the interact key/button that frame — walking through it does not auto-trigger anything.
2. Which map an exit gate leads to is read from a `nextLevel` custom property on that gate's Tiled object (e.g. `maps/demo_room.tmx`); a gate without that property is purely decorative (used for `demo_room_final`, which has none).
3. On level load (initial or transition), the player entity is repositioned to the new map's `playerStart` object, its transient movement state (velocity, grounded, wall-climbing, jump count) is reset, but `health`, `maxHealth`, `coins`, `items`, `maxItems`, and all active cooldown `Timer`s are left untouched.
4. All non-player entities from the previous level (enemies, pickups, chests, the old exit gate, etc.) are removed when a new level loads; the new level's object layer is spawned fresh via the existing `EntityFactory.spawnObjects`.
5. A contextual on-screen touch button appears only while the player is within an exit gate's proximity sensor, and disappears otherwise.

# Technical Design

### Current Implementation
- `GameScreen.show()` hardcodes a single `new MapLoader("maps/demo_room.tmx")`, builds every Ashley system exactly once, and never rebuilds anything afterward — there is no concept of "level" at all today.
- `EntityFactory.spawnObjects` already handles an `exitGate` object type, but only via `createDecoration(...)` — a `TransformComponent` + `TextureComponent`, no `CollisionComponent`, no behavior. It's currently pure decoration.
- `demo_room.tmx` (60x34 tiles = 2x2 flip-screen rooms) already has one `exitGate` object; `demo_room_start.tmx` and `demo_room_final.tmx` (each 30x17 tiles = exactly one room) have none yet.
- Map-dependent systems (`EnemySystem`, `MovementSystem`, `CollisionSystem`, `EnemyBulletCollisionSystem`, `DebugRenderSystem`) all take the `Array<Rectangle> collisionRects` from `MapLoader.getCollisionRects()` once, in their constructor; `TiledMapRenderSystem` takes a `TiledMap` once, wrapped by an `OrthogonalTiledMapRenderer`.
- `PlayerComponent` already holds all persistent player stats (`health`, `coins`, `items`, `maxItems`, ...) plus transient per-frame flags (`isWallClimbing`, `meleeHasHit`); `PlayerInputSystem` already has the exact pattern needed for a new one-shot action (see `requestTouchJump()`/`touchJumpRequested`).
- Custom Tiled object properties are an established pattern (`enemyType` on `enemy` objects) — reused here for `nextLevel` on `exitGate` objects.

### Key Decisions (confirmed with the user)
1. **Interact-to-exit, not walk-through:** an exit gate only fires while the player is near it **and** presses a dedicated interact key/button (chosen: **E** on keyboard) — not on simple overlap, unlike coin/dagger pickups.
2. **Per-gate `nextLevel` Tiled property** is the source of truth for where each gate leads, not a hardcoded level-order array — keeps the door open for future non-linear level graphs.
3. **In-place rebuild via a new `LevelManager`**, not a Screen swap: the `GameScreen`/`PooledEngine`/player `Entity` all stay alive across a level change, so stats persist automatically because it's literally the same `PlayerComponent` instance — no serialization needed.
4. **`demo_room_final` is a dead end** — no exit gate is added there; there's no win-screen infrastructure to hook into yet, matching today's codebase (no game-over handling either).
5. **Proximity sensor slightly larger than the gate sprite** (not exact-overlap) so the interact prompt/trigger feels like "walking up to a door" rather than requiring pixel-perfect overlap.

### Implementation Approach for the Map Swap
Rather than removing/re-adding the map-dependent systems on every transition (churny, and would reset `DebugRenderSystem`'s SHIFT+D toggle), the **same system instances persist for the whole game**, and only their underlying map data is swapped in place:
- `Array<Rectangle> collisionRects` is a single shared instance, created once and passed by reference to every system that needs it at `GameScreen.show()` time. `LevelManager.loadLevel()` does `collisionRects.clear(); collisionRects.addAll(newMapLoader.getCollisionRects());` — every system holding that same reference sees the new level's walls immediately, no re-wiring needed.
- `TiledMapRenderSystem` gets one new method, `setMap(TiledMap map)`, which disposes its current `OrthogonalTiledMapRenderer` and builds a new one around the new map — the system instance, its priority, and its place in the engine never change.
- The player `Entity` is never removed from the engine; `LevelManager` just repositions its `TransformComponent` and resets transient `MovementComponent`/`PlayerComponent` flags.
- Every other entity (enemies, pickups, chests, gates) from the old level is removed from the engine, then `EntityFactory.spawnObjects(...)` repopulates from the new map's object layer.

### Data Models / Contracts
```java
// New component
public class LevelExitComponent implements Component {
    /** Target .tmx asset path (e.g. "maps/demo_room.tmx"), read from the object's `nextLevel` property. */
    public String nextLevelPath = "";
}

// PlayerComponent additions
public boolean interactPressed = false; // one-shot: true only during the frame E/touch-interact was pressed
public boolean nearExit = false;        // true while inside any exit gate's proximity sensor; drives the UI prompt

// LevelManager (new, com.axehigh.platformer.map)
public class LevelManager {
    public LevelManager(PooledEngine engine, EntityFactory entityFactory, OrthographicCamera camera,
                         TiledMapRenderSystem tiledMapRenderSystem, Array<Rectangle> collisionRects,
                         MapLoader initialMapLoader) { ... }

    /** Swaps the active map: repositions the (persisted) player, keeps its stats, respawns objects. */
    public void loadLevel(String tmxPath, Entity player) { ... }

    public void dispose() { ... } // disposes whichever MapLoader is currently active
}
```

### Components
- **`LevelExitComponent`** (new) — marker + `nextLevelPath`, added only to exit-gate entities that actually have a `nextLevel` property.
- **`PlayerComponent`** (modified) — two new transient fields (`interactPressed`, `nearExit`); no change to persistent stat fields (confirms they survive level swaps for free).
- **`EntityFactory`** (modified) — `spawnObjects`'s `exitGate` case now reads the `nextLevel` property and calls a new `createExitGate(x, y, nextLevelPath)` (texture + `CollisionComponent`, sized like other pickups, + `LevelExitComponent` only if a path was provided).
- **`PlayerInputSystem`** (modified) — adds `requestTouchInteract()`/`touchInteractRequested` (mirrors `requestTouchJump`), and computes `player.interactPressed` each frame from `Input.Keys.E` or the touch flag.
- **`LevelExitSystem`** (new, `IteratingSystem` over `LevelExitComponent`+`TransformComponent`+`CollisionComponent`) — mirrors `EnemyContactSystem`'s single-player-resolved-once pattern: each frame resets `player.nearExit = false`, then per gate checks an inflated "sensor" rectangle (gate bounds padded by a few units) against the player's bounds; on overlap sets `player.nearExit = true`, and if `player.interactPressed` is also true, calls `levelManager.loadLevel(exit.nextLevelPath, playerEntity)`.
- **`TiledMapRenderSystem`** (modified) — adds `setMap(TiledMap map)` to swap the wrapped map/renderer without recreating the system.
- **`LevelManager`** (new) — owns the map-swap orchestration described above.
- **`TouchControlsStage`** (modified) — adds a small contextual "interact" button (styled as an up-arrow, per the existing UI guideline), hidden by default; exposes `setInteractVisible(boolean)`.
- **`GameScreen`** (modified) — initial map path becomes `maps/demo_room_start.tmx`; constructs `LevelManager` and `LevelExitSystem` alongside the existing systems; `render()` gains one line syncing `touchControlsStage`'s interact button to `playerComponent.nearExit`; `dispose()` now delegates map disposal to `levelManager.dispose()` instead of disposing `mapLoader` directly.

### File Structure
```
core/src/main/java/com/axehigh/platformer/
  ecs/components/
    LevelExitComponent.java      (new)
    PlayerComponent.java         (modified: +interactPressed, +nearExit)
    Mappers.java                 (modified: +LEVEL_EXIT)
  ecs/systems/
    LevelExitSystem.java         (new)
    PlayerInputSystem.java       (modified: interact input)
    TiledMapRenderSystem.java    (modified: +setMap)
  map/
    LevelManager.java            (new)
    EntityFactory.java           (modified: createExitGate)
  ui/
    TouchControlsStage.java      (modified: interact button)
  screens/
    GameScreen.java              (modified: level-1 path, wiring, dispose)
assets/maps/
  demo_room_start.tmx            (add exitGate object, nextLevel=maps/demo_room.tmx)
  demo_room.tmx                  (add nextLevel=maps/demo_room.tmx... final property to existing exitGate)
resources/docs-ai/
  ashley-ecs.md                  (sync new component/system)
  gameplay.md                    (sync new level-progression mechanic)
```

### Architecture Diagram
```mermaid
graph TD
    Player[Player Entity / PlayerComponent] -->|stays alive| Engine[PooledEngine]
    Gate[Exit Gate Entity - LevelExitComponent] -->|proximity + E key| LevelExitSystem
    LevelExitSystem -->|nearExit flag| Player
    LevelExitSystem -->|loadLevel(path, player)| LevelManager
    LevelManager -->|new MapLoader| MapLoader
    LevelManager -->|setMap| TiledMapRenderSystem
    LevelManager -->|clear+refill| CollisionRects[shared collisionRects Array]
    LevelManager -->|reposition| Player
    LevelManager -->|spawnObjects| EntityFactory
    EntityFactory -->|new gates/enemies/pickups| Engine
    CollisionRects -.read by.-> MovementSystem
    CollisionRects -.read by.-> EnemySystem
    CollisionRects -.read by.-> CollisionSystem
    CollisionRects -.read by.-> DebugRenderSystem
```

### Risks
- **Mid-frame entity removal:** removing every non-player entity and spawning new ones happens inside `LevelExitSystem.update()`, itself called mid-`engine.update()`; Ashley's `PooledEngine` defers add/remove until the current system finishes, which is the same safe pattern already relied on elsewhere (e.g. `CollisionSystem` removing bullets/enemies) — low risk, but worth a quick sanity check after wiring.
- **Stale `GameScreen.mapLoader` field:** after a transition, `LevelManager` tracks the *current* `MapLoader` internally; `GameScreen`'s own `mapLoader` field becomes stale after the first transition, so `GameScreen.dispose()` must route through `levelManager.dispose()` rather than disposing its own field directly (called out explicitly above to avoid a double-dispose/leak).

# Testing

### Validation Approach
No automated/headless test harness exists for gameplay/ECS logic in this repo (consistent with prior sessions); validation is via `./gradlew :core:compileJava :lwjgl3:compileJava :core:build -x test` plus careful code/logic inspection of the new component wiring, proximity math, and map-swap ordering.

### Key Scenarios
- Game boots directly into `demo_room_start` (not `demo_room`), player spawns at its `playerStart`.
- Walking near the `demo_room_start` exit gate shows the touch interact button and sets `nearExit`; walking away hides it again.
- Pressing E (or the touch button) while `nearExit` is true loads `demo_room.tmx`, repositions the player at its `playerStart`, and all previous enemies/pickups/gate are gone while `demo_room`'s own objects (including its exit gate) are present.
- Player's `health`/`coins`/`items` collected in level 1 are unchanged immediately after the transition into level 2.
- Reaching and interacting with `demo_room`'s exit gate loads `demo_room_final.tmx` the same way.
- `demo_room_final` has no exit gate entity at all (verify no `LevelExitComponent` entity exists in that level's spawned objects), so nothing happens no matter where the player walks.

### Edge Cases
- Pressing E while **not** near any gate does nothing (no `nearExit`, no crash if zero `LevelExitComponent` entities exist in the current family).
- An `exitGate` object with no `nextLevel` property (if any ever added) spawns as decoration only, no `LevelExitComponent`, never triggers.
- SHIFT+D debug overlay toggle state survives a level transition (since `DebugRenderSystem` is never removed/recreated, only fed an updated `collisionRects` reference).

# Delivery Steps

### ✓ Step 1: Add exit-gate data model and wire the three maps
A Tiled `exitGate` object with a `nextLevel` property now spawns as a real, targetable gate entity instead of pure decoration.
- Add `LevelExitComponent` (`nextLevelPath` field) under `ecs/components/`, plus its `Mappers.LEVEL_EXIT` entry.
- Update `EntityFactory.spawnObjects`'s `exitGate` case to read the object's `nextLevel` custom property and call a new `createExitGate(x, y, nextLevelPath)`, which builds the existing `gfx/exit_gate.png` decoration plus a `CollisionComponent` (sized like other pickups) and a `LevelExitComponent` only when a `nextLevel` path was provided.
- Add a new `exitGate` object (with `nextLevel=maps/demo_room.tmx`) to `demo_room_start.tmx`.
- Add the `nextLevel=maps/demo_room_final.tmx` property to the existing `exitGate` object in `demo_room.tmx`.
- Leave `demo_room_final.tmx` without any `exitGate` object (dead end, per confirmed scope).

### ✓ Step 2: Build the in-place level-swap mechanism (LevelManager)
The game can tear down one level's map/entities and load another without destroying the engine or the player entity.
- Add `setMap(TiledMap map)` to `TiledMapRenderSystem` (disposes the old `OrthogonalTiledMapRenderer`, builds a new one around the new map).
- Add `LevelManager` under `map/`, constructed with the engine, `EntityFactory`, camera, the `TiledMapRenderSystem` instance, the shared `Array<Rectangle> collisionRects`, and the initial `MapLoader`.
- Implement `LevelManager.loadLevel(String tmxPath, Entity player)`: load a new `MapLoader`, call `setMap(...)`, clear+refill the shared `collisionRects` array in place, remove every engine entity except the given player, dispose the old `MapLoader`, `spawnObjects(...)` from the new object layer, reposition the player's `TransformComponent` to the new `playerStart`, reset its transient `MovementComponent`/`PlayerComponent` flags (velocity, grounded, wall-climbing, jump count, `interactPressed`, `nearExit`) while leaving stat fields untouched, and snap the camera to the new starting room.
- Update `GameScreen.show()` to load `maps/demo_room_start.tmx` as the initial map (instead of `demo_room.tmx`) and construct the new `LevelManager`; update `GameScreen.dispose()` to call `levelManager.dispose()` instead of disposing its own `mapLoader` field directly.

### ✓ Step 3: Add interact input and the exit-gate trigger system
Standing near an exit gate and pressing E (or a touch button) advances to that gate's target level while the player keeps their stats.
- Add `interactPressed` and `nearExit` fields to `PlayerComponent`.
- Add `requestTouchInteract()`/`touchInteractRequested` to `PlayerInputSystem` (mirrors the existing `requestTouchJump` pattern) and compute `player.interactPressed` each frame from `Input.Keys.E` or the touch flag.
- Add `LevelExitSystem` (`IteratingSystem` over `LevelExitComponent`+`TransformComponent`+`CollisionComponent`), resolving the single player entity once like `EnemyContactSystem`; each frame resets `player.nearExit`, then per gate checks the player's bounds against a sensor rectangle inflated a few units beyond the gate's own `CollisionComponent` bounds, setting `nearExit` on overlap and calling `levelManager.loadLevel(exit.nextLevelPath, playerEntity)` when `interactPressed` is also true that frame.
- Wire `LevelExitSystem` into `GameScreen` with the `LevelManager` from the previous stage and an appropriate priority alongside the other proximity/contact systems.

### ✓ Step 4: Add the contextual interact prompt and sync documentation
Players get a visible on-screen cue near an exit gate, and the ECS/gameplay docs describe the new level-progression mechanic.
- Add a small contextual "interact" touch button to `TouchControlsStage` (styled as an up-arrow per the existing UI guideline), hidden by default, wired to `PlayerInputSystem.requestTouchInteract()`, with a new `setInteractVisible(boolean)` method.
- Update `GameScreen.render()` to call `touchControlsStage.setInteractVisible(playerComponent.nearExit)` each frame.
- Sync `resources/docs-ai/ashley-ecs.md` (new `LevelExitComponent`, `LevelExitSystem`, `PlayerComponent`/`PlayerInputSystem`/`TiledMapRenderSystem` changes, `Mappers` entry) and `resources/docs-ai/gameplay.md` (new section describing the 3-level chain, exit-gate interact mechanic, and stat-persistence guarantee) per the project's doc-sync conventions.