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
project's external tilesets (`cave_tileset.tsx`, `items.tsx`, `enemy.tsx`) via `Layout`, so it
tracks the tilesets instead of hard-coding gid numbers. If a tileset changes, regeneration
picks up the change automatically.

## Usage

```powershell
& "C:\Users\pt184\AppData\Local\Programs\Python\Python314\python.exe" `
  .opencode/skills/tmx-map-generator/scripts/generate_tmx.py `
  --rooms 3 --seed 42 --out assets/maps/generated_room.tmx
```

- `--rooms N` — number of whole-screen rooms (default 3). Room size is fixed at 30×17 tiles ×
  128px = 3840×2176 (matches `GameConstants` `VIRTUAL_WIDTH/VIRTUAL_HEIGHT` scaled by tile size).
- `--seed N` — deterministic RNG for enemy/item placement; same seed ⇒ byte-identical output.
- `--out PATH` — output `.tmx`; the `tileset source=` is written relative to this path, so place
  the map under `assets/maps/` (e.g. `assets/maps/generated_room.tmx`) and the sources resolve
  to `level1/cave_tileset.tsx` etc.
- `--tilesets-dir PATH` — default `assets/maps/level1`.
- `--enemy-types walker,flyer,shooter` — which enemy types may appear.

Library use (`generate_map(...)`, `validate_map(...)`) is also supported; stdlib only
(`argparse`, `random`, `os`, `xml.etree.ElementTree`), so the Python314 interpreter (the only
one on this machine; `python`/`python3` are not on PATH) runs it as-is.

## Conventions Encoded (source of truth: `resources/docs-ai/map-design-for-tiled.md`)

- **Collision:** the `collision` layer is read by `MapLoader` (`core/.../map/MapLoader.java`):
  every non-empty cell is solid by default; opt-outs come from tile properties
  `solid=false` (passage), `hazard`, `oneWay`. gid `0` = empty. `EntityFactory` spawns
  entities from `objects`/`enemies` layer properties (`enemyType`) and item tiles.
- **Doorways:** each shared room wall has a 2-row gap in the collision layer (CSV rows
  `FLOOR_CSV_ROW-1` and `-2`, i.e. directly above the floor), tall enough for the ~240px
  player collision box. If `cave_tileset.tsx` has a tile with `solid=false` (a "passage" tile),
  doorways are filled with it (visible door); the current tileset has none, so doorways are
  open gaps and the generator prints a note.
- **Y-flip:** Tiled stores Y down; libGDX 1.14.2 `TmxMapLoader` flips object Y on load
  (`flipY=true`). The generator writes Tiled coordinates (tile objects: Y from map bottom;
  CSV rows: row 0 = top of map, floor = bottom row). `validate_map` converts back to world
  coordinates to check markers sit inside room rects.
- **Layers:** `background`(1), `collision`(2), `decoration`(5), `objects`(3), `enemies`(7),
  `Rooms`(6). The `Rooms` object layer is one rectangle per room (world X = room index ×
  3840, Y = 0, 3840×2176, no `camera` property ⇒ `CameraSystem` auto-picks per axis).

## Validation

`validate_map()` (run automatically before writing) fails loudly rather than emitting a broken
map. It checks: layer set + CSV shape; fully solid outer perimeter **except** the aligned
doorway cells on shared room walls; that every room-pair boundary has at least one row where
both wall cells are open (a real doorway); every marker lands inside some room rect;
`playerStart` exists and sits in room 0. Doorway cells are detected via
`Layout.is_non_solid_cell`, so passage/hazard/one-way tiles are treated as open.

## Notes

- **Grill Before Building:** per `AGENTS.md`, before extending this generator with a new
  mechanic (vertical shafts, platforms, hazards, a `camera` property, multi-column maps)
  clarify the design decision with the requester rather than silently guessing — most such
  additions also need a `resources/docs-ai/map-design-for-tiled.md` update.
- The old 16px-tile generator (recovered from git `98baa78^` as `generate_tmx_old.py`) is
  obsolete: it used the dead `passage`-tile-gid convention and `demo_room.tmx`; do not revive it.
