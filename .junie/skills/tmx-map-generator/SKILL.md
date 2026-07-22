---
name: tmx-map-generator
description: Use when asked to generate a new prototype/test .tmx map for this platformer (a linear chain of N rooms, default 3), with enemies/items scattered in, without hand-tracing collision CSV rows from scratch. Produces a standalone .tmx file only — does not wire it into GameScreen or the level chain.
---

# TMX Map Generator

Generates a hand-authored, playable-in-isolation `.tmx` map for this project: a **linear chain of rooms** (left-to-right), each fully enclosed by solid collision tiles with a single passage door connecting it to its neighbor(s), and a small random scattering of enemies/items. Reuses the exact tileset/tile IDs and layer conventions from `assets/maps/demo_room.tmx`.

This skill is a **tool**, not a runtime feature: it only writes a `.tmx` file. Wiring the result into `GameScreen`/`nextLevel`/exit gates is a separate, manual follow-up.

## Layer Structure (must match `demo_room.tmx`)

Every generated map has exactly 4 layers, in this order:

1. **`background`** (tile layer, id 1) — purely decorative tile `1` (or `0` for empty); never affects collision.
2. **`collision`** (tile layer, id 2) — the CSV grid that `MapLoader.isSolid(cell)` reads. Tile values used:
   - `0` = empty/non-blocking (a hole — fine in a room's *interior* floor space for platforming, never on a perimeter/wall).
   - `2` = solid/blocking wall or floor tile.
   - `3` = a **passage** tile — visually a doorway, but has a `solid=false` custom property on its tileset tile, so `isSolid` treats it as non-blocking. This is the only way to connect two rooms.
3. **`objects`** (object layer, id 3) — gameplay entities parsed by `EntityFactory.spawnObjects`: `playerStart`, `coin`, `dagger`, `chest`, `enemy` (with an `enemyType` custom property — see `resources/docs-ai/enemies.md` for the catalog: `walker`/`shooter`/`flyer`).
4. **`Rooms`** (object layer, id 4) — plain rectangles (world y-up coordinates) consumed by `MapLoader.getRooms()`/`CameraSystem`. One rectangle per generated room.

## The Perimeter/Passage Rule

This is the rule established by hand-fixing `demo_room.tmx` in this project — **encode it exactly**:

- Every room's **floor row**, **ceiling row**, **left wall column**, and **right wall column** must be entirely `2` tiles, **except** for exactly one `3` passage tile per room-to-room boundary.
- A room's **interior** (everything inside its perimeter) can freely contain `0` gaps — that's normal platforming space, not a bug.
- Passages must be **aligned**: the passage tile row/column on one side of a room boundary must line up with the passage tile on the neighboring room's matching side, or the doorway won't actually connect. Compute passage position relative to a **shared floor baseline** (all rooms sit on the same floor world-Y), not each room's own local coordinates.
- Outer map edges (the map's own bounding box) are always solid `2` — there's nothing beyond them to connect to.

## Room Size Presets

Rooms use one of a small fixed set of tile-dimension presets — **never** fully randomized width/height:

```python
ROOM_PRESETS = {
    "small":  (10, 8),   # (tile cols, tile rows)
    "medium": (20, 10),
    "large":  (30, 12),
}
```

Each room in the chain independently and randomly picks one of these three presets.

## Layout: Linear Chain

Rooms are laid out **left-to-right only** (no branching/random graph):

- Room 0 starts at tile column 0.
- Room `i+1` starts at the tile column immediately following room `i`'s right wall.
- All rooms share the same floor baseline (world-Y where every room's floor row sits), so passages between rooms of different heights still align — taller rooms simply extend further up from that shared floor.
- Exactly one passage tile connects each pair of adjacent rooms, placed at the shared-wall column, at a row height that's valid for **both** rooms' interior (i.e. within the shorter room's height).

## Enemy & Item Placement

- **Player start:** room 0 always gets exactly one `playerStart` object, placed on its interior floor near the left wall.
- **Enemies:** each room gets a small random count (e.g. 0-2) of `enemy` objects, type randomly drawn from `walker`/`shooter`/`flyer` (see `resources/docs-ai/enemies.md` for the current catalog before generating, in case types changed), each with a valid `enemyType` custom property.
- **Items:** each room gets a small random count (e.g. 0-3) of `coin`/`dagger`/`chest` objects.
- All placements must land on a **valid interior floor tile** — never on a perimeter/wall tile or a passage tile. Only sample from a room's interior floor row, excluding the passage columns.

## Workflow

1. Check `resources/docs-ai/enemies.md` for the current enemy catalog (types may have changed since this doc was written).
2. Pick room count `N` (default 3, or from `--rooms`/a caller-supplied value).
3. Assign each room a preset (`small`/`medium`/`large`) and lay them out left-to-right along a shared floor baseline; compute one aligned passage per room-to-room boundary.
4. Build the `background`/`collision` CSVs per room: solid perimeter, aligned passage(s), free interior.
5. Scatter `objects`: `playerStart` in room 0, then random enemies/items per room on valid interior floor tiles.
6. Emit the `Rooms` object layer (one rectangle per room, world y-up).
7. Assemble the full `.tmx` (matching `demo_room.tmx`'s map/tileset/layer headers) and write it to the output path.
8. Run `validate_map(path)` and confirm it reports zero problems before declaring the map done — report a summary of room count/presets/passages/object counts to the user.

## Usage

```bash
python3 .junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 3 --out assets/maps/generated_room.tmx --seed 42
```

Or from Python:

```python
from generate_tmx import generate_map, validate_map

generate_map("assets/maps/generated_room.tmx", room_count=3, seed=42)
problems = validate_map("assets/maps/generated_room.tmx")
assert not problems, problems
```

## Notes

- Stdlib-only (`xml.etree.ElementTree` for validation, plain string/CSV building for generation) — no Pillow/ImageMagick/external deps available in this sandbox, matching the `pixel-art-asset-generator` skill's approach.
- Output is standalone: no `nextLevel` property, no exit gate, no `GameScreen` wiring. Dropping the generated map into the game (e.g. as an exit gate target) is a manual follow-up step outside this skill's scope.
- Re-running with the same `--seed` reproduces an identical map, useful for debugging a specific layout.
