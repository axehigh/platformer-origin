---
name: tmx-map-generator
description: Use when generating a new standalone Tiled .tmx level map for this libgdx platformer (a linear chain of whole-screen rooms with doorways, enemies, and items) 
  or regenerating one with a different room count/seed. 
  Encodes the project's map conventions (collision/hazard/oneWay properties, Rooms object layer, flipY object Y), 
  reading tile gids live from the external .tsx tilesets instead of hard-coding them. 
  Also use to understand or debug why a generated map fails validation.
---

# TMX Map Generator (Standalone Level Maps)

Generates a playable, standalone Tiled `.tmx` map for this prototype: a linear left-to-right
chain of whole-screen rooms, each fully enclosed by solid collision tiles with a walk-through
doorway to its neighbour(s), plus a random scattering of enemies and items. The output is
hand-authored-style — no level catalog / exit-door / progression wiring, just the map file.

The generator is **convention-driven**: it reads tile gids and properties live from the
project's external tilesets, all of which live in `assets/maps/level1/`:
`dungeon_tiles.tsx` (collision), `items.tsx`, `enemy.tsx`, and `secret_wall.tsx` (secret-room
entry walls — cloned **inline** into the map once as a `secret_room_wall` tileset, because
Tiled can't attach the `secretRoom` property to a raw cell; see `Secret rooms` below). It
tracks the tilesets instead of hard-coding gid numbers, so if a tileset changes,
regeneration picks up the change automatically.

## Usage

All three tilesets are in `assets/maps/level1/`, and the script opens `dungeon_tiles.tsx`
relative to the **process working directory** (see `COLLISION_TILESET_PATH`). So run it with
the CWD set to `assets/maps/level1`, point `--tilesets-dir` at `.`, and write the output to
`../` so it lands in `assets/maps/`:

```powershell
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 3 --seed 42 --tilesets-dir . --out generated_room.tmx

# single room with the secret chamber carved INSIDE it (map stays 30x17):
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  "C:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills\tmx-map-generator\scripts\generate_tmx.py" `
  --rooms 1 --inside-secret --seed 42 --tilesets-dir . --out generated_single_secret_inside.tmx
```

(run from `C:\skuld\dev_olona\libgdx\platformer-origin\assets\maps\level1` — output lands in `level1/`
as `generated_room.tmx`, the map `LevelCatalog` loads as `maps/level1/generated_room.tmx`)

- `--rooms N` — number of whole-screen rooms (default 3). Room size is fixed at 30×17 tiles ×
  128px = 3840×2176 (matches `GameConstants` `VIRTUAL_WIDTH/VIRTUAL_HEIGHT` scaled by tile size).
- `--seed N` — deterministic RNG for enemy/item placement; same seed ⇒ byte-identical output.
- `--out PATH` — output `.tmx`; the `tileset source=` is written relative to this path. With
  the invocation above (output inside `level1/`) the sources resolve to `dungeon_tiles.tsx` +
  `items.tsx` + `enemy.tsx` (same dir) and the secret-wall image to `gfx/tiles/secret_wall.png`
  (`secret_wall.tsx`'s own `../gfx/tiles/secret_wall.png`, re-based onto the output location);
  the secret-wall tileset itself is written inline as `secret_room_wall`.
- `--tilesets-dir PATH` — directory holding `items.tsx`/`enemy.tsx`; must also contain
  `dungeon_tiles.tsx` because that one is opened from the CWD. Use `.` with the invocation
  above. (There is no `assets/maps/gfx/` directory; do not point this at one.)
- `--enemy-types walker,flyer,shooter` — which enemy types may appear.
- `--inside-secret` — instead of appending a full-screen secret room to the right of the map,
  carve a hidden `CHAMBER_W x CHAMBER_H` (6×8 tile) chamber **inside** the last normal room,
  flush against its left wall and sitting on the floor; the map stays `room_count × 30` tiles
  wide. Only valid with `--rooms 1` (the flush-left placement collides with the last room's left
  doorway otherwise — the generator fails loudly). The chamber's front (right) wall carries the
  breakable `secret_room_wall` guard on the
  two passage rows; its cavity (cols `col_start+1 .. col_start+4`, rows `FLOOR_CSV_ROW-6 ..
  FLOOR_CSV_ROW-1`) is hollow so the player stands inside after breaking the wall, and its
  interior markers carry `secretRoom="secret_room"` like the appended variant. The `secret_hide`
  veil covers the whole chamber footprint **except** the guard cells (so the crack stays visible
  and strikeable). The `Rooms`-layer `secret_room` rect is emitted **before** the enclosing room
  so `RoomState.findRoomIndexContaining(...)` picks it and the camera flips onto the chamber while
  the player is inside (the docs recommend this ordering for contained secret rooms).

Library use (`generate_map(...)`, `validate_map(...)`) is also supported; stdlib only
(`argparse`, `random`, `os`, `xml.etree.ElementTree`), so the Python314 interpreter (the only
one on this machine; `python`/`python3` are not on PATH) runs it as-is.

Note the CLI example output paths in this doc assume you run from `assets/maps/level1`; the
`tileset source=` paths are resolved relative to `--out`'s directory.

## Conventions Encoded (source of truth: `resources/docs-ai/map-design-for-tiled.md`)

- **Collision:** the `collision` layer is read by `MapLoader` (`core/.../map/MapLoader.java`):
  every non-empty cell is solid by default; opt-outs come from tile properties
  `solid=false` (passage), `hazard`, `oneWay`. gid `0` = empty. `EntityFactory` spawns
  entities from `objects`/`enemies` layer properties (`enemyType`) and item tiles.
- **Doorways:** each shared room wall has a 2-row gap in the collision layer (CSV rows
  `FLOOR_CSV_ROW-1` and `-2`, i.e. directly above the floor), tall enough for the ~240px
  player collision box. If `level1/dungeon_tiles.tsx` has a tile with `solid=false` (a
  "passage" tile), doorways are filled with it (visible door); the current tileset has none, so
  doorways are open gaps and the generator prints a note.
- **Y-flip:** Tiled stores Y down; libGDX 1.14.2 `TmxMapLoader` flips object Y on load
  (`flipY=true`). The generator writes Tiled coordinates (tile objects: Y from map bottom;
  CSV rows: row 0 = top of map, floor = bottom row). `validate_map` converts back to world
  coordinates to check markers sit inside room rects.
- **Layers:** `background`(1), `collision`(2), `decoration`(5), `objects`(3), `enemies`(7),
  `Rooms`(6), `secret_hide`(8). The `Rooms` object layer is one rectangle per room (world X =
  room index × 3840, Y = 0, 3840×2176, no `camera` property ⇒ `CameraSystem` auto-picks per
  axis).
- **Secret rooms:** every generated map gets one hidden `secret_room`. Two layouts, chosen by
  `--inside-secret`:
  * *Appended (default):* a full-screen room to the right of the last normal room (map width =
    `(room_count + 1) × 30` tiles). It is fully enclosed by solid collision tiles; its left wall
    carries the breakable `secret_room_wall` tile (from the inline clone, properties `secret=true`
    + `secretRoom="secret_room"`) in the entrance rows — the guard actually sits on the last
    normal room's right wall, with the secret room's matching left-wall cells left open so
    breaking the wall opens a walk-through passage.
  * *Inside chamber (`--inside-secret`):* a 6×8-tile box carved into the last room (map width
    stays `room_count × 30`). The guard is the chamber's own front (right) wall on the passage
    rows; the rest of the chamber boundary is the room's left wall/floor/roof, and the cavity is
    hollow. The `secret_hide` veil covers the whole footprint except the guard cells.
  In both layouts the interior loot/enemy markers (`chest_secret_room`, `coin_secret_room`,
  optional enemy) all carry the object property `secretRoom="secret_room"` so `MapLoader`
  partitions them OUT of the normal spawn layers and `SecretRoomRevealer` spawns them on reveal.
  A `secret_hide` layer (top of the stack) veils the secret footprint with rock, so it looks like
  solid wall until the wall breaks. The `Rooms` layer's secret rect is named `secret_room`.

## Validation

`validate_map()` (run automatically before writing) fails loudly rather than emitting a broken
map. It checks: layer set + CSV shape; fully solid outer perimeter **except** the aligned
doorway cells on shared room walls; that every room-pair boundary has at least one row where
both wall cells are open (a real doorway); every marker lands inside some room rect;
`playerStart` exists and sits in the first normal room. Doorway cells are detected via
`Layout.is_non_solid_cell`, so passage/hazard/one-way tiles are treated as open. For the
secret room it additionally checks: the secret guard cells equal the `secret_room_wall` gid
(or nothing), the secret room is fully enclosed (only the entrance open), the `secret_hide`
layer covers the whole secret footprint with no stray cells (the guard cells are allowed to be
unveiled for an inside chamber, so the crack stays visible), and every deferred marker (one
carrying `secretRoom="secret_room"`) sits inside the secret rect while no non-deferred marker
does. An inside chamber is detected automatically (its rect is strictly contained in a normal
room rect) and additionally checked for a hollow cavity.

## Notes

- **Grill Before Building:** per `AGENTS.md`, before extending this generator with a new
  mechanic (vertical shafts, platforms, hazards, a `camera` property, multi-column maps)
  clarify the design decision with the requester rather than silently guessing — most such
  additions also need a `resources/docs-ai/map-design-for-tiled.md` update.
- The old 16px-tile generator (recovered from git `98baa78^` as `generate_tmx_old.py`) is
  obsolete: it used the dead `passage`-tile-gid convention and `demo_room.tmx`; do not revive it.
