# Map Design for Tiled â€” Level Authoring Guide

This document is the **single source of truth for level/map design**: how a `.tmx` map is read by the game, what layers and custom properties exist, what each one means, and a step-by-step recipe for building a playable level. It's written so you can put the game down for three months, come back, and still remember how to design a level without re-reading the source.

It complements â€” but does not replace â€” `resources/docs-ai/ashley-ecs.md` (ECS shape: `MapLoader`, `EntityFactory`, `Room`, `RoomState`) and `resources/docs-ai/gameplay.md`; the `resources/docs-ai/enemies.md` catalog covers enemy placement in detail. `resources/docs-ai/ashley-ecs.md` covers what the parsing classes do in code.

> **Maintenance rule:** Any change to map parsing â€” layer names, object marker types, custom properties, tileset tile properties, room/camera semantics â€” MUST update this file in the same change (see `AGENTS.md`). If the change alters `MapLoader`/`EntityFactory`/`Room`/`CameraSystem` shape, also update `ashley-ecs.md`; if it changes what a player sees, update `gameplay.md`.

---

## 1. The big picture

A level is a single `.tmx` file (Tiled 1.11+). At level start the game:

1.  Loads the `.tmx` with `TmxMapLoader` (inside `MapLoader`).
2.  Reads the **`collision` tile layer** â†’ the static solid AABBs used for all physics (`collisionRects`).
3.  Reads the **`Rooms` object layer** â†’ the camera-framing/enemy-activation zones (`RoomState.rooms`).
4.  Reads the **`objects`** and **`enemies`** object layers â†’ `EntityFactory` spawns pickups, chests, torches, the exit gate, enemies, and moving platforms.
5.  Reads the **`playerStart`** marker â†’ where the player spawns.

Levels are chained together as **separate `.tmx` files**: the exit gate in one map points at the next map's path (see `nextLevel` below). Each playable level must also be registered in `core/.../map/LevelCatalog.java` (it's what the level-select screen reads).

### Units, scale, and coordinates (important)

*   **1 world unit == 1 pixel** as drawn in Tiled. The world's Y axis points **up** in-game; Tiled draws Y **down**. The loader flips object/tile Y coordinates automatically, so just design normally in Tiled â€” never hand-edit raw Y values in the `.tmx` XML.
*   The game was built for a base virtual resolution of `480Ã—272` "16px-world-units", scaled by `unitScale = tileWidth / 16f` (`GameConstants.VIRTUAL_WIDTH/HEIGHT`). The world **height** is fixed at `VIRTUAL_HEIGHT Ã— unitScale`, but the world **width expands with the screen's aspect ratio** (`OffsetFitViewport`) so wide screens show more world instead of black bars: floored at the classic `480/272` ratio and capped at `GameConstants.MAX_WORLD_ASPECT` (â‰ˆ21:9) only for physically ultra-wide screens. **All current maps use 128Ã—128 tiles**, so:
    *   `unitScale = 128 / 16 = 8`.
    *   One on-screen frame = at least `480 * 8 = 3840` world px tall-frame width Ã— `VIRTUAL_HEIGHT * 8 = 2176` px tall = **30 tiles Ã— 17 tiles minimum**, wider on wider screens (a 16:9 desktop shows â‰ˆ30.2 tiles; a banded phone in landscape shows proportionally more).
    *   Design rooms around whole screens (multiples of 30Ã—17 tiles) for clean flip-screen framing; rooms narrower than the widest target screens will simply show a sliver of the neighbors, and the camera's per-room `camera="flip"` property can pin framing where that matters.
*   Mixing tile sizes across a level chain works (scale is recalculated per map), but keep every map in a chain the **same tile size** to avoid jarring resizes.
*   Object/room rectangles are placed and sized in **world px** (the same numbers you see in Tiled), not in tiles.

---

## 2. Layers

Layer **names matter â€” they are read by string**. Get them exactly right or the game silently ignores them. Tiled draws tile layers in order; the game renders `background` first, then `collision` (yes, the solid tiles are visible), then `decoration` on top.

| Layer | Kind | Purpose | Required? |
|---|---|---|---|
| `background` | Tile layer | Decorative backdrop (brick, pillars, windowsâ€¦). Never affects collision. | yes (can be empty) |
| `collision` | Tile layer | Solid/blocking geometry. Every painted tile is solid unless the tile opts out (see Â§3). | yes |
| `decoration` | Tile layer | Foreground decor drawn above the collision tiles. Never affects collision. | optional |
| `objects` | Object layer | Player start, pickups, chests, torches, exit gate, moving platforms (see Â§4). | yes |
| `enemies` | Object layer | Enemy markers (see Â§4). Separate layer keeps enemies easy to find. | optional |
| `Rooms` | Object layer | Plain rectangles defining camera zones (see Â§6). | optional (see Â§6) |
| `secret_hide` | Tile layer | Rock veil painted over a secret room's footprint to hide its existence until the secret wall is broken (see Â§3.5). Must render above every other tile layer. | optional |

Only **tile** layers render visually; object layers are never drawn, they only spawn entities. The exit gate is the one exception worth knowing: its `exitGate` object spawns a **logic-only trigger** (no sprite â€” the gate's decoration is painted by you in the `background`/`decoration`/`collision` layers), so a gate needs real map art to be visible.

---

## 3. The `collision` layer â€” the tile language

The collision layer is a tile grid. `MapLoader.buildCollisionRects()` walks every cell and classifies it:

*   **Empty cell** (`0`) â†’ free space (a platforming gap).
*   **Painted tile** â†’ one of the kinds below, decided by that tile's boolean custom properties (see the full table in Â§5). Precedence, first match wins:
    1.  `hazard = true` â†’ **non-solid hazard** AABB (spikes/lava; damages the player on touch â€” see Â§3.2).
    2.  `solid = false` â†’ free space (passage doorways).
    3.  `oneWay = true` â†’ **drop-through platform** AABB (player top-only; fully solid for enemies/popped items; flyers pass through â€” see Â§3.3).
    4.  anything else â†’ **solid wall** (blocks everything).
    5.  A solid wall tile **additionally** marked `secret = true` is a **breakable secret wall** â€” solid exactly like a regular wall until the player melee-strikes it (see Â§3.4).

### Â§3.1 The passage rule

Room-to-room doorways must be a tile marked **non-solid**. In Tiled:

1.  Open the tileset file (e.g. `assets/maps/tileset/dungeon_tiles.tsx`).
2.  Select the doorway tile you use for passages.
3.  Add a **boolean custom property `solid` set to `false`** on that tile.

That single tileset edit makes every map using the tile treat it as a walk-through doorway. (This is the only way to connect two rooms â€” the walls between rooms are otherwise solid.) If you forget it, the doorway is an invisible wall and the player can't progress.

### Â§3.2 Hazards (spikes, lava)

A **non-solid** tile that damages the player on contact. Paint it in the `collision` layer; the tile's `hazard = true` property (set on the tileset tile, e.g. `assets/maps/tileset/hazards.tsx`) turns it into a damage zone instead of a wall:

*   On AABB overlap the player loses **1 HP**, gets the usual 0.3s hit-stun + 2s invulnerability grace, and is **not** knocked back (no directional push).
*   Hazards are fully non-solid â€” nothing (player, enemies, bullets) is blocked by them. The grace period turns a sustained overlap into one hit per second, not instant shredding.
*   **Smaller damage zone:** by default the danger box is the full tile. To shrink it, open the hazard tile in Tiled's **Tile Collision Editor** and draw a shape (rectangle or polygon) around the actual spikes/lava art â€” `MapLoader` then emits one world-space hazard box per shape instead of the full tile (a tile with no shapes, or a flipped cell, still gets the full tile). Verify with the SHIFT+D hazard overlay (red).
*   **Placement gotcha:** the player's collision box is *smaller than a tile* (30Ã—40 px vs a 128Ã—128 tile â€” the design model is a 1Ã—1-tile box, see `gameplay.md` Â§2.A) and its feet sit on the floor surface, so a hazard painted in the row directly on top of a floor tile sits at/below the player's feet and a standing player won't overlap it. Hazards damage reliably when the player's body travels **through** them (jump over a spike barrier, fall into a lava pool, walk a spike row you must jump). Put solid ground under a pit of lava/spikes so a falling player lands on it.

### Â§3.3 Drop-through platforms (one-way)

A **drop-through platform for the player, a normal solid tile for everyone else**. Paint it in the `collision` layer; the tile's `oneWay = true` property (set on the tileset tile, e.g. `assets/maps/tileset/drop_platform.tsx`) makes it a platform instead of a wall:

*   The player can **land on its top** (it sticks only when the player's feet were at/above the platform's top before the move) and can **jump up through** it from below.
*   While standing on it, the contextual **`v` button** appears (mobile) or **`S`/`DOWN`** (keyboard) starts a short ~0.25s pass-through window: the player drops straight through the platform, then normal gravity takes over.
*   **Enemies and popped items treat it as a fully-solid tile (all four sides)** â€” they land on top, are blocked by its sides/underside, and ground enemies patrol along it exactly like a regular floor. **Flying enemies pass through it entirely.**
*   Player bullets still fly through (they're excluded from `MovementSystem` entirely, and `PlayerBulletSystem` only resolves them against `collisionRects`).

### Â§3.4 Secret walls (breakable)

A **solid** wall tile that opens a doorway when the player melee-strikes it. Paint it in the `collision` layer; the tile's `secret = true` property (set on the tileset tile, e.g. `assets/maps/tileset/secret_wall.tsx`) marks it as breakable:

*   The tile is **fully solid** (blocks the player, enemies, and bullets exactly like a regular wall) â€” breaking it is what opens the route. It is *not* a passage: an unbroken secret wall has no effect on movement until struck.
*   **One melee swing breaks at most one** secret tile whose rect overlaps the strike hitbox (reach-dependent; see `gameplay.md` Â§2.Z). On break, the tile's sprite disappears (its collision-layer cell is blanked), its rect is removed from the collision set, a smoke puff spawns at the tile center, and a wall-break SFX plays â€” the doorway is walkable the very next frame.
*   **Design the room behind it:** carve the alcove/room into the `collision` layer (solid walls on the other sides), drop a `Rooms` rectangle over it (put it *before* the enclosing room in the layer â€” see Â§6), and place pickups/enemies inside via the `objects`/`enemies` layers. Two proven shapes, both produced by `generate_tmx.py`:
    *   **Appended full-screen room:** see the `secret_room` example in `assets/maps/world1/level_05.tmx` â€” the full 30-tile room right of room 2 (cols 90â€“119); its west boundary wall at col 89 has the two body-height rows 14â€“15 replaced with the breakable `secret_wall` tile the player strikes from the last normal room.
    *   **Chamber carved inside a room (`--inside-secret`):** see `assets/maps/world1/level_08.tmx` â€” a 6Ã—8-tile box flush against the room's left wall and sitting on the floor. Its **front** (right) wall carries the breakable guard on the two passage rows (rows 14â€“15, col 5); the roof/floor/left wall are the room's own. The chamber is hollow so the player can stand inside, and the `secret_hide` veil covers its footprint **except** the guard cells, so the crack stays visible and strikeable before reveal. Its `Rooms` rect is emitted before the enclosing room so the camera flips onto the chamber while the player is inside.
*   **Verify with SHIFT+D:** an unbroken secret wall shows as a normal yellow solid rect (it lives in the shared `collisionRects` set); after breaking, the rect is gone and the cell is empty.

### Â§3.5 Secret rooms (hidden until the wall breaks)

A secret room is **invisible until revealed** â€” the player must find and strike its hidden wall to see it at all. Three parts:

*   **`secret_hide` tile layer:** paint rock tiles (visually identical to the surrounding walls) over the entire secret-room footprint, in the `secret_hide` layer (top of the layer stack, so it covers the room). The veil is **purely visual** â€” it has no collision (never add it to the `collision` layer). On reveal, every veil cell over the room is blanked.
*   **The secret wall carries the room name:** the breakable `secret=true` wall tiles declare an extra `secretRoom = "<room name>"` property naming the `Rooms` rectangle they protect. Because Tiled can't attach properties to raw cells, `generate_tmx.py` clones the base `secret_wall.tsx` tile once per map into an inline tileset (e.g. `secret_room_wall`) whose tile carries both `secret=true` and `secretRoom="secret_room"`. (Hand-authored maps: just add `secretRoom` to your tileset tile.) The room name is read from the **tile's own property** â€” cell-level properties are lost the moment the cell's tile is blanked.
*   **Deferred markers:** the room's loot/enemy objects carry a `secretRoom = "<room name>"` **object** property too. `MapLoader.getSecretRooms()` partitions those markers **out** of the normal `objects`/`enemies` spawn layers into the room's deferred set, so nothing spawns until reveal. On reveal (`SecretRoomRevealer.reveal(roomName)` â€” triggered when the `secretRoom`-tagged wall breaks), the veil cells blank, the deferred markers spawn exactly once (idempotent per room), and a smoke puff appears at each veil cell. Collision is unaffected throughout: it stays solely in the `collision` layer (single source of truth).

### Perimeter / layout rules of thumb

*   Every room needs solid **floor**, **ceiling**, **left wall**, **right wall** â€” except for the passage tile(s) on each room boundary.
*   The outer edges of the whole map should be solid â€” there's nothing beyond them.
*   Align passages: the doorway on one side of a room boundary must line up with the doorway on the neighboring room's matching side, or the connection doesn't actually link. Keep all rooms on a **shared floor baseline** so doorways at the same height line up.

### Â§3.6 Vertical room links (platform shafts)

Rooms stacked vertically (a grid map, e.g. `world2`) connect through a **platform shaft** instead of a doorway â€” the engine has no ladders/climb, so the climb is made of drop-through platforms. A shaft is a 2-column channel at interior columns **`col_start+2..col_start+3`** (relative to the room's left wall) carved through both rooms' shared boundary:

*   **Lower room:** the two shaft columns are hollow from the ceiling down to one row above the floor; the floor row itself stays solid. A `oneWay` platform sits on every other row above the floor (for a 24Ã—10 room: platforms at rows `floorâˆ’2, âˆ’4, âˆ’6, âˆ’8`). The player hops platform to platform, then jumps through the open ceiling.
*   **Upper room:** the matching two floor cells at the shaft columns are hollow â€” a **hatch** â€” so the player pops up into the room. The same hatch is the hole they fall back down through; the floor around it stays solid.
*   **Spacing:** 2-row platform steps fit the player's single-jump envelope (2 up / 4 across â€” see `gameplay.md` Â§2.A); 2 columns wide makes the shaft read as a visible vertical gap. Keep markers (enemies/items) out of the shaft columns.
*   `generate_tmx.py` builds shafts automatically for grid layouts (`--grid-cols`/`--grid-rows`, see Â§9); hand-authored maps carve the same shape straight into the `collision` layer â€” hollow the two columns, place `oneWay` tiles on alternating rows, and open the two hatch cells in the upper room's floor.

---

## 4. The `objects` / `enemies` layers â€” markers

Both layers hold **map objects** (rectangles and/or tile objects). Each object is identified by its **`type`**, which the game reads from (in order): the object's own `type`/`Type` field, a custom property named `type`, or the **placed tile's** `type` property (for tile objects). Unknown/unhandled types are ignored.

Two ways to place a marker:

*   **Rectangle object** â€” draw a rectangle, then set its Type (e.g. `playerStart`) in the object properties. Size matters for `platform` (it defines the sprite + collision box); for everything else the rectangle is just a spawn point (position = its bottom-left in game coords).
*   **Tile object** â€” draw a tile from a tileset that already carries a `type` property. The `items.tsx` and `enemy.tsx` tilesets are pre-wired this way (e.g. the `coin` tiles, the `chest` tile, the animated `coin` tile, and the `enemy` tiles), so you can just paint the object with the stamp tool.

### Marker type reference

| `type` | Spawns | Custom properties | Notes |
|---|---|---|---|
| `playerStart` | The player | â€” | Exactly **one** per map, usually in the first room. Rectangle position = spawn point. |
| `coin` | Coin pickup | â€” | Collectible objects are **never** drawn from the Tiled tile sprite â€” the spawned entity always renders its own `Coin_01..06` atlas spin animation, regardless of the marker source. So you can place coins as plain rectangles OR stamp any `items.tsx` coin tile (static or animated); the on-screen result is identical. Every coin renders at **half a map tile** (`DEFAULT_COIN_SIZE * unitScale`, i.e. 64px on a 128px-tile map) **centered on the marker rect â€” the marker's drawn size is ignored** (it's a pure placement guide), so map coins and chest/enemy-dropped coins are always the same size. |
| `chest` | Chest (opens on melee strike) | `potionType` (string, optional) | Built from the `gfx/origin-game.atlas` `Chest_01_Locked`/`Chest_01_Unlocked` regions (128x128 â†’ one map tile), never from a Tiled tile sprite. Any `items.tsx` chest tile just marks the spot. Without `potionType`, the chest drops 2â€“6 coins. With `potionType` set (e.g. `healing`, `strength`, `speed`, `invulnerability`), it drops a single potion of that type instead of coins. |
| `torch` | Decorative torch | â€” | Flickers visually. |
| `dagger` | Dagger pickup | â€” | Collectible item. |
| `exitGate` | Exit gate / level transition (trigger only, no sprite) | `nextLevel` (string, optional), `isFinal` (string, default `"false"`) | The gate spawns a **logic-only** entity: a collision box (sized from the object rectangle) + optional level transition. **No gate art is drawn** â€” paint the door's decoration yourself in the `background`/`decoration`/`collision` layers. The gate is a real, interactive transition trigger only when it has a `nextLevel` **or** `isFinal="true"`; with neither it's purely decorative. `isFinal="true"` triggers the **Victory Screen** (`VictoryScreen`) instead of loading the next level â€” used on the last level of a world, and may omit `nextLevel` entirely (e.g. World 2's `level_10_final`). |
| `enemy` | Enemy | `enemyType` (string, default `"walker"`), `aiMode` (string), `speed` (float), `patrolRange` (float) | Put these on the `enemies` layer (or `objects`). Catalog: `walker` (goblin), `flyer` (mosquito), `shooter` (spider), `knight` (15 HP). See `resources/docs-ai/enemies.md`. |
| `trap` | Trap | `trapType` (string, default `"acidDrop"`), `direction` (string), `interval` (float), `speed` (float), `damage` (int), `duration` (float), `cooldown` (float), `pulseSpeed` (float) | Place on `objects`/`enemies`. `trapType` = `"acidDrop"` (dripping projectiles) or `"flame"` (pulsing fire). See the traps subsection below. |
| `platform` | Moving platform | `amplitudeX`, `amplitudeY`, `speed`, `phase`, `axis` (see Â§5) | The object **rectangle size defines both the sprite and collision box**. |
| (any other) | â€” | â€” | Ignored. `background`-type markers etc. won't spawn anything. |

### Traps â€” tile hazards & spawned trap entities

Traps come in two flavours: **tile-based hazards** (painted in the `collision` layer) and **spawned trap entities** (placed as `type="trap"` objects on the `objects`/`enemies` layer). Both deal 1 HP damage with no knockback and share the 2-second invulnerability grace period.

#### Tile-based hazards (spikes, lava, stalactites)

These are tiles in the `collision` layer with `hazard = true` on the tileset tile. They're not entities â€” `HazardSystem` iterates `hazardRects` built by `MapLoader` at level start. See Â§3.2 for placement rules.

| Tileset | Tile | Image | Damage shape | Notes |
|---|---|---|---|---|
| `hazards.tsx` tile 0 | spikes | `hazards/spikes.png` (128Ã—128) | Full tile | Standard floor/ceiling spikes |
| `hazards.tsx` tile 1 | lava | `hazards/lava.png` (128Ã—128) | Full tile | Lava pool |
| `dungeon_tiles.tsx` tile 32 | dungeon spikes | `dungeon/spikes.png` (128Ã—128) | Narrow bottom (0,96,128,32) | Wall-mounted spikes â€” hitbox only the bottom strip |
| `dungeon_tiles.tsx` tile 50 | stalactite | `caves/bg-stalactite.png` (128Ã—128) | Narrow top (0,0,128,32) | Hanging stalactite â€” hitbox only the top tip |

All four deal **1 HP**, no knockback, same grace period as every other damage source.

#### Spawned trap entities (`type="trap"`)

Place a rectangle object on the `objects` or `enemies` layer with `type="trap"`. Add a `trapType` property to pick the variant. The rectangle position is the spawn point; size is ignored (collision is defined by the trap code).

**Trap A: Acid/Lava Drop Spawner (`trapType = "acidDrop"`)**

An invisible entity that periodically shoots projectiles in the configured direction. The spawner is invisible â€” only the projectiles are visible.

| Property | Type | Default | Description |
|---|---|---|---|
| `trapType` | string | `"acidDrop"` | Required. Identifies this as an acid-drop spawner. |
| `direction` | string | `"down"` | Projectile direction: `"up"` (lava geyser from floor), `"down"` (acid drip from ceiling), `"left"`/`"right"` (side-mounted sprayer). |
| `interval` | float | `2.0` | Seconds between projectile spawns. |
| `speed` | float | `200` | Projectile velocity in world-units/s (before unitScale). |
| `damage` | int | `1` | HP dealt on contact. |

Projectile behaviour (**down-direction drip** â€” the common case): the spawner is a **visible animated acid tube** (atlas `acid_tube1..4`, 64px sprites rendered at `AcidTubeScale = 0.25f Ã— unitScale` â€” exactly one 128px tile). Each interval it plays a one-shot **discharging animation**; only when that completes does it release a drop (so the drop visually forms/detaches from the tube). Each drop spawns **centered on the tube** (the marker + half a tile, no editor offset). The drop then **hangs at the spawn point** for ~0.15 s (the `dripBuild` effect, so it visibly bulges before releasing), then falls, **accelerating under gravity** (`ACID_DROP_GRAVITY`) like a heavy droplet rather than at constant speed. On landing on a floor/wall it **turns into an `ACID_POOL`** (a ~1 tile wide puddle from the atlas `acid_blob1..7` clip, 128Ã—128px sprites flattened) that plays the **splash animation once** (`SPLASHING` state, `PlayMode.NORMAL`, `0.05s`/frame â†’ ~0.35s splash) and then holds the final splat frame for the remaining pool life â€” that deals 1 HP on contact and lingers ~1.5 s before vanishing. **Up/left/right directions** keep constant speed (no gravity) and simply vanish on wall/ceiling contact (no pool). Uses the `acid_drop` atlas region (32Ã—32 single frame, rendered at `AcidDropScale = 0.25f Ã— unitScale` â€” half a tile); all drops are removed after 5 s lifetime. All three acid visuals come from the game atlas â€” the old standalone `gfx/acid_drop.png`/`gfx/acid_pool.png` are no longer used.

Because the drop often spawns inside/on a wall cell (a ceiling fixture), drops get a short **spawn-grace window** (~0.12 s, started the moment the drip releases) during which wall-collision culling is skipped â€” letting a drop clear the fixture it spawned on instead of being removed on its first frame.

> **Note:** the acid tile in `assets/maps/tileset/hazards.tsx` carries **no standalone image** â€” the visible tube (and any marker preview) comes from the runtime `acid_tube1..4` sprites in `gfx/origin-game.atlas`, not from a TSX-referenced PNG. The old `acid_trap.png` image + Tile Collision Editor point were removed with the drop-offset feature (drops now always center on the tube).

**Trap B: Flame Trap (`trapType = "flame"`)**

An animated flame that pulses between small and large on a timed cycle. The collision box scales with the visual â€” anchored at the source wall/floor/ceiling and extending outward in the configured direction.

| Property | Type | Default | Description |
|---|---|---|---|
| `trapType` | string | `"flame"` | Required. Identifies this as a flame trap. |
| `direction` | string | `"down"` | Flame direction: `"down"` (hangs from ceiling), `"up"` (rises from floor), `"left"`/`"right"` (wall-mounted, rotated 270Â°/90Â°). |
| `duration` | float | `2.0` | Seconds the flame is ON (growing from min to max scale). |
| `cooldown` | float | `1.5` | Seconds the flame is OFF between pulses. |
| `pulseSpeed` | float | `2.0` | Oscillation speed for the grow/shrink animation. |
| `damage` | int | `1` | HP dealt on contact. |

Visual: `fire1..10` atlas sprites (256Ã—256), animated across the ON phase. Initial cooldown is randomised (`0` to `cooldown`) so multiple flames in a room don't pulse in sync.

**Planned but not yet coded:** The atlas contains `blade1..7` and `lightning1..9` sprite regions reserved for future blade-trap and lightning-trap types. No `TrapType` enum, no `EntityFactory` code, and no system logic exist for them yet.

#### Trap room awareness

Both spawners and flames check `roomIndex` against `RoomState.activeRoomIndex` each frame. In inactive rooms: spawners pause their timers, drops continue moving (they're transient), flames freeze their pulse cycle. Matches enemy freeze behaviour.

#### Trap debugging

Trap AABBs appear in the SHIFT+D collision debug overlay (lime-coloured boxes) like any `CollisionComponent` entity. Flame traps show dynamically scaling boxes as they pulse.

### Â§4.5 Effect property (`effect` property)

Tiles on **any tile layer** (background, decoration, collision, etc.) can carry an `effect` property to spawn a runtime effect entity at that tile's position. The tile's own sprite is rendered by the Tiled map renderer â€” the effect entity carries **no texture**, only the effect component (e.g. `LightComponent`). This means what you paint in Tiled is what renders in-game (true WYSIWYG). The tile layer determines draw order (background = behind player, decoration = in front).

**How it works:**
1. Stamp a tile with `effect="light"` (or future `"particle"`, `"sound"`) on any tile layer.
2. At level load, `MapLoader.scanEffectLayers()` iterates every tile layer in the map, reads the `effect` property from each cell, and records world positions.
3. `EntityFactory.spawnEffects()` creates minimal effect entities at those positions.

**Supported effect types:**

| `effect` value | Component added | Behaviour |
|---|---|---|
| `"light"` | `LightComponent` | Flickering glow halo. Light center is read from the tile's **collision-editor shape** (draw a Point in Tiled's Tile Collision Editor on the tile to set the exact flame/glow position â€” true WYSIWYG). If no shape is drawn, the light defaults to the tile center. Per-tile properties: `lightRadius` (default 96), `lightColor` (RGB hex like `"FF8040"`), `lightFlickerSpeed` (default 6 rad/s). |
| `"particle"` | *(planned)* | Future: particle emitter at the tile position. |
| `"sound"` | *(planned)* | Future: positional sound emitter at the tile position. |

**Per-tile properties** (set on the tileset tile, e.g. `items.tsx`):

| Property | Type | Default | Effect |
|---|---|---|---|
| `effect` | string | â€” | Effect type: `"light"`, `"particle"`, `"sound"`. |
| `lightRadius` | float | `96` | (light only) Halo radius in world units. |
| `lightColor` | string | warm orange | (light only) RGB hex (`"FF8040"`) or RGBA hex (`"FF8040FF"`). |
| `lightFlickerSpeed` | float | `6` | (light only) Flicker oscillation speed in rad/s. |

**Pre-wired tiles in `items.tsx`:**

| Tile | `effect` | Image | Notes |
|---|---|---|---|
| id 20 | `"light"` | `tiles/bg/torch.png` (128Ã—156) | Wall torch. Default radius 96. Draw a Point in the Tile Collision Editor to position the light center (e.g. at the flame tip). |

**Adding a new effect tile:**
1. Add a tile to `items.tsx` (or any tileset) with the desired image.
2. Set `effect="light"` on the tile properties.
3. Optionally override `lightRadius` / `lightColor` / `lightFlickerSpeed`.
4. Open the tile in Tiled's **Tile Collision Editor** and draw a Point where the light center should be (e.g. at the flame tip for a torch). The point's coordinates are in tile-local pixel space â€” the light spawns at that exact world position.
5. Stamp the tile on any tile layer in Tiled â€” done.

**Adding a new effect type** (e.g. `"particle"`):
1. Add a branch in `EntityFactory.spawnEffects()` for the new `effectType`.
2. Create a `create*Effect()` method that returns an entity with the appropriate component(s).
3. Update this documentation section.

**Backward compatibility:** The existing `type="torch"` rectangle markers on the `objects` layer still work via the `spawnObjects()` switch. The new tile-property approach is an alternative â€” torches can be placed either way. Maps can mix both approaches.

### World 1 â€” level content inventory

The shipped `maps/world1/level_01..10.tmx` chain. Enemy counts mix `enemyType`s (e.g. `3 enemies (2 walkers, 1 shooter)`); difficulty ramps from the tutorial-sized 16Ã—12 openings (levels 01/02/10) up to the hardest, the loop-back final level 10 (which includes the only `knight` on any map). Content scoping baseline is roughly **2â€“3 enemies + a chest + 2â€“4 coins + torches per room**, matching the `generate_tmx.py` density; any future content edits should keep this table accurate.

| Level | Rooms | Enemies | Chests | Coins | Daggers | Torches | Platforms |
|---|---|---|---|---|---|---|---|
| `level_01` | 1 (16Ã—12) | 3 (2 walkers, 1 shooter) | 1 | 3 | â€” | 3 | 2 |
| `level_02` | 1 (16Ã—12) | 3 (2 walkers, 1 shooter) | 1 | 4 | â€” | 3 | 2 |
| `level_03` | 2 (32Ã—17) | 5 walkers | 1 | 1 | 1 | 3 | â€” |
| `level_04` | 1 whole-map (32Ã—17) | 3 (2 walkers, 1 shooter) | 6 | 13 | â€” | 2 | â€” |
| `level_05` | 3 + secret (120Ã—17) | 6 (4 walkers, 2 flyers) | 3 (+1 secret) | 1 (+2 secret) | â€” | â€” | â€” |
| `level_06` | 1 (30Ã—17) | 6 (5 walkers, 1 flyer) | 1 | 4 | â€” | 2 | â€” |
| `level_07` | 1 (30Ã—17) | 3 (2 walkers, 1 flyer) | 1 | 4 | â€” | 1 | â€” |
| `level_08` | 1 + secret chamber (30Ã—17) | 2 shooters | 2 (+1 secret) | 1 (+3 secret) | â€” | â€” | 13 |
| `level_09` | 2 + secret alcove (32Ã—17) | 5 (4 walkers, 1 flyer) | 1 (+1 secret) | 3 (+2 secret) | â€” | 4 | â€” |
| `level_10` | 1 (16Ã—12) | 4 (1 walker, 2 shooters, 1 knight) | 1 | 3 | â€” | 2 | 2 |

The secret-room counts (marked *secret*) are deferred markers carrying `secretRoom="secret_room"` (Â§3.5). Levels 01/02/10 place their enemies in the `objects` layer (they have no separate `enemies` layer). Level 08's 13 "platforms" are **static `collision`-layer blocks** (11 solid gid-2 groups + 2 one-way gid-1 tiles) forming a jump/staircase course from the spawn up to the secret-room roof â€” see the `collision` layer of `assets/maps/world1/level_08.tmx`.

### World 2 â€” level content inventory (originally generated; now hand-maintained)

All of `maps/world2/level_01..10.tmx` were **initially generated** by `.junie/skills/tmx-map-generator/scripts/generate_tmx.py` (mobile-oriented 24Ã—10-tile rooms, dead-zone scroll on phones, flip on desktop). They are now **living checked-in assets** and are **never regenerated** â€” any change (wiring, gates, layout) is made by editing the `.tmx` directly. Each level is a fully-connected grid (every adjacent room pair has a doorway or platform shaft), `playerStart` sits in the bottom-left room, and the `exitGate` stands in the **top-right** room, chaining `level_N â†’ level_N+1`. The final level is `level_10_final.tmx`, whose exit gate carries `isFinal="true"` and **no** `nextLevel`, so completing it triggers the World-2 victory flow instead of chaining on. Content per level is a random scatter (0â€“2 enemies + 0â€“3 items per room). Every map also paints a `type="door"` tile (dungeon `door.png`, from `tileset/dungeon_tiles.tsx`) on the `decoration` layer on the row **just above the floor** (its bottom edge resting on the `collision` floor surface) beneath the `playerStart` and beneath the `exitGate` (the gate's column, `col_end-2`), so both doors stand on the floor instead of looking like they float or sink into it; the tile image is 2 tiles tall and renders upward from its single cell.

| Level | Map (tiles) | Rooms grid | Exit â†’ |
|---|---|---|---|
| `level_01` | 48Ã—20 | 2Ã—2 | `level_02` |
| `level_02` | 72Ã—10 | 3Ã—1 | `level_03` |
| `level_03` | 72Ã—20 | 3Ã—2 | `level_04` |
| `level_04` | 48Ã—20 | 2Ã—2 | `level_05` |
| `level_05` | 120Ã—20 | 5Ã—2 | `level_06` |
| `level_06` | 96Ã—40 | 4Ã—4 | `level_07` |
| `level_07` | 72Ã—10 | 3Ã—1 | `level_08` |
| `level_08` | 72Ã—20 | 3Ã—2 | `level_09` |
| `level_09` | 120Ã—20 | 5Ã—2 | `level_10_final` |
| `level_10_final` | â€” | â€” | victory (`isFinal`) |

Regenerating any of these with the generator (seeds `1001`â€“`1010` respectively) reproduces them byte-identically; changing a seed changes only the enemy/item scatter, not the room grid or exit wiring.

---

## 5. Custom properties â€” the full reference

All properties are read as `float`/`string`/`boolean` and tolerate being set as either int or string in Tiled. Units are **world px** unless noted.

### Object/tile properties (markers)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `type` | string | â€” | The marker discriminator (`playerStart`, `coin`, `chest`, `torch`, `dagger`, `exitGate`, `enemy`, `platform`). |
| `enemyType` | string | `"walker"` | Picks the enemy variant: `walker` / `flyer` / `shooter` / `knight`. See `enemies.md`. |
| `aiMode` | string | `"patrol"` | Enemy patrol behavior: `"side-to-side"` (or `"sidetoside"`, case-insensitive) â†’ endless walking that turns only on walls/ledges/hazards; anything else/absent â†’ origin-bounded `patrol`. Flyers ignore it (never grounded). See `enemies.md`. |
| `speed` | float | per-type default (`20`) | (enemy only) Horizontal patrol speed override, world px/s. Applied before the `unitScale` (tile-size) scaling. |
| `patrolRange` | float | per-type default (`64`) | (enemy only) Patrol-range override, world px. Only used in `PATROL` mode (`SIDE_TO_SIDE` ignores it). Applied before the `unitScale` scaling. |
| `nextLevel` | string | â€” | (exitGate only) The next `.tmx` path **relative to the `assets/` folder**, e.g. `maps/world1/level_03.tmx`. Cycles to that map on interaction. |
| `trapType` | string | `"acidDrop"` | (trap only) Trap variant: `"acidDrop"` (spawner + projectiles) or `"flame"` (pulsing fire). |
| `direction` | string | `"down"` | (trap only) Direction the trap fires/extends: `"up"`, `"down"`, `"left"`, `"right"`. |
| `interval` | float | `2.0` | (acid drop spawner only) Seconds between projectile spawns. |
| `speed` | float | `200` | (acid drop only) Projectile velocity in world-units/s (before unitScale). |
| `duration` | float | `2.0` | (flame only) Seconds the flame stays ON. |
| `cooldown` | float | `1.5` | (flame only) Seconds the flame stays OFF between pulses. |
| `pulseSpeed` | float | `2.0` | (flame only) Oscillation speed for grow/shrink animation. |
| `potionType` | string | â€” | (chest only) If set, the chest drops a potion of this type instead of coins. Valid values: `healing`, `strength`, `speed`, `invulnerability`. Omit for a standard coin chest. |

### Moving-platform properties (the `platform` rectangle)

A platform oscillates around its spawn position with a sine wave:

```
pos = base + amplitude * sin(angle + phase)
angle += speed * dt        (each frame)
```

| Property | Type | Default | Meaning |
|---|---|---|---|
| `amplitudeX` | float | `0` | How far (world px) the platform travels **horizontally** away from its spawn, each side. Peak-to-peak travel is 2Ã— this. |
| `amplitudeY` | float | `0` | Same, but **vertically** (up and down). |
| `speed` | float | `1` | Oscillation speed in **radians per second**. `2Ï€` (â‰ˆ6.28) = one full up-down cycle per second; `1` is slow; `Ï€` (â‰ˆ3.14) is a ~2s cycle. |
| `phase` | float | `0` | Starting angle offset in **radians**. Use different phases on several platforms to desync them. |
| `axis` | string | â€” | Convenience shortcut: `"x"` zeroes the vertical axis, `"y"` zeroes the horizontal. If you set `amplitudeX`/`amplitudeY` explicitly, those win. |

**Examples** (from `assets/maps/world1/level_01.tmx`):

*   Horizontal shuttle: rectangle `type="platform"` + `amplitudeX=80` + `speed=1` â†’ slides 80px left/right of spawn.
*   Vertical lift: `amplitudeY=80` + `speed=1` â†’ bobs 80px up/down.

**Gotchas:**

*   A platform with no `amplitude`/`axis` is a static block (still lands on it, still collides).
*   The platform only **moves** while its owning room is the active one (`RoomState.activeRoomIndex`); it freezes when you're in another room. The player can still stand on a frozen platform.
*   Place the platform rectangle *inside* a `Rooms` rectangle so it's tied to that room.

### Tile properties (collision / tilesets)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `solid` | boolean | `true` | Set `false` on a tileset tile to make that tile **non-blocking** even in the `collision` layer (the passage-doorway use case, Â§3.1). |
| `oneWay` | boolean | `false` | Set `true` on a tileset tile to make it a **drop-through platform**: the player gets top-only solidity (jump up through; drop with the `v` button / `S` / `DOWN`), while enemies and popped items treat it as a fully-solid tile (all four sides) and flying enemies pass through (see Â§3.3). Ignored when the tile is `hazard`. |
| `hazard` | boolean | `false` | Set `true` on a tileset tile to make it a **non-solid hazard** (spikes/lava): 1 HP on touch, no knockback, invulnerability grace (see Â§3.2). Wins over `solid`/`oneWay`. |
| `secret` | boolean | `false` | Set `true` on a **solid** tileset tile to make it a **breakable secret wall**: solid until the player melee-strikes it, then it disappears and opens the way (see Â§3.4). Ignored on `hazard` and `solid = false` tiles. |
| `secretRoom` | string | â€” | On a **secret wall tile**: names the `Rooms` rectangle (matched by **name**) that this wall protects, turning the plain secret wall into a **hidden secret room** entry â€” breaking the wall reveals the whole room (Â§3.5). On a **map object marker** (`objects`/`enemies` layers): defers that marker â€” it is partitioned out of the normal spawn layers and only spawned when its named room is revealed (Â§3.5). |
| `type` | string | â€” | On tileset tiles: lets you paint tile objects that auto-spawn as markers (`coin`, `chest`, `enemy`â€¦). |
| `effect` | string | â€” | On **tile layer** tiles: spawns a runtime effect entity at the tile position (see Â§4.5). Values: `"light"` (flickering glow halo), `"particle"` (planned), `"sound"` (planned). |
| `lightRadius` | float | `96` | (effect="light" only) Halo radius in world units. |
| `lightColor` | string | warm orange | (effect="light" only) RGB hex (`"FF8040"`) or RGBA hex (`"FF8040FF"`). |
| `lightFlickerSpeed` | float | `6` | (effect="light" only) Flicker oscillation speed in rad/s. |

### Room properties (the `Rooms` layer)

| Property | Type | Default | Meaning |
|---|---|---|---|
| `camera` | string | `auto` | Camera mode for that room: `flip` (always static, center-framed), `scroll` (always dead-zone scroll), or omit for `auto` (infer from size â€” see Â§6). |

---

## 6. The `Rooms` layer & camera

Draw one **rectangle per room** in the `Rooms` object layer (a `RectangleMapObject`; plain shape, no special type needed). `MapLoader.getRooms()` turns each into a `Room`, and `CameraSystem` uses whichever room currently contains the player for both **camera framing** and **enemy/platform activation**.
*   **No `Rooms` layer at all**, or a **`Rooms` layer with no rectangles** â†’ the game falls back to a single room covering the whole map (the whole map scrolls like one big room). Useful for one-screen test maps, but you lose per-room camera/enemy control.

### Camera behavior (per axis)

For each axis (X and Y) independently (the "viewport size" is always the **effective** size â€” `camera.viewportWidth/Height Ã— camera.zoom`):

*   **Flip axis** (room size â‰¤ effective viewport size on that axis, or forced via `camera="flip"`): camera locks to the room's **center** and **snaps instantly** when the player enters. Classic Castlevania-style screen change. The viewport may overshoot a room smaller than the screen.
*   **Scroll axis** (room size > effective viewport size on that axis, or forced via `camera="scroll"`): dead-zone scrolling. The camera holds still while the player roams inside a margin from each screen edge â€” `GameConstants.CAMERA_SCROLL_MARGIN` at zoom 1, or 30% of the effective (zoomed) view per axis once the camera zooms in (`GameConstants.MOBILE_SCROLL_MARGIN_FRACTION`) â€” then scrolls only when the player crosses that margin, clamped so the screen never leaves the room.
*   `camera="flip"` forces static framing even for big rooms; `camera="scroll"` forces scrolling, but a room still smaller than the viewport on an axis always centers (you can't scroll what's smaller than the screen).
*   **No smooth follow / no lerp.** Transitions are instant snaps. On level start the camera frames the starting room via `CameraSystem.snapToRoom(...)` (flip rooms center, scroll rooms put the player in view) â€” not the player.

> **Zoom note:** The game's default layout zooms the camera in everywhere â€” `LayoutMode.BAND_ZOOM` is the shipped default for desktop, mobile, and tablet (toggled from the Pause dialog, plus the faked `DeviceClass` previews; see `com.axehigh.platformer.ui.LayoutMode`). A zoomed camera shows a smaller effective frame, so an otherwise screen-sized 30Ã—17 room becomes *bigger than the frame* and flips to dead-zone scroll on every platform â€” flip-screen framing is only preserved if a room is authored smaller than the effective (zoomed) view or forced via the room's `camera="flip"` property. The zoomed dead-zone margin is a 30% fraction of the visible view (`MOBILE_SCROLL_MARGIN_FRACTION`) so the camera starts tracking well before the player reaches a screen edge. This needs **no map change**: rooms stay as authored, the camera just follows.

### Design tips

*   Screen-sized rooms: make a room **exactly 30Ã—17 tiles** (3840Ã—2176 px at 128px tiles) â†’ pure flip-screen, no scrolling.
*   Rooms **wider/taller than 30Ã—17 tiles** â†’ dead-zone scroll; give the player room to roam.
*   Small rooms (smaller than the screen) are fine â€” the camera centers on them and shows a little of the next area.
*   Every `Rooms` rectangle should **contain the `playerStart` marker** (and each enemy/platform's spawn), or that entity won't be tied to any room (`roomIndex = -1`, always active).
*   **Secret rooms:** carve a small room into the `collision` layer, drop a `Rooms` rectangle over it, and place pickups/enemies inside. `RoomState.findRoomIndexContaining(...)` returns the **first** room rectangle containing a point, so put the secret room **above** (before) the enclosing room in the layer for it to win the camera framing (a chamber carved *inside* a room is emitted first for exactly this reason). To make it *hidden until broken* (see Â§3.5): name the rect (e.g. `secret_room`), paint a `secret_hide` veil over its footprint (leave the guard cells un-veiled for an inside chamber, so the crack is visible), tag the entry-wall tile with `secretRoom="secret_room"`, and tag the interior markers with the same `secretRoom` object property so they spawn on reveal (see `secret_room` in `assets/maps/world1/level_05.tmx` and the inside chamber in `assets/maps/world1/level_08.tmx`).

---

## 7. Step-by-step: build a new level

1.  **Map setup.** In Tiled: New map, orthogonal, tile size **128Ã—128** (or matching your chain), infinite off, CSV tile format. Add tilesets from `assets/maps/tileset/`: `dungeon_tiles.tsx` for terrain (solid walls, one-way platforms, and spike hazards are all tiles with baked-in `oneWay`/`hazard` properties), `items.tsx` for pickups, `enemy.tsx` for enemies, `secret_wall.tsx` for breakable secret walls.
2.  **`background` layer.** Paint the decorative backdrop (walls, pillars, windows). Anything goes â€” it never blocks.
3.  **`collision` layer.** Paint the solid geometry: floor, walls, platforms. Leave gaps where you want jumps. Use `drop_platform.tsx` tiles for ledges you want to drop through, `hazards.tsx` tiles for spike/lava damage zones (their behavior is baked into the tileset tile properties â€” see Â§3.2, Â§3.3), and `secret_wall.tsx` tiles for breakable secret walls (Â§3.4). For any room-to-room doorway, use your `solid = false` passage tile (see Â§3.1). Keep the map's outer border solid. For a hidden secret room: seal the room with solid tiles, and paint its breakable entry wall from a tileset tile carrying `secret=true` + `secretRoom="<rect name>"` (Â§3.5).
4.  **`decoration` layer** (optional). Foreground decor that draws on top. Stamp tiles with `effect="light"` (e.g. the torch from `items.tsx`) to add flickering glow halos â€” the tile renders visually, the effect entity adds the light (Â§4.5).
5.  **`objects` layer.**
    *   Draw one `playerStart` rectangle where the player should spawn.
    *   Scatter `coin` / `chest` / `dagger` / `torch` markers (either as rectangles with the right `type`, or stamp the pre-typed tiles from `items.tsx`).
    *   Add moving platforms as **rectangle objects** with `type="platform"` and the Â§5 properties.
    *   Add the `exitGate` at the far end with `nextLevel = maps/<path>/<next>.tmx` (or leave it decorative for a final level). The object rectangle sizes the trigger zone; the gate itself draws no sprite, so paint the door's decoration into the map layers (see Â§4).
    *   Add `trap` markers for hazards (acid drops, flames). Place them on the `objects` or `enemies` layer with `type="trap"` and the relevant `trapType`/`direction`/timing properties (see the traps subsection in Â§4).
6.  **`enemies` layer.** Place `enemy` markers (stamp from `enemy.tsx` or draw rectangles + `enemyType`). Refer to `enemies.md` for behavior tuning (flyers need patrol range clear of walls, shooters only fire in their own room).
7.  **`Rooms` layer.** Draw one rectangle per room (see Â§6). Add `camera="flip"`/`"scroll"` only when you want to override the size-based default. **Name a secret room's rectangle** (e.g. `secret_room`) and match that name in the entry wall's `secretRoom` property and the interior markers' `secretRoom` property (Â§3.5).
8.  **`secret_hide` layer** (only for hidden secret rooms). Above every other tile layer, paint rock tiles over the whole secret-room footprint so it looks like solid wall from outside (Â§3.5). No collision â€” this layer is purely visual.
9.  **Register the level.** Add `LEVELS.add(new LevelDefinition("my_key", "My Level Name", "maps/my_level.tmx"))` in `core/.../map/LevelCatalog.java`.
10. **Test.** Run the desktop build. Turn on collision debug (SHIFT+D, or the Pause dialog) to verify the collision AABBs and room rects look right.

---

## 8. Common pitfalls (checklist when something feels wrong)

*   **Layer name typo** â€” layers are matched by exact name (`collision`, `objects`, `enemies`, `Rooms`, `background`, `decoration`, `secret_hide`). A typo = silently ignored layer.
*   **Doorway is an invisible wall** â€” the passage tile doesn't have `solid = false`. Fix it on the tileset tile (this affects all maps using that tileset).
*   **Wrong `type` spelling** â€” `playerStart`, `coin`, `chest`, `torch`, `dagger`, `exitGate`, `enemy`, `trap`, `platform` are exact. Anything else spawns nothing.
*   **Platform never moves** â€” it's outside every `Rooms` rectangle and/or has no `amplitudeX/Y` and no `axis`. Give it an amplitude and place it inside a room.
*   **Platform sprite is huge/stretched** â€” the platform's collision box and sprite are its rectangle size; keep the rectangle near the tile/asset's aspect ratio.
*   **Exit gate does nothing** â€” `nextLevel` is missing (or the path is wrong). Paths are relative to `assets/`.
*   **Enemy behaves oddly** â€” check `enemyType` spelling; flyers will fly into walls if `patrolRange` isn't clear of walls (they have no automatic wall avoidance â€” see `enemies.md`).
*   **Camera snaps wrong room / player not in view** â€” `playerStart` isn't inside the intended `Rooms` rectangle, or the rooms don't tile the map.
*   **Unexpectedly scrolling when you wanted flip** â€” the room is larger than the screen (30Ã—17 tiles); add `camera="flip"` to force static framing.
*   **Spikes/lava never hurt a standing player** â€” the player's collision box is smaller than a tile and its feet sit on the floor, so hazards must be placed where the player's body travels *through* them (see Â§3.2).
*   **Acid drops pass through walls** â€” drops disappear only on collision-layer contact; if the spawner is placed with a clear path through a passage, the drop sails through. Place spawners so their line of fire hits a solid wall/floor/ceiling. Drops also live for 5 s â€” long enough to cross a room.
*   **Flame trap too fast/too slow** â€” `pulseSpeed` controls the grow/shrink animation speed, not the on/off cycle. The on/off rhythm is `duration` (on) + `cooldown` (off). Multiple flames in a room start at random phases; to stagger them, use different `cooldown` values.
*   **Effect tile does nothing** â€” `effect="light"` only works on **tile layers** (background, decoration, collision, etc.), not the `objects` layer. If you stamp the tile on `objects`, it spawns nothing via the effect system (though `type="torch"` on objects still works via the old path). `MapLoader.scanEffectLayers()` scans all tile layers.
*   **Light halo offset wrong** â€” the light center is read from the tile's collision-editor shape (draw a Point in Tiled's Tile Collision Editor). If no shape is drawn, it defaults to the tile center. Use a Point to precisely position the flame/glow.
*   **Drop platform behaves like a wall** â€” the tile doesn't carry `oneWay = true` on the tileset tile; or the platform's collision was authored as a plain solid tile. Also remember one-way platforms are drop-through **only for the player** â€” enemies and popped items stand on them like solid tiles, and flying enemies fly through them.
*   **Secret wall doesn't break** â€” the tile isn't flagged `secret = true` on the tileset tile (a plain solid tile won't break); also remember one swing breaks at most one tile, and the strike hitbox must actually reach the wall (it's a solid block until broken).
*   **Secret room spawns loot from the start / never reveals** â€” the interior markers don't carry the `secretRoom` object property (so they spawn normally instead of being deferred), or the entry wall's tile doesn't carry `secretRoom` naming the `Rooms` rect (so breaking it never triggers a reveal). Remember the wall's `secretRoom` is a **tile** property read from the tile itself â€” `cell.setTile(null)` wipes it, which is why the name can't live on the cell.
*   **Secret room is visible through the wall before reveal** â€” the `secret_hide` layer doesn't cover the full room footprint (or the room's `Rooms` rect is bigger than the painted veil). The veil must tile every cell over the rect, and it must sit at the top of the layer stack.

---

## 9. Related tooling & docs

*   `.opencode/skills/tmx-map-generator` â€” generates a standalone prototype `.tmx` (a linear chain of rooms with enemies/items and walk-through doorways, plus a hidden secret room â€” either appended to the right, or, with `--inside-secret`, carved as a 6Ã—8-tile chamber inside the last room) so you can test a layout without hand-tracing collision CSVs. Rooms **default to 24Ã—10 tiles** (mobile-oriented, dead-zone scroll under the `BAND_ZOOM` camera); pass `--room-width 30 --room-height 17` for whole-screen desktop rooms. `--platforms N` additionally decorates every room with a deterministic, always-jumpable staircase of N floating one-way platforms (2 rows up / 2 cols right per step â€” within the player's single-jump envelope of 2 up / 4 across â€” a `bg-*` filler tile behind each, and a coin on the top platform) while keeping the flat floor intact â€” see the skill's SKILL.md. **Templates:** `--template NAME[,ROOM[,COL]]` (repeatable) and `--template-pick N` stamp ASCII-art courses from `scripts/templates/*.tmpl` (e.g. `staircase`, `chasm-bridge`, `hazard-strip`) floor-anchored into rooms â€” the generator then runs jump-aware design checks (supported ground + reachability within the jump envelope, warnings only). **Grid layouts:** `--grid-cols C --grid-rows R` tiles `CÃ—R` rooms over the map (width Ã— height = `CÃ—W Ã— RÃ—H`), links horizontal neighbours with doorways and vertical neighbours with one-way **platform shafts** (Â§3.6), and places `playerStart` in the bottom-left room; grid maps use `--no-secret` to omit the secret room (required for multi-row grids). Run `generate_tmx.py` (from `assets/maps/world1`) with `--rooms N --seed S --out level_05.tmx` or `--grid-cols 2 --grid-rows 2 --no-secret --seed S --out level_01.tmx`; see the skill's SKILL.md for CLI + conventions. It reads the collision tileset from `world1/dungeon_tiles.tsx`, the item/enemy tilesets from `world1/`, and clones `world1/secret_wall.tsx` inline for the secret room's entry wall; output is standalone (no `LevelCatalog`/exit-gate wiring by design â€” the exit gate is added by hand).
*   `resources/docs-ai/enemies.md` â€” enemy catalog, stats, and how to add new types.
*   `resources/docs-ai/gameplay.md` Â§AB â€” trap design spec (acid drops, flames, damage resolution).
*   `resources/docs-ai/ashley-ecs.md` â€” the `MapLoader`/`EntityFactory`/`Room`/`RoomState`/`CameraSystem` code shape and priorities.
*   `resources/docs-ai/gameplay.md` â€” movement/combat mechanics and how they read map data.

