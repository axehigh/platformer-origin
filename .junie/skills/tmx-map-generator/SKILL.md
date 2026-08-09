---
name: tmx-map-generator
description: Use when generating a new standalone Tiled .tmx level map for this libgdx platformer (a linear chain — or, with --grid-cols/--grid-rows, a 2D grid — of rooms with doorways, vertical platform shafts, enemies, and items, optionally decorated with floating one-way platform staircases via --platforms N)
  or regenerating one with a different room count/seed. 
  Rooms default to whole-screen 30x17 tiles but are configurable (e.g. 24 wide x 10 high tiles for mobile-oriented scroll rooms).
  Encodes the project's map conventions (collision/hazard/oneWay properties, Rooms object layer, flipY object Y), 
  reading tile gids live from the external .tsx tilesets instead of hard-coding them. 
  Also use to understand or debug why a generated map fails validation.
---

# TMX Map Generator (Standalone Level Maps)

Generates a playable, standalone Tiled `.tmx` map for this prototype: a linear left-to-right
chain of whole-screen rooms (or, with `--grid-cols`/`--grid-rows`, a 2D grid of rooms), each
fully enclosed by solid collision tiles with a walk-through doorway to its horizontal
neighbour(s), a one-way platform shaft to its vertical neighbour(s) in grid maps, plus a
random scattering of enemies and items. The output is hand-authored-style — no level catalog /
exit-door / progression wiring, just the map file.

The generator is **convention-driven**: it reads tile gids and properties live from the
project's external tilesets, all of which live in `assets/maps/world1/`:
`dungeon_tiles.tsx` (collision), `items.tsx`, `enemy.tsx`, and `secret_wall.tsx` (secret-room
entry walls — cloned **inline** into the map once as a `secret_room_wall` tileset, because
Tiled can't attach the `secretRoom` property to a raw cell; see `Secret rooms` below). It
tracks the tilesets instead of hard-coding gid numbers, so if a tileset changes,
regeneration picks up the change automatically.

## Usage

All three tilesets are in `assets/maps/world1/`, and the script opens `dungeon_tiles.tsx`
relative to the **process working directory** (see `COLLISION_TILESET_PATH`). So run it with
the CWD set to `assets/maps/world1`, point `--tilesets-dir` at `.`, and write the output to
`../world2/` (for a world-2 map) or `../` (for a world-1 map):

```powershell
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 3 --seed 42 --tilesets-dir . --out generated_room.tmx

# single room with the secret chamber carved INSIDE it (map stays 30x17):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 1 --inside-secret --seed 42 --tilesets-dir . --out generated_single_secret_inside.tmx

# mobile-oriented rooms: 24 tiles wide x 10 tiles high (see "Room size" below):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 2 --room-width 24 --room-height 10 --seed 42 --tilesets-dir . --out generated_mobile_24x10.tmx

# world-2: 2x2 grid of 24x10 rooms, vertical platform shafts, no secret room,
# exit gate chaining into the next level (map = 48x20):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --grid-cols 2 --grid-rows 2 --room-width 24 --room-height 10 --no-secret `
  --exit-next maps/world2/level_02.tmx --seed 42 `
  --tilesets-dir . --out ..\world2\level_01.tmx

# each room decorated with a floating one-way platform staircase (see "Platforming style"):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 3 --platforms 3 --seed 42 --tilesets-dir . --out generated_platforming.tmx
```

(run from `C:\skuld\dev_olona\libgdx\platformer-origin\assets\maps\world1` — output lands in
`world1/` or `world2/`, the map `LevelCatalog` loads as `maps/world1/generated_room.tmx` or
`maps/world2/level_01.tmx`)

- `--rooms N` — number of rooms in the chain (default 3). Room size defaults to 30×17 tiles ×
  128px = 3840×2176 (matches `GameConstants` `VIRTUAL_WIDTH/VIRTUAL_HEIGHT` scaled by tile size),
  unless overridden with `--room-width`/`--room-height`.
- `--grid-cols C`, `--grid-rows R` — instead of a linear chain, tile a `C`×`R` grid of rooms
  (map = `C×room_width` × `R×room_height` tiles). Horizontal neighbours connect by walk-through
  doorways, vertical neighbours by one-way **platform shafts** (see **Grid layouts** below), and
  `playerStart` lands in the bottom-left room. Omitted → a 1-row chain of `--rooms` rooms
  (identical to the legacy layout). **Multi-row grids require `--no-secret`.**
- `--no-secret` — omit the secret room/veil/deferred markers entirely (required for multi-row
  grids; legal for chains too).
- `--exit-next PATH` — place one `exitGate` marker (rectangle, `type="exitGate"`, ~140×152px,
  property `nextLevel=PATH`) in the room **farthest from the player start** (the top-right room;
  the rightmost room on a 1-row map), standing on the floor near the room's right wall. The game
  reads `nextLevel` to chain into the next map (see `LevelExitSystem`); a map with no flag emits
  no gate. Deterministic (pure geometry, no RNG).
- **Door decorations:** if `world1/dungeon_tiles.tsx` has a tile with `type="door"` (it does —
  the 2-tile-tall `door.png`), that tile is painted on the `decoration` layer on the row **just
  above the floor** beneath the `playerStart` and (with `--exit-next`) beneath the `exitGate`
  (the gate's column, `col_end-2`). The door image is 2 tiles tall and renders upward from the
  cell, so the door's bottom edge rests on the collision floor surface — it stands on the floor
  instead of looking like it floats or sinks into it.
- `--room-width W`, `--room-height H` — room dimensions in tiles (defaults `30` and `17`). The
  map then spans `room_count × W` tiles wide × `H` tiles tall (plus one extra room when the
  appended secret room is enabled), so the map is always larger than a single room. All the
  room-derived geometry scales with these: floor/passage rows, doorway placement, secret-room
  and inside-chamber footprint, shaft platform spacing, and the marker/`Rooms` Y conversions.
  See **Room size** below.
- `--seed N` — deterministic RNG for enemy/item placement; same seed ⇒ byte-identical output.
- `--out PATH` — output `.tmx`; the `tileset source=` is written relative to this path. With
  the invocation above (output inside `world1/` or `world2/`) the sources resolve to
  `dungeon_tiles.tsx` + `items.tsx` + `enemy.tsx` (same dir, or `../world1/…` for a world-2
  output) and the secret-wall image to `gfx/tiles/secret_wall.png` (`secret_wall.tsx`'s own
  `../gfx/tiles/secret_wall.png`, re-based onto the output location); the secret-wall tileset
  itself is written inline as `secret_room_wall`. With `--no-secret` no secret-wall tileset is
  emitted at all.
- `--tilesets-dir PATH` — directory holding `items.tsx`/`enemy.tsx`; must also contain
  `dungeon_tiles.tsx` because that one is opened from the CWD. Use `.` with the invocation
  above. (The shared images live in `assets/maps/gfx/`; do not point this at the images
  directory — it must hold the `.tsx` files.)
- `--enemy-types walker,flyer,shooter` — which enemy types may appear.
- `--platforms N` — decorate each room with N floating one-way platforms in a deterministic,
  always-jumpable staircase (see **Platforming style** below). Composes with every other flag
  (chains, grids, mobile room sizes, secret rooms). Default 0 = flat floor.
- `--inside-secret` — instead of appending a full-screen secret room to the right of the map,
  carve a hidden `CHAMBER_W x CHAMBER_H` (6×8 tile) chamber **inside** the last normal room,
  flush against its left wall and sitting on the floor; the map stays `room_count × room_width`
  tiles wide. Only valid with `--rooms 1` (the flush-left placement collides with the last room's left
  doorway otherwise — the generator fails loudly). The chamber's front (right) wall carries the
  breakable `secret_room_wall` guard on the
  two passage rows; its cavity (cols `col_start+1 .. col_start+4`, rows `floor_row-6 ..
  floor_row-1`) is hollow so the player stands inside after breaking the wall, and its
  interior markers carry `secretRoom="secret_room"` like the appended variant. The `secret_hide`
  veil covers the whole chamber footprint **except** the guard cells (so the crack stays visible
  and strikeable). The `Rooms`-layer `secret_room` rect is emitted **before** the enclosing room
  so `RoomState.findRoomIndexContaining(...)` picks it and the camera flips onto the chamber while
  the player is inside (the docs recommend this ordering for contained secret rooms).

Library use (`generate_map(...)`, `validate_map(...)`) is also supported; stdlib only
(`argparse`, `random`, `os`, `xml.etree.ElementTree`), so the Python314 interpreter (the only
one on this machine; `python`/`python3` are not on PATH) runs it as-is.

Note the CLI example output paths in this doc assume you run from `assets/maps/world1`; the
`tileset source=` paths are resolved relative to `--out`'s directory.

## Room size

Rooms default to the whole-screen 30×17 tiles (the desktop viewport). For **mobile-oriented**
maps, generate 24×10-tile rooms (`--room-width 24 --room-height 10`, 3072×1280px): under the
phone `BAND_ZOOM` camera (`MOBILE_ZOOM = 0.55`, ~2112×1197px effective frame) such a room is
**bigger than the frame**, so it uses the dead-zone scroll camera on phones while on desktop
(zoom 1, frame 3840×2176) it is a small flip room whose viewport overshoots into the neighbour.
No engine change is needed — `MapLoader`/`CameraSystem` already handle arbitrary room sizes;
only the room dimensions, doorways, and secret-room/chamber footprints scale.

## Grid layouts & vertical platform shafts

`--grid-cols C --grid-rows R` tiles `C×R` rooms over a map of `C×room_width × R×room_height`
tiles (e.g. 2×2 of 24×10 → a 48×20 map). Conventions:

- **Grid geometry:** rooms are laid out row-major (top-left is room 0). Horizontal neighbours
  (same grid row) connect through the usual 2-row **doorways**; vertical neighbours (same grid
  column) connect through **platform shafts** — the engine has no ladders.
- **Shaft shape:** a 2-column channel at interior columns `col_start+2..col_start+3` (relative
  to the room's left wall) hollowed from the lower room's ceiling down to one row above its
  floor, with a `oneWay` platform every 2 rows (a 24×10 room gets platforms at rows
  `floor−2, −4, −6, −8`; the floor row stays solid). The upper room's matching two floor cells
  at the shaft columns are hollowed into a **hatch** so the player climbs through (and can fall
  back down). Step spacing of 2 rows fits the ~2.5-tile jump. See
  `resources/docs-ai/map-design-for-tiled.md` §3.6.
- **`playerStart`** lands in the bottom-left room (`(grid_rows−1, 0)`).
- **`--no-secret` is required** for multi-row grids: the appended secret room only makes sense
  for a 1-row chain. With `--no-secret` the `secret_hide` veil is emitted empty and no
  secret-wall tileset/markers are written.
- A 1-row grid (`--grid-rows 1`, or just `--rooms N`) is byte-identical to the legacy chain
  output (verified by regression).

## Conventions Encoded (source of truth: `resources/docs-ai/map-design-for-tiled.md`)

- **Collision:** the `collision` layer is read by `MapLoader` (`core/.../map/MapLoader.java`):
  every non-empty cell is solid by default; opt-outs come from tile properties
  `solid=false` (passage), `hazard`, `oneWay`. gid `0` = empty. `EntityFactory` spawns
  entities from `objects`/`enemies` layer properties (`enemyType`) and item tiles.
- **Doorways:** each shared room wall has a 2-row gap in the collision layer (CSV rows
  `FLOOR_CSV_ROW-1` and `-2`, i.e. directly above the floor), tall enough for the ~240px
  player collision box. If `world1/dungeon_tiles.tsx` has a tile with `solid=false` (a
  "passage" tile), doorways are filled with it (visible door); the current tileset has none, so
  doorways are open gaps and the generator prints a note.
- **Y-flip:** Tiled stores Y down; libGDX 1.14.2 `TmxMapLoader` flips object Y on load
  (`flipY=true`). The generator writes Tiled coordinates (tile objects: Y from map bottom;
  CSV rows: row 0 = top of map, floor = bottom row). `validate_map` converts back to world
  coordinates to check markers sit inside room rects.
- **Layers:** `background`(1), `collision`(2), `decoration`(5), `objects`(3), `enemies`(7),
  `Rooms`(6), `secret_hide`(8). The `Rooms` object layer is one rectangle per room (world X =
  room column × 3072 for 24-wide rooms, Y = grid row × 1280 for 10-high rooms; no `camera`
  property ⇒ `CameraSystem` auto-picks per axis).
- **Secret rooms:** every generated map gets one hidden `secret_room` unless `--no-secret` is
  passed. Two layouts, chosen by `--inside-secret`:
  * *Appended (default):* a full-screen room to the right of the last normal room (map width =
    `(room_count + 1) × room_width` tiles). It is fully enclosed by solid collision tiles; its
    left wall carries the breakable `secret_room_wall` tile (from the inline clone, properties
    `secret=true` + `secretRoom="secret_room"`) in the entrance rows — the guard actually sits
    on the last normal room's right wall, with the secret room's matching left-wall cells left
    open so breaking the wall opens a walk-through passage. Only valid on a 1-row map.
  * *Inside chamber (`--inside-secret`):* a 6×8-tile box carved into the last room (map width
    stays `room_count × room_width`). The guard is the chamber's own front (right) wall on the
    passage rows; the rest of the chamber boundary is the room's left wall/floor/roof, and the
    cavity is hollow. The `secret_hide` veil covers the whole footprint except the guard cells.
  In both layouts the interior loot/enemy markers (`chest_secret_room`, `coin_secret_room`,
  optional enemy) all carry the object property `secretRoom="secret_room"` so `MapLoader`
  partitions them OUT of the normal spawn layers and `SecretRoomRevealer` spawns them on reveal.
  A `secret_hide` layer (top of the stack) veils the secret footprint with rock, so it looks like
  solid wall until the wall breaks. The `Rooms` layer's secret rect is named `secret_room`.

## Platforming style (`--platforms N`)

`--platforms N` floats N one-way platforms in each room's interior, breaking up the flat floor
into a small vertical course above the walkable ground (the floor itself stays intact — no pits
in v1). Placement is **deterministic, not random**: the first platform sits 2 rows above the
floor at interior column `col_start+4`, and each next platform steps **2 rows up / 2 columns
right** — the same spacing as the vertical-shaft platforms (2-row steps fit the ~2.5-tile
jump), so every platform is reachable by construction and no reachability analysis is needed.
N is clamped to the room's interior size (a 30×17 room fits up to 7; a 24×10 room up to 3).

Each room also gets one **coin** on the *highest* platform (name `coin_platform_r<index>`) and a
`bg-*` filler tile from `dungeon_tiles.tsx` (`bg-barrel`/`bg-crate`, matched by image basename —
never hard-coded gids) painted on the `background` layer directly behind each platform. Nothing
else changes: the one-way tiles are interior-only, so `validate_map()`'s perimeter/doorway/shaft
checks are untouched. See `resources/docs-ai/map-design-for-tiled.md` §3.3 for one-way platform
behavior.

## Validation

`validate_map()` (run automatically before writing) fails loudly rather than emitting a broken
map. It checks: layer set + CSV shape; fully solid outer perimeter **except** the aligned
doorway cells on shared room walls (and, for grid maps, the platform-shaft hatch cells); that
every horizontal room-pair boundary has at least one aligned open row on the passage rows (a
real doorway); that every vertical room-pair boundary has aligned 2-column openings exactly at
the shaft columns (`col_start+2..col_start+3`) with a one-way platform somewhere in the shaft
and a solid floor under it; every marker lands inside some room rect; `playerStart` exists and
sits in a normal room. Doorway cells are detected via `Layout.is_non_solid_cell`, so
passage/hazard/one-way tiles are treated as open. For the
secret room it additionally checks: the secret guard cells equal the `secret_room_wall` gid
(or nothing), the secret room is fully enclosed (only the entrance open), the `secret_hide`
layer covers the whole secret footprint with no stray cells (the guard cells are allowed to be
unveiled for an inside chamber, so the crack stays visible), and every deferred marker (one
carrying `secretRoom="secret_room"`) sits inside the secret rect while no non-deferred marker
does. An inside chamber is detected automatically (its rect is strictly contained in a normal
room rect) and additionally checked for a hollow cavity. On a `--no-secret` map the `secret_hide`
layer must be entirely empty. When `--exit-next` was used, validation additionally requires
exactly one `exitGate` marker whose `nextLevel` equals the requested path, sitting inside a
normal room rect, plus **exactly two** door decorations on the `decoration` layer — one on the
row just above the exit room's floor in the gate's column (`col_end-2`), one anywhere for the
spawn — and each decoration cell must use a `type="door"` tile gid.

## Notes

- **Grill Before Building:** per `AGENTS.md`, before extending this generator with a new
  mechanic (platforms, hazards, a `camera` property, secret-room variants, extra grid
  topologies) clarify the design decision with the requester rather than silently guessing —
  most such additions also need a `resources/docs-ai/map-design-for-tiled.md` update.
- The old 16px-tile generator (recovered from git `98baa78^` as `generate_tmx_old.py`) is
  obsolete: it used the dead `passage`-tile-gid convention and `demo_room.tmx`; do not revive it.
