#!/usr/bin/env python3
"""Generates a standalone, hand-authored-style .tmx map for the axehigh platformer:
a linear left-to-right chain of whole-screen rooms (default 1), each fully enclosed by
solid collision tiles with a walk-through doorway connecting it to its neighbour(s),
and a small random scattering of enemies/items.

Conventions are read live from the project's external tilesets (dungeon_tiles.tsx,
items.tsx, enemy.tsx) -- solid / one-way / hazard / passage tiles are resolved by their
tile properties, and item/enemy markers by their tile `type` / `enemyType` -- so the
output tracks the tilesets instead of hard-coded gids. See
`resources/docs-ai/map-design-for-tiled.md` for the layer/property reference.

Stdlib only (argparse, random, os, xml.etree.ElementTree) -- no external deps.

Usage:
    # run from assets/maps/level1 (CWD matters -- dungeon_tiles.tsx is opened from here)
    python3 generate_tmx.py --rooms 3 --out generated_room.tmx --seed 42
    python3 generate_tmx.py --rooms 1 --inside-secret --out generated_room.tmx --seed 42

Or:
    from generate_tmx import generate_map, validate_map
    generate_map("assets/maps/level1/generated_room.tmx", room_count=3, seed=42)
    generate_map("assets/maps/level1/generated_room.tmx", room_count=1, seed=42, inside_secret=True)
    problems = validate_map("assets/maps/level1/generated_room.tmx")
"""

import argparse
import os
import random
import xml.etree.ElementTree as ET

TILE_SIZE = 128
SCREEN_TILE_W = 30
SCREEN_TILE_H = 17

FLOOR_CSV_ROW = SCREEN_TILE_H - 1
WALK_CSV_ROW = FLOOR_CSV_ROW - 1
PASSAGE_HEIGHT_TILES = 2

# The hidden secret chamber carved inside a room (`--inside-secret`): a walled box sitting on the
# room floor, flush against the room's left wall. Footprint is CHAMBER_W x CHAMBER_H tiles; the
# interior (cavity) is the footprint minus its boundary wall.
CHAMBER_W = 6
CHAMBER_H = 8

COLLISION_TILESET_PATH = "dungeon_tiles.tsx"
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
    def __init__(self, index, col_start):
        self.index = index
        self.col_start = col_start
        self.col_end = col_start + SCREEN_TILE_W - 1

    @property
    def interior_cols(self):
        return list(range(self.col_start + 1, self.col_end))


class Layout:
    """Resolved gid language + layout, shared between generation and validation."""

    def __init__(self, tilesets_dir, room_count, inside_secret=False):
        def load(name):
            return Tileset(os.path.join(tilesets_dir, name))

        self.cave = Tileset(COLLISION_TILESET_PATH)
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
        self.enemy_tiles = [
            (tile_id, props)
            for tile_id, props in sorted(self.enemy.tiles.items())
            if props["type"] == "enemy"
        ]

        self.map_cols = room_count * SCREEN_TILE_W if inside_secret else (room_count + 1) * SCREEN_TILE_W
        self.map_rows = SCREEN_TILE_H
        self.map_height_px = self.map_rows * TILE_SIZE
        self.rooms = [Room(i, i * SCREEN_TILE_W) for i in range(room_count)]
        self.inside_secret = inside_secret
        self.secret_room = None
        self.chamber = None
        if inside_secret:
            # The hidden chamber is carved into the LAST room, flush against its left wall and
            # sitting on the room floor (footprint rows FLOOR_CSV_ROW-CHAMBER_H+1 .. FLOOR_CSV_ROW).
            last = self.rooms[-1]
            self.chamber = {
                "col_start": last.col_start,
                "front_col": last.col_start + CHAMBER_W - 1,
                "top_row": FLOOR_CSV_ROW - CHAMBER_H + 1,
                "cavity_cols": list(range(last.col_start + 1, last.col_start + CHAMBER_W - 1)),
            }
        else:
            # A hidden bonus room sealed off behind a breakable wall: never a normal doorway, entered
            # only once the player strikes its secret wall. Painted over with the `secret_hide` veil.
            self.secret_room = Room(room_count, room_count * SCREEN_TILE_W)
        # The single tile of the cloned per-room secret-wall tileset (tile id 0 -> firstgid).
        self.secret_wall_gid = self.secret_wall.firstgid

        # Passage rows: the two rows directly above the floor (walk-through doorway
        # tall enough for the ~240px-tall player collision box).
        self.passage_rows = [FLOOR_CSV_ROW - 1, FLOOR_CSV_ROW - 2]

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


def _floor_surface_world_y():
    # Floor occupies the bottom CSV row, world-up [0, TILE_SIZE]; its top surface is at TILE_SIZE.
    return (SCREEN_TILE_H - FLOOR_CSV_ROW) * TILE_SIZE


def _tile_obj_tiled_y(world_bottom):
    # libGDX sets tile-object Y (world-up bottom-left) = mapHeightPixels - tiledY.
    return SCREEN_TILE_H * TILE_SIZE - world_bottom


def _rect_tiled_y(world_bottom, height):
    # libGDX sets rectangle-object Y (world-up bottom-left) = mapHeightPixels - tiledY - height.
    return SCREEN_TILE_H * TILE_SIZE - world_bottom - height


def _build_collision_grid(layout, passages):
    grid = _new_grid(layout.map_cols, layout.map_rows, 0)

    def set_cell(col, csv_row, value):
        grid[csv_row][col] = value

    solid = layout.solid_gids[0] if layout.solid_gids else 1

    for room in layout.rooms:
        for col in range(room.col_start, room.col_end + 1):
            set_cell(col, 0, solid)  # ceiling
            set_cell(col, FLOOR_CSV_ROW, solid)  # floor
        for csv_row in range(0, layout.map_rows):
            set_cell(room.col_start, csv_row, solid)  # left wall
            set_cell(room.col_end, csv_row, solid)  # right wall

    if layout.inside_secret:
        # Carve the hidden chamber into the last room: roof row + front wall, with the breakable
        # secret wall on the front wall's passage rows. The cavity (cavity_cols, rows top_row+1 ..
        # FLOOR_CSV_ROW-1) stays empty so the player can stand inside once the wall breaks; the room's
        # own left wall and floor double as the chamber's left wall and floor.
        chamber = layout.chamber
        for col in range(chamber["col_start"], chamber["col_start"] + CHAMBER_W):
            set_cell(col, chamber["top_row"], solid)  # roof (walkable platform until revealed)
        for csv_row in range(chamber["top_row"], FLOOR_CSV_ROW + 1):
            set_cell(chamber["front_col"], csv_row, solid)  # front wall
        for csv_row in layout.passage_rows:
            set_cell(chamber["front_col"], csv_row, layout.secret_wall_gid)  # breakable guard
    else:
        # The secret room is a fully enclosed box too, but its left wall never gets a doorway:
        # it is entered only through the breakable secret wall set in the last normal room's right
        # wall (see below), so its own left-wall column is open on the passage rows to let the
        # player walk through once the wall is broken.
        secret = layout.secret_room
        for col in range(secret.col_start, secret.col_end + 1):
            set_cell(col, 0, solid)  # ceiling
            set_cell(col, FLOOR_CSV_ROW, solid)  # floor
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

    return grid


def _build_passages(layout):
    passages = []
    for i in range(len(layout.rooms) - 1):
        passages.append((layout.rooms[i], layout.rooms[i + 1], layout.passage_rows))
    return passages


def _build_objects(layout, passages, rng, enemy_types):
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

    floor_world_y = _floor_surface_world_y()
    for room in layout.rooms:
        interior = list(room.interior_cols)
        if layout.inside_secret and room.index == len(layout.rooms) - 1:
            # Keep the last room's markers out of the chamber footprint (its front wall and cavity).
            interior = [c for c in interior if c > layout.chamber["front_col"]]
        rng.shuffle(interior)

        if room.index == 0:
            col = interior.pop(0)
            x = col * TILE_SIZE
            rect_object(objects, "playerStart", x, _rect_tiled_y(floor_world_y, TILE_SIZE),
                        TILE_SIZE, TILE_SIZE, name="playerStart")

        enemy_count = rng.randint(0, 2)
        for _ in range(min(enemy_count, len(interior))):
            col = interior.pop()
            enemy_type = rng.choice(enemy_types)
            x = col * TILE_SIZE
            marker = layout.enemy_marker(enemy_type)
            if marker is not None:
                gid, w, h = marker
                tile_object(enemies, gid, x, _tile_obj_tiled_y(floor_world_y), w, h,
                            name=f"enemy_r{room.index}_{enemy_type}")
            else:
                rect_object(enemies, "enemy", x, _rect_tiled_y(floor_world_y, TILE_SIZE),
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
            tile_object(objects, gid, x, _tile_obj_tiled_y(floor_world_y), TILE_SIZE, TILE_SIZE,
                        name=f"{item_type}_r{room.index}")

    # Secret room/chamber: guaranteed chest + a small random scattering, ALL deferred. Every marker carries
    # the `secretRoom` object property, so MapLoader partitions them out of the normal spawn layers
    # and SecretRoomRevealer spawns them only when the room is revealed.
    if layout.inside_secret:
        secret_interior = list(layout.chamber["cavity_cols"])
    else:
        secret_interior = list(layout.secret_room.interior_cols)
    rng.shuffle(secret_interior)

    if layout.chest_gids and secret_interior:
        col = secret_interior.pop()
        tile_object(objects, layout.chest_gids[0], col * TILE_SIZE,
                    _tile_obj_tiled_y(floor_world_y), TILE_SIZE, TILE_SIZE,
                    name=f"chest_{SECRET_ROOM_NAME}", properties={"secretRoom": SECRET_ROOM_NAME})

    for _ in range(rng.randint(2, 4)):
        if not secret_interior or not layout.coin_gids:
            break
        col = secret_interior.pop()
        tile_object(objects, layout.coin_gids[-1], col * TILE_SIZE,
                    _tile_obj_tiled_y(floor_world_y), TILE_SIZE, TILE_SIZE,
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
            tile_object(enemies, gid, x, _tile_obj_tiled_y(floor_world_y), w, h,
                        name=f"enemy_{SECRET_ROOM_NAME}_{enemy_type}",
                        properties={"secretRoom": SECRET_ROOM_NAME})
        else:
            rect_object(enemies, "enemy", x, _rect_tiled_y(floor_world_y, TILE_SIZE),
                        TILE_SIZE, TILE_SIZE,
                        properties={"enemyType": enemy_type, "secretRoom": SECRET_ROOM_NAME},
                        name=f"enemy_{SECRET_ROOM_NAME}_{enemy_type}")

    return objects, enemies


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
        y_tiled = SCREEN_TILE_H * TILE_SIZE - h
        parts.append(f'  <object id="{obj_id}" name="{SECRET_ROOM_NAME}" x="{x}" y="{y_tiled}" width="{w}" height="{h}"/>')
        obj_id += 1
    for room in layout.rooms:
        x = room.col_start * TILE_SIZE
        w = SCREEN_TILE_W * TILE_SIZE
        h = SCREEN_TILE_H * TILE_SIZE
        parts.append(f'  <object id="{obj_id}" name="room{room.index}" x="{x}" y="0" width="{w}" height="{h}"/>')
        obj_id += 1
    if not layout.inside_secret:
        secret = layout.secret_room
        x = secret.col_start * TILE_SIZE
        w = SCREEN_TILE_W * TILE_SIZE
        h = SCREEN_TILE_H * TILE_SIZE
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


def generate_map(output_path, room_count=3, seed=None, tilesets_dir="assets/maps/level1", enemy_types=None,
                 inside_secret=False):
    """Builds a linear chain of room_count whole-screen rooms (30x17 tiles at 128px),
    perimeter-sealed except for one walk-through doorway to each neighbour, scatters a
    playerStart in room 0 and a small random count of enemies/items per room, and writes
    a complete .tmx (background/collision/decoration/objects/enemies/Rooms layers).

    With inside_secret=True the map keeps one room per screen and carves a hidden
    CHAMBER_W x CHAMBER_H secret chamber into the last room instead of appending a
    full-screen secret room to the right of the map. The chamber sits flush against the
    last room's left wall, which would clash with that room's left doorway, so
    inside_secret is only supported for a single room (room_count=1)."""
    if inside_secret and room_count != 1:
        raise ValueError(
            "--inside-secret carves the chamber flush against the last room's left wall, which is "
            "where its doorway to the previous room lives; use --rooms 1 (or drop --inside-secret).")
    layout = Layout(tilesets_dir, room_count, inside_secret)
    rng = random.Random(seed)

    out_dir = os.path.dirname(os.path.abspath(output_path))
    os.makedirs(out_dir, exist_ok=True)

    passages = _build_passages(layout)
    collision_grid = _build_collision_grid(layout, passages)
    background_grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    decoration_grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    secret_hide_grid = _build_secret_hide_grid(layout)

    objects, enemies = _build_objects(layout, passages, rng, enemy_types or DEFAULT_ENEMY_TYPES)
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
    secret_wall_tileset = _secret_wall_tileset_xml(layout, output_path)

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
    problems = validate_map(output_path, tilesets_dir)
    if problems:
        raise RuntimeError("Generated map failed validation:\n" + "\n".join(problems))

    note = ""
    if not layout.passage_gids:
            note = (" [no 'solid=false' tile found in dungeon_tiles.tsx -> doorways are open gaps; "
                    "add the property to a doorway tile for a visible door]")
    secret_desc = f"inside-chamber in last room" if inside_secret else f"secret room appended right of room {room_count - 1}"
    print(f"Generated {output_path}: {room_count} rooms, {len(passages)} doorways, "
          f"{len(objects)} object markers, {len(enemies)} enemy markers ({secret_desc}).{note}")
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


def validate_map(path, tilesets_dir=None):
    """Returns a list of problems found (perimeter holes, CSV shape mismatches, unaligned
    doorways, markers outside rooms); empty list means the map is safe to load."""
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

    layout = None
    if tilesets_dir:
        try:
            layout = Layout(tilesets_dir, 1)
        except Exception:
            layout = None

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
    if secret_rect is None:
        problems.append(f"missing '{SECRET_ROOM_NAME}' Rooms-layer rect")
    normal_rooms = [(name, rect) for name, rect in room_objects if name != SECRET_ROOM_NAME]
    normal_rects = [rect for _, rect in normal_rooms]
    room_rects = [rect for _, rect in room_objects]
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

    # Doorway cells: for each normal room-to-room boundary, the wall cells on both shared
    # columns that line up at the same rows are the walk-through doorway(s). The secret room
    # is deliberately sealed instead (its entrance is a breakable secret wall, checked below).
    doorway_cells = set()
    for a, b in zip(normal_rects, normal_rects[1:]):
        a_col_end = a[0] // TILE_SIZE + a[2] // TILE_SIZE - 1
        b_col_start = b[0] // TILE_SIZE
        rows_with_gap = []
        for csv_row in range(0, height):
            va = cell(a_col_end, csv_row)
            vb = cell(b_col_start, csv_row)
            if is_open(va) and is_open(vb):
                rows_with_gap.append(csv_row)
                doorway_cells.add((a_col_end, csv_row))
                doorway_cells.add((b_col_start, csv_row))
        if not rows_with_gap:
            problems.append(f"rooms at cols {a_col_end + 1}/{b_col_start}: no aligned doorway")

    solid_ok = lambda v: v not in (None, 0) and not is_open(v)
    for name, (rx, world_y, rw, rh) in normal_rooms:
        col_start = rx // TILE_SIZE
        col_end = col_start + rw // TILE_SIZE - 1
        k_top = min((world_y + rh - 1) // TILE_SIZE, height - 1)
        k_bottom = max(world_y // TILE_SIZE, 0)
        row_start = height - 1 - k_top
        row_end = height - 1 - k_bottom
        for col in range(col_start, col_end + 1):
            for csv_row in (row_start, row_end):
                if (col, csv_row) in doorway_cells:
                    continue
                v = cell(col, csv_row)
                if not solid_ok(v):
                    problems.append(f"{name}: hole at col {col}, csv_row {csv_row} (value={v})")
        for csv_row in range(row_start, row_end + 1):
            for col in (col_start, col_end):
                if (col, csv_row) in doorway_cells:
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
    # exposed so the crack is visible and strikeable before reveal.
    hide_layer = next((layer for layer in root.findall("layer") if layer.get("name") == "secret_hide"), None)
    if hide_layer is None:
        problems.append("Missing 'secret_hide' layer")
    elif secret_rect is not None:
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
        r0 = normal_rects[0]
        if not (r0[0] <= ox < r0[0] + r0[2] and r0[1] <= oy < r0[1] + r0[3]):
            problems.append("playerStart not inside the first room rect")

    return problems


def main():
    parser = argparse.ArgumentParser(description="Generate a prototype .tmx map for the platformer.")
    parser.add_argument("--rooms", type=int, default=3, help="Number of whole-screen rooms in the chain (default: 3).")
    parser.add_argument("--out", type=str, required=True, help="Output .tmx path.")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducible output.")
    parser.add_argument("--tilesets-dir", type=str, default="assets/maps/level1",
                        help="Directory containing items.tsx/enemy.tsx (default: assets/maps/level1); "
                             "the collision tileset is loaded from gfx/dungeon_tiles.tsx.")
    parser.add_argument("--enemy-types", type=str, default=None,
                        help="Comma-separated enemy types to scatter (default: walker,flyer,shooter).")
    parser.add_argument("--inside-secret", action="store_true",
                        help="Carve a hidden CHAMBER_W x CHAMBER_H secret chamber inside the last room "
                             "instead of appending a full-screen secret room to the right of the map.")
    args = parser.parse_args()

    enemy_types = None
    if args.enemy_types:
        enemy_types = [t.strip() for t in args.enemy_types.split(",") if t.strip()]

    generate_map(args.out, room_count=args.rooms, seed=args.seed,
                 tilesets_dir=args.tilesets_dir, enemy_types=enemy_types,
                 inside_secret=args.inside_secret)


if __name__ == "__main__":
    main()
