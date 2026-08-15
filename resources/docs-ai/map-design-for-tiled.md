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
| `secret_hide` | Tile layer | Rock veil painted over a secret room's footprint to hide its existence until the secret wall is broken (see §3.5). Must render above every other tile layer. | optional |

Only **tile** layers render visually; object layers are never drawn, they only spawn entities. The exit gate is the one exception worth knowing: its `exitGate` object spawns a **logic-only trigger** (no sprite — the gate's decoration is painted by you in the `background`/`decoration`/`collision` layers), so a gate needs real map art to be visible.

---

## 3. The `collision` layer — the tile language

The collision layer is a tile grid. `MapLoader.buildCollisionRects()` walks every cell and classifies it:

*   **Empty cell** (`0`) → free space (a platforming gap).
*   **Painted tile** → one of the kinds below, decided by that tile's boolean custom properties (see the full table in §5). Precedence, first match wins:
    1.  `hazard = true` → **non-solid hazard** AABB (spikes/lava; damages the player on touch — see §3.2).
    2.  `solid = false` → free space (passage doorways).
    3.  `oneWay = true` → **drop-through platform** AABB (player top-only; fully solid for enemies/popped items; flyers pass through — see §3.3).
    4.  anything else → **solid wall** (blocks everything).
    5.  A solid wall tile **additionally** marked `secret = true` is a **breakable secret wall** — solid exactly like a regular wall until the player melee-strikes it (see §3.4).

### §3.1 The passage rule

Room-to-room doorways must be a tile marked **non-solid**. In Tiled:

1.  Open the tileset file (e.g. `assets/maps/tileset/dungeon_tiles.tsx`).
2.  Select the doorway tile you use for passages.
3.  Add a **boolean custom property `solid` set to `false`** on that tile.

That single tileset edit makes every map using the tile treat it as a walk-through doorway. (This is the only way to connect two rooms — the walls between rooms are otherwise solid.) If you forget it, the doorway is an invisible wall and the player can't progress.

### §3.2 Hazards (spikes, lava)

A **non-solid** tile that damages the player on contact. Paint it in the `collision` layer; the tile's `hazard = true` property (set on the tileset tile, e.g. `assets/maps/tileset/hazards.tsx`) turns it into a damage zone instead of a wall:

*   On AABB overlap the player loses **1 HP**, gets the usual 0.3s hit-stun + 2s invulnerability grace, and is **not** knocked back (no directional push).
*   Hazards are fully non-solid — nothing (player, enemies, bullets) is blocked by them. The grace period turns a sustained overlap into one hit per second, not instant shredding.
*   **Smaller damage zone:** by default the danger box is the full tile. To shrink it, open the hazard tile in Tiled's **Tile Collision Editor** and draw a shape (rectangle or polygon) around the actual spikes/lava art — `MapLoader` then emits one world-space hazard box per shape instead of the full tile (a tile with no shapes, or a flipped cell, still gets the full tile). Verify with the SHIFT+D hazard overlay (red).
*   **Placement gotcha:** the player's collision box is *smaller than a tile* (30×40 px vs a 128×128 tile — the design model is a 1×1-tile box, see `gameplay.md` §2.A) and its feet sit on the floor surface, so a hazard painted in the row directly on top of a floor tile sits at/below the player's feet and a standing player won't overlap it. Hazards damage reliably when the player's body travels **through** them (jump over a spike barrier, fall into a lava pool, walk a spike row you must jump). Put solid ground under a pit of lava/spikes so a falling player lands on it.

### §3.3 Drop-through platforms (one-way)

A **drop-through platform for the player, a normal solid tile for everyone else**. Paint it in the `collision` layer; the tile's `oneWay = true` property (set on the tileset tile, e.g. `assets/maps/tileset/drop_platform.tsx`) makes it a platform instead of a wall:

*   The player can **land on its top** (it sticks only when the player's feet were at/above the platform's top before the move) and can **jump up through** it from below.
*   While standing on it, the contextual **`v` button** appears (mobile) or **`S`/`DOWN`** (keyboard) starts a short ~0.25s pass-through window: the player drops straight through the platform, then normal gravity takes over.
*   **Enemies and popped items treat it as a fully-solid tile (all four sides)** — they land on top, are blocked by its sides/underside, and ground enemies patrol along it exactly like a regular floor. **Flying enemies pass through it entirely.**
*   Player bullets still fly through (they're excluded from `MovementSystem` entirely, and `CollisionSystem` only resolves them against `collisionRects`).

### §3.4 Secret walls (breakable)

A **solid** wall tile that opens a doorway when the player melee-strikes it. Paint it in the `collision` layer; the tile's `secret = true` property (set on the tileset tile, e.g. `assets/maps/tileset/secret_wall.tsx`) marks it as breakable:

*   The tile is **fully solid** (blocks the player, enemies, and bullets exactly like a regular wall) — breaking it is what opens the route. It is *not* a passage: an unbroken secret wall has no effect on movement until struck.
*   **One melee swing breaks at most one** secret tile whose rect overlaps the strike hitbox (reach-dependent; see `gameplay.md` §2.Z). On break, the tile's sprite disappears (its collision-layer cell is blanked), its rect is removed from the collision set, a smoke puff spawns at the tile center, and a wall-break SFX plays — the doorway is walkable the very next frame.
*   **Design the room behind it:** carve the alcove/room into the `collision` layer (solid walls on the other sides), drop a `Rooms` rectangle over it (put it *before* the enclosing room in the layer — see §6), and place pickups/enemies inside via the `objects`/`enemies` layers. Two proven shapes, both produced by `generate_tmx.py`:
    *   **Appended full-screen room:** see the `secret_room` example in `assets/maps/world1/level_05.tmx` — the full 30-tile room right of room 2 (cols 90–119); its west boundary wall at col 89 has the two body-height rows 14–15 replaced with the breakable `secret_wall` tile the player strikes from the last normal room.
    *   **Chamber carved inside a room (`--inside-secret`):** see `assets/maps/world1/level_08.tmx` — a 6×8-tile box flush against the room's left wall and sitting on the floor. Its **front** (right) wall carries the breakable guard on the two passage rows (rows 14–15, col 5); the roof/floor/left wall are the room's own. The chamber is hollow so the player can stand inside, and the `secret_hide` veil covers its footprint **except** the guard cells, so the crack stays visible and strikeable before reveal. Its `Rooms` rect is emitted before the enclosing room so the camera flips onto the chamber while the player is inside.
*   **Verify with SHIFT+D:** an unbroken secret wall shows as a normal yellow solid rect (it lives in the shared `collisionRects` set); after breaking, the rect is gone and the cell is empty.

### §3.5 Secret rooms (hidden until the wall breaks)

A secret room is **invisible until revealed** — the player must find and strike its hidden wall to see it at all. Three parts:

*   **`secret_hide` tile layer:** paint rock tiles (visually identical to the surrounding walls) over the entire secret-room footprint, in the `secret_hide` layer (top of the layer stack, so it covers the room). The veil is **purely visual** — it has no collision (never add it to the `collision` layer). On reveal, every veil cell over the room is blanked.
*   **The secret wall carries the room name:** the breakable `secret=true` wall tiles declare an extra `secretRoom = "<room name>"` property naming the `Rooms` rectangle they protect. Because Tiled can't attach properties to raw cells, `generate_tmx.py` clones the base `secret_wall.tsx` tile once per map into an inline tileset (e.g. `secret_room_wall`) whose tile carries both `secret=true` and `secretRoom="secret_room"`. (Hand-authored maps: just add `secretRoom` to your tileset tile.) The room name is read from the **tile's own property** — cell-level properties are lost the moment the cell's tile is blanked.
*   **Deferred markers:** the room's loot/enemy objects carry a `secretRoom = "<room name>"` **object** property too. `MapLoader.getSecretRooms()` partitions those markers **out** of the normal `objects`/`enemies` spawn layers into the room's deferred set, so nothing spawns until reveal. On reveal (`SecretRoomRevealer.reveal(roomName)` — triggered when the `secretRoom`-tagged wall breaks), the veil cells blank, the deferred markers spawn exactly once (idempotent per room), and a smoke puff appears at each veil cell. Collision is unaffected throughout: it stays solely in the `collision` layer (single source of truth).

### Perimeter / layout rules of thumb

*   Every room needs solid **floor**, **ceiling**, **left wall**, **right wall** — except for the passage tile(s) on each room boundary.
*   The outer edges of the whole map should be solid — there's nothing beyond them.
*   Align passages: the doorway on one side of a room boundary must line up with the doorway on the neighboring room's matching side, or the connection doesn't actually link. Keep all rooms on a **shared floor baseline** so doorways at the same height line up.

### §3.6 Vertical room links (platform shafts)

Rooms stacked vertically (a grid map, e.g. `world2`) connect through a **platform shaft** instead of a doorway — the engine has no ladders/climb, so the climb is made of drop-through platforms. A shaft is a 2-column channel at interior columns **`col_start+2..col_start+3`** (relative to the room's left wall) carved through both rooms' shared boundary:

*   **Lower room:** the two shaft columns are hollow from the ceiling down to one row above the floor; the floor row itself stays solid. A `oneWay` platform sits on every other row above the floor (for a 24×10 room: platforms at rows `floor−2, −4, −6, −8`). The player hops platform to platform, then jumps through the open ceiling.
*   **Upper room:** the matching two floor cells at the shaft columns are hollow — a **hatch** — so the player pops up into the room. The same hatch is the hole they fall back down through; the floor around it stays solid.
*   **Spacing:** 2-row platform steps fit the player's single-jump envelope (2 up / 4 across — see `gameplay.md` §2.A); 2 columns wide makes the shaft read as a visible vertical gap. Keep markers (enemies/items) out of the shaft columns.
*   `generate_tmx.py` builds shafts automatically for grid layouts (`--grid-cols`/`--grid-rows`, see §9); hand-authored maps carve the same shape straight into the `collision` layer — hollow the two columns, place `oneWay` tiles on alternating rows, and open the two hatch cells in the upper room's floor.

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
| `coin` | Coin pickup | — | Collectible objects are **never** drawn from the Tiled tile sprite — the spawned entity always renders its own `Coin_01..06` atlas spin animation, regardless of the marker source. So you can place coins as plain rectangles OR stamp any `items.tsx` coin tile (static or animated); the on-screen result is identical. Every coin renders at **half a map tile** (`DEFAULT_COIN_SIZE * unitScale`, i.e. 64px on a 128px-tile map) **centered on the marker rect — the marker's drawn size is ignored** (it's a pure placement guide), so map coins and chest/enemy-dropped coins are always the same size. |
| `chest` | Chest (opens on touch, drops coins) | — | Built from the `gfx/origin-game.atlas` `Chest_01_Locked`/`Chest_01_Unlocked` regions (128x128 → one map tile), never from a Tiled tile sprite. Any `items.tsx` chest tile just marks the spot. |
| `torch` | Decorative torch | — | Flickers visually. |
| `dagger` | Dagger pickup | — | Collectible item. |
| `exitGate` | Exit gate / level transition (trigger only, no sprite) | `nextLevel` (string, required to make it functional) | The gate spawns a **logic-only** entity: a collision box (sized from the object rectangle) + optional level transition. **No gate art is drawn** — paint the door's decoration yourself in the `background`/`decoration`/`collision` layers. With `nextLevel` it's a real transition; without it, purely decorative (e.g. a final-level dead end). |
| `enemy` | Enemy | `enemyType` (string, default `"walker"`), `aiMode` (string), `speed` (float), `patrolRange` (float) | Put these on the `enemies` layer (or `objects`). Catalog: `walker` (goblin), `flyer` (mosquito), `shooter` (spider), `knight` (15 HP). See `resources/docs-ai/enemies.md`. |
| `platform` | Moving platform | `amplitudeX`, `amplitudeY`, `speed`, `phase`, `axis` (see §5) | The object **rectangle size defines both the sprite and collision box**. |
| (any other) | — | — | Ignored. `background`-type markers etc. won't spawn anything. |

### World 1 — level content inventory

The shipped `maps/world1/level_01..10.tmx` chain. Enemy counts mix `enemyType`s (e.g. `3 enemies (2 walkers, 1 shooter)`); difficulty ramps from the tutorial-sized 16×12 openings (levels 01/02/10) up to the hardest, the loop-back final level 10 (which includes the only `knight` on any map). Content scoping baseline is roughly **2–3 enemies + a chest + 2–4 coins + torches per room**, matching the `generate_tmx.py` density; any future content edits should keep this table accurate.

| Level | Rooms | Enemies | Chests | Coins | Daggers | Torches | Platforms |
|---|---|---|---|---|---|---|---|
| `level_01` | 1 (16×12) | 3 (2 walkers, 1 shooter) | 1 | 3 | — | 3 | 2 |
| `level_02` | 1 (16×12) | 3 (2 walkers, 1 shooter) | 1 | 4 | — | 3 | 2 |
| `level_03` | 2 (32×17) | 5 walkers | 1 | 1 | 1 | 3 | — |
| `level_04` | 1 whole-map (32×17) | 3 (2 walkers, 1 shooter) | 6 | 13 | — | 2 | — |
| `level_05` | 3 + secret (120×17) | 6 (4 walkers, 2 flyers) | 3 (+1 secret) | 1 (+2 secret) | — | — | — |
| `level_06` | 1 (30×17) | 6 (5 walkers, 1 flyer) | 1 | 4 | — | 2 | — |
| `level_07` | 1 (30×17) | 3 (2 walkers, 1 flyer) | 1 | 4 | — | 1 | — |
| `level_08` | 1 + secret chamber (30×17) | 2 shooters | 2 (+1 secret) | 1 (+3 secret) | — | — | 13 |
| `level_09` | 2 + secret alcove (32×17) | 5 (4 walkers, 1 flyer) | 1 (+1 secret) | 3 (+2 secret) | — | 4 | — |
| `level_10` | 1 (16×12) | 4 (1 walker, 2 shooters, 1 knight) | 1 | 3 | — | 2 | 2 |

The secret-room counts (marked *secret*) are deferred markers carrying `secretRoom="secret_room"` (§3.5). Levels 01/02/10 place their enemies in the `objects` layer (they have no separate `enemies` layer). Level 08's 13 "platforms" are **static `collision`-layer blocks** (11 solid gid-2 groups + 2 one-way gid-1 tiles) forming a jump/staircase course from the spawn up to the secret-room roof — see the `collision` layer of `assets/maps/world1/level_08.tmx`.

### World 2 — level content inventory (generated)

All of `maps/world2/level_01..10.tmx` are **generated** by `.junie/skills/tmx-map-generator/scripts/generate_tmx.py` (mobile-oriented 24×10-tile rooms, dead-zone scroll on phones, flip on desktop). Each level is a fully-connected grid (every adjacent room pair has a doorway or platform shaft), `playerStart` sits in the bottom-left room, and the `exitGate` (`--exit-next`) stands in the **top-right** room, chaining `level_N → level_N+1` with `level_10` looping back to `level_01` — matching the World 1 convention. Content per level is the generator's random scatter (0–2 enemies + 0–3 items per room, deterministic per `--seed`). Every map also paints a `type="door"` tile (dungeon `door.png`, from `tileset/dungeon_tiles.tsx`) on the `decoration` layer on the row **just above the floor** (its bottom edge resting on the `collision` floor surface) beneath the `playerStart` and beneath the `exitGate` (the gate's column, `col_end-2`), so both doors stand on the floor instead of looking like they float or sink into it; the tile image is 2 tiles tall and renders upward from its single cell.

| Level | Map (tiles) | Rooms grid | Exit → |
|---|---|---|---|
| `level_01` | 48×20 | 2×2 | `level_02` |
| `level_02` | 72×10 | 3×1 | `level_03` |
| `level_03` | 72×20 | 3×2 | `level_04` |
| `level_04` | 48×20 | 2×2 | `level_05` |
| `level_05` | 120×20 | 5×2 | `level_06` |
| `level_06` | 96×40 | 4×4 | `level_07` |
| `level_07` | 72×10 | 3×1 | `level_08` |
| `level_08` | 72×20 | 3×2 | `level_09` |
| `level_09` | 120×20 | 5×2 | `level_10` |
| `level_10` | 96×40 | 4×4 | `level_01` (loop) |

Regenerating any of these with the generator (seeds `1001`–`1010` respectively) reproduces them byte-identically; changing a seed changes only the enemy/item scatter, not the room grid or exit wiring.

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
| `nextLevel` | string | — | (exitGate only) The next `.tmx` path **relative to the `assets/` folder**, e.g. `maps/world1/level_03.tmx`. Cycles to that map on interaction. |

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

**Examples** (from `assets/maps/world1/level_01.tmx`):

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
| `oneWay` | boolean | `false` | Set `true` on a tileset tile to make it a **drop-through platform**: the player gets top-only solidity (jump up through; drop with the `v` button / `S` / `DOWN`), while enemies and popped items treat it as a fully-solid tile (all four sides) and flying enemies pass through (see §3.3). Ignored when the tile is `hazard`. |
| `hazard` | boolean | `false` | Set `true` on a tileset tile to make it a **non-solid hazard** (spikes/lava): 1 HP on touch, no knockback, invulnerability grace (see §3.2). Wins over `solid`/`oneWay`. |
| `secret` | boolean | `false` | Set `true` on a **solid** tileset tile to make it a **breakable secret wall**: solid until the player melee-strikes it, then it disappears and opens the way (see §3.4). Ignored on `hazard` and `solid = false` tiles. |
| `secretRoom` | string | — | On a **secret wall tile**: names the `Rooms` rectangle (matched by **name**) that this wall protects, turning the plain secret wall into a **hidden secret room** entry — breaking the wall reveals the whole room (§3.5). On a **map object marker** (`objects`/`enemies` layers): defers that marker — it is partitioned out of the normal spawn layers and only spawned when its named room is revealed (§3.5). |
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

For each axis (X and Y) independently (the "viewport size" is always the **effective** size — `camera.viewportWidth/Height × camera.zoom`):

*   **Flip axis** (room size ≤ effective viewport size on that axis, or forced via `camera="flip"`): camera locks to the room's **center** and **snaps instantly** when the player enters. Classic Castlevania-style screen change. The viewport may overshoot a room smaller than the screen.
*   **Scroll axis** (room size > effective viewport size on that axis, or forced via `camera="scroll"`): dead-zone scrolling. The camera holds still while the player roams inside a margin from each screen edge — `GameConstants.CAMERA_SCROLL_MARGIN` at zoom 1, or 30% of the effective (zoomed) view per axis once the camera zooms in (`GameConstants.MOBILE_SCROLL_MARGIN_FRACTION`) — then scrolls only when the player crosses that margin, clamped so the screen never leaves the room.
*   `camera="flip"` forces static framing even for big rooms; `camera="scroll"` forces scrolling, but a room still smaller than the viewport on an axis always centers (you can't scroll what's smaller than the screen).
*   **No smooth follow / no lerp.** Transitions are instant snaps. On level start the camera frames the starting room via `CameraSystem.snapToRoom(...)` (flip rooms center, scroll rooms put the player in view) — not the player.

> **Mobile note:** On phones, the game's touch layout can zoom the camera in (`LayoutMode.BAND_ZOOM`, toggled from the Pause dialog). A zoomed camera shows a smaller effective frame, so an otherwise screen-sized 30×17 room becomes *bigger than the frame* and flips to dead-zone scroll on mobile — flip-screen framing is preserved on desktop/tablet. The zoomed dead-zone margin is a 30% fraction of the visible view (`MOBILE_SCROLL_MARGIN_FRACTION`) so the camera starts tracking well before the player reaches a screen edge. This needs **no map change**: rooms stay as authored, the camera just follows on phones. See `com.axehigh.platformer.ui.LayoutMode`.

### Design tips

*   Screen-sized rooms: make a room **exactly 30×17 tiles** (3840×2176 px at 128px tiles) → pure flip-screen, no scrolling.
*   Rooms **wider/taller than 30×17 tiles** → dead-zone scroll; give the player room to roam.
*   Small rooms (smaller than the screen) are fine — the camera centers on them and shows a little of the next area.
*   Every `Rooms` rectangle should **contain the `playerStart` marker** (and each enemy/platform's spawn), or that entity won't be tied to any room (`roomIndex = -1`, always active).
*   **Secret rooms:** carve a small room into the `collision` layer, drop a `Rooms` rectangle over it, and place pickups/enemies inside. `RoomState.findRoomIndexContaining(...)` returns the **first** room rectangle containing a point, so put the secret room **above** (before) the enclosing room in the layer for it to win the camera framing (a chamber carved *inside* a room is emitted first for exactly this reason). To make it *hidden until broken* (see §3.5): name the rect (e.g. `secret_room`), paint a `secret_hide` veil over its footprint (leave the guard cells un-veiled for an inside chamber, so the crack is visible), tag the entry-wall tile with `secretRoom="secret_room"`, and tag the interior markers with the same `secretRoom` object property so they spawn on reveal (see `secret_room` in `assets/maps/world1/level_05.tmx` and the inside chamber in `assets/maps/world1/level_08.tmx`).

---

## 7. Step-by-step: build a new level

1.  **Map setup.** In Tiled: New map, orthogonal, tile size **128×128** (or matching your chain), infinite off, CSV tile format. Add tilesets from `assets/maps/tileset/`: `dungeon_tiles.tsx` for terrain (solid walls, one-way platforms, and spike hazards are all tiles with baked-in `oneWay`/`hazard` properties), `items.tsx` for pickups, `enemy.tsx` for enemies, `secret_wall.tsx` for breakable secret walls.
2.  **`background` layer.** Paint the decorative backdrop (walls, pillars, windows). Anything goes — it never blocks.
3.  **`collision` layer.** Paint the solid geometry: floor, walls, platforms. Leave gaps where you want jumps. Use `drop_platform.tsx` tiles for ledges you want to drop through, `hazards.tsx` tiles for spike/lava damage zones (their behavior is baked into the tileset tile properties — see §3.2, §3.3), and `secret_wall.tsx` tiles for breakable secret walls (§3.4). For any room-to-room doorway, use your `solid = false` passage tile (see §3.1). Keep the map's outer border solid. For a hidden secret room: seal the room with solid tiles, and paint its breakable entry wall from a tileset tile carrying `secret=true` + `secretRoom="<rect name>"` (§3.5).
4.  **`decoration` layer** (optional). Foreground decor that draws on top.
5.  **`objects` layer.**
    *   Draw one `playerStart` rectangle where the player should spawn.
    *   Scatter `coin` / `chest` / `dagger` / `torch` markers (either as rectangles with the right `type`, or stamp the pre-typed tiles from `items.tsx`).
    *   Add moving platforms as **rectangle objects** with `type="platform"` and the §5 properties.
    *   Add the `exitGate` at the far end with `nextLevel = maps/<path>/<next>.tmx` (or leave it decorative for a final level). The object rectangle sizes the trigger zone; the gate itself draws no sprite, so paint the door's decoration into the map layers (see §4).
6.  **`enemies` layer.** Place `enemy` markers (stamp from `enemy.tsx` or draw rectangles + `enemyType`). Refer to `enemies.md` for behavior tuning (flyers need patrol range clear of walls, shooters only fire in their own room).
7.  **`Rooms` layer.** Draw one rectangle per room (see §6). Add `camera="flip"`/`"scroll"` only when you want to override the size-based default. **Name a secret room's rectangle** (e.g. `secret_room`) and match that name in the entry wall's `secretRoom` property and the interior markers' `secretRoom` property (§3.5).
8.  **`secret_hide` layer** (only for hidden secret rooms). Above every other tile layer, paint rock tiles over the whole secret-room footprint so it looks like solid wall from outside (§3.5). No collision — this layer is purely visual.
9.  **Register the level.** Add `LEVELS.add(new LevelDefinition("my_key", "My Level Name", "maps/my_level.tmx"))` in `core/.../map/LevelCatalog.java`.
10. **Test.** Run the desktop build. Turn on collision debug (SHIFT+D, or the Pause dialog) to verify the collision AABBs and room rects look right.

---

## 8. Common pitfalls (checklist when something feels wrong)

*   **Layer name typo** — layers are matched by exact name (`collision`, `objects`, `enemies`, `Rooms`, `background`, `decoration`, `secret_hide`). A typo = silently ignored layer.
*   **Doorway is an invisible wall** — the passage tile doesn't have `solid = false`. Fix it on the tileset tile (this affects all maps using that tileset).
*   **Wrong `type` spelling** — `playerStart`, `coin`, `chest`, `torch`, `dagger`, `exitGate`, `enemy`, `platform` are exact. Anything else spawns nothing.
*   **Platform never moves** — it's outside every `Rooms` rectangle and/or has no `amplitudeX/Y` and no `axis`. Give it an amplitude and place it inside a room.
*   **Platform sprite is huge/stretched** — the platform's collision box and sprite are its rectangle size; keep the rectangle near the tile/asset's aspect ratio.
*   **Exit gate does nothing** — `nextLevel` is missing (or the path is wrong). Paths are relative to `assets/`.
*   **Enemy behaves oddly** — check `enemyType` spelling; flyers will fly into walls if `patrolRange` isn't clear of walls (they have no automatic wall avoidance — see `enemies.md`).
*   **Camera snaps wrong room / player not in view** — `playerStart` isn't inside the intended `Rooms` rectangle, or the rooms don't tile the map.
*   **Unexpectedly scrolling when you wanted flip** — the room is larger than the screen (30×17 tiles); add `camera="flip"` to force static framing.
*   **Spikes/lava never hurt a standing player** — the player's collision box is smaller than a tile and its feet sit on the floor, so hazards must be placed where the player's body travels *through* them (see §3.2).
*   **Drop platform behaves like a wall** — the tile doesn't carry `oneWay = true` on the tileset tile; or the platform's collision was authored as a plain solid tile. Also remember one-way platforms are drop-through **only for the player** — enemies and popped items stand on them like solid tiles, and flying enemies fly through them.
*   **Secret wall doesn't break** — the tile isn't flagged `secret = true` on the tileset tile (a plain solid tile won't break); also remember one swing breaks at most one tile, and the strike hitbox must actually reach the wall (it's a solid block until broken).
*   **Secret room spawns loot from the start / never reveals** — the interior markers don't carry the `secretRoom` object property (so they spawn normally instead of being deferred), or the entry wall's tile doesn't carry `secretRoom` naming the `Rooms` rect (so breaking it never triggers a reveal). Remember the wall's `secretRoom` is a **tile** property read from the tile itself — `cell.setTile(null)` wipes it, which is why the name can't live on the cell.
*   **Secret room is visible through the wall before reveal** — the `secret_hide` layer doesn't cover the full room footprint (or the room's `Rooms` rect is bigger than the painted veil). The veil must tile every cell over the rect, and it must sit at the top of the layer stack.

---

## 9. Related tooling & docs

*   `.opencode/skills/tmx-map-generator` — generates a standalone prototype `.tmx` (a linear chain of rooms with enemies/items and walk-through doorways, plus a hidden secret room — either appended to the right, or, with `--inside-secret`, carved as a 6×8-tile chamber inside the last room) so you can test a layout without hand-tracing collision CSVs. Rooms **default to 24×10 tiles** (mobile-oriented, dead-zone scroll under the `BAND_ZOOM` camera); pass `--room-width 30 --room-height 17` for whole-screen desktop rooms. `--platforms N` additionally decorates every room with a deterministic, always-jumpable staircase of N floating one-way platforms (2 rows up / 2 cols right per step — within the player's single-jump envelope of 2 up / 4 across — a `bg-*` filler tile behind each, and a coin on the top platform) while keeping the flat floor intact — see the skill's SKILL.md. **Templates:** `--template NAME[,ROOM[,COL]]` (repeatable) and `--template-pick N` stamp ASCII-art courses from `scripts/templates/*.tmpl` (e.g. `staircase`, `chasm-bridge`, `hazard-strip`) floor-anchored into rooms — the generator then runs jump-aware design checks (supported ground + reachability within the jump envelope, warnings only). **Grid layouts:** `--grid-cols C --grid-rows R` tiles `C×R` rooms over the map (width × height = `C×W × R×H`), links horizontal neighbours with doorways and vertical neighbours with one-way **platform shafts** (§3.6), and places `playerStart` in the bottom-left room; grid maps use `--no-secret` to omit the secret room (required for multi-row grids). Run `generate_tmx.py` (from `assets/maps/world1`) with `--rooms N --seed S --out level_05.tmx` or `--grid-cols 2 --grid-rows 2 --no-secret --seed S --out level_01.tmx`; see the skill's SKILL.md for CLI + conventions. It reads the collision tileset from `world1/dungeon_tiles.tsx`, the item/enemy tilesets from `world1/`, and clones `world1/secret_wall.tsx` inline for the secret room's entry wall; output is standalone (no `LevelCatalog`/exit-gate wiring by design — the exit gate is added by hand).
*   `resources/docs-ai/enemies.md` — enemy catalog, stats, and how to add new types.
*   `resources/docs-ai/ashley-ecs.md` — the `MapLoader`/`EntityFactory`/`Room`/`RoomState`/`CameraSystem` code shape and priorities.
*   `resources/docs-ai/gameplay.md` — movement/combat mechanics and how they read map data.
