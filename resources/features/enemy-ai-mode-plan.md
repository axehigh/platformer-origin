# Plan: Enemy AI Modes & Smarter Patrol

**Status:** ready to implement.
**Design confirmed by requester:** enemies walk ~2× further than today (default `patrolRange`
32 → 64); a new `aiMode` Tiled property selects behavior — absent/unknown → current origin-bounded
patrol (`PATROL`), `"side-to-side"` → endless walking that turns only on walls/ledges/hazards;
**all** ground enemies (both modes) refuse to walk over dangerous terrain (spike/lava hazard
tiles); every turn pauses the enemy ~0.3s. Per-marker `speed` / `patrolRange` Tiled overrides.
No new `Component` class → no `Mappers` change; reuses the existing `Timer` helper.

---

## 1. `EnemyComponent.java`
- New nested enum `AiMode { PATROL, SIDE_TO_SIDE }` + field `public AiMode aiMode = AiMode.PATROL;`
  (data-only, matches the component style; no `Poolable`).
- Default `patrolRange` 32 → **64** (still scaled by `unitScale` in `EntityFactory`).
- New `public final Timer turnPause = new Timer();` (shared `Timer` helper per AGENTS.md).

## 2. `EntityFactory.java` — `createEnemy(...)`
- Signature gains `MapObject object, TiledMapTile tile` (already available at the `"enemy"` case
  in `spawnObjects`, line 215).
- Read per-marker overrides with the existing `getProperty`/`getFloatProperty` helpers:
  - `aiMode` (string) → `AiMode`; `"side-to-side"`/`"sidetoside"` (ignore-case) →
    `SIDE_TO_SIDE`, anything else/absent → `PATROL`.
  - `speed` (world units/s) → replaces `enemyComponent.speed` before the `*= unitScale`.
  - `patrolRange` (world units) → replaces `enemyComponent.patrolRange` before `*= unitScale`.

## 3. `EnemySystem.java`
- Constructor gains `Array<Rectangle> hazardRects` (both overloads); `GameScreen.java:133` passes
  `mapLoader.getHazardRects()`. `LevelManager` already clears+refills `hazardRects` in place, so
  the reference stays valid across level swaps (same pattern as `collisionRects`).
- New constants: `HAZARD_PROBE_AHEAD = 16f`, `HAZARD_PROBE_HEIGHT = 40f`, `TURN_PAUSE_DURATION = 0.3f`
  (all scaled by `unitScale`).
- Turn logic (after room/hitStun/postHitIdle checks, plus a `turnPause` early-out that zeroes
  velocity while active):
  - `blockedByWall` (grounded + `velocity.x == 0`), `atLedge` (existing ground probe), **and new**
    `atHazard` (grounded + `hazardAhead(...)`) all flip direction — applies to **every** grounded
    enemy, both modes.
  - `hazardAhead(...)`: probe rect just past the leading foot, from feet up `HAZARD_PROBE_HEIGHT`,
    ahead `HAZARD_PROBE_AHEAD`; `overlaps` any `hazardRects`.
  - `PATROL`: keep the `originX` ± `patrolRange` bound. `SIDE_TO_SIDE`: **skip** the bound (turns
    on wall/ledge/hazard only).
  - On any turn: `direction = -direction` **and** `turnPause.start(TURN_PAUSE_DURATION)`; while
    active, velocity zeroed and patrol skipped, so the enemy visibly pauses at each turnaround.
- Flyers (never grounded) are unaffected by wall/ledge/hazard probes; a `SIDE_TO_SIDE` flyer flies
  straight (documented; aiMode is aimed at ground-walking enemies).

## 4. Tests — `core/src/test/.../ecs/systems/EnemySystemTest.java`
- `setUp()`: pass a `hazardRects` array to the constructor.
- New cases (existing 11 must stay green):
  - `sideToSideIgnoresPatrolRange` — `SIDE_TO_SIDE` far past `originX + patrolRange` keeps
    direction (no range turn).
  - `sideToSideTurnsBeforeHazard` — hazard rect ahead of the leading foot → direction flips +
    `turnPause` active.
  - `defaultModeTurnsBeforeHazard` — `PATROL` + hazard ahead → direction flips (hazard avoidance
    is on for all ground enemies).
  - `turnPauseStopsEnemyAfterTurn` — after a ledge/wall turn the enemy's `velocity.x == 0` while
    the pause is active, then resumes.
- No `EntityFactory` unit test (needs assets/GL): `aiMode`/`speed`/`patrolRange` reads verified via
  compile + desktop run. Run: `./gradlew core:test`.

## 5. Docs sync (AGENTS.md rules)
- `enemies.md`: `aiMode` field + `SIDE_TO_SIDE` behavior, hazard avoidance for all ground enemies,
  ~0.3s turn pause, per-marker `speed`/`patrolRange` overrides.
- `ashley-ecs.md`: `EnemyComponent` row (`aiMode`, `turnPause`, `patrolRange = 64`),
  `EnemySystem` row (injected `hazardRects`, hazard probe, turn pause, side-to-side).
- `gameplay.md` §2.G (patrol section): same behavior summary.
- `map-design-for-tiled.md`: `enemy` marker row + §5 property table (`aiMode`, `speed`,
  `patrolRange`).

## 6. Out of scope
- `tmx-map-generator` untouched (does not emit `aiMode`/overrides today).
- No new enemy types; `aiMode` is a per-marker behavior switch on existing types.

## Verification
`./gradlew core:test` (new + existing 11), then desktop run: set `aiMode="side-to-side"` on a
walker in Tiled and watch it patrol room-wide, stop at walls/ledges/spike rows, and pause at each
turn (Collision Debug SHIFT+D shows the hazard probe behavior implicitly via the red hazard
overlay).
