# Plan: Jump envelope + ASCII template system for `generate_tmx.py`

Status: **approved — ready to implement**

Two changes to the `.junie/skills/tmx-map-generator` skill:

1. Encode the player's jump envelope (design model) so the generator authors levels within reachable bounds.
2. Add a `.tmpl` ASCII-template system so fun/jump-aware courses can be stamped into rooms (explicitly or auto-scattered).

---

## 1. Player jump envelope + size (design model — NO engine/code change)

| Metric | Value |
|---|---|
| Player footprint | **1 × 1 tile** (design model; the real sprite/collision box is smaller) |
| Single jump — up | **2 tiles** (ledge clearance from feet) |
| Single jump — across | **4 tiles** |
| Double jump — up | **3 tiles** total from ground |
| Double jump — across | **7 tiles** |
| Doorway height | keep **2-row** (`PASSAGE_HEIGHT_TILES=2` untouched) |

- Heights are measured as ledge clearance (how high the feet rise), so a 2-tile jump comfortably clears a 2-tile obstacle for a 1-tile player.
- These numbers match the current physics (`PlayerInputSystem`: `JUMP_VELOCITY=220f`, `DOUBLE_JUMP_FACTOR=0.7f`, `maxJumps=2`; `MovementSystem` gravity `-600f`; `MOVE_SPEED=90f`). **No Java change.**
- Encode as constants in `generate_tmx.py` and cite in docs.

## 2. ASCII template system — `.tmpl` files, letters only

- Library lives in `scripts/templates/*.tmpl` (next to the script).
- `#` lines = comments; `# SYMBOL=TYPE` = per-template legend override. Unknown symbol = hard error.
- **Default legend** (resolved live from tileset `type`/properties at load time — convention-driven, like the rest of the generator):
  - `G` → solid ground: first solid `type="Ground"` tile (skips `oneWay`/`hazard` ground variants)
  - `X` → generic solid wall (first solid tile)
  - `P` → one-way platform (`Ground`+`oneWay` tile, else any `oneWay` tile)
  - `H` → hazard tile
  - `D` → `type="Door"` tile → painted on the **decoration** layer (2-tile-tall door standing on the floor)
  - `.` / space → air
  - Layer routing: `G/X/P/H` → collision; `D` → decoration.
- **No tileset rename/move needed** — the existing `type="Ground"`, `type="Door"`, `oneWay`, `hazard` tags are enough.

## 3. Placement & stamping

- **Floor-anchored**: the template's **bottom row = the room's floor row**; it must be **solid ground (`G`/`X` only)** — this keeps the room floor intact, prevents perimeter holes, and guarantees `validate_map()` passes. Anything else on the bottom row = hard error.
- Stamping **overwrites** cells (air hollows out the base floor *above* the bottom row).
- CLI: `--template NAME[,ROOM[,COL]]` repeatable.
  - `NAME` = library name (looked up as `templates/NAME.tmpl`) or a direct path.
  - `ROOM` = room index (default `0`), `COL` = left-edge column offset inside the room (default first interior column).
- Library API: `generate_map(..., templates=[("name", room, col), ...])`.
- Fits-interior enforced (width/height) — hard error if it doesn't fit.
- Templates stamp **last**, so they win over the base floor/platforms.

## 4. Auto-scatter

- `--template-pick N` (library: `template_pick=N`): stamp N distinct random library templates into N distinct random rooms that fit; deterministic per `--seed`; logs each placement.

## 5. Jump-aware validation (warnings, not failures)

After stamping, per template:
- **Support check**: every `G`/`X` cell (above the bottom row) needs a solid cell directly below (or be part of a vertical run down to the base). `P` may float.
- **Reachability check**: BFS over standable surfaces (column tops of solid runs + each one-way platform top) from the leftmost lowest surface, using the envelope — same-row gaps ≤ 4 cols (single) / ≤ 7 (double); upward rise ≤ 2 rows within 4 cols (single) / ≤ 3 rows within 7 (double); any downward move allowed; an intermediate solid run taller than the takeoff surface blocks the hop. Unreached surfaces → warning.

## 6. Starter library (from the user's suggested dict, corrected to jump-valid geometry)

The pasted `PLATFORM_TEMPLATES_JUMP_AWARE` dict was raw suggestions (single-tile supports, floor holes, mislabeled double-jump comments, a wall-climb template). Converted to `.tmpl` letters-only, jumps-only reachable:

1. **`staircase.tmpl`** — the original stair, 1-up/1-right steps, full base row.
2. **`platform-hop.tmpl`** — one-way stepping stones, 2-up/2-across (single-jump spacing).
3. **`chasm-bridge.tmpl`** — two pillars over a 5-wide gap (double-jump only; the "wide chasm" idea, corrected).
4. **`high-platform.tmpl`** — a 3-up one-way platform, double-jump-only reward spot.
5. **`hazard-strip.tmpl`** — raised ledges around a jumpable hazard strip.
6. *(Dropped `vertical_wall_jump` — required wall-climb; jumps-only library. Wall-climb-tagged templates can be added later via `requires=wallclimb`.)*

## 7. Files

| File | Change |
|---|---|
| `.junie/skills/tmx-map-generator/scripts/generate_tmx.py` | jump constants; `Template` parser; `_apply_templates`; `_template_warnings`; `--template` / `--template-pick` CLI + library params; docstring examples |
| `.junie/skills/tmx-map-generator/scripts/templates/staircase.tmpl` | new |
| `.junie/skills/tmx-map-generator/scripts/templates/platform-hop.tmpl` | new |
| `.junie/skills/tmx-map-generator/scripts/templates/chasm-bridge.tmpl` | new |
| `.junie/skills/tmx-map-generator/scripts/templates/high-platform.tmpl` | new |
| `.junie/skills/tmx-map-generator/scripts/templates/hazard-strip.tmpl` | new |
| `.junie/skills/tmx-map-generator/SKILL.md` | "Player jump envelope" section; template usage/legend/CLI; platforming-style cites envelope |
| `resources/docs-ai/map-design-for-tiled.md` | §3.6 + `--platforms` cite envelope instead of "~2.5-tile jump"; §9 tooling note re: templates; fix stale "≈120×240px player" text to the 1-tile design model |
| `resources/docs-ai/gameplay.md` | §2.A gains explicit jump envelope (2/3 up, 4/7 across, ×0.7 double-jump factor, 1-tile design model) |

## 8. Verification

- Run the generator with each starter template + `--template-pick` against a 30×17 room and a 24×10 room; confirm `validate_map()` passes and no unreachable-surface/unsupported-ground warnings fire for the starter set.
- Confirm output `.tmx` opens in the game / Tiled (spot-check layer CSV + `Rooms` rects unchanged).
