#!/usr/bin/env python3
"""Generates a standalone, hand-authored-style .tmx map for the axehigh platformer:
a linear left-to-right chain of whole-screen rooms (default 1) -- or, with --grid-cols /
--grid-rows, a 2D grid of rooms -- each fully enclosed by solid collision tiles with a
walk-through doorway connecting it to its horizontal neighbour(s) and a one-way platform
shaft connecting it to its vertical neighbour(s), plus a small random scattering of
enemies/items.

Conventions are read live from the project's external tilesets (dungeon_tiles.tsx,
items.tsx, enemy.tsx) -- solid / one-way / hazard / passage tiles are resolved by their
tile properties, and item/enemy markers by their tile `type` / `enemyType` -- so the
output tracks the tilesets instead of hard-coded gids. See
`resources/docs-ai/map-design-for-tiled.md` for the layer/property reference.

Stdlib only (argparse, random, os, xml.etree.ElementTree) -- no external deps.

Usage:
    # Run from the assets/maps directory -- the *.tsx tilesets live in assets/maps/tileset/
    # and are resolved relative to the CWD (--tilesets-dir defaults to "tileset").
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 3 \
        --tilesets-dir tileset --out world_demo/generated_room.tmx --seed 42
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 1 --inside-secret \
        --tilesets-dir tileset --out world_demo/generated_room.tmx --seed 42
    # mobile-oriented rooms: 24 wide x 10 high tiles (scroll under the BAND_ZOOM camera)
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 2 --room-width 24 \
        --room-height 10 --tilesets-dir tileset --out world_demo/generated_mobile.tmx --seed 42
    # 2x2 grid of rooms with vertical platform shafts (no secret room):
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --grid-cols 2 --grid-rows 2 \
        --room-width 24 --room-height 10 --no-secret --tilesets-dir tileset \
        --out world_demo/generated_grid.tmx --seed 42
    # ...and chain-connected to the next level via an exit gate in the far room:
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --grid-cols 2 --grid-rows 2 \
        --room-width 24 --room-height 10 --no-secret --tilesets-dir tileset \
        --exit-next maps/world2/level_02.tmx --out world_demo/generated_grid.tmx --seed 42
    # floating one-way platform staircases in each room (deterministic, always-jumpable):
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 3 --platforms 3 \
        --tilesets-dir tileset --out world_demo/generated_platforming.tmx --seed 42

Or (library use; run from assets/maps or pass an absolute tilesets_dir):
    from generate_tmx import generate_map, validate_map
    generate_map("assets/maps/world_demo/generated_room.tmx", room_count=3, seed=42)
    generate_map("assets/maps/world_demo/generated_room.tmx", room_count=1, seed=42, inside_secret=True)
    generate_map("assets/maps/world_demo/generated_room.tmx", room_count=2, seed=42, room_width=24, room_height=10)
    generate_map("assets/maps/world_demo/generated_grid.tmx", grid_cols=2, grid_rows=2,
                 room_width=24, room_height=10, no_secret=True, seed=42)
    problems = validate_map("assets/maps/world_demo/generated_room.tmx")
"""

import argparse
import os
import random
import re
import xml.etree.ElementTree as ET

TILE_SIZE = 128
#: Default whole-screen room size in tiles: 30x17 matches the 30x17-tile viewport (3840x2176px
#: at 128px tiles). Both are overridable via --room-width/--room-height (e.g. 24x10 tiles for
#: mobile-oriented rooms that dead-zone scroll under the BAND_ZOOM camera).
SCREEN_TILE_W = 30
SCREEN_TILE_H = 17
PASSAGE_HEIGHT_TILES = 2

# The hidden secret chamber carved inside a room (`--inside-secret`): a walled box sitting on the
# room floor, flush against the room's left wall. Footprint is CHAMBER_W x CHAMBER_H tiles; the
# interior (cavity) is the footprint minus its boundary wall.
CHAMBER_W = 6
CHAMBER_H = 8

# Size of the exit-gate trigger rectangle emitted with --exit-next, matching the hand-authored
# world-1 gates (~140x152 px) so LevelExitSystem builds a comparable proximity sensor.
EXIT_GATE_W = 140
EXIT_GATE_H = 152

COLLISION_TILESET = "dungeon_tiles.tsx"
ITEMS_TILESET = "items.tsx"
ENEMY_TILESET = "enemy.tsx"
SECRET_WALL_TILESET = "secret_wall.tsx"

DEFAULT_ENEMY_TYPES = ["walker", "flyer", "shooter"]
ITEM_TYPES = ["coin", "chest"]

#: Name shared by the hidden room's Rooms-layer rect, its breakable wall tiles, and its deferred
#: object/enemy markers (the engine matches on this via the `secretRoom` tile/object property).
SECRET_ROOM_NAME = "secret_room"


def _to_bool(value):
    if value is None:
        return False
    return str(value).strip().lower() in ("1", "true", "yes")


class Tileset:
    """Parsed external .tsx tileset. Local tile id -> property dict."""

    def __init__(self, tsx_path):
        self.path = tsx_path
        self.name = None
        self.tilewidth = TILE_SIZE
        self.tileheight = TILE_SIZE
        self.tilecount = 0
        self.firstgid = None
        self.tiles = {}
        self._parse()

    def _parse(self):
        tree = ET.parse(self.path)
        root = tree.getroot()
        self.name = root.get("name")
        self.tilewidth = int(root.get("tilewidth", TILE_SIZE))
        self.tileheight = int(root.get("tileheight", TILE_SIZE))
        self.tilecount = int(root.get("tilecount", 0))
        for tile_el in root.findall("tile"):
            tile_id = int(tile_el.get("id"))
            props = {
                "solid": True,
                "oneWay": False,
                "hazard": False,
                "type": tile_el.get("type"),
                "enemyType": None,
                "animated": tile_el.find("animation") is not None,
                "imageWidth": None,
                "imageHeight": None,
            }
            image_el = tile_el.find("image")
            if image_el is not None:
                props["imageWidth"] = int(image_el.get("width", self.tilewidth))
                props["imageHeight"] = int(image_el.get("height", self.tileheight))
                props["image"] = image_el.get("source")
            props_el = tile_el.find("properties")
            if props_el is not None:
                for p in props_el.findall("property"):
                    name = p.get("name")
                    value = p.get("value")
                    if name == "solid":
                        props["solid"] = _to_bool(value)
                    elif name == "oneWay":
                        props["oneWay"] = _to_bool(value)
                    elif name == "hazard":
                        props["hazard"] = _to_bool(value)
                    elif name == "enemyType":
                        props["enemyType"] = value
                    elif name == "type":
                        props["type"] = value
            self.tiles[tile_id] = props

    def gid(self, local_id):
        if self.firstgid is None:
            raise RuntimeError(f"firstgid not assigned for {self.name}")
        return self.firstgid + local_id


class Room:
    def __init__(self, index, col_start, row_start=0, width=SCREEN_TILE_W, height=SCREEN_TILE_H,
                 grid_col=0, grid_row=0):
        self.index = index
        self.width = width
        self.height = height
        self.col_start = col_start
        self.col_end = col_start + width - 1
        self.row_start = row_start  # top CSV row (ceiling) of the room within the map
        self.row_end = row_start + height - 1  # bottom CSV row (floor) of the room
        self.grid_col = grid_col
        self.grid_row = grid_row

    @property
    def interior_cols(self):
        return list(range(self.col_start + 1, self.col_end))

    @property
    def floor_row(self):
        return self.row_end

    @property
    def passage_rows(self):
        return [self.row_end - 1, self.row_end - 2]


class Layout:
    """Resolved gid language + layout, shared between generation and validation."""

    def __init__(self, tilesets_dir, room_count, inside_secret=False,
                 room_width=SCREEN_TILE_W, room_height=SCREEN_TILE_H,
                 grid_cols=None, grid_rows=None, no_secret=False):
        def load(name):
            return Tileset(os.path.join(tilesets_dir, name))

        self.cave = Tileset(os.path.join(tilesets_dir, COLLISION_TILESET))
        self.items = load(ITEMS_TILESET)
        self.enemy = load(ENEMY_TILESET)
        self.secret_wall = load(SECRET_WALL_TILESET)

        self.cave.firstgid = 1
        self.items.firstgid = self.cave.firstgid + self.cave.tilecount
        self.enemy.firstgid = self.items.firstgid + self.items.tilecount
        self.secret_wall.firstgid = self.enemy.firstgid + self.enemy.tilecount

        self.solid_gids = []
        self.passage_gids = []
        self.one_way_gids = []
        self.hazard_gids = []
        for tile_id, props in sorted(self.cave.tiles.items()):
            if props["hazard"]:
                self.hazard_gids.append(self.cave.gid(tile_id))
            elif not props["solid"]:
                self.passage_gids.append(self.cave.gid(tile_id))
            elif props["oneWay"]:
                self.one_way_gids.append(self.cave.gid(tile_id))
            else:
                self.solid_gids.append(self.cave.gid(tile_id))

        self.coin_gids = self._tiles_with_type(self.items, "coin")
        self.chest_gids = self._tiles_with_type(self.items, "chest")
        # Tiles with a `type="door"` attribute in the cave tileset: painted on the decoration
        # layer beneath the playerStart and exitGate markers so the door stands on the floor,
        # just above the collision layer, instead of looking like it floats.
        self.door_gids = self._tiles_with_type(self.cave, "door")
        self.enemy_tiles = [
            (tile_id, props)
            for tile_id, props in sorted(self.enemy.tiles.items())
            if props["type"] == "enemy"
        ]
        # Background filler tiles (image sources like `bg-barrel.png`/`bg-crate.png`): painted on the
        # background layer behind the one-way platforms of a `--platforms` map.
        self.bg_gids = [
            self.cave.gid(tile_id)
            for tile_id, props in sorted(self.cave.tiles.items())
            if os.path.basename(props.get("image") or "").startswith("bg-")
        ]

        self.room_width = room_width
        self.room_height = room_height
        # Grid geometry: a linear chain is a 1-row grid (grid_cols = room_count). Vertical
        # neighbours connect via one-way platform shafts instead of doorways.
        self.grid_cols = grid_cols if grid_cols is not None else room_count
        self.grid_rows = grid_rows if grid_rows is not None else 1
        # The bottom CSV row of a top-row room: its floor. Kept for the secret-room/chamber
        # paths (linear only) and as the "map floor row" reference.
        self.floor_row = room_height - 1
        self.map_rows = self.grid_rows * room_height
        if no_secret or inside_secret:
            self.map_cols = self.grid_cols * room_width
        else:
            # Appended secret room adds one extra room to the right of the whole grid.
            self.map_cols = (self.grid_cols + 1) * room_width
        self.map_height_px = self.map_rows * TILE_SIZE
        self.rooms = []
        for gr in range(self.grid_rows):
            for gc in range(self.grid_cols):
                self.rooms.append(Room(len(self.rooms), gc * room_width, gr * room_height,
                                       room_width, room_height, grid_col=gc, grid_row=gr))
        # The room the player starts in: bottom-left of the grid (index 0 for a 1-row chain).
        self.player_room_index = (self.grid_rows - 1) * self.grid_cols if self.grid_rows > 1 else 0
        self.inside_secret = inside_secret
        self.no_secret = no_secret
        self.secret_room = None
        self.chamber = None
        if inside_secret:
            # The hidden chamber is carved into the LAST room, flush against its left wall and
            # sitting on the room floor (footprint rows floor_row-CHAMBER_H+1 .. floor_row).
            last = self.rooms[-1]
            self.chamber = {
                "col_start": last.col_start,
                "front_col": last.col_start + CHAMBER_W - 1,
                "top_row": self.floor_row - CHAMBER_H + 1,
                "cavity_cols": list(range(last.col_start + 1, last.col_start + CHAMBER_W - 1)),
            }
        elif not no_secret:
            # A hidden bonus room sealed off behind a breakable wall: never a normal doorway, entered
            # only once the player strikes its secret wall. Painted over with the `secret_hide` veil.
            self.secret_room = Room(self.grid_cols, self.grid_cols * room_width, 0, room_width, room_height)
        # The single tile of the cloned per-room secret-wall tileset (tile id 0 -> firstgid).
        self.secret_wall_gid = self.secret_wall.firstgid

        # Passage rows: the two rows directly above the floor (walk-through doorway
        # tall enough for the ~240px-tall player collision box).
        self.passage_rows = [self.floor_row - 1, self.floor_row - 2]

    def room_at(self, grid_row, grid_col):
        """The Room at (grid_row, grid_col), or None when out of range."""
        if 0 <= grid_row < self.grid_rows and 0 <= grid_col < self.grid_cols:
            return self.rooms[grid_row * self.grid_cols + grid_col]
        return None

    @staticmethod
    def _tiles_with_type(ts, tile_type):
        return [ts.gid(tile_id) for tile_id, props in sorted(ts.tiles.items()) if props["type"] == tile_type]

    def enemy_marker(self, enemy_type):
        """Returns (gid, width, height) for a tile-object enemy marker, or None when the
        tileset has no tile whose properties resolve to that enemyType. A tile-object
        marker only spawns the right variant if its tile carries the enemyType property
        (EntityFactory reads it from the tile); otherwise the caller must emit a rectangle
        object with an explicit enemyType property instead."""
        for tile_id, props in self.enemy_tiles:
            if props["enemyType"] == enemy_type:
                return (
                    self.enemy.gid(tile_id),
                    props["imageWidth"] or self.enemy.tilewidth,
                    props["imageHeight"] or self.enemy.tileheight,
                )
        if enemy_type == "walker":
            for tile_id, props in self.enemy_tiles:
                if props["enemyType"] is None:
                    return (
                        self.enemy.gid(tile_id),
                        props["imageWidth"] or self.enemy.tilewidth,
                        props["imageHeight"] or self.enemy.tileheight,
                    )
        return None

    def is_non_solid_cell(self, value):
        """True for empty cells, passage tiles, and (for convenience) one-way/hazard
        tiles -- i.e. a cell that does not block walking."""
        if value == 0:
            return True
        return value in self.passage_gids or value in self.one_way_gids or value in self.hazard_gids


def _new_grid(cols, rows, fill):
    return [[fill for _ in range(cols)] for _ in range(rows)]


def _grid_to_csv(grid):
    lines = [",".join(str(v) for v in row) + "," for row in grid[:-1]]
    lines.append(",".join(str(v) for v in grid[-1]))
    return "\n" + "\n".join(lines) + "\n"


def _floor_surface_world_y(layout):
    # Floor occupies the bottom CSV row, world-up [0, TILE_SIZE]; its top surface is at TILE_SIZE
    # (rooms tile the map vertically, so the floor row is also the map's bottom row).
    return (layout.map_rows - layout.floor_row) * TILE_SIZE


def _tile_obj_tiled_y(map_height_px, world_bottom):
    # libGDX sets tile-object Y (world-up bottom-left) = mapHeightPixels - tiledY.
    return map_height_px - world_bottom


def _rect_tiled_y(map_height_px, world_bottom, height):
    # libGDX sets rectangle-object Y (world-up bottom-left) = mapHeightPixels - tiledY - height.
    return map_height_px - world_bottom - height


def _build_collision_grid(layout, passages, vertical_links):
    grid = _new_grid(layout.map_cols, layout.map_rows, 0)

    def set_cell(col, csv_row, value):
        grid[csv_row][col] = value

    solid = layout.solid_gids[0] if layout.solid_gids else 1

    for room in layout.rooms:
        for col in range(room.col_start, room.col_end + 1):
            set_cell(col, room.row_start, solid)  # ceiling
            set_cell(col, room.row_end, solid)  # floor
        for csv_row in range(room.row_start, room.row_end + 1):
            set_cell(room.col_start, csv_row, solid)  # left wall
            set_cell(room.col_end, csv_row, solid)  # right wall

    if layout.inside_secret:
        # Carve the hidden chamber into the last room: roof row + front wall, with the breakable
        # secret wall on the front wall's passage rows. The cavity (cavity_cols, rows top_row+1 ..
        # floor_row-1) stays empty so the player can stand inside once the wall breaks; the room's
        # own left wall and floor double as the chamber's left wall and floor.
        chamber = layout.chamber
        for col in range(chamber["col_start"], chamber["col_start"] + CHAMBER_W):
            set_cell(col, chamber["top_row"], solid)  # roof (walkable platform until revealed)
        for csv_row in range(chamber["top_row"], layout.floor_row + 1):
            set_cell(chamber["front_col"], csv_row, solid)  # front wall
        for csv_row in layout.passage_rows:
            set_cell(chamber["front_col"], csv_row, layout.secret_wall_gid)  # breakable guard
    elif not layout.no_secret:
        # The secret room is a fully enclosed box too, but its left wall never gets a doorway:
        # it is entered only through the breakable secret wall set in the last normal room's right
        # wall (see below), so its own left-wall column is open on the passage rows to let the
        # player walk through once the wall is broken.
        secret = layout.secret_room
        for col in range(secret.col_start, secret.col_end + 1):
            set_cell(col, 0, solid)  # ceiling
            set_cell(col, layout.floor_row, solid)  # floor
        for csv_row in range(0, layout.map_rows):
            set_cell(secret.col_start, csv_row, solid)  # left wall (entrance overwritten below)
            set_cell(secret.col_end, csv_row, solid)  # right wall

        # The secret entrance: the last normal room's right wall carries the breakable secret wall
        # on the passage rows (a visible cracked patch in the rock), and the secret room's matching
        # left-wall cells are left open so breaking the wall opens a walk-through passage.
        last_room = layout.rooms[-1]
        for csv_row in layout.passage_rows:
            set_cell(last_room.col_end, csv_row, layout.secret_wall_gid)
            set_cell(secret.col_start, csv_row, 0)

    for room_a, room_b, rows in passages:
        for csv_row in rows:
            set_cell(room_a.col_end, csv_row, 0)
            set_cell(room_b.col_start, csv_row, 0)

    # Vertical platform shafts: between vertically-adjacent rooms, hollow a 2-column shaft
    # (cols col_start+2..col_start+3) with one-way platforms every 2 rows for the climb, and
    # open a matching 2-cell hatch through the upper room's floor so the player can ascend into
    # it. The lower room's floor row stays solid (the shaft bottom).
    for upper, lower in vertical_links:
        shaft_cols = [lower.col_start + 2, lower.col_start + 3]
        one_way = layout.one_way_gids[0] if layout.one_way_gids else solid
        for col in shaft_cols:
            for csv_row in range(lower.row_start, lower.row_end):
                set_cell(col, csv_row, 0)  # hollow shaft (ceiling through one above the floor)
        for csv_row in range(lower.row_end - 2, lower.row_start, -2):
            for col in shaft_cols:
                set_cell(col, csv_row, one_way)  # one-way stepping platforms
        for col in shaft_cols:
            set_cell(col, upper.row_end, 0)  # hatch through the upper room's floor

    return grid


def _build_passages(layout):
    """Horizontal doorways: every pair of rooms sharing a vertical wall (same grid row,
    adjacent columns). Each doorway is a 2-row gap on the shared wall just above the floor."""
    passages = []
    for room in layout.rooms:
        nbr = layout.room_at(room.grid_row, room.grid_col + 1)
        if nbr is not None:
            passages.append((room, nbr, room.passage_rows))
    return passages


def _build_vertical_links(layout):
    """Vertical platform shafts: every pair of rooms sharing a horizontal wall (same grid
    column, adjacent rows), climbed through a one-way platform shaft."""
    links = []
    for gr in range(layout.grid_rows - 1):
        for gc in range(layout.grid_cols):
            upper = layout.room_at(gr, gc)
            lower = layout.room_at(gr + 1, gc)
            links.append((upper, lower))
    return links


def _build_objects(layout, passages, rng, enemy_types, vertical_links, exit_next=None):
    objects = []  # (layer, xml string)
    enemies = []
    door_cells = []  # (col, row) decoration-layer door placements, painted after markers
    next_id = 1

    def tile_object(layer, gid, x_tiled, y_tiled, w, h, name=None, properties=None):
        nonlocal next_id
        name_attr = f' name="{name}"' if name else ""
        if properties:
            inner = "".join(f'<property name="{k}" value="{v}"/>' for k, v in properties.items())
            xml = (f'  <object id="{next_id}"{name_attr} gid="{gid}" x="{x_tiled}" y="{y_tiled}" '
                   f'width="{w}" height="{h}">\n'
                   f'   <properties>\n    {inner}\n   </properties>\n  </object>')
        else:
            xml = f'  <object id="{next_id}"{name_attr} gid="{gid}" x="{x_tiled}" y="{y_tiled}" width="{w}" height="{h}"/>'
        next_id += 1
        layer.append(xml)

    def rect_object(layer, obj_type, x_tiled, y_tiled, w, h, properties=None, name=None):
        nonlocal next_id
        name_attr = f' name="{name}"' if name else ""
        if properties:
            inner = "".join(f'<property name="{k}" value="{v}"/>' for k, v in properties.items())
            xml = (f'  <object id="{next_id}"{name_attr} type="{obj_type}" x="{x_tiled}" y="{y_tiled}" '
                   f'width="{w}" height="{h}">\n'
                   f'   <properties>\n    {inner}\n   </properties>\n  </object>')
        else:
            xml = (f'  <object id="{next_id}"{name_attr} type="{obj_type}" x="{x_tiled}" y="{y_tiled}" '
                   f'width="{w}" height="{h}"/>')
        next_id += 1
        layer.append(xml)

    # Keep enemies/items out of the platform-shaft columns so the climb stays clear.
    shaft_cols_by_room = {}
    for upper, lower in vertical_links:
        cols = {lower.col_start + 2, lower.col_start + 3}
        shaft_cols_by_room.setdefault(upper.index, set()).update(cols)
        shaft_cols_by_room.setdefault(lower.index, set()).update(cols)

    for room in layout.rooms:
        floor_world_y = (layout.map_rows - room.row_end) * TILE_SIZE
        interior = [c for c in room.interior_cols if c not in shaft_cols_by_room.get(room.index, ())]
        if layout.inside_secret and room.index == len(layout.rooms) - 1:
            # Keep the last room's markers out of the chamber footprint (its front wall and cavity).
            interior = [c for c in interior if c > layout.chamber["front_col"]]
        rng.shuffle(interior)

        if room.index == layout.player_room_index:
            col = interior.pop(0)
            x = col * TILE_SIZE
            rect_object(objects, "playerStart", x, _rect_tiled_y(layout.map_height_px, floor_world_y, TILE_SIZE),
                        TILE_SIZE, TILE_SIZE, name="playerStart")
            door_cells.append((col, room.floor_row - 1))

        enemy_count = rng.randint(0, 2)
        for _ in range(min(enemy_count, len(interior))):
            col = interior.pop()
            enemy_type = rng.choice(enemy_types)
            x = col * TILE_SIZE
            marker = layout.enemy_marker(enemy_type)
            if marker is not None:
                gid, w, h = marker
                tile_object(enemies, gid, x, _tile_obj_tiled_y(layout.map_height_px, floor_world_y), w, h,
                            name=f"enemy_r{room.index}_{enemy_type}")
            else:
                rect_object(enemies, "enemy", x, _rect_tiled_y(layout.map_height_px, floor_world_y, TILE_SIZE),
                            TILE_SIZE, TILE_SIZE, properties={"enemyType": enemy_type},
                            name=f"enemy_r{room.index}_{enemy_type}")

        item_count = rng.randint(0, 3)
        for _ in range(min(item_count, len(interior))):
            col = interior.pop()
            item_type = rng.choice(ITEM_TYPES)
            x = col * TILE_SIZE
            gids = layout.coin_gids if item_type == "coin" else layout.chest_gids
            if not gids:
                continue
            gid = gids[-1] if (item_type == "coin" and gids) else gids[0]
            tile_object(objects, gid, x, _tile_obj_tiled_y(layout.map_height_px, floor_world_y), TILE_SIZE, TILE_SIZE,
                        name=f"{item_type}_r{room.index}")

    # Exit gate (--exit-next): a logic-only level-transition trigger placed in the room farthest
    # from the player start (top-right; rightmost room on a 1-row map), standing on the floor near
    # the room's right wall. Placement is pure geometry (no RNG), so it is deterministic for a seed.
    if exit_next:
        exit_room = layout.rooms[layout.grid_cols - 1]
        exit_floor_world_y = (layout.map_rows - exit_room.row_end) * TILE_SIZE
        x = exit_room.col_end * TILE_SIZE - EXIT_GATE_W - 64
        y_tiled = layout.map_height_px - exit_floor_world_y - EXIT_GATE_H
        rect_object(objects, "exitGate", x, y_tiled, EXIT_GATE_W, EXIT_GATE_H,
                    properties={"nextLevel": exit_next}, name="exitGate")
        # The gate's left edge sits at col_end*TILE-204, i.e. inside column col_end-2; the door
        # stands in that column on the row above the room's floor row, below the gate marker.
        door_cells.append((exit_room.col_end - 2, exit_room.floor_row - 1))

    # Secret room/chamber: guaranteed chest + a small random scattering, ALL deferred. Every marker carries
    # the `secretRoom` object property, so MapLoader partitions them out of the normal spawn layers
    # and SecretRoomRevealer spawns them only when the room is revealed. (Skipped for --no-secret maps.)
    if layout.no_secret:
        return objects, enemies, door_cells

    if layout.inside_secret:
        secret_interior = list(layout.chamber["cavity_cols"])
    else:
        secret_interior = list(layout.secret_room.interior_cols)
    rng.shuffle(secret_interior)

    if layout.chest_gids and secret_interior:
        col = secret_interior.pop()
        tile_object(objects, layout.chest_gids[0], col * TILE_SIZE,
                    _tile_obj_tiled_y(layout.map_height_px, floor_world_y), TILE_SIZE, TILE_SIZE,
                    name=f"chest_{SECRET_ROOM_NAME}", properties={"secretRoom": SECRET_ROOM_NAME})

    for _ in range(rng.randint(2, 4)):
        if not secret_interior or not layout.coin_gids:
            break
        col = secret_interior.pop()
        tile_object(objects, layout.coin_gids[-1], col * TILE_SIZE,
                    _tile_obj_tiled_y(layout.map_height_px, floor_world_y), TILE_SIZE, TILE_SIZE,
                    name=f"coin_{SECRET_ROOM_NAME}", properties={"secretRoom": SECRET_ROOM_NAME})

    for _ in range(rng.randint(0, 1)):
        if not secret_interior:
            break
        col = secret_interior.pop()
        enemy_type = rng.choice(enemy_types)
        x = col * TILE_SIZE
        marker = layout.enemy_marker(enemy_type)
        if marker is not None:
            gid, w, h = marker
            tile_object(enemies, gid, x, _tile_obj_tiled_y(layout.map_height_px, floor_world_y), w, h,
                        name=f"enemy_{SECRET_ROOM_NAME}_{enemy_type}",
                        properties={"secretRoom": SECRET_ROOM_NAME})
        else:
            rect_object(enemies, "enemy", x, _rect_tiled_y(layout.map_height_px, floor_world_y, TILE_SIZE),
                        TILE_SIZE, TILE_SIZE,
                        properties={"enemyType": enemy_type, "secretRoom": SECRET_ROOM_NAME},
                        name=f"enemy_{SECRET_ROOM_NAME}_{enemy_type}")

    return objects, enemies, door_cells


def _apply_platforming(layout, collision_grid, background_grid, objects, platforms):
    """Decorates each room with `platforms` one-way platforms (--platforms N): a deterministic
    staircase starting 2 rows above the floor and stepping 2 rows up / 2 columns right per
    platform -- the same spacing as the proven vertical-shaft platforms, so every platform is
    reachable by construction (no reachability analysis needed). Paints an existing bg-* filler
    tile (bg-barrel/bg-crate) on the background layer behind each platform and drops a coin on
    the top platform of every room. Appends coin markers to `objects` (returned)."""
    if platforms <= 0 or not layout.one_way_gids:
        return objects

    max_object_id = 0
    for xml in objects:
        m = re.search(r'<object id="(\d+)"', xml)
        if m:
            max_object_id = max(max_object_id, int(m.group(1)))

    for room in layout.rooms:
        first_row = room.floor_row - 2
        base_col = room.col_start + 4
        if layout.inside_secret and room.index == len(layout.rooms) - 1:
            # Keep the staircase clear of the hidden chamber's footprint (front wall + cavity).
            base_col = max(base_col, layout.chamber["front_col"] + 2)
        max_platforms = (first_row - room.row_start) // 2
        max_platforms = min(max_platforms, (room.col_end - 2 - base_col) // 2 + 1)
        count = min(platforms, max_platforms)
        if count <= 0:
            continue
        top_platform = None
        for k in range(count):
            col = base_col + k * 2
            row = first_row - k * 2
            collision_grid[row][col] = layout.one_way_gids[0]
            if layout.bg_gids:
                background_grid[row][col] = layout.bg_gids[k % 2 if len(layout.bg_gids) > 1 else 0]
            if k == count - 1:
                top_platform = (col, row)
        if top_platform is not None and layout.coin_gids:
            col, row = top_platform
            surface_world_y = (layout.map_rows - row) * TILE_SIZE
            max_object_id += 1
            objects.append(
                f'  <object id="{max_object_id}" name="coin_platform_r{room.index}" '
                f'gid="{layout.coin_gids[-1]}" x="{col * TILE_SIZE}" '
                f'y="{_tile_obj_tiled_y(layout.map_height_px, surface_world_y)}" '
                f'width="{TILE_SIZE}" height="{TILE_SIZE}"/>')
    return objects


def _paint_door_cells(decoration_grid, layout, door_cells):
    """Paints the `type="door"` tile (first door gid in the cave tileset) into the decoration
    layer at each (col, row) cell. Cells sit on the row just above the room floor, and the door
    tile image is two tiles tall, so the door's bottom edge rests on the floor surface, just
    above the collision layer. Returns the number of cells painted."""
    if not layout.door_gids:
        return 0
    gid = layout.door_gids[0]
    for col, row in door_cells:
        if 0 <= col < layout.map_cols and 0 <= row < layout.map_rows:
            decoration_grid[row][col] = gid
    return len(door_cells)


def _rooms_xml(layout, start_id):
    parts = []
    obj_id = start_id
    if layout.inside_secret:
        # Emit the secret chamber's rect BEFORE the enclosing room so it wins the camera framing
        # while the player is inside it (RoomState.findRoomIndexContaining returns the first match).
        x = layout.chamber["col_start"] * TILE_SIZE
        w = CHAMBER_W * TILE_SIZE
        h = CHAMBER_H * TILE_SIZE
        # Tiled Y: rect Y (from map top) such that the world-up bottom of the rect lands on the floor.
        y_tiled = layout.map_height_px - h
        parts.append(f'  <object id="{obj_id}" name="{SECRET_ROOM_NAME}" x="{x}" y="{y_tiled}" width="{w}" height="{h}"/>')
        obj_id += 1
    for room in layout.rooms:
        x = room.col_start * TILE_SIZE
        w = layout.room_width * TILE_SIZE
        h = layout.room_height * TILE_SIZE
        y_tiled = room.grid_row * room.height * TILE_SIZE  # Tiled Y from map top
        parts.append(f'  <object id="{obj_id}" name="room{room.index}" x="{x}" y="{y_tiled}" width="{w}" height="{h}"/>')
        obj_id += 1
    if not layout.inside_secret and not layout.no_secret:
        secret = layout.secret_room
        x = secret.col_start * TILE_SIZE
        w = layout.room_width * TILE_SIZE
        h = layout.room_height * TILE_SIZE
        parts.append(f'  <object id="{obj_id}" name="{SECRET_ROOM_NAME}" x="{x}" y="0" width="{w}" height="{h}"/>')
        obj_id += 1
    return "\n".join(parts), obj_id


def _build_secret_hide_grid(layout):
    """Paints a rock-looking tile over the whole hidden-room footprint so, before the breakable
    wall is struck, the hidden space reads as solid rock and the map appears to just end there
    (or, for an inside chamber, the chamber reads as a solid rock mass). The breakable guard
    cells themselves are left exposed so the crack is visible and strikeable."""
    grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    veil = layout.solid_gids[0] if layout.solid_gids else 1
    if layout.no_secret:
        return grid  # no hidden footprint to veil
    if layout.inside_secret:
        chamber = layout.chamber
        for csv_row in range(chamber["top_row"], layout.map_rows):
            for col in range(chamber["col_start"], chamber["col_start"] + CHAMBER_W):
                if col == chamber["front_col"] and csv_row in layout.passage_rows:
                    continue  # keep the breakable guard visible before reveal
                grid[csv_row][col] = veil
    else:
        secret = layout.secret_room
        for csv_row in range(0, layout.map_rows):
            for col in range(secret.col_start, secret.col_end + 1):
                grid[csv_row][col] = veil
    return grid


def _secret_wall_tileset_xml(layout, out_path):
    # The base secret_wall.tsx tile's image is written relative to the tsx's own directory
    # (../gfx/tiles/secret_wall.png); the inline clone tileset must reference it relative to the
    # .tmx output instead, so resolve it from the tsx's location.
    image_abs = os.path.normpath(os.path.join(os.path.dirname(layout.secret_wall.path), layout.secret_wall.tiles[0]["image"]))
    img_src = _relative_source(out_path, image_abs)
    return (
        f' <tileset firstgid="{layout.secret_wall.firstgid}" name="secret_room_wall" '
        f'tilewidth="{TILE_SIZE}" tileheight="{TILE_SIZE}" tilecount="1" columns="1">\n'
        f'  <tile id="0">\n'
        f'   <image source="{img_src}" width="{TILE_SIZE}" height="{TILE_SIZE}"/>\n'
        f'   <properties>\n'
        f'    <property name="secret" type="bool" value="true"/>\n'
        f'    <property name="secretRoom" type="string" value="{SECRET_ROOM_NAME}"/>\n'
        f'   </properties>\n'
        f'  </tile>\n'
        f' </tileset>'
    )


def _relative_source(out_path, ts_path):
    rel = os.path.relpath(ts_path, os.path.dirname(out_path) or ".")
    return rel.replace(os.sep, "/")


def generate_map(output_path, room_count=3, seed=None, tilesets_dir="tileset", enemy_types=None,
                 inside_secret=False, room_width=SCREEN_TILE_W, room_height=SCREEN_TILE_H,
                 grid_cols=None, grid_rows=None, no_secret=False, exit_next=None, platforms=0):
    """Builds a chain (default) or grid of whole-screen rooms (room_width x room_height tiles at
    128px; defaults 30x17 matching the viewport), perimeter-sealed except for one walk-through
    doorway to each horizontal neighbour and a one-way platform shaft to each vertical neighbour,
    scatters a playerStart and a small random count of enemies/items per room, and writes a
    complete .tmx (background/collision/decoration/objects/enemies/Rooms layers).

    With grid_cols/grid_rows the rooms tile a grid map (grid_cols x grid_rows rooms, map
    width x height = grid_cols*room_width x grid_rows*room_height); the player starts in the
    bottom-left room and vertical neighbours connect through platform shafts. Grid maps have
    no secret room, so --no-secret is required.

    With inside_secret=True the map keeps one room per screen and carves a hidden
    CHAMBER_W x CHAMBER_H secret chamber into the last room instead of appending a
    full-screen secret room to the right of the map. The chamber sits flush against the
    last room's left wall, which would clash with that room's left doorway, so
    inside_secret is only supported for a single room (room_count=1).

    With exit_next=<tmx path> an exitGate trigger is placed in the room farthest from the
    player start (top-right), carrying a nextLevel property pointing at that path, so the
    map is chain-connected to the next level (see LevelExitSystem).

    When the cave tileset defines a `type="door"` tile (dungeon_tiles.tsx), that tile is
    painted on the decoration layer beneath the playerStart and the exitGate markers -- on
    the row just above the room floor, sitting on the collision floor surface -- so the door
    stands on the floor instead of looking like it floats.

    With platforms=N each room additionally gets a deterministic staircase of N one-way
    platforms floating above the floor (see _apply_platforming): 2-row/2-col steps, the same
    spacing as the vertical-shaft platforms, so every platform is reachable by construction. A
    coin sits on each room's top platform and a bg-* filler tile (bg-barrel/bg-crate) is painted
    behind each platform on the background layer."""
    grid_cols = grid_cols if grid_cols is not None else room_count
    grid_rows = grid_rows if grid_rows is not None else 1
    if inside_secret and room_count != 1:
        raise ValueError(
            "--inside-secret carves the chamber flush against the last room's left wall, which is "
            "where its doorway to the previous room lives; use --rooms 1 (or drop --inside-secret).")
    if (grid_cols > 1 or grid_rows > 1) and inside_secret:
        raise ValueError("--inside-secret is only supported for a single room; grid layouts use --no-secret.")
    if grid_rows > 1 and not no_secret:
        raise ValueError(
            "multi-row grid layouts require --no-secret (the appended secret room only makes sense "
            "for a linear 1-row chain); pass --no-secret to generate the plain grid map.")
    layout = Layout(tilesets_dir, room_count, inside_secret, room_width, room_height,
                    grid_cols, grid_rows, no_secret)
    rng = random.Random(seed)

    out_dir = os.path.dirname(os.path.abspath(output_path))
    os.makedirs(out_dir, exist_ok=True)

    passages = _build_passages(layout)
    vertical_links = _build_vertical_links(layout)
    collision_grid = _build_collision_grid(layout, passages, vertical_links)
    background_grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    decoration_grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    secret_hide_grid = _build_secret_hide_grid(layout)

    objects, enemies, door_cells = _build_objects(layout, passages, rng, enemy_types or DEFAULT_ENEMY_TYPES,
                                                  vertical_links, exit_next)
    painted_doors = _paint_door_cells(decoration_grid, layout, door_cells)
    if platforms > 0:
        _apply_platforming(layout, collision_grid, background_grid, objects, platforms)
    rooms_start_id = layout.map_cols * layout.map_rows + 1
    rooms_xml, next_object_id = _rooms_xml(layout, rooms_start_id)

    collision_csv = _grid_to_csv(collision_grid)
    background_csv = _grid_to_csv(background_grid)
    decoration_csv = _grid_to_csv(decoration_grid)
    secret_hide_csv = _grid_to_csv(secret_hide_grid)
    objects_xml = "\n".join(objects)
    enemies_xml = "\n".join(enemies)

    cave_src = _relative_source(output_path, layout.cave.path)
    items_src = _relative_source(output_path, layout.items.path)
    enemy_src = _relative_source(output_path, layout.enemy.path)
    secret_wall_tileset = _secret_wall_tileset_xml(layout, output_path) if not layout.no_secret else ""

    tmx = f'''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.11.2" orientation="orthogonal" renderorder="right-down" width="{layout.map_cols}" height="{layout.map_rows}" tilewidth="{TILE_SIZE}" tileheight="{TILE_SIZE}" infinite="0" nextlayerid="9" nextobjectid="{next_object_id}">
 <tileset firstgid="{layout.cave.firstgid}" source="{cave_src}"/>
 <tileset firstgid="{layout.items.firstgid}" source="{items_src}"/>
 <tileset firstgid="{layout.enemy.firstgid}" source="{enemy_src}"/>
{secret_wall_tileset}
 <layer id="1" name="background" width="{layout.map_cols}" height="{layout.map_rows}">
  <data encoding="csv">{background_csv}</data>
 </layer>
 <layer id="2" name="collision" width="{layout.map_cols}" height="{layout.map_rows}">
  <data encoding="csv">{collision_csv}</data>
 </layer>
 <layer id="5" name="decoration" width="{layout.map_cols}" height="{layout.map_rows}">
  <data encoding="csv">{decoration_csv}</data>
 </layer>
 <objectgroup id="3" name="objects">
{objects_xml}
 </objectgroup>
 <objectgroup id="7" name="enemies">
{enemies_xml}
 </objectgroup>
 <objectgroup id="6" name="Rooms">
{rooms_xml}
 </objectgroup>
 <layer id="8" name="secret_hide" width="{layout.map_cols}" height="{layout.map_rows}">
  <data encoding="csv">{secret_hide_csv}</data>
 </layer>
</map>
'''

    with open(output_path, "w", newline="\n") as f:
        f.write(tmx)
    problems = validate_map(output_path, tilesets_dir, room_width, room_height, no_secret=no_secret,
                            exit_next=exit_next)
    if problems:
        raise RuntimeError("Generated map failed validation:\n" + "\n".join(problems))

    note = ""
    if not layout.passage_gids:
            note = (" [no 'solid=false' tile found in dungeon_tiles.tsx -> doorways are open gaps; "
                    "add the property to a doorway tile for a visible door]")
    if layout.no_secret:
        layout_desc = f"{layout.grid_cols}x{layout.grid_rows} grid of {layout.room_width}x{layout.room_height} rooms"
        secret_desc = "no secret room"
    elif inside_secret:
        layout_desc = f"{layout.grid_cols} room(s) of {layout.room_width}x{layout.room_height}"
        secret_desc = "inside-chamber in last room"
    else:
        layout_desc = f"{layout.grid_cols} rooms of {layout.room_width}x{layout.room_height}"
        secret_desc = f"secret room appended right of room {layout.grid_cols - 1}"
    print(f"Generated {output_path}: {layout_desc}, {len(passages)} doorways, "
          f"{len(vertical_links)} vertical shafts, "
          f"{len(objects)} object markers, {len(enemies)} enemy markers ({secret_desc}), "
          f"{painted_doors} door decorations.{note}")
    return output_path


def _parse_grid_layer(layer_el, width, height):
    text = layer_el.find("data").text.strip()
    rows = [line.strip().rstrip(",") for line in text.split("\n") if line.strip()]
    return [[int(v) for v in row.split(",")] for row in rows], width, height


def _parse_collision_grid(root):
    width = int(root.get("width"))
    height = int(root.get("height"))
    collision = None
    for layer in root.findall("layer"):
        if layer.get("name") == "collision":
            collision = layer
            break
    if collision is None:
        return None, width, height
    return _parse_grid_layer(collision, width, height)


def validate_map(path, tilesets_dir=None, room_width=None, room_height=None, no_secret=False,
                 exit_next=None):
    """Returns a list of problems found (perimeter holes, CSV shape mismatches, unaligned
    doorways, markers outside rooms); empty list means the map is safe to load.

    room_width/room_height (tiles) describe the generated rooms; when omitted they are inferred
    from the map: the first normal room rect's size, falling back to the whole map size.

    no_secret=True validates a grid map with no hidden room: no secret_room rect is expected, the
    secret_hide veil must be empty, and vertically-adjacent rooms are checked for aligned
    platform-shaft openings instead of the appended-secret sealing.

    exit_next=<tmx path> additionally requires exactly one exitGate marker carrying a nextLevel
    property equal to exit_next and sitting inside a normal room rect, plus exactly two door
    decorations on the decoration layer (one beneath the playerStart, one beneath the gate)."""
    problems = []
    root = ET.parse(path).getroot()
    grid, width, height = _parse_collision_grid(root)
    if grid is None:
        return ["Missing 'collision' layer"]
    if len(grid) != height:
        problems.append(f"collision layer has {len(grid)} rows, expected {height}")
        return problems
    for i, row in enumerate(grid):
        if len(row) != width:
            problems.append(f"collision row {i} has {len(row)} cols, expected {width}")

    rooms_layer = None
    for group in root.findall("objectgroup"):
        if group.get("name") == "Rooms":
            rooms_layer = group
            break
    if rooms_layer is None:
        problems.append("Missing 'Rooms' object layer")
        return problems

    room_objects = []
    for room_obj in rooms_layer.findall("object"):
        name = room_obj.get("name")
        rx = int(float(room_obj.get("x")))
        ry_tiled = int(float(room_obj.get("y")))
        rw = int(float(room_obj.get("width")))
        rh = int(float(room_obj.get("height")))
        world_y = height * TILE_SIZE - ry_tiled - rh
        room_objects.append((name, (rx, world_y, rw, rh)))
    room_objects.sort(key=lambda r: r[1][0])

    secret_rect = next((rect for name, rect in room_objects if name == SECRET_ROOM_NAME), None)
    if secret_rect is None and not no_secret:
        problems.append(f"missing '{SECRET_ROOM_NAME}' Rooms-layer rect")
    normal_rooms = [(name, rect) for name, rect in room_objects if name != SECRET_ROOM_NAME]
    normal_rects = [rect for _, rect in normal_rooms]
    room_rects = [rect for _, rect in room_objects]

    # The doorway/perimeter checks need the generated room's tile size to pick the right passage
    # rows; infer it from the first normal room rect when the caller didn't pass it in.
    infer_w = room_width
    infer_h = room_height
    if infer_w is None or infer_h is None:
        if normal_rects:
            first = normal_rects[0]
            infer_w = infer_w or first[2] // TILE_SIZE
            infer_h = infer_h or first[3] // TILE_SIZE
        else:
            infer_w = infer_w or width
            infer_h = infer_h or height

    layout = None
    if tilesets_dir:
        try:
            layout = Layout(tilesets_dir, 1, room_width=infer_w, room_height=infer_h)
        except Exception:
            layout = None
    secret_wall_gid = layout.secret_wall_gid if layout is not None else None

    def cell(col, csv_row):
        if csv_row < 0 or csv_row >= len(grid) or col < 0 or col >= width:
            return None
        return grid[csv_row][col]

    def is_open(v):
        if v is None:
            return False
        if v == 0:
            return True
        if layout is not None:
            return layout.is_non_solid_cell(v)
        return False

    # Recover the room grid from the normal room rects: rooms sharing a world-Y are one row,
    # and distinct X positions within it are the columns (a linear chain is a 1-row grid).
    def rect_to_cells(rect):
        rx, world_y, rw, rh = rect
        col_start = rx // TILE_SIZE
        col_end = col_start + rw // TILE_SIZE - 1
        k_top = min((world_y + rh - 1) // TILE_SIZE, height - 1)
        k_bottom = max(world_y // TILE_SIZE, 0)
        return col_start, col_end, height - 1 - k_top, height - 1 - k_bottom

    row_keys = sorted({rect[1] for rect in normal_rects}, reverse=True)
    col_keys = sorted({rect[0] for rect in normal_rects})
    row_index = {wy: i for i, wy in enumerate(row_keys)}
    col_index = {x: i for i, x in enumerate(col_keys)}
    grid_cols = len(col_keys)
    grid_rows = len(row_keys)

    grid_rooms = {}
    for name, rect in normal_rooms:
        gr = row_index[rect[1]]
        gc = col_index[rect[0]]
        col_start, col_end, row_start, row_end = rect_to_cells(rect)
        grid_rooms[(gr, gc)] = {
            "name": name,
            "col_start": col_start, "col_end": col_end,
            "row_start": row_start, "row_end": row_end,
            "passage_rows": [row_end - 1, row_end - 2],
            "rect": rect,
        }

    # Doorway cells (horizontal neighbours) and platform-shaft hatch cells (vertical neighbours)
    # are exempt from the perimeter-solidity checks below.
    exempt_cells = set()
    for gr in range(grid_rows):
        for gc in range(grid_cols):
            room = grid_rooms.get((gr, gc))
            if room is None:
                continue
            nbr = grid_rooms.get((gr, gc + 1))
            if nbr is not None:
                rows_with_gap = []
                for csv_row in range(room["row_start"], room["row_end"] + 1):
                    va = cell(room["col_end"], csv_row)
                    vb = cell(nbr["col_start"], csv_row)
                    if is_open(va) and is_open(vb):
                        rows_with_gap.append(csv_row)
                        exempt_cells.add((room["col_end"], csv_row))
                        exempt_cells.add((nbr["col_start"], csv_row))
                if not rows_with_gap:
                    problems.append(f"{room['name']}/{nbr['name']}: no aligned doorway")
                for csv_row in rows_with_gap:
                    if csv_row not in room["passage_rows"]:
                        problems.append(f"{room['name']}/{nbr['name']}: doorway gap at unexpected row {csv_row}")

            lower = grid_rooms.get((gr + 1, gc))
            if lower is not None:
                shaft_cols = [room["col_start"] + 2, room["col_start"] + 3]
                open_cols = []
                for col in range(room["col_start"] + 1, room["col_end"]):
                    v_top = cell(col, room["row_end"])
                    v_bot = cell(col, lower["row_start"])
                    if is_open(v_top) and is_open(v_bot):
                        open_cols.append(col)
                    elif is_open(v_top) or is_open(v_bot):
                        problems.append(
                            f"vertical link {room['name']}/{lower['name']}: partial opening at col {col}")
                for col in shaft_cols:
                    if col not in open_cols:
                        problems.append(
                            f"vertical link {room['name']}/{lower['name']}: no opening at shaft col {col}")
                    exempt_cells.add((col, room["row_end"]))
                    exempt_cells.add((col, lower["row_start"]))
                for col in open_cols:
                    if col not in shaft_cols:
                        problems.append(
                            f"vertical link {room['name']}/{lower['name']}: unexpected opening at col {col}")
                if layout is not None and layout.one_way_gids:
                    if not any(cell(col, r) in layout.one_way_gids
                               for col in shaft_cols
                               for r in range(lower["row_start"] + 1, lower["row_end"])):
                        problems.append(
                            f"vertical link {room['name']}/{lower['name']}: no one-way platform in the shaft")
                if grid_rooms.get((gr + 2, gc)) is None:
                    for col in shaft_cols:
                        if is_open(cell(col, lower["row_end"])):
                            problems.append(
                                f"vertical link {room['name']}/{lower['name']}: "
                                f"lower room floor open in shaft at col {col}")

    solid_ok = lambda v: v not in (None, 0) and not is_open(v)
    for name, (rx, world_y, rw, rh) in normal_rooms:
        col_start, col_end, row_start, row_end = rect_to_cells((rx, world_y, rw, rh))
        for col in range(col_start, col_end + 1):
            for csv_row in (row_start, row_end):
                if (col, csv_row) in exempt_cells:
                    continue
                v = cell(col, csv_row)
                if not solid_ok(v):
                    problems.append(f"{name}: hole at col {col}, csv_row {csv_row} (value={v})")
        for csv_row in range(row_start, row_end + 1):
            for col in (col_start, col_end):
                if (col, csv_row) in exempt_cells:
                    continue
                v = cell(col, csv_row)
                if not solid_ok(v):
                    problems.append(f"{name}: hole at col {col}, csv_row {csv_row} (value={v})")

    # The secret room must be fully enclosed, with its only entrance guarded by the breakable
    # secret-wall tile. Two layouts are supported:
    #   * appended (default): a whole-screen room right of the last normal room, entered on its LEFT
    #     wall — an open gap on the passage rows, with the breakable guard on the last normal room's
    #     right wall (the player strikes it from the room side).
    #   * inside chamber (--inside-secret): a CHAMBER_W x CHAMBER_H box carved into the last room,
    #     entered on its RIGHT (front) wall, whose passage rows ARE the breakable guard. The chamber
    #     is hollow so the player can stand inside once the wall breaks.
    secret_inside = False
    if secret_rect is not None and layout is not None:
        rx, world_y, rw, rh = secret_rect
        col_start = rx // TILE_SIZE
        col_end = col_start + rw // TILE_SIZE - 1
        k_top = min((world_y + rh - 1) // TILE_SIZE, height - 1)
        k_bottom = max(world_y // TILE_SIZE, 0)
        row_start = height - 1 - k_top
        row_end = height - 1 - k_bottom
        secret_inside = any(rx >= n[0] and world_y >= n[1]
                            and rx + rw <= n[0] + n[2] and world_y + rh <= n[1] + n[3]
                            for n in normal_rects)
        entrance_col = col_start if not secret_inside else col_end
        entrance_rows = [r for r in layout.passage_rows if row_start <= r <= row_end]
        for col in range(col_start, col_end + 1):
            for csv_row in (row_start, row_end):
                v = cell(col, csv_row)
                if not solid_ok(v):
                    problems.append(f"{SECRET_ROOM_NAME}: hole at col {col}, csv_row {csv_row} (value={v})")
        for csv_row in range(row_start, row_end + 1):
            for col in (col_start, col_end):
                v = cell(col, csv_row)
                if csv_row in entrance_rows and col == entrance_col:
                    if secret_inside:
                        if v != secret_wall_gid:
                            problems.append(
                                f"{SECRET_ROOM_NAME}: entrance cell at col {col}, csv_row {csv_row} "
                                f"is not the secret-wall tile (value={v}, expected {secret_wall_gid})")
                    elif not is_open(v):
                        problems.append(
                            f"{SECRET_ROOM_NAME}: entrance cell at col {col}, csv_row {csv_row} is not open (value={v})")
                elif not solid_ok(v):
                    problems.append(f"{SECRET_ROOM_NAME}: hole at col {col}, csv_row {csv_row} (value={v})")
        if not secret_inside and normal_rects:
            guard_col = normal_rects[-1][0] // TILE_SIZE + normal_rects[-1][2] // TILE_SIZE - 1
            for csv_row in entrance_rows:
                v = cell(guard_col, csv_row)
                if v != secret_wall_gid:
                    problems.append(
                        f"{SECRET_ROOM_NAME}: guard cell at col {guard_col}, csv_row {csv_row} "
                        f"is not the secret-wall tile (value={v}, expected {secret_wall_gid})")
        if secret_inside:
            for csv_row in range(row_start + 1, row_end):
                for col in range(col_start + 1, col_end):
                    v = cell(col, csv_row)
                    if not is_open(v):
                        problems.append(f"{SECRET_ROOM_NAME}: cavity cell at col {col}, csv_row {csv_row} is not open (value={v})")

    # The secret_hide veil must exist and cover the secret room's entire footprint (and only it).
    # For an inside chamber the breakable guard cells (the front wall's passage rows) are left
    # exposed so the crack is visible and strikeable before reveal. On a --no-secret map the veil
    # must be entirely empty.
    hide_layer = next((layer for layer in root.findall("layer") if layer.get("name") == "secret_hide"), None)
    if hide_layer is None:
        problems.append("Missing 'secret_hide' layer")
    elif secret_rect is None:
        if no_secret:
            hide_grid, hide_w, hide_h = _parse_grid_layer(hide_layer, width, height)
            if hide_grid is not None:
                for csv_row in range(0, height):
                    for col in range(0, width):
                        if hide_grid[csv_row][col] != 0:
                            problems.append(f"secret_hide: stray veil cell at col {col}, csv_row {csv_row} on a no-secret map")
    else:
        hide_grid, hide_w, hide_h = _parse_grid_layer(hide_layer, width, height)
        if hide_grid is not None:
            rx, world_y, rw, rh = secret_rect
            col_start = rx // TILE_SIZE
            col_end = col_start + rw // TILE_SIZE - 1
            k_top = min((world_y + rh - 1) // TILE_SIZE, height - 1)
            k_bottom = max(world_y // TILE_SIZE, 0)
            row_start = height - 1 - k_top
            row_end = height - 1 - k_bottom
            entrance_col = col_start if not secret_inside else col_end
            entrance_rows = [r for r in layout.passage_rows if row_start <= r <= row_end] if layout is not None else []
            for csv_row in range(0, height):
                for col in range(0, width):
                    is_secret_cell = col_start <= col <= col_end and row_start <= csv_row <= row_end
                    is_guard_cell = (secret_inside and col == entrance_col and csv_row in entrance_rows)
                    value = hide_grid[csv_row][col]
                    if is_secret_cell and not is_guard_cell and value == 0:
                        problems.append(f"secret_hide: uncovered cell at col {col}, csv_row {csv_row}")
                    if not is_secret_cell and value != 0:
                        problems.append(f"secret_hide: stray veil cell at col {col}, csv_row {csv_row}")

    # Every marker (playerStart/enemies/pickups) must land inside some room rectangle.
    def inside_room(x, y):
        return any(rx <= x < rx + rw and world_y <= y < world_y + rh for rx, world_y, rw, rh in room_rects)

    def inside_rect(x, y, rect):
        rx, world_y, rw, rh = rect
        return rx <= x < rx + rw and world_y <= y < world_y + rh

    def world_from_object(obj):
        is_tile = obj.get("gid") is not None
        ox = int(float(obj.get("x")))
        oy_tiled = int(float(obj.get("y")))
        oh = int(float(obj.get("height")))
        world_y = height * TILE_SIZE - oy_tiled - (0 if is_tile else oh)
        return ox, world_y

    for group in root.findall("objectgroup"):
        if group.get("name") in ("objects", "enemies"):
            for obj in group.findall("object"):
                ox, oy = world_from_object(obj)
                if not inside_room(ox, oy):
                    problems.append(f"marker {obj.get('id')} at ({ox},{oy}) outside every room rect")
                prop = obj.find("properties/property[@name='secretRoom']")
                room_name = prop.get("value") if prop is not None else None
                if room_name is not None:
                    if room_name != SECRET_ROOM_NAME:
                        problems.append(
                            f"marker {obj.get('id')} has unexpected secretRoom '{room_name}' (expected '{SECRET_ROOM_NAME}')")
                    if secret_rect is None or not inside_rect(ox, oy, secret_rect):
                        problems.append(f"deferred marker {obj.get('id')} at ({ox},{oy}) outside the secret room rect")
                elif secret_rect is not None and inside_rect(ox, oy, secret_rect):
                    problems.append(f"marker {obj.get('id')} inside the secret room rect is missing the secretRoom property")

    player_starts = [
        obj for group in root.findall("objectgroup")
        for obj in group.findall("object")
        if obj.get("type") == "playerStart"
    ]
    if not player_starts:
        problems.append("missing playerStart marker")
    elif normal_rects:
        ox, oy = world_from_object(player_starts[0])
        if not any(r0[0] <= ox < r0[0] + r0[2] and r0[1] <= oy < r0[1] + r0[3]
                   for r0 in normal_rects):
            problems.append("playerStart not inside any normal room rect")

    if exit_next is not None:
        gates = [
            obj for group in root.findall("objectgroup")
            for obj in group.findall("object")
            if obj.get("type") == "exitGate" or obj.get("name") == "exitGate"
        ]
        if len(gates) != 1:
            problems.append(f"expected exactly one exitGate marker, found {len(gates)}")
        else:
            gate = gates[0]
            prop = gate.find("properties/property[@name='nextLevel']")
            value = prop.get("value") if prop is not None else None
            if value != exit_next:
                problems.append(f"exitGate nextLevel='{value}' != expected '{exit_next}'")
            ox, oy = world_from_object(gate)
            if not any(r0[0] <= ox < r0[0] + r0[2] and r0[1] <= oy < r0[1] + r0[3]
                       for r0 in normal_rects):
                problems.append("exitGate not inside any normal room rect")

        # Door decorations: exactly two `type="door"` tiles on the decoration layer, one on the
        # row just above the floor of the exit room in the gate's column, the other (anywhere)
        # beneath the spawn.
        decoration = next((layer for layer in root.findall("layer")
                           if layer.get("name") == "decoration"), None)
        door_gids = layout.door_gids if layout is not None else []
        if decoration is None:
            problems.append("missing 'decoration' layer")
        else:
            dgrid, _, _ = _parse_grid_layer(decoration, width, height)
            door_cells = [(col, row) for row in range(height) for col in range(width)
                          if dgrid[row][col] != 0]
            if len(door_cells) != 2:
                problems.append(f"expected exactly 2 door decorations (player start + exit gate), "
                                f"found {len(door_cells)}")
            elif normal_rects:
                exit_rect = max(normal_rects, key=lambda r: r[0])
                exit_col = (exit_rect[0] + exit_rect[2]) // TILE_SIZE - 3
                exit_floor_row = (height - 1) - exit_rect[1] // TILE_SIZE - 1
                if (exit_col, exit_floor_row) not in door_cells:
                    problems.append(f"no door decoration at expected exit cell "
                                    f"(col {exit_col}, floor row {exit_floor_row})")
            if door_gids:
                for col, row in door_cells:
                    if dgrid[row][col] not in door_gids:
                        problems.append(f"door decoration at ({col}, {row}) uses gid "
                                        f"{dgrid[row][col]}, not a type=\"door\" tile")

    return problems


def main():
    parser = argparse.ArgumentParser(description="Generate a prototype .tmx map for the platformer.")
    parser.add_argument("--rooms", type=int, default=3, help="Number of whole-screen rooms in the chain (default: 3).")
    parser.add_argument("--grid-cols", type=int, default=None,
                        help="Columns of rooms in a grid layout (default: --rooms for a 1-row chain).")
    parser.add_argument("--grid-rows", type=int, default=None,
                        help="Rows of rooms in a grid layout (default: 1 for a linear chain).")
    parser.add_argument("--no-secret", action="store_true",
                        help="Omit the secret room entirely (required for grid layouts).")
    parser.add_argument("--exit-next", type=str, default=None,
                        help="Place an exitGate trigger (with a nextLevel property) in the room "
                             "farthest from the player start, pointing at this .tmx path.")
    parser.add_argument("--out", type=str, required=True, help="Output .tmx path.")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducible output.")
    parser.add_argument("--tilesets-dir", type=str, default="tileset",
                        help="Directory holding the *.tsx tilesets (dungeon_tiles.tsx, items.tsx, "
                             "enemy.tsx, secret_wall.tsx; default: 'tileset' relative to the CWD -- "
                             "run from assets/maps).")
    parser.add_argument("--enemy-types", type=str, default=None,
                        help="Comma-separated enemy types to scatter (default: walker,flyer,shooter).")
    parser.add_argument("--inside-secret", action="store_true",
                        help="Carve a hidden CHAMBER_W x CHAMBER_H secret chamber inside the last room "
                             "instead of appending a full-screen secret room to the right of the map.")
    parser.add_argument("--room-width", type=int, default=SCREEN_TILE_W,
                        help=f"Room width in tiles (default: {SCREEN_TILE_W}, matching the viewport; "
                             f"e.g. 24 for mobile-oriented rooms).")
    parser.add_argument("--room-height", type=int, default=SCREEN_TILE_H,
                        help=f"Room height in tiles (default: {SCREEN_TILE_H}, matching the viewport; "
                             f"e.g. 10 for mobile-oriented rooms).")
    parser.add_argument("--platforms", type=int, default=0,
                        help="Per room, add this many floating one-way platforms in a deterministic, "
                             "always-jumpable staircase (2 rows up / 2 cols right per step) with a coin "
                             "on the top platform and a bg-* filler tile behind each (default: 0 = flat floor).")
    args = parser.parse_args()

    enemy_types = None
    if args.enemy_types:
        enemy_types = [t.strip() for t in args.enemy_types.split(",") if t.strip()]

    generate_map(args.out, room_count=args.rooms, seed=args.seed,
                 tilesets_dir=args.tilesets_dir, enemy_types=enemy_types,
                 inside_secret=args.inside_secret,
                 room_width=args.room_width, room_height=args.room_height,
                 grid_cols=args.grid_cols, grid_rows=args.grid_rows,
                 no_secret=args.no_secret, exit_next=args.exit_next,
                 platforms=args.platforms)


if __name__ == "__main__":
    main()
