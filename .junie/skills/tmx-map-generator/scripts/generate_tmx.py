#!/usr/bin/env python3
"""Generates a standalone, hand-authored-style .tmx map for the axehigh platformer:
a linear left-to-right chain of whole-screen rooms (default 3), each fully enclosed by
solid collision tiles with a walk-through doorway connecting it to its neighbour(s),
and a small random scattering of enemies/items.

Conventions are read live from the project's external tilesets (cave_tileset.tsx,
items.tsx, enemy.tsx) -- solid / one-way / hazard / passage tiles are resolved by their
tile properties, and item/enemy markers by their tile `type` / `enemyType` -- so the
output tracks the tilesets instead of hard-coded gids. See
`resources/docs-ai/map-design-for-tiled.md` for the layer/property reference.

Stdlib only (argparse, random, os, xml.etree.ElementTree) -- no external deps.

Usage:
    python3 generate_tmx.py --rooms 3 --out assets/maps/generated_room.tmx --seed 42

Or:
    from generate_tmx import generate_map, validate_map
    generate_map("assets/maps/generated_room.tmx", room_count=3, seed=42)
    problems = validate_map("assets/maps/generated_room.tmx")
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

COLLISION_TILESET = "cave_tileset.tsx"
ITEMS_TILESET = "items.tsx"
ENEMY_TILESET = "enemy.tsx"

DEFAULT_ENEMY_TYPES = ["walker", "flyer", "shooter"]
ITEM_TYPES = ["coin", "chest"]


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

    def __init__(self, tilesets_dir, room_count):
        def load(name):
            return Tileset(os.path.join(tilesets_dir, name))

        self.cave = load(COLLISION_TILESET)
        self.items = load(ITEMS_TILESET)
        self.enemy = load(ENEMY_TILESET)

        self.cave.firstgid = 1
        self.items.firstgid = self.cave.firstgid + self.cave.tilecount
        self.enemy.firstgid = self.items.firstgid + self.items.tilecount

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

        self.map_cols = room_count * SCREEN_TILE_W
        self.map_rows = SCREEN_TILE_H
        self.map_height_px = self.map_rows * TILE_SIZE
        self.rooms = [Room(i, i * SCREEN_TILE_W) for i in range(room_count)]

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

    def tile_object(layer, gid, x_tiled, y_tiled, w, h, name=None):
        nonlocal next_id
        name_attr = f' name="{name}"' if name else ""
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

    return objects, enemies


def _rooms_xml(layout, start_id):
    parts = []
    obj_id = start_id
    for room in layout.rooms:
        x = room.col_start * TILE_SIZE
        w = SCREEN_TILE_W * TILE_SIZE
        h = SCREEN_TILE_H * TILE_SIZE
        parts.append(f'  <object id="{obj_id}" name="room{room.index}" x="{x}" y="0" width="{w}" height="{h}"/>')
        obj_id += 1
    return "\n".join(parts), obj_id


def _relative_source(out_path, ts_path):
    rel = os.path.relpath(ts_path, os.path.dirname(out_path) or ".")
    return rel.replace(os.sep, "/")


def generate_map(output_path, room_count=3, seed=None, tilesets_dir="assets/maps/level1", enemy_types=None):
    """Builds a linear chain of room_count whole-screen rooms (30x17 tiles at 128px),
    perimeter-sealed except for one walk-through doorway to each neighbour, scatters a
    playerStart in room 0 and a small random count of enemies/items per room, and writes
    a complete .tmx (background/collision/decoration/objects/enemies/Rooms layers)."""
    layout = Layout(tilesets_dir, room_count)
    rng = random.Random(seed)

    out_dir = os.path.dirname(os.path.abspath(output_path))
    os.makedirs(out_dir, exist_ok=True)

    passages = _build_passages(layout)
    collision_grid = _build_collision_grid(layout, passages)
    background_grid = _new_grid(layout.map_cols, layout.map_rows, 0)
    decoration_grid = _new_grid(layout.map_cols, layout.map_rows, 0)

    objects, enemies = _build_objects(layout, passages, rng, enemy_types or DEFAULT_ENEMY_TYPES)
    rooms_start_id = layout.map_cols * layout.map_rows + 1
    rooms_xml, next_object_id = _rooms_xml(layout, rooms_start_id)

    collision_csv = _grid_to_csv(collision_grid)
    background_csv = _grid_to_csv(background_grid)
    decoration_csv = _grid_to_csv(decoration_grid)
    objects_xml = "\n".join(objects)
    enemies_xml = "\n".join(enemies)

    cave_src = _relative_source(output_path, layout.cave.path)
    items_src = _relative_source(output_path, layout.items.path)
    enemy_src = _relative_source(output_path, layout.enemy.path)

    tmx = f'''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.11.2" orientation="orthogonal" renderorder="right-down" width="{layout.map_cols}" height="{layout.map_rows}" tilewidth="{TILE_SIZE}" tileheight="{TILE_SIZE}" infinite="0" nextlayerid="8" nextobjectid="{next_object_id}">
 <tileset firstgid="{layout.cave.firstgid}" source="{cave_src}"/>
 <tileset firstgid="{layout.items.firstgid}" source="{items_src}"/>
 <tileset firstgid="{layout.enemy.firstgid}" source="{enemy_src}"/>
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
</map>
'''

    with open(output_path, "w", newline="\n") as f:
        f.write(tmx)
    problems = validate_map(output_path, tilesets_dir)
    if problems:
        raise RuntimeError("Generated map failed validation:\n" + "\n".join(problems))

    note = ""
    if not layout.passage_gids:
        note = (" [no 'solid=false' tile found in cave_tileset.tsx -> doorways are open gaps; "
                "add the property to a doorway tile for a visible door]")
    print(f"Generated {output_path}: {room_count} rooms, {len(passages)} doorways, "
          f"{len(objects)} object markers, {len(enemies)} enemy markers.{note}")
    return output_path


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
    text = collision.find("data").text.strip()
    rows = [line.strip().rstrip(",") for line in text.split("\n") if line.strip()]
    return [[int(v) for v in row.split(",")] for row in rows], width, height


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

    room_rects = []
    for room_obj in rooms_layer.findall("object"):
        rx = int(float(room_obj.get("x")))
        ry_tiled = int(float(room_obj.get("y")))
        rw = int(float(room_obj.get("width")))
        rh = int(float(room_obj.get("height")))
        world_y = height * TILE_SIZE - ry_tiled - rh
        room_rects.append((rx, world_y, rw, rh))
    room_rects.sort(key=lambda r: r[0])

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

    # Doorway cells: for each room-to-room boundary, the wall cells on both shared
    # columns that line up at the same rows are the walk-through doorway(s).
    doorway_cells = set()
    for a, b in zip(room_rects, room_rects[1:]):
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
    for name, (rx, world_y, rw, rh) in [("room%d" % i, r) for i, r in enumerate(room_rects)]:
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

    # Every marker (playerStart/enemies/pickups) must land inside some room rectangle.
    def inside_room(x, y):
        return any(rx <= x < rx + rw and world_y <= y < world_y + rh for rx, world_y, rw, rh in room_rects)

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

    player_starts = [
        obj for group in root.findall("objectgroup")
        for obj in group.findall("object")
        if obj.get("type") == "playerStart"
    ]
    if not player_starts:
        problems.append("missing playerStart marker")
    elif room_rects:
        ox, oy = world_from_object(player_starts[0])
        r0 = room_rects[0]
        if not (r0[0] <= ox < r0[0] + r0[2] and r0[1] <= oy < r0[1] + r0[3]):
            problems.append("playerStart not inside the first room rect")

    return problems


def main():
    parser = argparse.ArgumentParser(description="Generate a prototype .tmx map for the platformer.")
    parser.add_argument("--rooms", type=int, default=3, help="Number of whole-screen rooms in the chain (default: 3).")
    parser.add_argument("--out", type=str, required=True, help="Output .tmx path.")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducible output.")
    parser.add_argument("--tilesets-dir", type=str, default="assets/maps/level1",
                        help="Directory containing cave_tileset.tsx/items.tsx/enemy.tsx (default: assets/maps/level1).")
    parser.add_argument("--enemy-types", type=str, default=None,
                        help="Comma-separated enemy types to scatter (default: walker,flyer,shooter).")
    args = parser.parse_args()

    enemy_types = None
    if args.enemy_types:
        enemy_types = [t.strip() for t in args.enemy_types.split(",") if t.strip()]

    generate_map(args.out, room_count=args.rooms, seed=args.seed,
                 tilesets_dir=args.tilesets_dir, enemy_types=enemy_types)


if __name__ == "__main__":
    main()
