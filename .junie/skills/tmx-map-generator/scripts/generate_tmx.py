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
    # whole-screen desktop rooms (default room size is the mobile-oriented 24x10; see below):
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 2 --room-width 30 \
        --room-height 17 --tilesets-dir tileset --out world_demo/generated_desktop.tmx --seed 42
    # 2x2 grid of rooms with vertical platform shafts (no secret room; 24x10 is now the default):
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --grid-cols 2 --grid-rows 2 \
        --no-secret --tilesets-dir tileset --out world_demo/generated_grid.tmx --seed 42
    # ...and chain-connected to the next level via an exit gate in the far room:
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --grid-cols 2 --grid-rows 2 \
        --no-secret --tilesets-dir tileset \
        --exit-next maps/world2/level_02.tmx --out world_demo/generated_grid.tmx --seed 42
    # floating one-way platform staircases in each room (deterministic, always-jumpable):
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 3 --platforms 3 \
        --tilesets-dir tileset --out world_demo/generated_platforming.tmx --seed 42
    # ASCII-art courses stamped floor-anchored into rooms (see scripts/templates/*.tmpl).
    # Templates fit around the entrance/exit anchors and must leave every doorway approach
    # corridor open, so push a course off a doorway with a col offset when needed:
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 2 \
        --template staircase,0 --template chasm-bridge,1,3 \
        --tilesets-dir tileset --out world_demo/generated_templates.tmx --seed 42
    # ...or auto-scatter N distinct random templates into N distinct rooms that fit:
    python3 ../.junie/skills/tmx-map-generator/scripts/generate_tmx.py --rooms 5 --template-pick 3 \
        --tilesets-dir tileset --out world_demo/generated_templates.tmx --seed 42

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
import sys
import xml.etree.ElementTree as ET

TILE_SIZE = 128
#: Default room size in tiles: 24x10 (3072x1280px at 128px tiles) -- mobile-oriented rooms that
#: dead-zone scroll under the phone's BAND_ZOOM camera (the desktop viewport is still the
#: whole-screen 30x17 = 3840x2176px). Both are overridable via --room-width/--room-height.
DEFAULT_ROOM_WIDTH = 24
DEFAULT_ROOM_HEIGHT = 10
PASSAGE_HEIGHT_TILES = 2

#: The player's jump envelope, in tiles -- the design model every generated platform, shaft, and
#: template course must stay within. The player is modeled as a 1x1-tile box; heights are ledge
#: clearance from the feet (a 2-tile single jump clears a 2-tile obstacle for a 1-tile player).
#: Derived from the physics in resources/docs-ai/gameplay.md §2.A and PlayerInputSystem
#: (JUMP_VELOCITY=220f, DOUBLE_JUMP_FACTOR=0.7f, maxJumps=2; MovementSystem gravity=-600f;
#: MOVE_SPEED=90f) at unitScale 8 (128px tiles) -- these numbers match, so this is a design spec,
#: not an engine change.
JUMP_HEIGHT_SINGLE = 2     #: max climbable ledge height with a ground jump
JUMP_HEIGHT_DOUBLE = 3     #: max climbable ledge height with the double jump (from ground)
JUMP_DISTANCE_SINGLE = 4   #: max horizontal gap cleared by a ground jump
JUMP_DISTANCE_DOUBLE = 7   #: max horizontal gap cleared by a double jump

# The hidden secret chamber carved inside a room (`--inside-secret`): a walled box sitting on the
# room floor, flush against the room's left wall. Footprint is CHAMBER_W x CHAMBER_H tiles; the
# interior (cavity) is the footprint minus its boundary wall.
CHAMBER_W = 6
CHAMBER_H = 4

# Size of the exit-gate trigger rectangle emitted with --exit-next, matching the hand-authored
# world-1 gates (~140x152 px) so LevelExitSystem builds a comparable proximity sensor.
EXIT_GATE_W = 140
EXIT_GATE_H = 152

COLLISION_TILESET = "dungeon_tiles.tsx"
ITEMS_TILESET = "items.tsx"
ENEMY_TILESET = "enemy.tsx"
SECRET_WALL_TILESET = "secret_wall.tsx"

DEFAULT_ENEMY_TYPES = ["walker", "flyer", "shooter", "knight"]
ITEM_TYPES = ["coin", "chest"]

#: Name shared by the hidden room's Rooms-layer rect, its breakable wall tiles, and its deferred
#: object/enemy markers (the engine matches on this via the `secretRoom` tile/object property).
SECRET_ROOM_NAME = "secret_room"

#: Directory holding the ASCII-art `.tmpl` template library (a sibling of this script).
TEMPLATE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates")

#: Default template symbol legend: symbol -> resolver key. `type:<T>` picks the first fully-solid
#: cave tile carrying tileset `type` T ("Ground", "Door"); `prop:<name>` picks the first tile with
#: boolean property <name> (`solid`/`oneWay`/`hazard`). "." and " " are air (whitespace between
#: symbols is ignored -- pure alignment). A template overrides a symbol with a `# SYMBOL=resolver`
#: comment line.
DEFAULT_TEMPLATE_LEGEND = {
    "G": "type:Ground",
    "X": "prop:solid",
    "P": "prop:oneWay",
    "H": "prop:hazard",
    "D": "type:Door",
}


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
    def __init__(self, index, col_start, row_start=0, width=DEFAULT_ROOM_WIDTH, height=DEFAULT_ROOM_HEIGHT,
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
                 room_width=DEFAULT_ROOM_WIDTH, room_height=DEFAULT_ROOM_HEIGHT,
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
        # Case-insensitive match: the tileset tags "Ground"/"Door" are capitalized, while the
        # generator historically looked for lowercase "door" -- tolerate either casing.
        target = tile_type.lower()
        return [ts.gid(tile_id) for tile_id, props in sorted(ts.tiles.items())
                if (props["type"] or "").lower() == target]

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


def _shaft_cols_by_room(vertical_links):
    """Column channels reserved for vertical platform-shaft climbs, keyed by room index."""
    cols_by_room = {}
    for upper, lower in vertical_links:
        cols = {lower.col_start + 2, lower.col_start + 3}
        cols_by_room.setdefault(upper.index, set()).update(cols)
        cols_by_room.setdefault(lower.index, set()).update(cols)
    return cols_by_room


def _room_marker_cols(layout, room, vertical_links, template_cols_by_room=None):
    """Interior columns usable for floor markers (spawn/enemies/items): minus the platform-shaft
    channels, the inside-secret chamber footprint, and any template footprints already planned."""
    cols = [c for c in room.interior_cols
            if c not in _shaft_cols_by_room(vertical_links).get(room.index, ())]
    if layout.inside_secret and layout.chamber is not None and room.index == len(layout.rooms) - 1:
        cols = [c for c in cols if c > layout.chamber["front_col"]]
    if template_cols_by_room:
        cols = [c for c in cols if c not in template_cols_by_room.get(room.index, ())]
    return cols


def _pick_anchors(layout, rng, vertical_links, exit_next=None, avoid_cols=None):
    """Choose the fixed entrance/exit anchors BEFORE any template planning: the playerStart
    column (seeded RNG, clear of shaft/chamber columns and of any explicit template footprint
    in the player room via avoid_cols) and the deterministic exit-gate column. Returns
    (spawn_col, door_cells) where door_cells are the decoration-layer (col, row) door
    placements derived from those anchors. Templates must fit around these columns, never over."""
    spawn_room = layout.rooms[layout.player_room_index]
    interior = _room_marker_cols(layout, spawn_room, vertical_links)
    if avoid_cols:
        interior = [c for c in interior if c not in avoid_cols]
    if not interior:
        raise ValueError(
            "cannot place the playerStart: every usable interior column of the player room is "
            "taken by explicit templates -- use a narrower template or a different room")
    rng.shuffle(interior)
    spawn_col = interior.pop(0)
    door_cells = [(spawn_col, spawn_room.floor_row - 1)]
    if exit_next:
        exit_room = layout.rooms[layout.grid_cols - 1]
        door_cells.append((exit_room.col_end - 2, exit_room.floor_row - 1))
    return spawn_col, door_cells


def _doorway_approach_cols(layout):
    """Interior columns immediately inside each doorway / secret entrance -- the corridors the
    player walks through to move between rooms. A template may stamp right up to them, but solid
    cells may never cover their passage rows (enforced per-cell in _template_fits), so
    room-to-room navigation always stays possible. Vertical platform shafts are reserved
    separately as whole columns."""
    cols_by_room = {}
    for room in layout.rooms:
        nbr = layout.room_at(room.grid_row, room.grid_col + 1)
        if nbr is not None:
            cols_by_room.setdefault(room.index, set()).add(room.col_end - 1)
            cols_by_room.setdefault(nbr.index, set()).add(nbr.col_start + 1)
    if not layout.no_secret and not layout.inside_secret:
        # The last normal room's right wall is the appended secret room's entrance (open gap).
        last = layout.rooms[-1]
        cols_by_room.setdefault(last.index, set()).add(last.col_end - 1)
    return cols_by_room


def _reserved_template_cols(layout, spawn_col, exit_next=None):
    """Interior columns templates may never stamp into (whole-column reservation): the fixed
    entrance/exit anchors, so the doors are never buried and the spawn never lands inside a wall.
    Doorway approach corridors are protected separately at fit time (only the passage-row cells
    must stay open, so templates can still stamp right up to a doorway)."""
    cols_by_room = {}
    spawn_room = layout.rooms[layout.player_room_index]
    cols_by_room.setdefault(spawn_room.index, set()).add(spawn_col)
    if exit_next:
        exit_room = layout.rooms[layout.grid_cols - 1]
        cols_by_room.setdefault(exit_room.index, set()).update(
            {exit_room.col_end - 3, exit_room.col_end - 2})
    return cols_by_room


def _build_objects(layout, rng, enemy_types, vertical_links, exit_next=None,
                   spawn_col=None, template_cols_by_room=None):
    objects = []  # (layer, xml string)
    enemies = []
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

    for room in layout.rooms:
        floor_world_y = (layout.map_rows - room.row_end) * TILE_SIZE
        interior = _room_marker_cols(layout, room, vertical_links, template_cols_by_room)
        if room.index == layout.player_room_index:
            if spawn_col in interior:
                interior.remove(spawn_col)
            col = spawn_col
            x = col * TILE_SIZE
            rect_object(objects, "playerStart", x, _rect_tiled_y(layout.map_height_px, floor_world_y, TILE_SIZE),
                        TILE_SIZE, TILE_SIZE, name="playerStart")

        rng.shuffle(interior)
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
    # Its column (col_end-2, the door beneath it) is reserved from templates by _pick_anchors.
    if exit_next:
        exit_room = layout.rooms[layout.grid_cols - 1]
        exit_floor_world_y = (layout.map_rows - exit_room.row_end) * TILE_SIZE
        x = exit_room.col_end * TILE_SIZE - EXIT_GATE_W - 64
        y_tiled = layout.map_height_px - exit_floor_world_y - EXIT_GATE_H
        rect_object(objects, "exitGate", x, y_tiled, EXIT_GATE_W, EXIT_GATE_H,
                    properties={"nextLevel": exit_next}, name="exitGate")

    # Secret room/chamber: guaranteed chest + a small random scattering, ALL deferred. Every marker carries
    # the `secretRoom` object property, so MapLoader partitions them out of the normal spawn layers
    # and SecretRoomRevealer spawns them only when the room is revealed. (Skipped for --no-secret maps.)
    if layout.no_secret:
        return objects, enemies

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

    return objects, enemies


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


def _resolve_template_path(name):
    """Library names resolve to TEMPLATE_DIR/NAME.tmpl; anything path-like is used as-is (relative
    to the CWD). Raises ValueError when the file does not exist."""
    looks_like_path = any(sep in name for sep in ("/", "\\", os.sep)) or name.lower().endswith(".tmpl")
    if looks_like_path:
        path = name if os.path.isabs(name) else os.path.join(os.getcwd(), name)
    else:
        path = os.path.join(TEMPLATE_DIR, name if name.lower().endswith(".tmpl") else name + ".tmpl")
    if not os.path.exists(path):
        raise ValueError(f"template {name!r} not found (looked for {path})")
    return path


def _first_solid_by_type(layout, tile_type):
    """The first cave tile whose `type` equals tile_type and that is a fully-solid ground tile
    (not oneWay/hazard), so `G` paints a real floor tile rather than a drop-through platform."""
    target = tile_type.lower()
    for tile_id, props in sorted(layout.cave.tiles.items()):
        if (props["type"] or "").lower() != target:
            continue
        if props["hazard"] or props["oneWay"] or not props["solid"]:
            continue
        return layout.cave.gid(tile_id)
    return None


class Template:
    """A floor-anchored ASCII-art shape stamped into a room's collision/decoration layers.

    Symbols resolve to tileset gids at load time via the symbol legend (see DEFAULT_TEMPLATE_LEGEND),
    so retagging a tileset just changes what a template paints without editing the template. The
    bottom row is the template's base and MUST be solid ground (`G`/`X` only): it replaces the room's
    floor row under the footprint, keeping the floor intact and the map perimeter solid (so
    `validate_map` never sees a template-caused hole). Symbols above the base overwrite cells
    verbatim -- air hollows the base room's floor. Whitespace between symbols is ignored (pure
    alignment); use "." for an explicit air cell so solid runs can be drawn contiguously.
    """

    def __init__(self, path, layout):
        self.path = path
        self.name = os.path.splitext(os.path.basename(path))[0]
        self.legend = dict(DEFAULT_TEMPLATE_LEGEND)
        self.requires = set()
        self.rows = []
        self.width = 0
        self.height = 0
        self._parse()

    def _parse(self):
        with open(self.path, encoding="utf-8") as f:
            lines = [line.rstrip("\n") for line in f]
        for line in lines:
            stripped = line.strip()
            if not stripped:
                continue
            if stripped.startswith("#"):
                m = re.match(r"^#\s*([A-Za-z])\s*=\s*(type:\S+|prop:\S+)\s*$", stripped)
                if m:
                    self.legend[m.group(1)] = m.group(2)
                continue
            if stripped.startswith("requires="):
                # Reserved for future template tags (e.g. requires=wallclimb); accepted, unused.
                self.requires.update(t.strip() for t in stripped.split("=", 1)[1].split(",") if t.strip())
                continue
            # Collapse internal whitespace: spaces between symbols are just alignment; use "." for
            # an explicit air cell so solid runs can be drawn contiguously.
            self.rows.append("".join(line.split()))
        if not self.rows:
            raise ValueError(f"template {self.path} is empty")
        self.width = max(len(r) for r in self.rows)
        self.height = len(self.rows)
        for row in self.rows:
            for ch in row:
                if ch not in self.legend and ch not in (".", " "):
                    raise ValueError(
                        f"template {self.path}: unknown symbol {ch!r} "
                        f"(add a '# {ch}=...' legend line)")
        # The base row must be solid ground across the full template width so the room floor and the
        # map perimeter stay intact under the footprint.
        base = self.rows[-1]
        for tc in range(self.width):
            ch = base[tc] if tc < len(base) else " "
            if ch not in ("G", "X"):
                raise ValueError(
                    f"template {self.path}: bottom row must be solid ground (G/X only) "
                    f"across the full width; found {ch!r} at column {tc}")

    def resolve(self, layout):
        """Maps each legend symbol to (layer, gid). layer is "collision" or "decoration"."""
        resolved = {}
        for symbol, key in self.legend.items():
            if key.startswith("type:"):
                tile_type = key.split(":", 1)[1].lower()
                if tile_type == "door":
                    gid = layout.door_gids[0] if layout.door_gids else None
                    layer = "decoration"
                else:
                    gid = _first_solid_by_type(layout, tile_type)
                    layer = "collision"
            else:
                prop = key.split(":", 1)[1].lower()
                layer = "collision"
                if prop == "oneway":
                    gid = layout.one_way_gids[0] if layout.one_way_gids else None
                elif prop == "hazard":
                    gid = layout.hazard_gids[0] if layout.hazard_gids else None
                elif prop == "solid":
                    gid = layout.solid_gids[0] if layout.solid_gids else None
                else:
                    raise ValueError(f"template {self.path}: unsupported resolver {key!r}")
            if gid is None:
                raise ValueError(
                    f"template {self.path}: cannot resolve symbol {symbol!r} ({key}) "
                    f"-- no matching tile in the tilesets")
            resolved[symbol] = (layer, gid)
        return resolved

    def cell(self, tc, tr):
        """The template symbol at (column, row), or ' ' when the row is shorter than the column."""
        row = self.rows[tr]
        return row[tc] if tc < len(row) else " "


def _forbidden_interior_cols(layout, room, extra_forbidden=None):
    """Interior columns the generator's own geometry reserves -- platform-shaft channels, the
    inside-secret chamber footprint, and (via extra_forbidden) the fixed entrance/exit anchors
    and doorway approach corridors. Templates must not stamp into these."""
    cols = set()
    for upper, lower in _build_vertical_links(layout):
        if upper.index == room.index or lower.index == room.index:
            cols.update({lower.col_start + 2, lower.col_start + 3})
    if layout.inside_secret and layout.chamber is not None and room.index == len(layout.rooms) - 1:
        cols.update(range(layout.chamber["col_start"], layout.chamber["col_start"] + CHAMBER_W))
    if extra_forbidden:
        cols.update(extra_forbidden.get(room.index, ()))
    return cols


def _template_fits(layout, room, tpl, col_offset, extra_forbidden=None):
    """True when the template's floor-anchored footprint fits the room's interior: within the
    interior columns (never the shared walls/ceiling), within the room height, clear of the
    reserved shaft/chamber/entrance/exit columns, and clear of every doorway approach corridor:
    a template may stamp right up to a doorway, but solid cells may never cover the doorway's
    passage rows in the corridor columns, so room-to-room travel always stays possible."""
    start_col = room.col_start + 1 + col_offset
    end_col = start_col + tpl.width - 1
    if start_col < room.col_start + 1 or end_col > room.col_end - 1:
        return False
    # floor_row - tpl.height + 1 is the template's top row; it must stay below the room ceiling
    # (room.row_start is the perimeter ceiling row, which the template must never touch).
    if room.floor_row - tpl.height + 1 <= room.row_start:
        return False
    forbidden = _forbidden_interior_cols(layout, room, extra_forbidden)
    if any(col in forbidden for col in range(start_col, end_col + 1)):
        return False
    # Doorway approach corridors: the two passage rows just above the base may not be walled off.
    approach_cols = _doorway_approach_cols(layout).get(room.index, set())
    if approach_cols:
        for tc in range(tpl.width):
            if start_col + tc not in approach_cols:
                continue
            for tr in (tpl.height - 2, tpl.height - 3):  # passage rows above the base row
                if tr >= 0 and tpl.cell(tc, tr) in ("G", "X"):
                    return False
    return True


def _plan_template_footprints(layout, placements, extra_forbidden=None):
    """Pure geometry: computes the floor-anchored footprint of every requested template placement
    and fit-checks it against the room (and the reserved anchor/approach columns) WITHOUT
    stamping any grids. Returns the list of (tpl, room, start_col, start_row) footprints used by
    _apply_templates (stamping), _template_warnings (design checks), and the marker scatterers.
    Raises ValueError on any placement that does not fit."""
    footprints = []
    for name, room_index, col_offset in placements:
        path = _resolve_template_path(name)
        tpl = Template(path, layout)
        if not (0 <= room_index < len(layout.rooms)):
            raise ValueError(
                f"template {tpl.name}: room index {room_index} out of range (0..{len(layout.rooms) - 1})")
        room = layout.rooms[room_index]
        if not _template_fits(layout, room, tpl, col_offset, extra_forbidden):
            start_col = room.col_start + 1 + col_offset
            end_col = start_col + tpl.width - 1
            raise ValueError(
                f"template {tpl.name}: {tpl.width}x{tpl.height} does not fit room {room_index} "
                f"at col offset {col_offset} (cols {start_col}..{end_col}, interior cols "
                f"{room.col_start + 1}..{room.col_end - 1}, height {room.height}) "
                f"without blocking a doorway approach, entrance, or exit column")
        start_col = room.col_start + 1 + col_offset
        start_row = room.floor_row - tpl.height + 1
        footprints.append((tpl, room, start_col, start_row))
    return footprints


def _footprint_cols(footprints):
    """Interior columns covered by the planned template footprints, keyed by room index. Floor
    markers must not spawn on a cell a template is about to overwrite."""
    cols_by_room = {}
    for tpl, room, start_col, _ in footprints:
        cols_by_room.setdefault(room.index, set()).update(range(start_col, start_col + tpl.width))
    return cols_by_room


def _apply_templates(layout, collision_grid, decoration_grid, footprints):
    """Stamps pre-planned template footprints (see _plan_template_footprints) into the grids.
    Floor-anchored: the template's bottom row replaces the room's floor row under the footprint;
    cells above overwrite (air hollows the base room). Returns the footprints unchanged."""
    for tpl, room, start_col, start_row in footprints:
        resolved = tpl.resolve(layout)
        for tr in range(tpl.height):
            csv_row = start_row + tr
            for tc in range(tpl.width):
                ch = tpl.cell(tc, tr)
                col = start_col + tc
                if ch in (".", " "):
                    collision_grid[csv_row][col] = 0
                    continue
                layer, gid = resolved[ch]
                if layer == "decoration":
                    decoration_grid[csv_row][col] = gid
                else:
                    collision_grid[csv_row][col] = gid
    return footprints


def _hop_blocked(tpl, c1, r1, c2, r2):
    """Heuristic: a solid (G/X) run in a column strictly between two surfaces, taller than the
    takeoff surface (row index smaller = higher), blocks a straight horizontal/diagonal hop."""
    if r1 - r2 < 0:
        return False  # falling to a lower surface is never blocked in this heuristic
    lo, hi = (c1, c2) if c1 < c2 else (c2, c1)
    for tc in range(lo + 1, hi):
        for tr in range(tpl.height):
            ch = tpl.cell(tc, tr)
            if ch in ("G", "X") and tr < r1:
                return True
    return False


def _template_warnings(layout, footprints):
    """Post-stamp design checks -- warnings only, never failing generation. Every solid ground cell
    above the base must be supported from below, and every standable surface (solid-run tops and
    one-way platforms) must be reachable from the room floor within the player's jump envelope. A
    real playthrough has more nuance (wall-climb, hazards, item placement), so these are heuristics,
    not a proof of reachability. Overlapping template footprints in the same room are also reported
    (later stamps win and clobber earlier ones)."""
    warnings = []
    for i, (tpl_i, room_i, sci, sri) in enumerate(footprints):
        for tpl_j, room_j, scj, srj in footprints[:i]:
            if room_i.index != room_j.index:
                continue
            overlap_cols = range(max(sci, scj), min(sci + tpl_i.width, scj + tpl_j.width))
            overlap_rows = range(max(sri, srj), min(sri + tpl_i.height, srj + tpl_j.height))
            if len(list(overlap_cols)) > 0 and len(list(overlap_rows)) > 0:
                warnings.append(
                    f"{tpl_j.name} and {tpl_i.name} overlap in room {room_i.index} "
                    f"(cols {max(sci, scj)}..{min(sci + tpl_i.width, scj + tpl_j.width) - 1}) -- "
                    f"{tpl_i.name} stamps last and clobbers the overlap")
    for tpl, room, start_col, start_row in footprints:
        # Support: every G/X cell above the base needs a solid cell directly below it.
        unsupported = []
        for tr in range(tpl.height - 2, -1, -1):
            for tc in range(tpl.width):
                ch = tpl.cell(tc, tr)
                if ch not in ("G", "X"):
                    continue
                below = tpl.cell(tc, tr + 1)
                if below not in ("G", "X"):
                    unsupported.append((tc, tr, ch))
        if unsupported:
            spots = ", ".join(f"({tc},{tr}:{ch})" for tc, tr, ch in unsupported[:6])
            more = f", +{len(unsupported) - 6} more" if len(unsupported) > 6 else ""
            warnings.append(
                f"{tpl.name} in room {room.index}: unsupported ground cells {spots}{more} "
                f"-- floating solid tiles ({unsupported[0][2]} with nothing solid beneath)")

        # Reachability: BFS over standable surfaces from the leftmost base-row surface.
        surfaces = []
        for tc in range(tpl.width):
            top = None
            for tr in range(tpl.height):
                if tpl.cell(tc, tr) in ("G", "X"):
                    top = tr
            if top is not None:
                surfaces.append((tc, top))
            for tr in range(tpl.height):
                if tpl.cell(tc, tr) == "P":
                    surfaces.append((tc, tr))
        if surfaces:
            base_surfaces = [s for s in surfaces if s[1] == tpl.height - 1]
            start = sorted(base_surfaces)[0] if base_surfaces else sorted(surfaces)[0]
            reached = {start}
            queue = [start]
            while queue:
                cur = queue.pop()
                for nxt in surfaces:
                    if nxt in reached:
                        continue
                    dc = abs(nxt[0] - cur[0])
                    rise = cur[1] - nxt[1]
                    if rise > JUMP_HEIGHT_DOUBLE or dc > JUMP_DISTANCE_DOUBLE:
                        continue
                    if _hop_blocked(tpl, cur[0], cur[1], nxt[0], nxt[1]):
                        continue
                    reached.add(nxt)
                    queue.append(nxt)
            unreached = sorted(s for s in surfaces if s not in reached)
            if unreached:
                spots = ", ".join(f"({tc},{tr})" for tc, tr in unreached[:6])
                more = f", +{len(unreached) - 6} more" if len(unreached) > 6 else ""
                warnings.append(
                    f"{tpl.name} in room {room.index}: unreachable surfaces {spots}{more} -- "
                    f"outside the jump envelope (single {JUMP_HEIGHT_SINGLE}-up/{JUMP_DISTANCE_SINGLE}-across, "
                    f"double {JUMP_HEIGHT_DOUBLE}-up/{JUMP_DISTANCE_DOUBLE}-across) or walled off")
    return warnings


def _pick_template_placements(layout, count, rng, used_rooms=None, extra_forbidden=None):
    """Picks `count` distinct (template_name, room_index, col) placements that fit -- respecting
    the reserved anchor/approach columns via extra_forbidden -- each in a distinct room not already
    in `used_rooms`, deterministically for the given rng. Fewer than `count` when the library or
    the room layout cannot fit that many."""
    used_rooms = set(used_rooms or ())
    if count <= 0 or not os.path.isdir(TEMPLATE_DIR):
        return []
    lib = sorted(os.path.splitext(f)[0] for f in os.listdir(TEMPLATE_DIR) if f.lower().endswith(".tmpl"))
    candidates = []
    for name in lib:
        try:
            path = _resolve_template_path(name)
            tpl = Template(path, layout)
        except ValueError:
            continue
        for room_index, room in enumerate(layout.rooms):
            if room_index in used_rooms:
                continue
            interior = (room.col_end - 1) - (room.col_start + 1) + 1
            if tpl.width > interior or tpl.height > room.height:
                continue
            for col in range(0, interior - tpl.width + 1):
                if _template_fits(layout, room, tpl, col, extra_forbidden):
                    candidates.append((name, room_index, col))
    rng.shuffle(candidates)
    picked = []
    used_rooms = set(used_rooms)
    for name, room_index, col in candidates:
        if len(picked) >= count:
            break
        if room_index in used_rooms:
            continue
        picked.append((name, room_index, col))
        used_rooms.add(room_index)
    return picked


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
                 inside_secret=False, room_width=DEFAULT_ROOM_WIDTH, room_height=DEFAULT_ROOM_HEIGHT,
                 grid_cols=None, grid_rows=None, no_secret=False, exit_next=None, platforms=0,
                 templates=None, template_pick=0):
    """Builds a chain (default) or grid of rooms (room_width x room_height tiles at 128px;
    defaults 24x10 -- the mobile-oriented default; pass 30x17 for whole-screen desktop rooms),
    perimeter-sealed except for one walk-through doorway to each horizontal neighbour and a
    one-way platform shaft to each vertical neighbour,
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
    behind each platform on the background layer.

    With templates=<[(name, room, col), ...]> ASCII-art courses from the template library
    (scripts/templates/*.tmpl) are floor-anchored into named rooms -- bottom row = the room's
    floor row, must be solid ground -- stamping over the base floor/platforms (they win). Room
    index and column offset are optional; room defaults to 0, column to the room's first interior
    column. Template courses must fit the room's interior (hard error otherwise). Auto-scatter
    with template_pick=N stamps N distinct random library templates into N distinct rooms that
    fit, deterministically per seed. After stamping, jump-aware design checks
    (_template_warnings) report unsupported/floating ground and surfaces unreachable within the
    player's jump envelope (warnings only; see JUMP_*_* constants)."""
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

    templates = templates or []
    if template_pick > 0 and not os.path.isdir(TEMPLATE_DIR):
        raise ValueError(f"template_pick={template_pick} but the template library directory does not exist: {TEMPLATE_DIR}")

    passages = _build_passages(layout)
    vertical_links = _build_vertical_links(layout)

    # Doors first: the entrance/exit anchors are chosen before any template planning. The spawn
    # column and the exit-gate columns are reserved so templates fit around them and can never
    # bury a door, spawn the player in a wall, or block a doorway approach corridor.
    explicit_placements = []
    for item in templates:
        if isinstance(item, str):
            explicit_placements.append((item, 0, 0))
        else:
            name, room_index = item[0], item[1]
            col = item[2] if len(item) > 2 else 0
            explicit_placements.append((name, room_index, col))

    # The spawn column must stay clear of any explicit template stamped into the player room, so
    # pre-plan just those (spawn not reserved yet) and hand their footprint columns to the anchor
    # picker. Random --template-pick placements are picked afterwards and already respect the
    # reserved columns via _template_fits, so they can never collide.
    player_explicit = [p for p in explicit_placements if p[1] == layout.player_room_index]
    spawn_avoid = set()
    if player_explicit:
        spawn_avoid = _footprint_cols(
            _plan_template_footprints(layout, player_explicit)).get(layout.player_room_index, set())

    spawn_col, door_cells = _pick_anchors(layout, rng, vertical_links, exit_next,
                                          avoid_cols=spawn_avoid)
    reserved_cols = _reserved_template_cols(layout, spawn_col, exit_next)

    placements = list(explicit_placements)
    placements += _pick_template_placements(layout, template_pick, rng,
                                            used_rooms={p[1] for p in placements},
                                            extra_forbidden=reserved_cols)

    out_dir = os.path.dirname(os.path.abspath(output_path))
    os.makedirs(out_dir, exist_ok=True)

    collision_grid = _build_collision_grid(layout, passages, vertical_links)
    background_grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    decoration_grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    secret_hide_grid = _build_secret_hide_grid(layout)

    # Pure footprint planning before stamping, so the markers can avoid template footprints and
    # doors can paint last (templates can never clobber or bury them).
    template_footprints = _plan_template_footprints(layout, placements, extra_forbidden=reserved_cols)
    objects, enemies = _build_objects(layout, rng, enemy_types or DEFAULT_ENEMY_TYPES, vertical_links,
                                      exit_next=exit_next, spawn_col=spawn_col,
                                      template_cols_by_room=_footprint_cols(template_footprints))
    if platforms > 0:
        _apply_platforming(layout, collision_grid, background_grid, objects, platforms)
    _apply_templates(layout, collision_grid, decoration_grid, template_footprints)
    painted_doors = _paint_door_cells(decoration_grid, layout, door_cells)
    template_warnings = _template_warnings(layout, template_footprints)
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
    problems, warnings = validate_map(output_path, tilesets_dir, room_width, room_height,
                                      no_secret=no_secret, exit_next=exit_next)
    for w in warnings:
        print(f"  [warn] {w}", file=sys.stderr)
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
    if template_footprints:
        names = ", ".join(f"{tpl.name}@room{tpl_index}" for tpl, tpl_room, _, _ in template_footprints
                          for tpl_index in [tpl_room.index])
        print(f"  templates stamped: {names}")
    if template_warnings:
        print("  template design warnings (not failures):")
        for w in template_warnings:
            print(f"    - {w}")
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
    """Returns ``(problems, warnings)`` where each is a list of strings.  An empty *problems*
    list means the map is safe to load; *warnings* are non-fatal notes (e.g. auto-fixed spawn
    positions) printed to stderr by the caller.

    room_width/room_height (tiles) describe the generated rooms; when omitted they are inferred
    from the map: the first normal room rect's size, falling back to the whole map size.

    no_secret=True validates a grid map with no hidden room: no secret_room rect is expected, the
    secret_hide veil must be empty, and vertically-adjacent rooms are checked for aligned
    platform-shaft openings instead of the appended-secret sealing.

    exit_next=<tmx path> additionally requires exactly one exitGate marker carrying a nextLevel
    property equal to exit_next and sitting inside a normal room rect, plus exactly two door
    decorations on the decoration layer (one beneath the playerStart, one beneath the gate)."""
    problems = []
    warnings = []
    root = ET.parse(path).getroot()
    grid, width, height = _parse_collision_grid(root)
    if grid is None:
        return ["Missing 'collision' layer"], []
    if len(grid) != height:
        problems.append(f"collision layer has {len(grid)} rows, expected {height}")
        return problems, warnings
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
        else:
            spawn_col = ox // TILE_SIZE
            spawn_row = (height - 1) - oy // TILE_SIZE
            if not is_open(cell(spawn_col, spawn_row)):
                # --- Auto-fix: walk upward until we find an open cell with ground below ---
                old_row = spawn_row
                fixed = False
                for candidate_row in range(spawn_row - 1, -1, -1):
                    if is_open(cell(spawn_col, candidate_row)):
                        # Verify there's a solid collision tile directly below (ground).
                        ground_row = candidate_row + 1
                        if is_open(cell(spawn_col, ground_row)):
                            continue  # no floor here, keep scanning upward
                        # Found a safe position: open air with solid ground.
                        spawn_row = candidate_row
                        fixed = True
                        break
                if fixed:
                    warnings.append(
                        f"playerStart was inside a solid tile at row {old_row}; "
                        f"auto-moved to row {spawn_row}")
                    # Rewrite the playerStart object Y so downstream code sees the
                    # corrected position without re-parsing.
                    new_oy = (height - 1 - spawn_row) * TILE_SIZE
                    player_starts[0].set("y", str(new_oy))
                else:
                    problems.append(
                        f"playerStart at col {spawn_col}, row {old_row} is inside a solid "
                        f"tile and no safe open position with ground found above "
                        f"(spawn-in-wall)")

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

    # Doors and the spawn must never be buried: every door decoration cell and the playerStart
    # cell must be open in the collision grid (the generation-time reservations make violations
    # impossible, so these are regression guards).
    decoration = next((layer for layer in root.findall("layer")
                       if layer.get("name") == "decoration"), None)
    if decoration is not None:
        dgrid, _, _ = _parse_grid_layer(decoration, width, height)
        for col, row in ((col, row) for row in range(height) for col in range(width)
                         if dgrid[row][col] != 0):
            if not is_open(cell(col, row)):
                problems.append(f"door decoration at ({col}, {row}) is buried by a solid "
                                f"collision tile")

    # Room-to-room navigation: every doorway approach corridor must stay open on its passage rows
    # (the two rows just above the floor), so a stamped course can never wall off a doorway.
    for gr in range(grid_rows):
        for gc in range(grid_cols):
            room = grid_rooms.get((gr, gc))
            if room is None:
                continue
            nbr = grid_rooms.get((gr, gc + 1))
            if nbr is not None:
                for pr in room["passage_rows"]:
                    for appr_col in (room["col_end"] - 1, nbr["col_start"] + 1):
                        if not is_open(cell(appr_col, pr)):
                            problems.append(
                                f"doorway approach col {appr_col}, row {pr} between "
                                f"{room['name']} and {nbr['name']} is solid -- blocked room-to-room "
                                f"travel")
    if secret_rect is not None and not secret_inside and normal_rects:
        last = normal_rooms[-1][1]
        last_col_end = (last[0] + last[2]) // TILE_SIZE - 1
        floor_row = (height - 1) - last[1] // TILE_SIZE
        for pr in (floor_row - 1, floor_row - 2):
            if not is_open(cell(last_col_end - 1, pr)):
                problems.append(
                    f"secret entrance approach col {last_col_end - 1}, row {pr} is solid -- "
                    f"blocked secret-room access")

    return problems, warnings


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
                        help="Comma-separated enemy types to scatter (default: walker,flyer,shooter,knight).")
    parser.add_argument("--inside-secret", action="store_true",
                        help="Carve a hidden CHAMBER_W x CHAMBER_H secret chamber inside the last room "
                             "instead of appending a full-screen secret room to the right of the map.")
    parser.add_argument("--room-width", type=int, default=DEFAULT_ROOM_WIDTH,
                        help=f"Room width in tiles (default: {DEFAULT_ROOM_WIDTH}, the mobile-oriented "
                             f"default; e.g. 30 for whole-screen desktop rooms).")
    parser.add_argument("--room-height", type=int, default=DEFAULT_ROOM_HEIGHT,
                        help=f"Room height in tiles (default: {DEFAULT_ROOM_HEIGHT}, the mobile-oriented "
                             f"default; e.g. 17 for whole-screen desktop rooms).")
    parser.add_argument("--platforms", type=int, default=0,
                        help="Per room, add this many floating one-way platforms in a deterministic, "
                             "always-jumpable staircase (2 rows up / 2 cols right per step) with a coin "
                             "on the top platform and a bg-* filler tile behind each (default: 0 = flat floor).")
    parser.add_argument("--template", action="append", default=None, metavar="NAME[,ROOM[,COL]]",
                        help="Stamp an ASCII-art course from the template library (scripts/templates/*.tmpl) "
                             "into a room, floor-anchored to the room floor. NAME is a library name or a "
                             "direct .tmpl path; ROOM is the room index (default 0); COL is the left-edge "
                             "column offset inside the room (default first interior column). Repeatable.")
    parser.add_argument("--template-pick", type=int, default=0,
                        help="Stamp N distinct random library templates into N distinct rooms that fit "
                             "(deterministic per --seed; defaults to fewer than N if the library/layout "
                             "cannot fit that many).")
    args = parser.parse_args()

    enemy_types = None
    if args.enemy_types:
        enemy_types = [t.strip() for t in args.enemy_types.split(",") if t.strip()]

    templates = []
    for spec in (args.template or []):
        parts = [p.strip() for p in spec.split(",")]
        name = parts[0]
        if not name:
            parser.error(f"--template {spec!r}: missing template name")
        room_index = int(parts[1]) if len(parts) > 1 and parts[1] else 0
        col = int(parts[2]) if len(parts) > 2 and parts[2] else 0
        if len(parts) > 3:
            parser.error(f"--template {spec!r}: expected NAME[,ROOM[,COL]] (got {len(parts)} parts)")
        templates.append((name, room_index, col))

    generate_map(args.out, room_count=args.rooms, seed=args.seed,
                 tilesets_dir=args.tilesets_dir, enemy_types=enemy_types,
                 inside_secret=args.inside_secret,
                 room_width=args.room_width, room_height=args.room_height,
                 grid_cols=args.grid_cols, grid_rows=args.grid_rows,
                 no_secret=args.no_secret, exit_next=args.exit_next,
                 platforms=args.platforms, templates=templates, template_pick=args.template_pick)


if __name__ == "__main__":
    main()
