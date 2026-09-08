# Map Design for Tiled — Level Authoring Guide

This document is the **single source of truth for level/map design**: how a `.tmx` map is read by the game, what layers and custom properties exist, what each one means, and a step-by-step recipe for building a playable level. It's written so you can put the game down for three months, come back, and still remember how to design a level without re-reading the source.

It complements — but does not replace — `resources/docs-ai/ashley-ecs.md` (ECS shape: `MapLoader`, `EntityFactory`, `Room`, `RoomState`) and `resources/docs-ai/gameplay.md`; the `resources/docs-ai/enemies.md` catalog covers enemy placement in detail. `resources/docs-ai/ashley-ecs.md` covers what the parsing classes do in code.

> **Maintenance rule:** Any change to map parsing — layer names, object marker types, custom properties, tileset tile properties, room/camera semantics — MUST update this file in the same change (see `AGENTS.md`). If the change alters `MapLoader`/`EntityFactory`/`Room`/`CameraSystem` shape, also update `ashley-ecs.md`; if it changes what a player sees, update `gameplay.md`.

---

## Table of Contents

1. [The Big Picture](#1-the-big-picture)
2. [Step-by-Step: Build a New Level](#2-step-by-step-build-a-new-level)
3. [Layers — What Goes Where](#3-layers--what-goes-where)
4. [Tile Behaviors — The Collision Layer Language](#4-tile-behaviors--the-collision-layer-language)
5. [Object Markers — Things You Can Place](#5-object-markers--things-you-can-place)
6. [Rooms & Camera](#6-rooms--camera)
7. [Effects & Lighting](#7-effects--lighting)
8. [Full Property Reference](#8-full-property-reference)
9. [Common Pitfalls](#9-common-pitfalls)
10. [Related Tooling & Docs](#10-related-tooling--docs)

---

## 1. The Big Picture

A level is a single `.tmx` file (Tiled 1.11+). At level start the game:

1. Loads the `.tmx` with `TmxMapLoader` (inside `MapLoader`).
2. Reads the **`collision` tile layer** → the static solid AABBs used for all physics (`collisionRects`).
3. Reads the **`Rooms` object layer** → the camera-framing/enemy-activation zones (`RoomState.rooms`).
4. Reads the **`objects`** and **`enemies`** object layers → `EntityFactory` spawns pickups, chests, torches, the exit gate, enemies, and moving platforms.
5. Reads the **`playerStart`** marker → where the player spawns.

Levels are chained together as **separate `.tmx` files**: the exit gate in one map points at the next map's path (see `nextLevel` below). Each playable level must also be registered in `core/.../map/LevelCatalog.java` (it's what the level-select screen reads).

### Units, Scale & Coordinates

*   **1 world unit == 1 pixel** as drawn in Tiled. The world's Y axis points **up** in-game; Tiled draws Y **down**. The loader flips object/tile Y coordinates automatically, so just design normally in Tiled — never hand-edit raw Y values in the `.tmx` XML.
*   The game was built for a base virtual resolution of `480×272` "16px-world-units", scaled by `unitScale = tileWidth / 16f` (`GameConstants.VIRTUAL_WIDTH/HEIGHT`). The world **height** is fixed at `VIRTUAL_HEIGHT × unitScale`, but the world **width expands with the screen's aspect ratio** (`OffsetFitViewport`) so wide screens show more world instead of black bars: floored at the classic `480/272` ratio and capped at `GameConstants.MAX_WORLD_ASPECT` (≈21:9) only for physically ultra-wide screens. **All current maps use 128×128 tiles**, so:
    *   `unitScale = 128 / 16 = 8`.
    *   One on-screen frame = at least `480 * 8 = 3840` world px tall-frame width × `VIRTUAL_HEIGHT * 8 = 2176` px tall = **30 tiles × 17 tiles minimum**, wider on wider screens (a 16:9 desktop shows ≈30.2 tiles; a banded phone in landscape shows proportionally more).
    *   Design rooms around whole screens (multiples of 30×17 tiles) for clean flip-screen framing; rooms narrower than the widest target screens will simply show a sliver of the neighbors, and the camera's per-room `camera="flip"` property can pin framing where that matters.
*   Mixing tile sizes across a level chain works (scale is recalculated per map), but keep every map in a chain the **same tile size** to avoid jarring resizes.
*   Object/room rectangles are placed and sized in **world px** (the same numbers you see in Tiled), not in tiles.

---

## 2. Step-by-Step: Build a New Level

This is the practical recipe. Follow it top to bottom.

### 1. Map Setup

In Tiled: New map, orthogonal, tile size **128×128** (or matching your chain), infinite off, CSV tile format. Add tilesets from `assets/maps/tileset/`:
- `dungeon_tiles.tsx` — terrain (solid walls, one-way platforms, and spike hazards are all tiles with baked-in `oneWay`/`hazard` properties)
- `items.tsx` — pickups, torches, effect lights
- `enemy.tsx` — enemies
- `secret_wall.tsx` — breakable secret walls
- `drop_platform.tsx` — drop-through platforms
- `hazards.tsx` — spikes, lava
- `crumble` tiles (inside `dungeon_tiles.tsx`) — collapsing platforms

### 2. Paint the `background` Layer

The decorative backdrop (brick walls, pillars, windows). Anything goes — it never blocks.

### 3. Paint the `collision` Layer

The solid geometry: floor, walls, platforms. This is where most of the gameplay lives.

- **Solid walls/floor:** Default tile behavior. Paint freely.
- **Gaps:** Leave cells empty for jumps.
- **Drop-through ledges:** Use `drop_platform.tsx` tiles (carry `oneWay = true`).
- **Spikes/lava:** Use `hazards.tsx` tiles (carry `hazard = true`). They damage on contact, are non-solid.
- **Crumbling platforms:** Use tiles with `crumble = true`. They shake and collapse under the player, then respawn.
- **Breakable secret walls:** Use `secret_wall.tsx` tiles (carry `secret = true`). Solid until the player melee-strikes them three times.
- **Room-to-room doorways:** Use a tile marked `solid = false` on the tileset (see §4.1). If you forget, the doorway is an invisible wall.
- **Secret rooms:** Seal the room with solid tiles; paint its breakable entry wall from a tileset tile carrying `secret=true` + `secretRoom="<rect name>"` (see §4.6).
- **Outer border:** Keep the map's outer border solid — there's nothing beyond.

### 4. Paint the `decoration` Layer (optional)

Foreground decor that draws on top of everything. Stamp tiles with `effect="light"` (e.g. the torch from `items.tsx`) to add flickering glow halos — the tile renders visually, the effect entity adds the light (see §7).

### 5. Place Objects on the `objects` Layer

- **`playerStart`:** Draw one rectangle where the player should spawn. Exactly one per map.
- **Coins, chests, daggers, torches:** Scatter markers (either as rectangles with the right `type`, or stamp the pre-typed tiles from `items.tsx`).
- **Moving platforms:** Rectangle objects with `type="platform"` and amplitude/speed properties (see §5.7).
- **Exit gate:** Place `exitGate` at the far end with `nextLevel = maps/<path>/<next>.tmx` (or leave it decorative for a final level). The object rectangle sizes the trigger zone; the gate draws no sprite, so paint the door's decoration into the map layers.
- **Traps:** Place `trap` markers for acid drops or flames. Use `type="trap"` with the relevant `trapType`/`direction`/timing properties (see §5.6).

### 6. Place Enemies on the `enemies` Layer

Place `enemy` markers (stamp from `enemy.tsx` or draw rectangles + `enemyType`). Refer to `enemies.md` for behavior tuning (flyers need patrol range clear of walls, shooters only fire in their own room).

### 7. Draw Room Boundaries on the `Rooms` Layer

Draw one rectangle per room (see §6). Add `camera="flip"`/`"scroll"` only when you want to override the size-based default. **Name a secret room's rectangle** (e.g. `secret_room`) and match that name in the entry wall's `secretRoom` property and the interior markers' `secretRoom` property.

### 8. Paint the `secret_hide` Layer (for hidden secret rooms only)

Above every other tile layer, paint rock tiles over the whole secret-room footprint so it looks like solid wall from outside (see §4.6). No collision — this layer is purely visual.

### 9. Register the Level

Add `LEVELS.add(new LevelDefinition("my_key", "My Level Name", "maps/my_level.tmx"))` in `core/.../map/LevelCatalog.java`.

### 10. Test

Run the desktop build. Turn on collision debug (SHIFT+D, or the Pause dialog) to verify the collision AABBs and room rects look right.

---

## 3. Layers — What Goes Where

Layer **names matter — they are read by string**. Get them exactly right or the game silently ignores them. Tiled draws tile layers in order; the game renders `background` first, then `collision` (yes, the solid tiles are visible), then `decoration` on top.

| Layer | Kind | Purpose | Required? |
|---|---|---|---|
| `background` | Tile layer | Decorative backdrop (brick, pillars, windows…). Never affects collision. | yes (can be empty) |
| `collision` | Tile layer | Solid/blocking geometry. Every painted tile is solid unless the tile opts out (see §4). | yes |
| `decoration` | Tile layer | Foreground decor drawn above the collision tiles. Never affects collision. | optional |
| `objects` | Object layer | Player start, pickups, chests, torches, exit gate, moving platforms (see §5). | yes |
| `enemies` | Object layer | Enemy markers (see §5). Separate layer keeps enemies easy to find. | optional |
| `Rooms` | Object layer | Plain rectangles defining camera zones (see §6). | optional (see §6) |
| `secret_hide` | Tile layer | Rock veil painted over a secret room's footprint to hide its existence until the secret wall is broken (see §4.6). Must render above every other tile layer. | optional |

Only **tile** layers render visually; object layers are never drawn, they only spawn entities. The exit gate is the one exception worth knowing: its `exitGate` object spawns a **logic-only trigger** (no sprite — the gate's decoration is painted by you in the `background`/`decoration`/`collision` layers), so a gate needs real map art to be visible.

---

## 4. Tile Behaviors — The Collision Layer Language

The collision layer is a tile grid. `MapLoader.buildCollisionRects()` walks every cell and classifies it based on the tile's boolean custom properties:

*   **Empty cell** (`0`) → free space (a platforming gap).
*   **Painted tile** → one of the kinds below, decided by that tile's properties. Precedence, first match wins:

| Priority | Property | Result |
|---|---|---|
| 1 | `hazard = true` | **Non-solid hazard** AABB (spikes/lava; damages the player on touch) |
| 2 | `solid = false` | **Free space** (passage doorways) |
| 3 | `crumble = true` | **Crumbling one-way platform** (top-only; shakes and collapses when stood on) |
| 4 | `oneWay = true` | **Drop-through platform** AABB (player top-only; solid for enemies/popped items) |
| 5 | anything else | **Solid wall** (blocks everything) |
| 6 | solid + `secret = true` | **Breakable secret wall** (solid until melee-striken) |

### 4.1 The Passage Rule (Room-to-Room Doorways)

Room-to-room doorways must be a tile marked **non-solid**. In Tiled:

1. Open the tileset file (e.g. `assets/maps/tileset/dungeon_tiles.tsx`).
2. Select the doorway tile you use for passages.
3. Add a **boolean custom property `solid` set to `false`** on that tile.

That single tileset edit makes every map using the tile treat it as a walk-through doorway. (This is the only way to connect two rooms — the walls between rooms are otherwise solid.) If you forget it, the doorway is an invisible wall and the player can't progress.

### 4.2 Hazards (Spikes, Lava)

A **non-solid** tile that damages the player on contact. Paint it in the `collision` layer; the tile's `hazard = true` property (set on the tileset tile, e.g. `assets/maps/tileset/hazards.tsx`) turns it into a damage zone instead of a wall.

- On AABB overlap the player loses **1 HP**, gets the usual 0.3s hit-stun + 2s invulnerability grace, and is **not** knocked back (no directional push).
- Hazards are fully non-solid — nothing (player, enemies, bullets) is blocked by them. The grace period turns a sustained overlap into one hit per second, not instant shredding.

**Shrinking the damage zone:** by default the danger box is the full tile. To shrink it, open the hazard tile in Tiled's **Tile Collision Editor** and draw a shape (rectangle or polygon) around the actual spikes/lava art — `MapLoader` then emits one world-space hazard box per shape instead of the full tile (a tile with no shapes, or a flipped cell, still gets the full tile). Verify with the SHIFT+D hazard overlay (red).

**Placement gotcha:** the player's collision box is *smaller than a tile* (30×40 px vs a 128×128 tile — the design model is a 1×1-tile box, see `gameplay.md` §2.A) and its feet sit on the floor surface, so a hazard painted in the row directly on top of a floor tile sits at/below the player's feet and a standing player won't overlap it. Hazards damage reliably when the player's body travels **through** them (jump over a spike barrier, fall into a lava pool, walk a spike row you must jump). Put solid ground under a pit of lava/spikes so a falling player lands on it.

**Available hazard tiles:**

| Tileset | Tile | Image | Damage shape | Notes |
|---|---|---|---|---|
| `hazards.tsx` tile 0 | spikes | `hazards/spikes.png` (128×128) | Full tile | Standard floor/ceiling spikes |
| `hazards.tsx` tile 1 | lava | `hazards/lava.png` (128×128) | Full tile | Lava pool |
| `dungeon_tiles.tsx` tile 32 | dungeon spikes | `dungeon/spikes.png` (128×128) | Narrow bottom (0,96,128,32) | Wall-mounted spikes — hitbox only the bottom strip |
| `dungeon_tiles.tsx` tile 50 | stalactite | `caves/bg-stalactite.png` (128×128) | Narrow top (0,0,128,32) | Hanging stalactite — hitbox only the top tip |

All four deal **1 HP**, no knockback, same grace period as every other damage source.

### 4.3 Drop-Through Platforms (One-Way)

A **drop-through platform for the player, a normal solid tile for everyone else**. Paint it in the `collision` layer; the tile's `oneWay = true` property (set on the tileset tile, e.g. `assets/maps/tileset/drop_platform.tsx`) makes it a platform instead of a wall.

- The player can **land on its top** (it sticks only when the player's feet were at/above the platform's top before the move) and can **jump up through** it from below.
- While standing on it, the contextual **`v` button** appears (mobile) or **`S`/`DOWN`** (keyboard) starts a short ~0.25s pass-through window: the player drops straight through the platform, then normal gravity takes over.
- **Enemies and popped items treat it as a fully-solid tile (all four sides)** — they land on top, are blocked by its sides/underside, and ground enemies patrol along it exactly like a regular floor. **Flying enemies pass through it entirely.**
- Player bullets still fly through (they're excluded from `MovementSystem` entirely, and `PlayerBulletSystem` only resolves them against `collisionRects`).

### 4.4 Crumbling Platforms

A **top-only platform that shakes and collapses under the player, then respawns**. Paint it in the `collision` layer; the tile's `crumble = true` property (set on the tileset tile) marks it as crumble (see `gameplay.md` §2.AE for the full mechanic).

- While `INTACT` the tile behaves exactly like a `oneWay` platform for the player (landable top, jump-up-through from below; its rect lives in the shared `oneWayRects` set), so the two are interchangeable to stand on — the difference is only that a crumble tile **arms** when the player stands on it.
- Standing on it while grounded (continuous contact past a short graze guard, not mid drop-through, past the post-respawn settle grace) **arms** it: the cell blanks and the tile visibly jitters for `CRUMBLE_SHAKE_DURATION` (0.5s) (redrawn with a random pixel offset by `CrumblingTileRenderSystem`), then **collapses** — its rect is removed and the collapse marks the spot with a smoke puff plus a gray stone-chip burst, and a standing player falls through the same frame (the system runs before `MovementSystem`).
- It **respawns** after `CRUMBLE_RESPAWN_DURATION` (2.5s) at the same spot — cell and rect restored, with a dust puff — but only once the hole is clear (no player or enemy standing in it), and a short settle grace prevents instant re-arm underfoot.
- **Player-triggered only:** enemies never arm a crumble tile, but one crumbled out from under an enemy drops the enemy into the pit.
- **Full-tile rect only:** crumble tiles are always one full-tile collision box — they can't carry Tile Collision Editor shapes (unlike `oneWay` tiles).

**Authoring rules of thumb:** paint crumble tiles with distinct **rotting/cracked platform art** so the behavior reads. Place them on **coin-route side paths** (short optional detours where grabbing the loot costs you the platform) and chase gauntlets — **never as the only route up a vertical shaft** (see §4.7). Avoid **flush-adjacent placement next to solid floor at the same height** — the player's overhanging collision box can graze-arm the tile unintentionally.

### 4.5 Secret Walls (Breakable)

A **solid** wall tile that opens a doorway when the player melee-strikes it. Paint it in the `collision` layer; the tile's `secret = true` property (set on the tileset tile, e.g. `assets/maps/tileset/secret_wall.tsx`) marks it as breakable.

- The tile is **fully solid** (blocks the player, enemies, and bullets exactly like a regular wall) — breaking it is what opens the route. It is *not* a passage: an unbroken secret wall has no effect on movement until struck.
- **One melee swing counts at most one hit** on a secret tile whose rect overlaps the strike hitbox (reach-dependent; see `gameplay.md` §2.Z), and a wall needs **three hits** (three swings) to break — the first two register a partial hit (a smoke puff at the tile), the third breaks it. On break, the tile's sprite disappears (its collision-layer cell is blanked), its rect is removed from the collision set, a smoke puff spawns at the tile center, and a wall-break SFX plays — the doorway is walkable the very next frame.
- **Verify with SHIFT+D:** an unbroken secret wall shows as a normal yellow solid rect (it lives in the shared `collisionRects` set); after breaking, the rect is gone and the cell is empty.

### 4.6 Secret Rooms (Hidden Until the Wall Breaks)

A secret room is **invisible until revealed** — the player must find and strike its hidden wall to see it at all. Three parts:

**Part 1: The `secret_hide` veil**
Paint rock tiles (visually identical to the surrounding walls) over the entire secret-room footprint, in the `secret_hide` layer (top of the layer stack, so it covers the room). The veil is **purely visual** — it has no collision (never add it to the `collision` layer). On reveal, every veil cell over the room is blanked.

**Part 2: The secret wall carries the room name**
The breakable `secret=true` wall tiles declare an extra `secretRoom = "<room name>"` property naming the `Rooms` rectangle they protect. Because Tiled can't attach properties to raw cells, `generate_tmx.py` clones the base `secret_wall.tsx` tile once per map into an inline tileset (e.g. `secret_room_wall`) whose tile carries both `secret=true` and `secretRoom="secret_room"`. (Hand-authored maps: just add `secretRoom` to your tileset tile.) The room name is read from the **tile's own property** — cell-level properties are lost the moment the cell's tile is blanked.

**Part 3: Deferred markers**
The room's loot/enemy objects carry a `secretRoom = "<room name>"` **object** property too. `MapLoader.getSecretRooms()` partitions those markers **out** of the normal `objects`/`enemies` spawn layers into the room's deferred set, so nothing spawns until reveal. On reveal (`SecretRoomRevealer.reveal(roomName)` — triggered when the `secretRoom`-tagged wall breaks), the veil cells blank, the deferred markers spawn exactly once (idempotent per room), and a smoke puff appears at each veil cell. Collision is unaffected throughout: it stays solely in the `collision` layer (single source of truth).

**Designing the room behind the wall:**
Carve the alcove/room into the `collision` layer (solid walls on the other sides), drop a `Rooms` rectangle over it (put it *before* the enclosing room in the layer — see §6), and place pickups/enemies inside via the `objects`/`enemies` layers. Two proven shapes, both produced by `generate_tmx.py`:

- **Appended full-screen room:** see the `secret_room` example in `assets/maps/world1/level_05.tmx` — the full 30-tile room right of room 2 (cols 90–119); its west boundary wall at col 89 has the two body-height rows 14–15 replaced with the breakable `secret_wall` tile the player strikes from the last normal room.
- **Chamber carved inside a room (`--inside-secret`):** see `assets/maps/world1/level_08.tmx` — a 6×8-tile box flush against the room's left wall and sitting on the floor. Its **front** (right) wall carries the breakable guard on the two passage rows (rows 14–15, col 5); the roof/floor/left wall are the room's own. The chamber is hollow so the player can stand inside, and the `secret_hide` veil covers its footprint **except** the guard cells, so the crack stays visible and strikeable before reveal. Its `Rooms` rect is emitted before the enclosing room so the camera flips onto the chamber while the player is inside.

### 4.7 Vertical Room Links (Platform Shafts)

Rooms stacked vertically (a grid map, e.g. `world2`) connect through a **platform shaft** instead of a doorway — the engine has no ladders/climb, so the climb is made of drop-through platforms. A shaft is a 2-column channel at interior columns **`col_start+2..col_start+3`** (relative to the room's left wall) carved through both rooms' shared boundary:

- **Lower room:** the two shaft columns are hollow from the ceiling down to one row above the floor; the floor row itself stays solid. A `oneWay` platform sits on every other row above the floor (for a 24×10 room: platforms at rows `floor−2, −4, −6, −8`). The player hops platform to platform, then jumps through the open ceiling.
- **Upper room:** the matching two floor cells at the shaft columns are hollow — a **hatch** — so the player pops up into the room. The same hatch is the hole they fall back down through; the floor around it stays solid.
- **Spacing:** 2-row platform steps fit the player's single-jump envelope (2 up / 4 across — see `gameplay.md` §2.A); 2 columns wide makes the shaft read as a visible vertical gap. Keep markers (enemies/items) out of the shaft columns.
- `generate_tmx.py` builds shafts automatically for grid layouts (`--grid-cols`/`--grid-rows`, see §10); hand-authored maps carve the same shape straight into the `collision` layer — hollow the two columns, place `oneWay` tiles on alternating rows, and open the two hatch cells in the upper room's floor.

### Perimeter / Layout Rules of Thumb

- Every room needs solid **floor**, **ceiling**, **left wall**, **right wall** — except for the passage tile(s) on each room boundary.
- The outer edges of the whole map should be solid — there's nothing beyond them.
- Align passages: the doorway on one side of a room boundary must line up with the doorway on the neighboring room's matching side, or the connection doesn't actually link. Keep all rooms on a **shared floor baseline** so doorways at the same height line up.

---

## 5. Object Markers — Things You Can Place

Both the `objects` and `enemies` layers hold **map objects** (rectangles and/or tile objects). Each object is identified by its **`type`**, which the game reads from (in order): the object's own `type`/`Type` field, a custom property named `type`, or the **placed tile's** `type` property (for tile objects). Unknown/unhandled types are ignored.

Two ways to place a marker:

- **Rectangle object** — draw a rectangle, then set its Type (e.g. `playerStart`) in the object properties. Size matters for `platform` (it defines the sprite + collision box); for everything else the rectangle is just a spawn point (position = its bottom-left in game coords).
- **Tile object** — draw a tile from a tileset that already carries a `type` property. The `items.tsx` and `enemy.tsx` tilesets are pre-wired this way (e.g. the `coin` tiles, the `chest` tile, the animated `coin` tile, and the `enemy` tiles), so you can just paint the object with the stamp tool.

### 5.1 Marker Type Reference

| `type` | Spawns | Custom Properties | Notes |
|---|---|---|---|
| `playerStart` | The player | — | Exactly **one** per map, usually in the first room. Rectangle position = spawn point. |
| `coin` | Coin pickup | — | Collectible objects are **never** drawn from the Tiled tile sprite — the spawned entity always renders its own `Coin_01..06` atlas spin animation, regardless of the marker source. Every coin renders at **half a map tile** (`DEFAULT_COIN_SIZE * unitScale`, i.e. 64px on a 128px-tile map) **centered on the marker rect — the marker's drawn size is ignored** (it's a pure placement guide). |
| `chest` | Chest (opens on melee strike) | `potionType` (string, optional) | Built from the atlas `Chest_01_Locked`/`Chest_01_Unlocked` regions (128×128 — one map tile). Without `potionType`: drops 2–6 coins. With `potionType` set (e.g. `healing`, `strength`, `speed`, `invulnerability`): drops a single potion of that type instead. |
| `torch` | Decorative torch | — | Flickers visually. |
| `dagger` | Dagger pickup | — | Collectible item. |
| `exitGate` | Exit gate / level transition | `nextLevel` (string, optional), `isFinal` (string, default `"false"`) | Spawns a **logic-only** entity: a collision box (sized from the object rectangle) + optional level transition. **No gate art is drawn** — paint the door's decoration yourself. The gate is interactive only when it has `nextLevel` **or** `isFinal="true"`; with neither it's purely decorative. `isFinal="true"` triggers the Victory Screen instead of loading the next level. |
| `enemy` | Enemy | `enemyType` (string, default `"walker"`), `aiMode` (string), `speed` (float), `patrolRange` (float), `loot` (string) | Put these on the `enemies` layer (or `objects`). Catalog: `walker` (goblin), `flyer` (mosquito), `shooter` (spider), `knight` (15 HP). `loot` defines drop behavior (e.g., `"coin:3, ammo:1"`). See `resources/docs-ai/enemies.md`. |
| `trap` | Trap | `trapType` (string, default `"acidDrop"`), `direction` (string), `interval` (float), `speed` (float), `damage` (int), `duration` (float), `cooldown` (float), `pulseSpeed` (float) | Place on `objects`/`enemies`. See §5.6. |
| `platform` | Moving platform | `amplitudeX`, `amplitudeY`, `speed`, `phase`, `axis` (see §5.7) | The object **rectangle size defines both the sprite and collision box**. |
| (any other) | — | — | Ignored. |

### 5.2 Coins

Coins are collected by the player touching them. Every coin renders at **half a map tile** (`DEFAULT_COIN_SIZE * unitScale`, i.e. 64px on a 128px-tile map) **centered on the marker rect — the marker's drawn size is ignored** (it's a pure placement guide), so map coins and chest/enemy-dropped coins are always the same size.

You can place coins as plain rectangles OR stamp any `items.tsx` coin tile (static or animated); the on-screen result is identical.

### 5.3 Chests

Chests open when the player melee-strikes them. Built from the atlas `Chest_01_Locked`/`Chest_01_Unlocked` regions (128×128 — one map tile), never from a Tiled tile sprite. Any `items.tsx` chest tile just marks the spot.

- **Without `potionType`:** drops 2–6 coins.
- **With `potionType`** (e.g. `healing`, `strength`, `speed`, `invulnerability`): drops a single potion of that type instead of coins.

### 5.4 Exit Gates

The exit gate spawns a **logic-only entity**: a collision box (sized from the object rectangle) + optional level transition. **No gate art is drawn** — paint the door's decoration yourself in the `background`/`decoration`/`collision` layers.

- The gate is a real, interactive transition trigger only when it has a `nextLevel` **or** `isFinal="true"`; with neither it's purely decorative.
- `nextLevel` path is **relative to the `assets/` folder**, e.g. `maps/world1/level_03.tmx`.
- `isFinal="true"` triggers the **Victory Screen** (`VictoryScreen`) instead of loading the next level — used on the last level of a world, and may omit `nextLevel` entirely (e.g. World 2's `level_10_final`).

### 5.5 Enemies

Place `enemy` markers on the `enemies` layer (or `objects`). The `enemyType` property picks the variant:

| `enemyType` | Creature | Notes |
|---|---|---|
| `walker` (default) | Goblin | Ground-based patrol enemy |
| `flyer` | Mosquito | Flies; needs patrol range clear of walls |
| `shooter` | Spider | Fires projectiles; only fires in its own room |
| `knight` | Knight | 15 HP, tough melee enemy |

The `loot` property defines drops (e.g., `"coin:3, ammo:1"`). Supports comma-separated list and `":"` or `"="` delimiters. Valid: `coin:X`, `ammo:X`, `potion:TYPE` (TYPE in `healing`, `strength`, `speed`, `invulnerability`). See `resources/docs-ai/enemies.md` for full details.

### 5.6 Traps

Traps come in two flavours: **tile-based hazards** (painted in the `collision` layer — see §4.2) and **spawned trap entities** (placed as `type="trap"` objects on the `objects`/`enemies` layer). Both deal 1 HP damage with no knockback and share the 2-second invulnerability grace period.

Place a rectangle object with `type="trap"`. The rectangle position is the spawn point; size is ignored (collision is defined by the trap code). Add a `trapType` property to pick the variant.

**Acid/Lava Drop Spawner (`trapType = "acidDrop"`):**

An invisible entity that periodically shoots projectiles in the configured direction. The spawner is a **visible animated acid tube** (atlas `acid_tube1..4`, 64px sprites rendered at `AcidTubeScale = 0.25f × unitScale` — exactly one 128px tile). Each interval it plays a one-shot **discharging animation**; only when that completes does it release a drop.

| Property | Type | Default | Description |
|---|---|---|---|
| `direction` | string | `"down"` | `"up"` (lava geyser from floor), `"down"` (acid drip from ceiling), `"left"`/`"right"` (side-mounted sprayer). |
| `interval` | float | `2.0` | Seconds between projectile spawns. |
| `speed` | float | `200` | Projectile velocity in world-units/s (before unitScale). |
| `damage` | int | `1` | HP dealt on contact. |

**Down-direction drip** (the common case): the drop spawns **centered on the tube** (the marker + half a tile, no editor offset), **hangs at the spawn point** for ~0.15 s (the `dripBuild` effect, so it visibly bulges before releasing), then falls **accelerating under gravity** (`ACID_DROP_GRAVITY`) like a heavy droplet. On landing on a floor/wall it **turns into an `ACID_POOL`** (a ~1 tile wide puddle from the atlas `acid_blob1..7` clip, 128×128px sprites flattened) that plays the **splash animation once** and then holds the final splat frame for the remaining pool life — deals 1 HP on contact and lingers ~1.5 s before vanishing.

**Up/left/right directions** keep constant speed (no gravity) and simply vanish on wall/ceiling contact (no pool). Uses the `acid_drop` atlas region (32×32 single frame, rendered at `AcidDropScale = 0.25f × unitScale` — half a tile); all drops are removed after 5 s lifetime.

Because the drop often spawns inside/on a wall cell (a ceiling fixture), drops get a short **spawn-grace window** (~0.12 s) during which wall-collision culling is skipped — letting a drop clear the fixture it spawned on.

> **Note:** the acid tile in `assets/maps/tileset/hazards.tsx` carries **no standalone image** — the visible tube (and any marker preview) comes from the runtime `acid_tube1..4` sprites in `gfx/origin-game.atlas`, not from a TSX-referenced PNG.

**Flame Trap (`trapType = "flame"`):**

An animated flame that pulses between small and large on a timed cycle. The collision box scales with the visual — anchored at the source wall/floor/ceiling and extending outward in the configured direction.

| Property | Type | Default | Description |
|---|---|---|---|
| `direction` | string | `"down"` | `"down"` (hangs from ceiling), `"up"` (rises from floor), `"left"`/`"right"` (wall-mounted, rotated 270°/90°). |
| `duration` | float | `2.0` | Seconds the flame is ON (growing from min to max scale). |
| `cooldown` | float | `1.5` | Seconds the flame is OFF between pulses. |
| `pulseSpeed` | float | `2.0` | Oscillation speed for the grow/shrink animation. |
| `damage` | int | `1` | HP dealt on contact. |

Visual: `fire1..10` atlas sprites (256×256), animated across the ON phase. Initial cooldown is randomised (`0` to `cooldown`) so multiple flames in a room don't pulse in sync.

**Planned but not yet coded:** The atlas contains `blade1..7` and `lightning1..9` sprite regions reserved for future blade-trap and lightning-trap types. No `TrapType` enum, no `EntityFactory` code, and no system logic exist for them yet.

**Trap room awareness:** Both spawners and flames check `roomIndex` against `RoomState.activeRoomIndex` each frame. In inactive rooms: spawners pause their timers, drops continue moving (they're transient), flames freeze their pulse cycle. Matches enemy freeze behaviour.

**Trap debugging:** Trap AABBs appear in the SHIFT+D collision debug overlay (lime-coloured boxes) like any `CollisionComponent` entity. Flame traps show dynamically scaling boxes as they pulse.

### 5.7 Moving Platforms

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

- Horizontal shuttle: rectangle `type="platform"` + `amplitudeX=80` + `speed=1` → slides 80px left/right of spawn.
- Vertical lift: `amplitudeY=80` + `speed=1` → bobs 80px up/down.

**Gotchas:**

- A platform with no `amplitude`/`axis` is a static block (still lands on it, still collides).
- The platform only **moves** while its owning room is the active one (`RoomState.activeRoomIndex`); it freezes when you're in another room. The player can still stand on a frozen platform.
- Place the platform rectangle *inside* a `Rooms` rectangle so it's tied to that room.

---

## 6. Rooms & Camera

Draw one **rectangle per room** in the `Rooms` object layer (a `RectangleMapObject`; plain shape, no special type needed). `MapLoader.getRooms()` turns each into a `Room`, and `CameraSystem` uses whichever room currently contains the player for both **camera framing** and **enemy/platform activation**.

*   **No `Rooms` layer at all**, or a **`Rooms` layer with no rectangles** → the game falls back to a single room covering the whole map (the whole map scrolls like one big room). Useful for one-screen test maps, but you lose per-room camera/enemy control.

### Camera Behavior (Per Axis)

For each axis (X and Y) independently:

| Room Size vs Viewport | Camera Mode | Behavior |
|---|---|---|
| Room ≤ viewport (or `camera="flip"`) | **Flip** | Camera locks to the room's **center** and **snaps instantly** when the player enters. Classic Castlevania-style screen change. The viewport may overshoot a room smaller than the screen. |
| Room > viewport (or `camera="scroll"`) | **Scroll** | Dead-zone scrolling. Camera holds still while the player roams inside a margin from each screen edge, then scrolls only when the player crosses that margin, clamped so the screen never leaves the room. |

- `camera="flip"` forces static framing even for big rooms; `camera="scroll"` forces scrolling, but a room still smaller than the viewport on an axis always centers (you can't scroll what's smaller than the screen).
- **No smooth follow / no lerp.** Transitions are instant snaps.
- On level start the camera frames the starting room via `CameraSystem.snapToRoom(...)` (flip rooms center, scroll rooms put the player in view) — not the player.

**Scroll margin:** `GameConstants.CAMERA_SCROLL_MARGIN` at zoom 1, or 30% of the effective (zoomed) view per axis once the camera zooms in (`GameConstants.MOBILE_SCROLL_MARGIN_FRACTION`) — the camera starts tracking well before the player reaches a screen edge.

**Zoom note:** The game's default layout zooms the camera in everywhere — `LayoutMode.BAND_ZOOM` is the shipped default for desktop, mobile, and tablet. A zoomed camera shows a smaller effective frame, so an otherwise screen-sized 30×17 room becomes *bigger than the frame* and flips to dead-zone scroll on every platform — flip-screen framing is only preserved if a room is authored smaller than the effective (zoomed) view or forced via the room's `camera="flip"` property. This needs **no map change**: rooms stay as authored, the camera just follows.

### Design Tips

- Screen-sized rooms: make a room **exactly 30×17 tiles** (3840×2176 px at 128px tiles) → pure flip-screen, no scrolling.
- Rooms **wider/taller than 30×17 tiles** → dead-zone scroll; give the player room to roam.
- Small rooms (smaller than the screen) are fine — the camera centers on them and shows a little of the next area.
- Every `Rooms` rectangle should **contain the `playerStart` marker** (and each enemy/platform's spawn), or that entity won't be tied to any room (`roomIndex = -1`, always active).
- **Secret rooms:** carve a small room into the `collision` layer, drop a `Rooms` rectangle over it, and place pickups/enemies inside. `RoomState.findRoomIndexContaining(...)` returns the **first** room rectangle containing a point, so put the secret room **above** (before) the enclosing room in the layer for it to win the camera framing (a chamber carved *inside* a room is emitted first for exactly this reason). To make it *hidden until broken* (see §4.6): name the rect (e.g. `secret_room`), paint a `secret_hide` veil over its footprint (leave the guard cells un-veiled for an inside chamber, so the crack is visible), tag the entry-wall tile with `secretRoom="secret_room"`, and tag the interior markers with the same `secretRoom` object property so they spawn on reveal (see `secret_room` in `assets/maps/world1/level_05.tmx` and the inside chamber in `assets/maps/world1/level_08.tmx`).

---

## 7. Effects & Lighting

Tiles on **any tile layer** (background, decoration, collision, etc.) can carry an `effect` property to spawn a runtime effect entity at that tile's position. The tile's own sprite is rendered by the Tiled map renderer — the effect entity carries **no texture**, only the effect component (e.g. `LightComponent`). This means what you paint in Tiled is what renders in-game (true WYSIWYG). The tile layer determines draw order (background = behind player, decoration = in front).

### How It Works

1. Stamp a tile with `effect="light"` (or future `"particle"`, `"sound"`) on any tile layer.
2. At level load, `MapLoader.scanEffectLayers()` iterates every tile layer in the map, reads the `effect` property from each cell, and records world positions.
3. `EntityFactory.spawnEffects()` creates minimal effect entities at those positions.

### Supported Effect Types

| `effect` value | Component added | Behaviour |
|---|---|---|
| `"light"` | `LightComponent` | Flickering glow halo. Light center is read from the tile's **collision-editor shape** (draw a Point in Tiled's Tile Collision Editor on the tile to set the exact flame/glow position — true WYSIWYG). If no shape is drawn, the light defaults to the tile center. |
| `"particle"` | *(planned)* | Future: particle emitter at the tile position. |
| `"sound"` | *(planned)* | Future: positional sound emitter at the tile position. |

### Per-Tile Properties

| Property | Type | Default | Effect |
|---|---|---|---|
| `effect` | string | — | Effect type: `"light"`, `"particle"`, `"sound"`. |
| `lightRadius` | float | `96` | (light only) Halo radius in world units. |
| `lightColor` | string | warm orange | (light only) RGB hex (`"FF8040"`) or RGBA hex (`"FF8040FF"`). |
| `lightFlickerSpeed` | float | `6` | (light only) Flicker oscillation speed in rad/s. |

### Pre-Wired Tiles in `items.tsx`

| Tile | `effect` | Image | Notes |
|---|---|---|---|
| id 20 | `"light"` | `tiles/bg/torch.png` (128×156) | Wall torch. Default radius 96. Draw a Point in the Tile Collision Editor to position the light center (e.g. at the flame tip). |

### Adding a New Effect Tile

1. Add a tile to `items.tsx` (or any tileset) with the desired image.
2. Set `effect="light"` on the tile properties.
3. Optionally override `lightRadius` / `lightColor` / `lightFlickerSpeed`.
4. Open the tile in Tiled's **Tile Collision Editor** and draw a Point where the light center should be (e.g. at the flame tip for a torch). The point's coordinates are in tile-local pixel space — the light spawns at that exact world position.
5. Stamp the tile on any tile layer in Tiled — done.

### Adding a New Effect Type (e.g. `"particle"`)

1. Add a branch in `EntityFactory.spawnEffects()` for the new `effectType`.
2. Create a `create*Effect()` method that returns an entity with the appropriate component(s).
3. Update this documentation section.

**Backward compatibility:** The existing `type="torch"` rectangle markers on the `objects` layer still work via the `spawnObjects()` switch. The new tile-property approach is an alternative — torches can be placed either way. Maps can mix both approaches.

---

## 8. Full Property Reference

All properties are read as `float`/`string`/`boolean` and tolerate being set as either int or string in Tiled. Units are **world px** unless noted.

### Object/Tile Properties (Markers)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `type` | string | — | The marker discriminator (`playerStart`, `coin`, `chest`, `torch`, `dagger`, `exitGate`, `enemy`, `trap`, `platform`). |
| `enemyType` | string | `"walker"` | Picks the enemy variant: `walker` / `flyer` / `shooter` / `knight`. See `enemies.md`. |
| `aiMode` | string | `"patrol"` | Enemy patrol behavior: `"side-to-side"` (or `"sidetoside"`, case-insensitive) → endless walking that turns only on walls/ledges/hazards; anything else/absent → origin-bounded `patrol`. Flyers ignore it (never grounded). |
| `speed` | float | per-type default (`20`) | (enemy only) Horizontal patrol speed override, world px/s. Applied before the `unitScale` (tile-size) scaling. |
| `patrolRange` | float | per-type default (`64`) | (enemy only) Patrol-range override, world px. Only used in `PATROL` mode (`SIDE_TO_SIDE` ignores it). Applied before the `unitScale` scaling. |
| `nextLevel` | string | — | (exitGate only) The next `.tmx` path **relative to the `assets/` folder**, e.g. `maps/world1/level_03.tmx`. Cycles to that map on interaction. |
| `isFinal` | string | `"false"` | (exitGate only) `"true"` triggers the Victory Screen instead of loading the next level. |
| `trapType` | string | `"acidDrop"` | (trap only) Trap variant: `"acidDrop"` (spawner + projectiles) or `"flame"` (pulsing fire). |
| `direction` | string | `"down"` | (trap only) Direction the trap fires/extends: `"up"`, `"down"`, `"left"`, `"right"`. |
| `interval` | float | `2.0` | (acid drop spawner only) Seconds between projectile spawns. |
| `speed` | float | `200` | (acid drop only) Projectile velocity in world-units/s (before unitScale). |
| `duration` | float | `2.0` | (flame only) Seconds the flame stays ON. |
| `cooldown` | float | `1.5` | (flame only) Seconds the flame stays OFF between pulses. |
| `pulseSpeed` | float | `2.0` | (flame only) Oscillation speed for grow/shrink animation. |
| `potionType` | string | — | (chest only) If set, the chest drops a potion of this type instead of coins. Valid values: `healing`, `strength`, `speed`, `invulnerability`. Omit for a standard coin chest. |
| `loot` | string | — | (enemy only) Drop definition, e.g. `"coin:3, ammo:1"`. Supports comma-separated list and `":"` or `"="` delimiters. Valid: `coin:X`, `ammo:X`, `potion:TYPE`. |
| `secretRoom` | string | — | (object markers only) Defers this marker — it is partitioned out of the normal spawn layers and only spawned when its named room is revealed. Must match a `Rooms` rect name. |

### Moving Platform Properties

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

### Tile Properties (Collision Layer / Tilesets)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `solid` | boolean | `true` | Set `false` on a tileset tile to make that tile **non-blocking** even in the `collision` layer (the passage-doorway use case, §4.1). |
| `oneWay` | boolean | `false` | Set `true` on a tileset tile to make it a **drop-through platform**: the player gets top-only solidity (jump up through; drop with the `v` button / `S` / `DOWN`), while enemies and popped items treat it as a fully-solid tile (all four sides) and flying enemies pass through (see §4.3). Ignored when the tile is `hazard`. |
| `crumble` | boolean | `false` | Set `true` on a tileset tile to make it a **crumbling (one-way) platform**: top-only like `oneWay` (its rect joins the shared `oneWayRects` set), but it shakes and collapses while the player stands on it, then respawns (see §4.4). Implies one-way behavior and is always a **full-tile rect** (no Tile Collision Editor shapes — custom shapes are unsupported on crumble tiles); player-triggered only. Ignored when the tile is `hazard` or `solid = false`. |
| `hazard` | boolean | `false` | Set `true` on a tileset tile to make it a **non-solid hazard** (spikes/lava): 1 HP on touch, no knockback, invulnerability grace (see §4.2). Wins over `solid`/`oneWay`. |
| `secret` | boolean | `false` | Set `true` on a **solid** tileset tile to make it a **breakable secret wall**: solid until the player melee-strikes it, then it disappears and opens the way (see §4.5). Ignored on `hazard` and `solid = false` tiles. |
| `secretRoom` | string | — | On a **secret wall tile**: names the `Rooms` rectangle (matched by **name**) that this wall protects, turning the plain secret wall into a **hidden secret room** entry — breaking the wall reveals the whole room (§4.6). On a **map object marker** (`objects`/`enemies` layers): defers that marker — it is partitioned out of the normal spawn layers and only spawned when its named room is revealed (§4.6). |
| `type` | string | — | On tileset tiles: lets you paint tile objects that auto-spawn as markers (`coin`, `chest`, `enemy`…). |
| `effect` | string | — | On **tile layer** tiles: spawns a runtime effect entity at the tile position (see §7). Values: `"light"` (flickering glow halo), `"particle"` (planned), `"sound"` (planned). |
| `lightRadius` | float | `96` | (effect="light" only) Halo radius in world units. |
| `lightColor` | string | warm orange | (effect="light" only) RGB hex (`"FF8040"`) or RGBA hex (`"FF8040FF"`). |
| `lightFlickerSpeed` | float | `6` | (effect="light" only) Flicker oscillation speed in rad/s. |

### Room Properties (The `Rooms` Layer)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `camera` | string | `auto` | Camera mode for that room: `flip` (always static, center-framed), `scroll` (always dead-zone scroll), or omit for `auto` (infer from size — see §6). |

---

## 9. Common Pitfalls

Checklist when something feels wrong:

| Symptom | Cause | Fix |
|---|---|---|
| Layer silently ignored | Layer name typo | Layers are matched by exact name (`collision`, `objects`, `enemies`, `Rooms`, `background`, `decoration`, `secret_hide`). A typo = silently ignored layer. |
| Doorway is an invisible wall | Passage tile missing `solid = false` | Fix it on the tileset tile (this affects all maps using that tileset). |
| Nothing spawns from an object | Wrong `type` spelling | `playerStart`, `coin`, `chest`, `torch`, `dagger`, `exitGate`, `enemy`, `trap`, `platform` are exact. Anything else spawns nothing. |
| Platform never moves | Outside every `Rooms` rectangle and/or missing amplitude | Give it an amplitude and place it inside a room. |
| Platform sprite is huge/stretched | Rectangle size doesn't match asset ratio | The platform's collision box and sprite are its rectangle size; keep the rectangle near the tile/asset's aspect ratio. |
| Exit gate does nothing | Missing `nextLevel` or wrong path | Paths are relative to `assets/`. |
| Enemy behaves oddly | `enemyType` spelling or `patrolRange` hits walls | Flyers have no automatic wall avoidance — keep range clear of walls. See `enemies.md`. |
| Camera snaps wrong room / player not in view | `playerStart` not inside intended `Rooms` rectangle, or rooms don't tile the map | Ensure every spawn point is inside a room rectangle. |
| Unexpectedly scrolling when you wanted flip | Room is larger than 30×17 tiles | Add `camera="flip"` to force static framing. |
| Spikes/lava never hurt a standing player | Hazard is on the floor row | Player's collision box is smaller than a tile and feet sit on the floor; place hazards where the player travels *through* them (see §4.2). |
| Acid drops pass through walls | Spawner has clear path through a passage | Place spawners so line of fire hits a solid wall/floor/ceiling. Drops live 5 s — enough to cross a room. |
| Flame trap too fast/too slow | Confusing `pulseSpeed` with on/off cycle | `pulseSpeed` = grow/shrink animation speed. On/off rhythm = `duration` (on) + `cooldown` (off). To stagger flames, use different `cooldown` values. |
| Effect tile does nothing | `effect="light"` on the `objects` layer | Effect system only works on **tile** layers; it scans all tile layers via `scanEffectLayers()`. Use `background`/`decoration`/etc. |
| Light halo offset wrong | No Point shape drawn in Tile Collision Editor | Draw a Point to precisely position the flame/glow center. |
| Drop platform behaves like a wall | Tileset tile missing `oneWay = true` | Remember: one-way is drop-through **only for the player** — enemies and popped items stand on them like solid tiles, flyers fly through. |
| Crumble tile arms when it shouldn't | Flush-adjacent to solid floor at same height | Player's overhanging collision box grazes it. Keep crumble tiles in open air; never make one the only way up a vertical shaft (respawns after 2.5s). |
| Secret wall doesn't break | Tile not flagged `secret = true` | Plain solid tiles won't break. Also needs **three** hits (one per swing), and the strike hitbox must reach the wall. |
| Secret room spawns loot from the start / never reveals | Interior markers missing `secretRoom` object property, or entry wall's tile missing `secretRoom` naming the `Rooms` rect | Markers must carry `secretRoom` to be deferred. The wall's `secretRoom` is a **tile** property read from the tile itself — it can't live on the cell. |
| Secret room visible through wall before reveal | `secret_hide` doesn't cover full room footprint (or `Rooms` rect bigger than veil) | Veil must tile every cell over the rect and sit at the top of the layer stack. |

---

## 10. Related Tooling & Docs

*   `.opencode/skills/tmx-map-generator` — generates a standalone prototype `.tmx` (a linear chain of rooms with enemies/items and walk-through doorways, plus a hidden secret room — either appended to the right, or, with `--inside-secret`, carved as a 6×8-tile chamber inside the last room) so you can test a layout without hand-tracing collision CSVs. Rooms **default to 24×10 tiles** (mobile-oriented, dead-zone scroll under the `BAND_ZOOM` camera); pass `--room-width 30 --room-height 17` for whole-screen desktop rooms. `--platforms N` additionally decorates every room with a deterministic, always-jumpable staircase of N floating one-way platforms (2 rows up / 2 cols right per step — within the player's single-jump envelope of 2 up / 4 across — a `bg-*` filler tile behind each, and a coin on the top platform) while keeping the flat floor intact — see the skill's SKILL.md. **Templates:** `--template NAME[,ROOM[,COL]]` (repeatable) and `--template-pick N` stamp ASCII-art courses from `scripts/templates/*.tmpl` (e.g. `staircase`, `chasm-bridge`, `hazard-strip`) floor-anchored into rooms — the generator then runs jump-aware design checks (supported ground + reachability within the jump envelope, warnings only). **Grid layouts:** `--grid-cols C --grid-rows R` tiles `C×R` rooms over the map (width × height = `C×W × R×H`), links horizontal neighbours with doorways and vertical neighbours with one-way **platform shafts** (§4.7), and places `playerStart` in the bottom-left room; grid maps use `--no-secret` to omit the secret room (required for multi-row grids). Run `generate_tmx.py` (from `assets/maps/world1`) with `--rooms N --seed S --out level_05.tmx` or `--grid-cols 2 --grid-rows 2 --no-secret --seed S --out level_01.tmx`; see the skill's SKILL.md for CLI + conventions.
*   `resources/docs-ai/enemies.md` — enemy catalog, stats, and how to add new types.
*   `resources/docs-ai/gameplay.md` §AB — trap design spec (acid drops, flames, damage resolution).
*   `resources/docs-ai/ashley-ecs.md` — the `MapLoader`/`EntityFactory`/`Room`/`RoomState`/`CameraSystem` code shape and priorities.
*   `resources/docs-ai/gameplay.md` — movement/combat mechanics and how they read map data.
