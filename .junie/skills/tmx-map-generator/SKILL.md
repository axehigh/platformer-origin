---
name: tmx-map-generator
description: Use when generating a NEW standalone Tiled .tmx level map for this libgdx platformer (a linear chain — or, with --grid-cols/--grid-rows, a 2D grid — of rooms with doorways, vertical platform shafts, enemies, and items, optionally decorated with reusable Tiled-authored template sections via --section NAME[,ROOM[,COL]] / --section-pick N from the assets/maps/templates/ canvases). Never to regenerate/overwrite an existing checked-in map (see the HARD RULE below). 
  Rooms default to 24x10 tiles (mobile-oriented scroll rooms) but are configurable (e.g. 30x17 for whole-screen desktop rooms).
  Encodes the project's map conventions (collision/hazard/oneWay properties, Rooms object layer, flipY object Y), 
  reading tile gids live from the external .tsx tilesets instead of hard-coding them. 
  Also use to understand or debug why a generated map fails validation.
---

# TMX Map Generator (Standalone Level Maps)

> ## ⛔ HARD RULE — NEVER REGENERATE EXISTING MAPS
> The shipped maps under `assets/maps/world1/` and `assets/maps/world2/` are **checked-in
> assets** — many have been hand-edited since generation (level-transition `nextLevel` wiring,
> `isFinal` victory gates, renames like `level_09_final→level_09`, layout tweaks). The generator
> **must never** be run to regenerate, overwrite, or "fix" an existing map. It exists **only** to
> create a brand-new map. If a shipped map needs a change, edit the `.tmx` directly (or use Tiled),
> never re-run the generator. This preserves hand-authored gates and wiring. Regeneration against
> an existing map silently destroys that work.

Generates a playable, standalone Tiled `.tmx` map for this prototype: a linear left-to-right
chain of whole-screen rooms (or, with `--grid-cols`/`--grid-rows`, a 2D grid of rooms), each
fully enclosed by solid collision tiles with a walk-through doorway to its horizontal
neighbour(s), a one-way platform shaft to its vertical neighbour(s) in grid maps, plus a
random scattering of enemies and items. The output is hand-authored-style — no level catalog /
exit-door / progression wiring, just the map file.

Tiled-authored **template sections** (see **Sections** below) can be stamped floor-anchored into
rooms, so the generator can author jump-aware level design beyond flat floors without hand-editing
the output. The canonical templates live in `assets/maps/templates/` and encode their section
rectangles in the same `Rooms` object layer the generator emits.

The generator is **convention-driven**: it reads tile gids and properties live from the
project's external tilesets, all of which live in `assets/maps/tileset/`. **Every generated
map always references the five canonical tilesets** in a fixed order
(`dungeon_tiles.tsx` collision, `items.tsx`, `enemy.tsx`, `bg.tsx`, `hazards.tsx`), plus —
unless the map is secret-free (the default) — `secret_wall.tsx` cloned **inline** as a
`secret_room_wall` tileset
(because Tiled can't attach the `secretRoom` property to a raw cell; see `Secret rooms`
below). `bg.tsx` holds the decorative door tiles (`type="door_enter"` / `type="door_exit"`)
and `drop_platform.tsx` is deliberately never referenced. The generator tracks the tilesets
instead of hard-coding gid numbers, so if a tileset changes, regeneration picks up the
change automatically.

## Usage

All five canonical tilesets are in `assets/maps/tileset/`, and the script opens every one relative to
`--tilesets-dir` (default `tileset`) resolved against the **process working directory**. So run
it with the CWD set to `assets/maps`, keep `--tilesets-dir tileset`, and write the output to
`world_demo/` (prototype), `world2/` (a world-2 map), or `world1/` (a world-1 map):

```powershell
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 3 --seed 42 --tilesets-dir tileset --out world_demo\generated_room.tmx

# single room with the secret chamber carved INSIDE it (map stays 24x10):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 1 --inside-secret --seed 42 --tilesets-dir tileset --out world_demo\generated_single_secret_inside.tmx

# appended secret room is OPT-IN now (default maps are secret-free):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 3 --secret --seed 42 --tilesets-dir tileset --out world_demo\generated_secret.tmx

# 24x10 is the default room size; whole-screen desktop rooms need explicit flags (see "Room size"):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 2 --room-width 30 --room-height 17 --seed 42 --tilesets-dir tileset --out world_demo\generated_desktop.tmx

# world-2: 2x2 grid of 24x10 rooms (now the default size), vertical platform shafts,
# no secret room (default), exit gate chaining into the next level (map = 48x20):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --grid-cols 2 --grid-rows 2 `
  --exit-next maps/world2/level_02.tmx --seed 42 `
  --tilesets-dir tileset --out world2\level_01.tmx

# see what template sections the canvas(es) offer (section rectangles in the Rooms layer):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --list-sections --tilesets-dir tileset

# Tiled-authored template sections stamped floor-anchored into rooms (see "Sections"):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 2 --section Pillar01,0 --section Pillar03,1,4 --seed 42 `
  --tilesets-dir tileset --out world_demo\generated_sections.tmx

# ...or auto-scatter N distinct random sections into N distinct rooms that fit:
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 5 --section-pick 3 --seed 42 --tilesets-dir tileset --out world_demo\generated_sections.tmx
```

(run from `C:\skuld\dev_olona\libgdx\platformer-origin\assets\maps` — output lands in
`world_demo/`, `world1/`, or `world2/`, the map `LevelCatalog` loads as `maps/world_demo/…`,
`maps/world1/…`, or `maps/world2/level_01.tmx`)

- `--rooms N` — number of rooms in the chain (default 3). Room size defaults to 24×10 tiles ×
  128px = 3072×1280 (the mobile-oriented default — rooms scroll under the phone's `BAND_ZOOM`
  camera), unless overridden with `--room-width`/`--room-height`.
- `--grid-cols C`, `--grid-rows R` — instead of a linear chain, tile a `C`×`R` grid of rooms
  (map = `C×room_width` × `R×room_height` tiles). Horizontal neighbours connect by walk-through
  doorways, vertical neighbours by one-way **platform shafts** (see **Grid layouts** below), and
  `playerStart` lands in the bottom-left room. Omitted → a 1-row chain of `--rooms` rooms
  (identical to the legacy layout). **Multi-row grids are always secret-free** — `--secret` on a
  multi-row grid is an error.
- `--secret` — append the full-screen secret room to the right of the last room. **Off by
  default** (maps are secret-free unless asked). Only valid on a 1-row map/grid.
- `--no-secret` — omit the secret room/veil/deferred markers. **Kept for compatibility only; it
  is now the default.**
- `--bare` — **empty arena**: only the sealed dungeon-tile frame (perimeter + floor), the
  `playerStart` marker, and (with `--exit-next`) the `exitGate` + its door decoration. No
  enemies, coins/chests, secrets, or sections — implies no secret, and is
  mutually exclusive with `--inside-secret` and `--section`/`--section-pick`.
  Used for `world3`'s long empty scroll rooms (`--rooms 1 --room-width 60 --room-height 10`).
- `--exit-next PATH` — place one `exitGate` marker (rectangle, `type="exitGate"`, ~140×152px,
  property `nextLevel=PATH`) in the room **farthest from the player start** (the top-right room;
  the rightmost room on a 1-row map), standing on the floor near the room's right wall. The game
  reads `nextLevel` to chain into the next map (see `LevelExitSystem`); a map with no flag emits
  no gate. Deterministic (pure geometry, no RNG).
- **Door decorations:** the entrance/exit door tiles come from **`tileset/bg.tsx`**, resolved
  by their `type` — `type="door_enter"` (the entrance door, `bg/door2.png`) and
  `type="door_exit"` (the exit door, `bg/door.png`). In Tiled these are set via the tile's
  **Class** field, which Tiled ≥1.10 serializes back to `type` in the file (don't switch the
  project to "Tiled 1.9" compatibility, which would write `class` and hide the tags from the
  generator). The `door_enter` tile is painted on the `decoration` layer on the row **just
  above the floor** beneath the `playerStart` and the `door_exit` tile beneath the `exitGate`,
  standing on the floor surface (the gate's column, `col_end-2`). **Doors are decided first and
  painted last:** the entrance/exit anchor columns are chosen before any section planning and
  reserved from sections, and the door decorations are painted after sections stamp, so a
  door can never be buried by a course or clobbered.
- `--room-width W`, `--room-height H` — room dimensions in tiles (defaults `24` and `10`). The
  map then spans `room_count × W` tiles wide × `H` tiles tall (plus one extra room when the
  appended secret room is enabled), so the map is always larger than a single room. All the
  room-derived geometry scales with these: floor/passage rows, doorway placement, secret-room
  and inside-chamber footprint, shaft platform spacing, and the marker/`Rooms` Y conversions.
  See **Room size** below.
- `--seed N` — deterministic RNG for enemy/item placement; same seed ⇒ byte-identical output.
- `--spawn-col N` — fix the `playerStart` column inside the player room instead of seeding it
  from RNG (must be an interior column of the player room, else the generator errors with the
  usable set). Deterministic; used by `world_0_tutorial`'s bare arenas to spawn on the left
  (`--spawn-col 3`) with a matching deterministic door position.
- `--out PATH` — output `.tmx`; the `tileset source=` is written relative to this path. With
  the invocation above (output inside `world_demo/`, `world1/`, or `world2/`) the sources
  resolve to `../tileset/dungeon_tiles.tsx` + `../tileset/items.tsx` + `../tileset/enemy.tsx` +
  `../tileset/bg.tsx` + `../tileset/hazards.tsx` and the secret-wall image to
  `gfx/tiles/secret_wall.png` (`secret_wall.tsx`'s own `../gfx/tiles/secret_wall.png`, re-based
  onto the output location); the secret-wall tileset itself is written inline as
  `secret_room_wall`. With `--no-secret` no secret-wall tileset is emitted at all.
- `--tilesets-dir PATH` — directory holding all five canonical `.tsx` tilesets
  (`dungeon_tiles.tsx`, `items.tsx`, `enemy.tsx`, `bg.tsx`, `hazards.tsx`) plus
  `secret_wall.tsx`, resolved against the CWD. Default `tileset` — use it with the invocation
  above, run from `assets/maps`. (The shared images live in `assets/maps/gfx/`; do not point
  this at the images directory — it must hold the `.tsx` files.)
- `--enemy-types walker,flyer,shooter,knight` — which enemy types may appear. (Default now includes the 15-HP knight; pass an explicit list to opt out, e.g. `--enemy-types walker,flyer,shooter`.)
- `--template-dir PATH` — directory holding the Tiled-authored template canvas `.tmx` files
  whose section rectangles get stamped into rooms (see **Sections** below). Default `templates`
  relative to the CWD (run from `assets/maps`, i.e. `assets/maps/templates/`). Resolved against
  the CWD.
- `--template-file CANVAS.tmx` — use this specific template canvas (repeatable). Canvases from
  `--template-dir` are ALSO loaded when both are given, so `file:section` disambiguation works.
- `--section NAME[,ROOM[,COL]]` — stamp a section from the template library into a room,
  **floor-anchored** (the section's bottom row lands on the room's floor row, so the room floor
  and map perimeter stay sealed — sections never punch validation holes). `NAME` is a section
  name from the library (see `--list-sections`); `ROOM` is the room index (default 0); `COL` is
  the left-edge column offset inside the room (default the first interior column). Repeatable —
  stamp several sections, possibly several per room (overlapping stamps warn; later stamps win).
  **Doors first:** sections must fit around the reserved entrance/exit anchor columns and may
  never wall off a doorway approach corridor — if an explicit placement can't, the generator
  fails loudly (push the section off the doorway with a `COL` offset, e.g.
  `--section Pillar03,1,4`). Sections stamp **after** the base shell and floors are built but
  **before** the door decorations. After stamping the generator runs jump-aware design checks
  (see **Sections** below). By default every `*.tmx` in `--template-dir` is loaded; a canvas
  whose `Rooms`-layer rectangles are empty contributes nothing.
- `--section-pick N` — auto-scatter: stamp N distinct random sections into N distinct random
  rooms that fit, deterministically per `--seed` (defaults to fewer than N if the library or
  room layout can't fit that many). Each placement is logged.
- `--list-sections` — print every available section (name, tile footprint, source canvas) and
  exit without generating anything. Uses `--template-dir`/`--template-file` and
  `--tilesets-dir` (for gid bridging).
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

Note the CLI example output paths in this doc assume you run from `assets/maps`; the
`tileset source=` paths are resolved relative to `--out`'s directory.

## Room size

Rooms **default to 24×10 tiles** (3072×1280px) — the mobile-oriented size. Under the phone
`BAND_ZOOM` camera (`MOBILE_ZOOM = 0.55`, ~2112×1197px effective frame) such a room is
**bigger than the frame**, so it uses the dead-zone scroll camera on phones while on desktop
(zoom 1, frame 3840×2176) it is a small flip room whose viewport overshoots into the neighbour.
For **whole-screen desktop rooms** (pure flip-screen framing, matching the 30×17-tile viewport),
pass `--room-width 30 --room-height 17` (3840×2176px). No engine change is needed —
`MapLoader`/`CameraSystem` already handle arbitrary room sizes; only the room dimensions,
doorways, and secret-room/chamber footprints scale.

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
  back down). Step spacing of 2 rows fits the player's single-jump envelope (2 up / 4 across —
  see **Player jump envelope** below). See
  `resources/docs-ai/map-design-for-tiled.md` §3.6.
- **`playerStart`** lands in the bottom-left room (`(grid_rows−1, 0)`).
- **`--no-secret` is the default** now; multi-row grids are always secret-free (the appended
  secret room only makes sense for a 1-row chain). `--secret` on a multi-row grid is an error.
  With no secret the `secret_hide` veil is emitted empty and no secret-wall tileset/markers are
  written.
- A 1-row grid (`--grid-rows 1`, or just `--rooms N`) is byte-identical to the legacy chain
  output (verified by regression).

## Conventions Encoded (source of truth: `resources/docs-ai/map-design-for-tiled.md`)

- **Collision:** the `collision` layer is read by `MapLoader` (`core/.../map/MapLoader.java`):
  every non-empty cell is solid by default; opt-outs come from tile properties
  `solid=false` (passage), `hazard`, `oneWay`. gid `0` = empty. `EntityFactory` spawns
  entities from `objects`/`enemies` layer properties (`enemyType`) and item tiles.
- **Doorways:** each shared room wall has a 2-row gap in the collision layer (CSV rows
  `FLOOR_CSV_ROW-1` and `-2`, i.e. directly above the floor), tall enough for the ~240px
  player collision box. If `tileset/dungeon_tiles.tsx` has a tile with `solid=false` (a
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
- **Secret rooms:** maps are **secret-free by default**; pass `--secret` (or `--inside-secret`)
  to get one. Two layouts, chosen by `--inside-secret`:
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

## Sections (Tiled-authored template canvases)

`--section NAME[,ROOM[,COL]]` / `--section-pick N` stamp reusable, jump-aware courses into
rooms so the generator can author fun level shapes (not just flat floors) without hand-editing
the output. Sections come from **standalone `.tmx` template canvases** authored in Tiled — not
ASCII art.

- **Library:** every `*.tmx` in `--template-dir` (default `templates`, i.e.
  `assets/maps/templates/`; `--template-file` adds explicit canvases). Each rectangle object in
  a canvas's section layer is one **section**. The generator reads the section layer under any
  of the names `Rooms` / `room` / `sections` — the same `Rooms` name real rooms use, so
  authoring is uniform. A canvas whose section layer is present but empty (e.g. a blank starter
  template) contributes nothing; a canvas with **no** section layer at all is treated as one
  whole-canvas section. `--list-sections` prints name / tile footprint / source canvas.
- **Gid bridging:** Tiled numbers tile gids differently than the generator (firstgids =
  `max_tile_id+1` per tileset, not declared `tilecount`), and a canvas may reference tilesets in
  any order. The generator re-homes every cell/object gid onto its own firstgid scheme by
  **source basename** — `dungeon_tiles.tsx` → collision, `items.tsx` → items, `enemy.tsx` →
  enemies, `bg.tsx` → doors/deco, `hazards.tsx` → hazards, `secret_wall.tsx` → secret guard; an
  unknown tileset source is a hard error. Tiled flip-flag bits (top 3 gid bits) are stripped.
  Canvas tile layers `background` / `collision` / `decoration` (Tiled y-down CSV) and object
  groups `objects` / `enemies` all bridge; the other layers (`secret_hide`, …) are ignored.
- **Floor-anchored + additive:** the section's bottom row lands on the room's floor row (never
  hollows it). Stamping is **additive**: a non-zero template cell overwrites the base shell, a
  zero cell leaves the base floor/walls untouched — so a section can raise structure or add
  decoration but can never punch a hole in the floor or open a pit (the map perimeter stays
  sealed, and `validate_map()` can never see a section-caused hole). Sections stamp after the
  base shell/floors are built but before the door decorations.
- **Fits enforced:** a section must fit the room's interior columns (never the shared
  walls/ceiling), stay clear of platform-shaft channels, the inside-secret chamber footprint,
  and the reserved entrance/exit anchor columns (the `playerStart` column and, with
  `--exit-next`, the gate's `col_end-3..col_end-2`), and **never wall off a doorway approach
  corridor** — a section may stamp right up to a doorway, but a fully-solid cell may not cover
  the doorway's passage rows in the corridor columns, so room-to-room travel always stays
  possible. Violations → hard error.
- **Spawn stays clear by construction:** the spawn column is picked after the explicit
  sections' footprints are known, so `--section Pillar01,0` can't bury the spawn for some seeds
  — the entrance auto-avoids explicit player-room sections; only if every usable column is
  taken does the generator error. `--section-pick` respects the reserved columns when choosing,
  so it can never collide either.
- **Section object/enemy markers** carry over: tile/rect objects inside a section's rect are
  re-emitted into the map's `objects`/`enemies` groups with renumbered ids and their
  coordinates translated onto the section's stamped location (so e.g. `coin`/`chest`/enemy
  markers authored in Tiled land where the section lands).
- **Jump-aware design checks (warnings, not failures)** — after stamping, per section:
  - *Support:* every fully-solid cell above the base needs a solid cell directly below (a
    floating solid ledge is reported; one-way platforms may float).
  - *Reachability:* BFS over standable surfaces (column tops of solid runs + each one-way
    platform top) from the floor, using the jump envelope — same-row gaps ≤ 4 cols (single) /
    ≤ 7 (double); upward rise ≤ 2 rows (single) / ≤ 3 rows (double) within those distances; any
    downward move allowed; an intermediate solid run taller than the takeoff surface blocks the
    hop. Unreachable surfaces → warning.
  - *Overlap:* two section footprints overlapping in the same room → warning (later stamps win).
- **Reference canvas:** `assets/maps/templates/template_01.tmx` (60×10) defines `Pillar01`
  (7×5), `Pillar02` (7×5), and `Pillar03` (7×7): floating pillar ledges sized and spaced for
  the 2-up/4-across single jump. Author new ones in Tiled and they show up in `--list-sections`
  with no code change.

## Player jump envelope (design model — NO engine/code change)

| Metric | Value |
|---|---|
| Player footprint | **1 × 1 tile** (design model; the real collision box is smaller: 30×40 px at 1× unitScale) |
| Single jump — up | **2 tiles** (ledge clearance from feet) |
| Single jump — across | **4 tiles** |
| Double jump — up | **3 tiles** total from ground |
| Double jump — across | **7 tiles** |

- Heights are measured as ledge clearance (how high the feet rise), so a 2-tile jump comfortably
  clears a 2-tile obstacle for a 1-tile player.
- Matches the current physics (`PlayerInputSystem`: `JUMP_VELOCITY=220f`,
  `DOUBLE_JUMP_FACTOR=0.7f`, `maxJumps=2`; `MovementSystem` gravity `-600f`; `MOVE_SPEED=90f`).
  **No Java change.**
- Enforced by `generate_tmx.py` (`JUMP_HEIGHT_SINGLE`/`JUMP_HEIGHT_DOUBLE`/
  `JUMP_DISTANCE_SINGLE`/`JUMP_DISTANCE_DOUBLE`) for the vertical-shaft platform spacing and for
  every stamped section's reachability checks (see **Sections** below). Source of truth for
  gameplay: `resources/docs-ai/gameplay.md` §2.A.

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
room rect) and additionally checked for a hollow cavity. On a no-secret map (the default) the
`secret_hide`
layer must be entirely empty. When `--exit-next` was used, validation additionally requires
exactly one `exitGate` marker whose `nextLevel` equals the requested path, sitting inside a
normal room rect, plus the two **expected** door decorations on the `decoration` layer — the
`door_exit` gid in the gate's column (`col_end-2`) one row above the exit room's floor, and
the `door_enter` gid in the `playerStart` column one row above the spawn room's floor. When
sections were stamped the strict decoration checks are relaxed (`allow_any_decoration`): no
"exactly two door cells" requirement and stray decoration gids are accepted — but the expected
enter/exit door cells are still verified to hold a door gid, and the buried-door check still
applies to every door-gid cell. On every map (with or
without `--exit-next`) validation additionally fails on any of: the `playerStart` marker cell
being a solid collision tile (spawn-in-wall), a door decoration cell backed by a solid
collision tile (buried door), stray decoration-layer gids outside the door tile set on
section-free maps, or a
solid tile on a doorway approach corridor's passage rows (blocked
room-to-room travel) — the generation-time reservations make these impossible, so these checks
are regression guards.

## Notes

- **Grill Before Building:** per `AGENTS.md`, before extending this generator with a new
  mechanic (platforms, hazards, a `camera` property, secret-room variants, extra grid
  topologies) clarify the design decision with the requester rather than silently guessing —
  most such additions also need a `resources/docs-ai/map-design-for-tiled.md` update.
- **Authoring a new template section:** draw a `background`/`collision`(+/`decoration`) course
  in Tiled, drop `items.tsx`/`enemy.tsx` tile objects or `objects`/`enemies` object groups for
  content, then add one rectangle per section to the `Rooms` layer. Gids self-align via the
  basename bridge; keep solid runs supported and jumps within the envelope (the design checks
  will warn otherwise). `--list-sections` confirms the registration.
- The old 16px-tile generator (recovered from git `98baa78^` as `generate_tmx_old.py`) is
  obsolete: it used the dead `passage`-tile-gid convention and `demo_room.tmx`; do not revive it.
