---
sessionId: session-260722-082352-60v2
---

# Requirements

### Overview & Goals
Add a new Agent Skill, `tmx-map-generator`, that lets the agent hand-author a valid, playable-in-isolation `.tmx` map for this platformer: a linear chain of rooms (default 3, configurable), each fully enclosed by solid collision tiles with single-passage doors connecting neighbors, populated with a small random assortment of enemies and items via the existing `objects`/`enemyType` Tiled property conventions. The skill produces a standalone `.tmx` file only — it does not wire the map into `GameScreen`/the level chain; that remains a manual follow-up.

### Scope
**In scope:**
- A new skill directory `.junie/skills/tmx-map-generator/` with `SKILL.md` (style rules, layer/property conventions, workflow) and a stdlib-only Python generator script.
- Room layout: a **linear chain** of rooms left-to-right, each room's size drawn from a small set of **fixed presets** (small/medium/large, matching the existing `smallRoom`/quadrant-room/`largeRoom` precedent in `demo_room.tmx`), each connected to the next via exactly one passage-tile (`solid=false`) doorway.
- Enemy/item placement: **simple random count per room** (a small random number of `walker`/`shooter`/`flyer` enemies per `enemies.md`, plus coins/dagger/chest objects), placed on valid floor tiles inside each room, using the existing `enemyType` custom-property pattern.
- A `background`, `collision`, `objects`, and `Rooms` layer per generated map, matching the 4-layer structure of `demo_room.tmx`/`demo_room_start.tmx`.
- A default room count of 3 when unspecified, overridable via a `--rooms N` CLI argument (or function parameter).
- Validation: a self-check step (perimeter/passage/CSV-shape checks, same style as the checks already run against `demo_room.tmx`) before declaring the generated map done.

**Out of scope:**
- Wiring the generated map into `GameScreen`, adding `nextLevel`/exit-gate properties, or otherwise linking it into the existing level chain (per user's confirmed scope: standalone `.tmx` only).
- Non-linear/branching room graphs (linear chain only, per confirmed scope).
- Fully randomized/procedural room dimensions (fixed presets only, per confirmed scope).
- Hand-drawn/artistic tile decoration — generated maps reuse the same tileset/tile IDs as `demo_room.tmx`, just laid out programmatically (this is explicitly prototype-quality, matching the project's existing map-fixing conventions).

### User Stories
- As the agent, when asked to generate a new test/prototype map, I can invoke this skill to produce a valid `.tmx` with N rooms (default 3), each safely enclosed and connected, without hand-tracing CSV rows from scratch each time.
- As the developer, I get a map with some enemies and items already scattered in it, so I can drop it into `GameScreen` (or an exit gate's `nextLevel`) and immediately have something playable to test with.

### Functional Requirements
1. Given a room count `N` (default 3) and an output path, the skill produces a single `.tmx` file with exactly `N` room rectangles chained left-to-right, each rectangle recorded in a `Rooms` object layer (matching `MapLoader.getRooms()`'s expected format).
2. Every room's `collision` layer perimeter (floor, ceiling, left wall, right wall) is fully solid (`2`) except for exactly one passage (`3`, `solid=false`) connecting it to each adjacent room in the chain — no accidental open-floor gaps, mirroring the hand-fix applied to `demo_room.tmx`.
3. Each room is assigned one of a small set of fixed size presets (e.g. small/medium/large tile dimensions) rather than randomized dimensions.
4. Each room's `objects` layer gets a random small number of enemy objects (type drawn from `enemies.md`'s catalog, with a valid `enemyType` property) and a random small number of item objects (coins/dagger/chest), placed on floor tiles that are not on top of a passage or wall.
5. The first room contains a `playerStart` object; layers/attributes (`background`, `objects` object IDs, tile GIDs) follow the same structure/conventions as `demo_room.tmx` so the output loads through the existing `MapLoader`/`EntityFactory` without code changes.
6. The skill's own validation step reports zero perimeter holes and confirms the CSV grid dimensions match the declared map width/height before finishing.

# Technical Design

### Current Implementation
- `assets/maps/demo_room.tmx` is the reference for the layer/property conventions the generator must reproduce: a `background` tile layer (id 1), a `collision` tile layer (id 2, CSV of tile IDs `1`/`2`/`0`/`3` where `3` is a tileset tile flagged `solid=false`, used as a passage/door), an `objects` object layer (id 3, holding `playerStart`, `coin`, `dagger`, `chest`, `torch`, `exitGate`, `enemy` objects — enemies carry an `enemyType` custom property per `enemies.md`), and a `Rooms` object layer (id 4, plain rectangles consumed by `MapLoader.getRooms()`/`CameraSystem`).
- `MapLoader` parses `collision` into `Array<Rectangle> collisionRects` via `isSolid(cell)` (true unless the tile has `solid=false`), and parses `Rooms` into rectangles in world (y-up) coordinates; `EntityFactory.spawnObjects` reads the `objects` layer and dispatches by object name/type, using `enemyType` for enemies.
- The most recent hand-fix to `demo_room.tmx` (done via a one-off subagent + Python `ElementTree`/CSV scripts) established the exact perimeter rule this skill must encode: every room's floor row, ceiling row, and left/right wall columns must be solid (`2`) tiles except for deliberate passage (`3`) gaps connecting to neighboring rooms — a room's *interior* floor space can freely contain `0` gaps for platforming, but its bounding perimeter and any inter-room separator walls cannot.
- The existing `pixel-art-asset-generator` skill (`.junie/skills/pixel-art-asset-generator/`) is the direct style precedent for how this skill should be structured: a `SKILL.md` describing conventions/workflow plus a stdlib-only Python helper script (no Pillow/ImageMagick/external deps available in this sandbox), with an inline verification/preview step.

### Key Decisions (confirmed with the user)
1. **Linear chain layout** — rooms are generated left-to-right, each connected only to its immediate neighbor(s) via a single passage, not a branching/random graph.
2. **Fixed size presets** — each room's tile dimensions are picked from a small preset set (small/medium/large), not fully randomized width/height, to keep perimeter-generation logic simple and always valid.
3. **Simple random count per room** — enemy/item counts per room are drawn from small random ranges (no special-casing the first/last room's pacing/role).
4. **Standalone `.tmx` output only** — the skill does not add `nextLevel` properties, exit gates, or otherwise touch `GameScreen`/the level chain; it only produces a loadable map file.
5. **Default room count of 3**, overridable by an explicit parameter/CLI flag — matches the issue's stated default.

### Proposed Changes
Add `.junie/skills/tmx-map-generator/` with two files, following the exact structure of `pixel-art-asset-generator`:
- `SKILL.md` — describes the 4-layer `.tmx` structure this project expects, the perimeter/passage collision rule, the room-size presets, the enemy/item catalog and placement rule, and a step-by-step workflow (pick room count → assign presets in a left-to-right chain → render `background`/`collision` CSVs per room with one passage per room-boundary → scatter `playerStart` (room 0) plus random enemies/items into `objects` → emit the `Rooms` layer → run the validation script → report room/passage/object summary).
- `scripts/generate_tmx.py` — a stdlib-only (`xml.etree.ElementTree` for validation, plain string/CSV building for generation) generator exposing a function like `generate_map(output_path, room_count=3, seed=None)` plus a `--rooms N --out path.tmx` CLI entry point, and a `validate_map(path)` helper (perimeter/passage/CSV-shape checks) reusing the exact validation logic already proven against `demo_room.tmx` in this session's subagent work.

### Data Models / Contracts
```python

# scripts/generate_tmx.py (conceptual shape)

ROOM_PRESETS = {
    "small":  (10, 8),   # (tile cols, tile rows)
    "medium": (20, 10),
    "large":  (30, 12),
}

def generate_map(output_path: str, room_count: int = 3, seed: int | None = None) -> None:
    """Builds a linear chain of `room_count` rooms (preset sizes chosen at random),
    each perimeter-sealed except for one passage to its neighbor(s), scatters a
    playerStart in room 0 and a small random count of enemies/items per room,
    and writes a complete .tmx (background/collision/objects/Rooms layers) to output_path."""

def validate_map(path: str) -> list[str]:
    """Returns a list of problems found (e.g. perimeter holes, CSV shape mismatches);
    empty list means the map is safe to load."""
```

### Components
- **`.junie/skills/tmx-map-generator/SKILL.md`** (new) — the skill's documented conventions and workflow, discoverable by name/description like every other skill.
- **`.junie/skills/tmx-map-generator/scripts/generate_tmx.py`** (new) — the actual generation + validation logic; no other project files are touched, since this is a tool the agent invokes on demand, not a runtime feature.

### File Structure
```
.junie/skills/tmx-map-generator/
  SKILL.md                 (new)
  scripts/
    generate_tmx.py        (new)
```

### Risks
- **Passage alignment across preset sizes:** a `small` room's passage row and a neighboring `large` room's passage row must line up in world Y or the doorway won't actually connect — the generator must compute each room's passage tile position relative to a shared floor baseline (matching how `demo_room.tmx`'s bottom rooms all share row 59 as their floor) rather than each room's own arbitrary local coordinate.
- **Enemy/item placement on top of a wall or passage tile:** random placement must exclude perimeter/passage columns and rows, or an enemy could spawn embedded in a wall — mitigated by only sampling from each room's interior floor-tile row.

# Testing

### Validation Approach
No automated/headless test harness exists for gameplay/ECS logic in this repo (consistent with prior sessions); validation is via `./gradlew :core:compileJava :lwjgl3:compileJava :core:build -x test` plus careful code/logic inspection of the new component wiring, proximity math, and map-swap ordering.

### Key Scenarios
- Generating with default args produces a 3-room `.tmx`; generating with `room_count=5` produces a 5-room chain.
- Every generated room's perimeter is fully solid except for exactly the passage tiles connecting it to its chain neighbor(s); `validate_map(...)` returns an empty problem list.
- Room 0 contains a `playerStart` object; every room contains a small random number of `enemy`/`coin`/`dagger`/`chest` objects, none positioned on a wall/passage tile.
- The generated `.tmx` opens without error when pointed to by a throwaway `new MapLoader("maps/<generated>.tmx")` call (parsed via `ElementTree`, matching this session's verification approach for hand-edited maps) and its `Rooms`/`collision`/`objects` layers match the shapes `MapLoader`/`EntityFactory` expect.

### Edge Cases
- `room_count=1` still produces a valid, fully-enclosed single room with no passages (no neighbor to connect to).
- A room using the `small` preset directly followed by a room using the `large` preset still gets a correctly-aligned single passage between them (no vertical misalignment from differing room heights).
- Re-running the generator with the same `seed` produces an identical map (useful for reproducing/debugging a specific generated layout).

# Delivery Steps

### * Step 1: Author the SKILL.md conventions document
A new skill is discoverable describing the .tmx layer structure, collision/passage rule, room presets, and enemy/item conventions this generator must follow.
- Create `.junie/skills/tmx-map-generator/SKILL.md` with frontmatter `name`/`description` (trigger: generating a new prototype `.tmx` map with N rooms).
- Document the 4-layer structure (`background`, `collision`, `objects`, `Rooms`) referencing `demo_room.tmx` as the concrete example.
- Document the perimeter/passage collision rule (solid `2` perimeter, `3` passage tiles, free interior `0` gaps) distilled from this session's map-fixing work.
- Document the `small`/`medium`/`large` room-size presets, the linear left-to-right chaining rule, and the enemy (`enemies.md` catalog + `enemyType` property) / item (`coin`/`dagger`/`chest`) placement rule.
- Document the workflow: pick room count (default 3) → assign presets → build collision CSV per room with one aligned passage per boundary → scatter objects → emit `Rooms` layer → validate → report summary.

### ✓ Step 2: Implement the room-chain and collision CSV generator
Running the generator script produces a syntactically valid `.tmx` file with N linearly-chained, fully-enclosed rooms.
- Create `.junie/skills/tmx-map-generator/scripts/generate_tmx.py` with `ROOM_PRESETS` (small/medium/large tile dimensions) and a room-chain builder that lays out `room_count` rooms left-to-right along a shared floor baseline.
- Implement the `background`/`collision` CSV construction per room: solid perimeter, one aligned passage per room-to-room boundary, free interior.
- Implement the `Rooms` object layer emission (one rectangle per room, world y-up coordinates matching `MapLoader.getRooms()`'s expected format).
- Implement `generate_map(output_path, room_count=3, seed=None)` wiring the above together into a single well-formed `.tmx` file (correct map/layer/tileset headers matching `demo_room.tmx`'s), plus a `--rooms N --out path --seed S` CLI entry point.

### ✓ Step 3: Add object placement (playerStart, enemies, items) and validation
Generated maps contain a player start point plus a scattering of enemies/items, and a validation pass confirms the map has no collision holes before it's considered finished.
- Add `playerStart` placement in room 0's interior floor.
- Add random enemy placement per room (type drawn from `enemies.md`'s catalog, `enemyType` custom property set) and random item placement per room (`coin`/`dagger`/`chest`), both restricted to each room's interior floor tiles (excluding perimeter/passage cells).
- Implement `validate_map(path)` (perimeter/passage/CSV-shape checks reusing this session's proven validation approach) and call it at the end of `generate_map(...)`, raising/reporting any problems found.
- Update `SKILL.md`'s workflow section to reference running `validate_map(...)` as the final step before reporting a generated map as done.