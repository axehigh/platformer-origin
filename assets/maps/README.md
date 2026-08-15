# Level / Map Generation

Generate prototype `.tmx` levels for the platformer with the
`generate_tmx.py` script. The generator produces fully playable maps: a
linear chain (or 2D grid) of rooms with doorways, enemies, items, an exit
gate, and an optional hidden secret room. Output maps self-validate on
generation.

> Working directory matters: the generator resolves `--tilesets-dir` and
> `--out` relative to the **current directory**, and every workflow below
> runs from `assets/maps/`.

---

## From IntelliJ IDEA (recommended)

Two committed Python run configurations are installed at
`.idea/runConfigurations/` and appear in the toolbar **Run** dropdown
(▶). Both use `/usr/bin/python3` and run from `assets/maps/`:

| Run config | What it produces |
|---|---|
| `Generate Demo Map` | `world_demo/generated_room.tmx` — 3-room linear demo chain (seed 42) |
| `Generate World2 Level` | `world2/generated_level.tmx` — 2x2 grid of 24x10 rooms (seed 42), exit gate pointing at `maps/world2/level_02.tmx` |

Edit the run configuration to change `--out`, `--seed`, room count, etc.
The World2 preset writes to `generated_level.tmx` on purpose so it never
overwrites the hand-polished real levels.

## From the terminal

Use the wrapper `generate_map.sh` (runs from any directory, always
executes with `assets/maps/` as CWD):

```sh
./generate_map.sh --help
./generate_map.sh --rooms 3 --seed 42 --tilesets-dir tileset --out world_demo/generated_room.tmx
./generate_map.sh --grid-cols 2 --grid-rows 2 --no-secret --tilesets-dir tileset \
    --exit-next maps/world2/level_02.tmx --out world2/generated_level.tmx --seed 42
```

Or call the script directly:

```sh
python3 ../../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --help
```

Override the interpreter with the `PYTHON` env var (e.g.
`PYTHON=/opt/homebrew/bin/python3 ./generate_map.sh --help`).

## Key options

| Option | Default | Meaning |
|---|---|---|
| `--rooms N` | `3` | Number of rooms in a linear chain |
| `--grid-cols / --grid-rows` | chain / `1` | Room grid layout (requires `--no-secret`) |
| `--no-secret` | off | Omit the secret room (required for grids) |
| `--room-width / --room-height` | `24` / `10` | Room size in tiles (`30x17` = whole-screen desktop rooms) |
| `--platforms N` | `0` | Floating one-way platform staircases per room (jumpable, with coin) |
| `--template NAME[,ROOM[,COL]]` | — | Stamp a reusable ASCII-art course (`--template-pick N` picks N at random) |
| `--exit-next path` | — | Exit gate with `nextLevel` property pointing at `path` |
| `--inside-secret` | off | Carve the secret chamber inside the last room instead of appending a room |
| `--enemy-types walker,flyer,shooter,knight` | all | Comma-separated enemy types to scatter (default includes the 15-HP knight) |
| `--seed N` | random | Reproducible output |
| `--out path` | **required** | Output `.tmx` path (relative to CWD = `assets/maps/`) |
| `--tilesets-dir dir` | `tileset` | Directory holding the `.tsx` tilesets |

## Conventions

- **Run from `assets/maps/`.** Tilesets resolve via `--tilesets-dir` (default `tileset`) and `--out` lands inside the maps tree.
- **Don't overwrite hand-edited levels.** `world1/`, `world2/`, and the `world_demo/` maps are hand-polished after generation. Regenerate to a `generated_*.tmx` name unless you intend a full replacement.
- **Room layout → camera.** Rooms come from the map's `Rooms` object layer; the game's `CameraSystem` flip-frames or dead-zone-scrolls per room based on room size vs viewport (see `resources/docs-ai/ashley-ecs.md`).
- **Validation.** The generator prints a summary line (rooms, doorways, shafts, object/enemy markers) and aborts on layout violations.

## Reference

Full option-by-option documentation, the tile/property conventions the
game reads at startup, and troubleshooting:
`.junie/skills/tmx-map-generator/SKILL.md`
