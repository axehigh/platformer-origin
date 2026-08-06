# Map Design for Tiled — Level Authoring Guide

This document is the **single source of truth for level/map design**: how a `.tmx` map is read by the game, what layers and custom properties exist, what each one means, and a step-by-step recipe for building a playable level. It's written so you can put the game down for three months, come back, and still remember how to design a level without re-reading the source.

It complements — but does not replace — `resources/docs-ai/ashley-ecs.md` (ECS shape: `MapLoader`, `EntityFactory`, `Room`, `RoomState`) and `resources/docs-ai/gameplay.md`; the `resources/docs-ai/enemies.md` catalog covers enemy placement in detail. `resources/docs-ai/ashley-ecs.md` covers what the parsing classes do in code.

> **Maintenance rule:** Any change to map parsing — layer names, object marker types, custom properties, tileset tile properties, room/camera semantics — MUST update this file in the same change (see `AGENTS.md`). If the change alters `MapLoader`/`EntityFactory`/`Room`/`CameraSystem` shape, also update `ashley-ecs.md`; if it changes what a player sees, update `gameplay.md`.

---

## 1. The big picture

A level is a single `.tmx` file (Tiled 1.11+). At level start the game:

1.  Loads the `.tmx` with `TmxMapLoader` (inside `MapLoader`).
2.  Reads the **`collision` tile layer** → the static solid AABBs used for all physics (`collisionRects`).
3.  Reads the **`Rooms` object layer** → the camera-framing/enemy-activation zones (`RoomState.rooms`).
4.  Reads the **`objects`** and **`enemies`** object layers → `EntityFactory` spawns pickups, chests, torches, the exit gate, enemies, and moving platforms.
5.  Reads the **`playerStart`** marker → where the player spawns.

Levels are chained together as **separate `.tmx` files**: the exit gate in one map points at the next map's path (see `nextLevel` below). Each playable level must also be registered in `core/.../map/LevelCatalog.java` (it's what the level-select screen reads).

### Units, scale, and coordinates (important)

*   **1 world unit == 1 pixel** as drawn in Tiled. The world's Y axis points **up** in-game; Tiled draws Y **down**. The loader flips object/tile Y coordinates automatically, so just design normally in Tiled — never hand-edit raw Y values in the `.tmx` XML.
*   The game was built for a fixed virtual resolution of `480×272` "16px-world-units", scaled by `unitScale = tileWidth / 16f` (`GameConstants.VIRTUAL_WIDTH/HEIGHT`). **All current maps use 128×128 tiles**, so:
    *   `unitScale = 128 / 16 = 8`.
    *   One on-screen frame = `VIRTUAL_WIDTH * 8 = 3840` world px wide × `VIRTUAL_HEIGHT * 8 = 2176` px tall = **exactly 30 tiles × 17 tiles**.
    *   Design rooms around whole screens (multiples of 30×17 tiles) for clean flip-screen framing.
*   Mixing tile sizes across a level chain works (scale is recalculated per map), but keep every map in a chain the **same tile size** to avoid jarring resizes.
*   Object/room rectangles are placed and sized in **world px** (the same numbers you see in Tiled), not in tiles.

---

## 2. Layers

Layer **names matter — they are read by string**. Get them exactly right or the game silently ignores them. Tiled draws tile layers in order; the game renders `background` first, then `collision` (yes, the solid tiles are visible), then `decoration` on top.

| Layer | Kind | Purpose | Required? |
|---|---|---|---|
| `background` | Tile layer | Decorative backdrop (brick, pillars, windows…). Never affects collision. | yes (can be empty) |
| `collision` | Tile layer | Solid/blocking geometry. Every painted tile is solid unless the tile opts out (see §3). | yes |
| `decoration` | Tile layer | Foreground decor drawn above the collision tiles. Never affects collision. | optional |
| `objects` | Object layer | Player start, pickups, chests, torches, exit gate, moving platforms (see §4). | yes |
| `enemies` | Object layer | Enemy markers (see §4). Separate layer keeps enemies easy to find. | optional |
| `Rooms` | Object layer | Plain rectangles defining camera zones (see §6). | optional (see §6) |

Only **tile** layers render visually; object layers are never drawn, they only spawn entities. The exit gate is the one exception worth knowing: its `exitGate` object spawns a **logic-only trigger** (no sprite — the gate's decoration is painted by you in the `background`/`decoration`/`collision` layers), so a gate needs real map art to be visible.

---

## 3. The `collision` layer — the tile language

The collision layer is a tile grid. `MapLoader.buildCollisionRects()` walks every cell and classifies it:

*   **Empty cell** (`0`) → free space (a platforming gap).
*   **Painted tile** → one of the kinds below, decided by that tile's boolean custom properties (see the full table in §5). Precedence, first match wins:
    1.  `hazard = true` → **non-solid hazard** AABB (spikes/lava; damages the player on touch — see §3.2).
    2.  `solid = false` → free space (passage doorways).
    3.  `oneWay = true` → **drop-through platform** AABB (player-only, top-only solid — see §3.3).
    4.  anything else → **solid wall**.

### §3.1 The passage rule

Room-to-room doorways must be a tile marked **non-solid**. In Tiled:

1.  Open the tileset file (e.g. `assets/maps/level1/cave_tileset.tsx`).
2.  Select the doorway tile you use for passages.
3.  Add a **boolean custom property `solid` set to `false`** on that tile.

That single tileset edit makes every map using the tile treat it as a walk-through doorway. (This is the only way to connect two rooms — the walls between rooms are otherwise solid.) If you forget it, the doorway is an invisible wall and the player can't progress.

### §3.2 Hazards (spikes, lava)

A **non-solid** tile that damages the player on contact. Paint it in the `collision` layer; the tile's `hazard = true` property (set on the tileset tile, e.g. `assets/maps/level1/hazards.tsx`) turns it into a damage zone instead of a wall:

*   On AABB overlap the player loses **1 HP**, gets the usual 0.3s hit-stun + 1s invulnerability grace, and is **not** knocked back (no directional push).
*   Hazards are fully non-solid — nothing (player, enemies, bullets) is blocked by them. The grace period turns a sustained overlap into one hit per second, not instant shredding.
*   **Smaller damage zone:** by default the danger box is the full tile. To shrink it, open the hazard tile in Tiled's **Tile Collision Editor** and draw a shape (rectangle or polygon) around the actual spikes/lava art — `MapLoader` then emits one world-space hazard box per shape instead of the full tile (a tile with no shapes, or a flipped cell, still gets the full tile). Verify with the SHIFT+D hazard overlay (red).
*   **Placement gotcha:** the player's collision box is *taller than a tile* (≈120×240 px vs a 128×128 tile) and its feet sit on the floor surface, so a hazard painted in the row directly on top of a floor tile sits at/below the player's feet and a standing player won't overlap it. Hazards damage reliably when the player's body travels **through** them (jump over a spike barrier, fall into a lava pool, walk a spike row you must jump). Put solid ground under a pit of lava/spikes so a falling player lands on it.

### §3.3 Drop-through platforms (one-way)

A **player-only, top-only** platform. Paint it in the `collision` layer; the tile's `oneWay = true` property (set on the tileset tile, e.g. `assets/maps/level1/drop_platform.tsx`) makes it a platform instead of a wall:

*   The player can **land on its top** (it sticks only when the player's feet were at/above the platform's top before the move) and can **jump up through** it from below.
*   While standing on it, the contextual **`v` button** appears (mobile) or **`S`/`DOWN`** (keyboard) starts a short ~0.25s pass-through window: the player drops straight through the platform, then normal gravity takes over.
*   **Enemies and projectiles ignore one-way platforms entirely** — they never land on or collide with them. Only the player uses them.

### Perimeter / layout rules of thumb

*   Every room needs solid **floor**, **ceiling**, **left wall**, **right wall** — except for the passage tile(s) on each room boundary.
*   The outer edges of the whole map should be solid — there's nothing beyond them.
*   Align passages: the doorway on one side of a room boundary must line up with the doorway on the neighboring room's matching side, or the connection doesn't actually link. Keep all rooms on a **shared floor baseline** so doorways at the same height line up.

---

## 4. The `objects` / `enemies` layers — markers

Both layers hold **map objects** (rectangles and/or tile objects). Each object is identified by its **`type`**, which the game reads from (in order): the object's own `type`/`Type` field, a custom property named `type`, or the **placed tile's** `type` property (for tile objects). Unknown/unhandled types are ignored.

Two ways to place a marker:

*   **Rectangle object** — draw a rectangle, then set its Type (e.g. `playerStart`) in the object properties. Size matters for `platform` (it defines the sprite + collision box); for everything else the rectangle is just a spawn point (position = its bottom-left in game coords).
*   **Tile object** — draw a tile from a tileset that already carries a `type` property. The `items.tsx` and `enemy.tsx` tilesets are pre-wired this way (e.g. the `coin` tiles, the `chest` tile, the animated `coin` tile, and the `enemy` tiles), so you can just paint the object with the stamp tool.

### Marker type reference

| `type` | Spawns | Custom properties | Notes |
|---|---|---|---|
| `playerStart` | The player | — | Exactly **one** per map, usually in the first room. Rectangle position = spawn point. |
| `coin` | Coin pickup | — | Tile objects from `items.tsx` auto-animate when the tile has a Tiled animation (use the animated coin tile for spin). |
| `chest` | Chest (opens on touch, drops coins) | — | `items.tsx` tileset. |
| `torch` | Decorative torch | — | Flickers visually. |
| `dagger` | Dagger pickup | — | Collectible item. |
| `exitGate` | Exit gate / level transition (trigger only, no sprite) | `nextLevel` (string, required to make it functional) | The gate spawns a **logic-only** entity: a collision box (sized from the object rectangle) + optional level transition. **No gate art is drawn** — paint the door's decoration yourself in the `background`/`decoration`/`collision` layers. With `nextLevel` it's a real transition; without it, purely decorative (e.g. a final-level dead end). |
| `enemy` | Enemy | `enemyType` (string, default `"walker"`), `aiMode` (string), `speed` (float), `patrolRange` (float) | Put these on the `enemies` layer (or `objects`). Catalog: `walker` (goblin), `flyer` (mosquito), `shooter` (spider), `knight` (15 HP). See `resources/docs-ai/enemies.md`. |
| `platform` | Moving platform | `amplitudeX`, `amplitudeY`, `speed`, `phase`, `axis` (see §5) | The object **rectangle size defines both the sprite and collision box**. |
| (any other) | — | — | Ignored. `background`-type markers etc. won't spawn anything. |

---

## 5. Custom properties — the full reference

All properties are read as `float`/`string`/`boolean` and tolerate being set as either int or string in Tiled. Units are **world px** unless noted.

### Object/tile properties (markers)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `type` | string | — | The marker discriminator (`playerStart`, `coin`, `chest`, `torch`, `dagger`, `exitGate`, `enemy`, `platform`). |
| `enemyType` | string | `"walker"` | Picks the enemy variant: `walker` / `flyer` / `shooter` / `knight`. See `enemies.md`. |
| `aiMode` | string | `"patrol"` | Enemy patrol behavior: `"side-to-side"` (or `"sidetoside"`, case-insensitive) → endless walking that turns only on walls/ledges/hazards; anything else/absent → origin-bounded `patrol`. Flyers ignore it (never grounded). See `enemies.md`. |
| `speed` | float | per-type default (`20`) | (enemy only) Horizontal patrol speed override, world px/s. Applied before the `unitScale` (tile-size) scaling. |
| `patrolRange` | float | per-type default (`64`) | (enemy only) Patrol-range override, world px. Only used in `PATROL` mode (`SIDE_TO_SIDE` ignores it). Applied before the `unitScale` scaling. |
| `nextLevel` | string | — | (exitGate only) The next `.tmx` path **relative to the `assets/` folder**, e.g. `maps/level1/level_1_demo_2.tmx`. Cycles to that map on interaction. |

### Moving-platform properties (the `platform` rectangle)

A platform oscillates around its spawn position with a sine wave:

```
pos = base + amplitude * sin(angle + phase)
angle += speed * dt        (each frame)
```

| Property | Type | Default | Meaning |
|---|---|---|---|
| `amplitudeX` | float | `0` | How far (world px) the platform travels **horizontally** away from its spawn, each side. Peak-to-peak travel is 2× this. |
| `amplitudeY` | float | `0` | Same, but **vertically** (up and down). |
| `speed` | float | `1` | Oscillation speed in **radians per second**. `2π` (≈6.28) = one full up-down cycle per second; `1` is slow; `π` (≈3.14) is a ~2s cycle. |
| `phase` | float | `0` | Starting angle offset in **radians**. Use different phases on several platforms to desync them. |
| `axis` | string | — | Convenience shortcut: `"x"` zeroes the vertical axis, `"y"` zeroes the horizontal. If you set `amplitudeX`/`amplitudeY` explicitly, those win. |

**Examples** (from `assets/maps/level1/level_1_demo.tmx`):

*   Horizontal shuttle: rectangle `type="platform"` + `amplitudeX=80` + `speed=1` → slides 80px left/right of spawn.
*   Vertical lift: `amplitudeY=80` + `speed=1` → bobs 80px up/down.

**Gotchas:**

*   A platform with no `amplitude`/`axis` is a static block (still lands on it, still collides).
*   The platform only **moves** while its owning room is the active one (`RoomState.activeRoomIndex`); it freezes when you're in another room. The player can still stand on a frozen platform.
*   Place the platform rectangle *inside* a `Rooms` rectangle so it's tied to that room.

### Tile properties (collision / tilesets)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `solid` | boolean | `true` | Set `false` on a tileset tile to make that tile **non-blocking** even in the `collision` layer (the passage-doorway use case, §3.1). |
| `oneWay` | boolean | `false` | Set `true` on a tileset tile to make it a **drop-through platform**: player-only, top-only solid; jump up through; drop with the `v` button / `S` / `DOWN` (see §3.3). Ignored when the tile is `hazard`. |
| `hazard` | boolean | `false` | Set `true` on a tileset tile to make it a **non-solid hazard** (spikes/lava): 1 HP on touch, no knockback, invulnerability grace (see §3.2). Wins over `solid`/`oneWay`. |
| `type` | string | — | On tileset tiles: lets you paint tile objects that auto-spawn as markers (`coin`, `chest`, `enemy`…). |

### Room properties (the `Rooms` layer)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `camera` | string | `auto` | Camera mode for that room: `flip` (always static, center-framed), `scroll` (always dead-zone scroll), or omit for `auto` (infer from size — see §6). |

---

## 6. The `Rooms` layer & camera

Draw one **rectangle per room** in the `Rooms` object layer (a `RectangleMapObject`; plain shape, no special type needed). `MapLoader.getRooms()` turns each into a `Room`, and `CameraSystem` uses whichever room currently contains the player for both **camera framing** and **enemy/platform activation**.

*   **No `Rooms` layer at all** → the game falls back to a single room covering the whole map (the whole map scrolls like one big room). Useful for one-screen test maps, but you lose per-room camera/enemy control.
*   A `Rooms` layer with no rectangles → no rooms at all (empty), which disables framing; keep at least one rectangle.

### Camera behavior (per axis)

For each axis (X and Y) independently:

*   **Flip axis** (room size ≤ viewport size on that axis, or forced via `camera="flip"`): camera locks to the room's **center** and **snaps instantly** when the player enters. Classic Castlevania-style screen change. The viewport may overshoot a room smaller than the screen.
*   **Scroll axis** (room size > viewport size on that axis, or forced via `camera="scroll"`): dead-zone scrolling. The camera holds still while the player roams inside a margin from each screen edge (`GameConstants.CAMERA_SCROLL_MARGIN`), then scrolls only when the player crosses that margin, clamped so the screen never leaves the room.
*   `camera="flip"` forces static framing even for big rooms; `camera="scroll"` forces scrolling, but a room still smaller than the viewport on an axis always centers (you can't scroll what's smaller than the screen).
*   **No smooth follow / no lerp.** Transitions are instant snaps. On level start the camera frames the starting room via `CameraSystem.snapToRoom(...)` (flip rooms center, scroll rooms put the player in view) — not the player.

### Design tips

*   Screen-sized rooms: make a room **exactly 30×17 tiles** (3840×2176 px at 128px tiles) → pure flip-screen, no scrolling.
*   Rooms **wider/taller than 30×17 tiles** → dead-zone scroll; give the player room to roam.
*   Small rooms (smaller than the screen) are fine — the camera centers on them and shows a little of the next area.
*   Every `Rooms` rectangle should **contain the `playerStart` marker** (and each enemy/platform's spawn), or that entity won't be tied to any room (`roomIndex = -1`, always active).

---

## 7. Step-by-step: build a new level

1.  **Map setup.** In Tiled: New map, orthogonal, tile size **128×128** (or matching your chain), infinite off, CSV tile format. Add tilesets from `assets/maps/level1/`: `cave_tileset.tsx` for terrain, `drop_platform.tsx` for drop-through platforms, `hazards.tsx` for spikes/lava, `items.tsx` for pickups, `enemy.tsx` for enemies.
2.  **`background` layer.** Paint the decorative backdrop (walls, pillars, windows). Anything goes — it never blocks.
3.  **`collision` layer.** Paint the solid geometry: floor, walls, platforms. Leave gaps where you want jumps. Use `drop_platform.tsx` tiles for ledges you want to drop through and `hazards.tsx` tiles for spike/lava damage zones (their behavior is baked into the tileset tile properties — see §3.2, §3.3). For any room-to-room doorway, use your `solid = false` passage tile (see §3.1). Keep the map's outer border solid.
4.  **`decoration` layer** (optional). Foreground decor that draws on top.
5.  **`objects` layer.**
    *   Draw one `playerStart` rectangle where the player should spawn.
    *   Scatter `coin` / `chest` / `dagger` / `torch` markers (either as rectangles with the right `type`, or stamp the pre-typed tiles from `items.tsx`).
    *   Add moving platforms as **rectangle objects** with `type="platform"` and the §5 properties.
    *   Add the `exitGate` at the far end with `nextLevel = maps/<path>/<next>.tmx` (or leave it decorative for a final level). The object rectangle sizes the trigger zone; the gate itself draws no sprite, so paint the door's decoration into the map layers (see §4).
6.  **`enemies` layer.** Place `enemy` markers (stamp from `enemy.tsx` or draw rectangles + `enemyType`). Refer to `enemies.md` for behavior tuning (flyers need patrol range clear of walls, shooters only fire in their own room).
7.  **`Rooms` layer.** Draw one rectangle per room (see §6). Add `camera="flip"`/`"scroll"` only when you want to override the size-based default.
8.  **Register the level.** Add `LEVELS.add(new LevelDefinition("my_key", "My Level Name", "maps/my_level.tmx"))` in `core/.../map/LevelCatalog.java`.
9.  **Test.** Run the desktop build. Turn on collision debug (SHIFT+D, or the Pause dialog) to verify the collision AABBs and room rects look right.

---

## 8. Common pitfalls (checklist when something feels wrong)

*   **Layer name typo** — layers are matched by exact name (`collision`, `objects`, `enemies`, `Rooms`, `background`, `decoration`). A typo = silently ignored layer.
*   **Doorway is an invisible wall** — the passage tile doesn't have `solid = false`. Fix it on the tileset tile (this affects all maps using that tileset).
*   **Wrong `type` spelling** — `playerStart`, `coin`, `chest`, `torch`, `dagger`, `exitGate`, `enemy`, `platform` are exact. Anything else spawns nothing.
*   **Platform never moves** — it's outside every `Rooms` rectangle and/or has no `amplitudeX/Y` and no `axis`. Give it an amplitude and place it inside a room.
*   **Platform sprite is huge/stretched** — the platform's collision box and sprite are its rectangle size; keep the rectangle near the tile/asset's aspect ratio.
*   **Exit gate does nothing** — `nextLevel` is missing (or the path is wrong). Paths are relative to `assets/`.
*   **Enemy behaves oddly** — check `enemyType` spelling; flyers will fly into walls if `patrolRange` isn't clear of walls (they have no automatic wall avoidance — see `enemies.md`).
*   **Camera snaps wrong room / player not in view** — `playerStart` isn't inside the intended `Rooms` rectangle, or the rooms don't tile the map.
*   **Unexpectedly scrolling when you wanted flip** — the room is larger than the screen (30×17 tiles); add `camera="flip"` to force static framing.
*   **Spikes/lava never hurt a standing player** — the player's collision box is taller than a tile and its feet sit on the floor, so hazards must be placed where the player's body travels *through* them (see §3.2).
*   **Drop platform behaves like a wall** — the tile doesn't carry `oneWay = true` on the tileset tile; or the platform's collision was authored as a plain solid tile. Also remember one-way platforms are player-only (enemies fall straight through).

---

## 9. Related tooling & docs

*   `.opencode/skills/tmx-map-generator` — generates a standalone prototype `.tmx` (a linear chain of whole-screen 30×17-tile rooms with enemies/items and walk-through doorways) so you can test a layout without hand-tracing collision CSVs. Run `generate_tmx.py` with `--rooms N --seed S --out assets/maps/….tmx`; see the skill's SKILL.md for CLI + conventions. It reads the collision tileset from `gfx/dungeon_tiles.tsx` and the item/enemy tilesets from `level1/`; output is standalone (no `LevelCatalog`/exit-gate wiring by design — the exit gate is added by hand).
*   `resources/docs-ai/enemies.md` — enemy catalog, stats, and how to add new types.
*   `resources/docs-ai/ashley-ecs.md` — the `MapLoader`/`EntityFactory`/`Room`/`RoomState`/`CameraSystem` code shape and priorities.
*   `resources/docs-ai/gameplay.md` — movement/combat mechanics and how they read map data.
