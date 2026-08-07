# Feature Plan: Secret Rooms with Breakable Walls

Status: Implemented — code, headless tests (149 passing, 21 suites), and a demo hidden secret room (`secret_room` in `assets/maps/level1/generated_room.tmx`: the full 30-tile room right of room 2, cols 90–119, whose west boundary wall at col 89 has two body-height rows 14–15 as the breakable `secret_wall` tile the player strikes from the last normal room) are in. Pending: in-game verification on desktop.

> Design evolution: the original plan below hid the room with solid wall + room-locked camera (the room was visible the moment the wall broke, and its loot spawned from level start). The implemented version goes further — see `resources/docs-ai/map-design-for-tiled.md` §3.5 and `gameplay.md` §2.Z: a **`secret_hide` tile layer** veils the whole room footprint with identical rock until reveal, the room's loot/enemy markers carry a `secretRoom="<room name>"` object property so they are **deferred** (partitioned out of the spawn layers, spawned once on reveal by `SecretRoomRevealer`), and the breakable wall tile carries a matching `secretRoom` tile property naming the `Rooms` rect it protects.
Approved decisions: sealed-room hiding, per-swing tile break, gap reveal, smoke + SFX feedback.

## Concept

The player strikes a wall (melee attack) and a hidden room is revealed. The room is fully
authored in the same `.tmx` and sealed inside a solid wall; one tile in that wall is flagged
`secret` and, when struck, becomes passable and visually disappears, opening a doorway to the
room. Camera framing (flip/scroll rooms) keeps the sealed room out of view until entered.

## Tiled authoring (designer steps)

1. New tileset `secret_wall.tsx` (mirror `drop_platform.tsx` / `hazards.tsx`):
   one 128x128 tile with a custom bool property `secret = true` on the tile.
2. Author the secret room as a normal room:
   - `background`-layer art,
   - floor/geometry as regular `collision`-layer tiles,
   - coins/chests/enemies/torches in the `objects`/`enemies` layers,
   - a rectangle in the `Rooms` object layer covering the room (camera framing +
     enemy room-activation for free).
3. Seal the entrance with normal wall tiles in the `collision` layer, then replace the
   one tile at body height with the `secret_wall` tile.
   No extra hiding layer needed — solid wall + room-locked camera hide it; breaking the
   tile is the reveal.

## Code changes

### `map/MapLoader.java`
- Add `PROPERTY_SECRET = "secret"`.
- New shared `Array<Rectangle> secretRects` + `getSecretRects()`.
- New `getCollisionLayer()` accessor (returns the `collision` `TiledMapTileLayer`).
- In `buildCollisionRects()`: a solid tile with `secret=true` lands in **both**
  `secretRects` and `collisionRects` (solid until struck, unlike `hazard` which is
  never solid).

### `ecs/systems/MeleeAttackSystem.java`
- Constructor gains the shared `secretRects`, shared `collisionRects`, and the current
  `TiledMapTileLayer` (collision layer = visual wall layer; blanking a cell removes the sprite).
- On a live strike frame, after the existing enemy -> chest checks, still gated by
  `!meleeHasHit`: check `strikeBounds` against `secretRects`. On overlap:
  - remove that rect from both `secretRects` and `collisionRects` (walkable next frame),
  - blank the cell: `collisionLayer.getCell(tileX, tileY).setTile(null)` (rect -> tile
    coords via layer tile size),
  - spawn the existing smoke puff at the tile center,
  - set `meleeHasHit = true` (one wall per swing — only tiles the strike box overlaps).
- Add `setCollisionLayer(TiledMapTileLayer)` for level swaps.

### `map/LevelManager.java`
- Constructor gains the shared `secretRects`.
- `loadLevel()` refills it (clear + addAll like `collisionRects`) and calls
  `meleeSystem.setCollisionLayer(newMapLoader.getCollisionLayer())` alongside the existing
  `setUnitScale` refreshes.

### `screens/GameScreen.java`
- Pass `mapLoader.getSecretRects()`, `getCollisionRects()`, `getCollisionLayer()` into
  `MeleeAttackSystem`; pass `secretRects` into `LevelManager`.

### Break SFX (`audio/AudioManager.java` + `ecs/systems/SfxSystem.java`)
- Load the already-present `sfx/Explosion1.mp3` as `SFX_WALL_BREAK`.
- Add `AudioManager.playWallBreak()` and `SfxSystem.playWallBreak()`, called from the break
  code (mirrors `playCoin`).

## Tests + docs

- Headless tests:
  - `MeleeAttackSystem` wall-break: array removal + cell blanking (self-contained, headless-safe).
  - `MapLoader` secret-parsing test if `TmxMapLoader` runs in the headless harness
    (verify during implementation).
- Sync docs per AGENTS.md:
  - `resources/docs-ai/ashley-ecs.md` (MeleeAttackSystem row, LevelManager section, GameScreen wiring).
  - `resources/docs-ai/gameplay.md` (new "Secret Walls & Rooms" section).

## Verification

- Desktop run (no Android rebuild): load a level with a secret wall, strike it, confirm the
  tile disappears, smoke + SFX fire, the player can walk through, and the camera frames the
  secret room on entry.
- Confirm secret walls still work after a level swap (LevelManager refill + collision-layer
  refresh).
