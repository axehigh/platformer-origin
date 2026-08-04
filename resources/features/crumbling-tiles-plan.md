# Plan: Crumbling (One-Way) Platform Tiles

**Status:** saved for later — not yet implemented.
**Design confirmed by requester:** collision-layer tile flagged `crumble=true` → behaves as a
one-way top-only platform; when the player lands on it it shakes ~0.5s then collapses (visual +
collision removed), respawns ~2.5s later, and re-arms only after the player leaves and re-enters
(no infinite crumble loop). Per-tile, player-only, uses the existing `Timer` helper. No new
`Component` → no `Mappers` change.

---

## 1. New helper `CrumblingTile` — `core/.../map/CrumblingTile.java`
Plain non-Component holder (same spirit as `Room`), one per crumble cell:
- `TiledMapTileLayer layer`, `int cellX/cellY`, `TiledMapTileLayer.Cell originalCell` (for
  blank/restore via `setCell`)
- `Rectangle rect` (world-space; the **same instance** added to `oneWayRects`, so it can be
  removed/added by identity)
- `enum State { INTACT, SHAKING, COLLAPSED }`, `Timer shakeTimer`, `Timer respawnTimer`,
  `boolean wasOverlapped` (edge-trigger guard)

## 2. `MapLoader.java`
- Add `PROPERTY_CRUMBLE = "crumble"`.
- In `buildCollisionRects()`, insert a branch after the `solid=false` skip and before the
  `oneWay` check: if tile is `crumble=true` → `oneWayRects.add(rect)` **and**
  `crumblingTiles.add(new CrumblingTile(layer, x, y, cell, rect))` (hazard keeps precedence).
- New field `Array<CrumblingTile> crumblingTiles` + `getCrumblingTiles()` (returns the same
  shared array instance, matching the `collisionRects` pattern).

## 3. New system `CrumblingTileSystem` — `core/.../ecs/systems/CrumblingTileSystem.java`
`EntitySystem` (non-iterating, mirrors `HazardSystem`): constructor takes the shared
`Array<CrumblingTile>` + priority; resolves the single player once in `addedToEngine`; keeps an
optional `PooledEngine` ref (guarded) for a collapse dust puff via the existing
`ParticleHelper.spawnSmallSmoke`.
- **update():** tick each tile's active `Timer`, compute player `worldBounds` overlap, then:
  - `INTACT` + overlap + `!wasOverlapped` → `SHAKING`, `shakeTimer.start(SHAKE_DURATION)`.
  - `SHAKING` + shake done → `COLLAPSED`: `oneWayRects.removeValue(tile.rect, true)` +
    `layer.setCell(x, y, null)` + dust puff.
  - `COLLAPSED` + respawn done → restore: `layer.setCell(x, y, originalCell)` +
    `oneWayRects.add(rect)` → `INTACT`, set `wasOverlapped = overlaps` so it re-arms only after
    the player leaves.
  - Every frame store `wasOverlapped = overlaps(player)`.
- Constants `SHAKE_DURATION = 0.5f`, `RESPAWN_DURATION = 2.5f` go in `GameConstants.java`.

## 4. `LevelManager.java`
Add `Array<CrumblingTile> crumblingTiles` constructor param; in `loadLevel()` refill it in place
alongside the other rect arrays: `crumbleTiles.clear(); crumbleTiles.addAll(newMapLoader.getCrumblingTiles());`
— mid-crumble tiles reset to INTACT on level swap for free.

## 5. `GameScreen.java`
- New `PRIORITY_CRUMBLE = 5` constant; `Array<CrumblingTile> crumblingTiles = mapLoader.getCrumblingTiles();`
- `engine.addSystem(new CrumblingTileSystem(crumblingTiles, PRIORITY_CRUMBLE))` **before**
  `MovementSystem` (stable sort → removal happens before this frame's gravity/collision, so the
  standing player falls the instant a tile collapses).
- Pass `crumblingTiles` into the `LevelManager` constructor.

## 6. Tests — `core/src/test/.../ecs/systems/CrumblingTileSystemTest.java`
`SystemTestBase` + JUnit4, headless (a real `TiledMapTileLayer` + `Cell` with a Mockito-mocked
`TiledMapTile` avoids GL). Cases: landing triggers SHAKING (rect still in `oneWayRects`, cell
intact); after 0.5s → rect removed + `getCell == null`; after respawn → rect re-added + cell
restored; leave/re-land re-triggers but standing-through-respawn doesn't; (PooledEngine) collapse
spawns a smoke particle. Run: `./gradlew core:test`.

## 7. Docs sync (AGENTS.md rules)
- `ashley-ecs.md`: `CrumblingTile` helper section, `CrumblingTileSystem` row, wiring/priority
  list (tier 5 before Movement), `LevelManager` section.
- `gameplay.md`: new §2 mechanic section (shake/collapse/respawn, player-only, one-way, per-tile,
  edge-trigger).
- `map-design-for-tiled.md`: `crumble=true` in §3 classification table + §5 tile-properties table
  + a short §3.4 authoring blurb.

## 8. Optional (not core)
- Author a demo: add `crumble=true` to a tileset tile and paint a crumbling bridge in a level to
  verify visually. Only after desktop build check.

## Verification
`./gradlew core:test` (new + existing), then desktop run with Collision Debug (SHIFT+D) to watch
the tile drop out of the cyan one-way overlay and respawn.
