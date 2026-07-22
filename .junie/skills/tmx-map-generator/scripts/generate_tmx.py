#!/usr/bin/env python3
"""Generates a standalone, hand-authored-style .tmx map for the axehigh platformer:
a linear left-to-right chain of rooms (default 3), each fully enclosed by solid
collision tiles with one passage connecting it to its neighbor(s), and a small
random scattering of enemies/items. Reuses the exact tileset/tile-id conventions
established by assets/maps/demo_room.tmx.

Stdlib only (xml.etree.ElementTree for validation, plain string/CSV building for
generation) -- no Pillow/ImageMagick/external deps available in this sandbox.

Usage:
    python3 generate_tmx.py --rooms 3 --out assets/maps/generated_room.tmx --seed 42

Or:
    from generate_tmx import generate_map, validate_map
    generate_map("assets/maps/generated_room.tmx", room_count=3, seed=42)
    problems = validate_map("assets/maps/generated_room.tmx")
"""

import argparse
import random
import xml.etree.ElementTree as ET

TILE_SIZE = 16

# (tile cols, tile rows) -- interior usable height is rows - 2 (floor + ceiling).
ROOM_PRESETS = {
    "small": (10, 8),
    "medium": (20, 10),
    "large": (30, 12),
}

ENEMY_TYPES = ["walker", "shooter", "flyer"]
ITEM_TYPES = ["coin", "dagger", "chest"]

BACKGROUND_TILE = "1"
SOLID_TILE = "2"
EMPTY_TILE = "0"
PASSAGE_TILE = "3"


class Room:
    def __init__(self, index, preset_name, col_start, row_count):
        self.index = index
        self.preset_name = preset_name
        cols, rows = ROOM_PRESETS[preset_name]
        self.cols = cols
        self.rows = rows
        self.col_start = col_start
        self.col_end = col_start + cols - 1
        # All rooms share the same floor baseline (bottom row of the whole grid).
        self.row_start = row_count - rows  # top row (row_from_bottom convention)
        self.row_end = row_count - 1  # floor row (bottom of the grid)


def _build_rooms(room_count, total_rows_hint):
    """Lays out room_count rooms left-to-right, each with a randomly picked preset,
    all sharing the same floor baseline (the bottom of the tallest room)."""
    preset_names = list(ROOM_PRESETS.keys())
    picks = [random.choice(preset_names) for _ in range(room_count)]
    max_rows = max(ROOM_PRESETS[p][1] for p in picks)
    total_rows = max(total_rows_hint, max_rows)

    rooms = []
    col_cursor = 0
    for i, preset_name in enumerate(picks):
        room = Room(i, preset_name, col_cursor, total_rows)
        rooms.append(room)
        col_cursor = room.col_end + 1
    total_cols = col_cursor
    return rooms, total_cols, total_rows


def _passage_row(room_a, room_b):
    """Picks a row (row_from_bottom) valid for both rooms' interior, near mid-height
    of the shorter room, so the doorway aligns on both sides of the shared wall."""
    shorter = room_a if room_a.rows <= room_b.rows else room_b
    interior_top = shorter.row_start + 1
    interior_bottom = shorter.row_end - 1
    return (interior_top + interior_bottom) // 2


def _new_grid(total_cols, total_rows, fill):
    return [[fill for _ in range(total_cols)] for _ in range(total_rows)]


def _set(grid, total_rows, col, row_from_bottom, value):
    """Sets a cell using row_from_bottom convention (0 = bottom of the map)."""
    layer_y = row_from_bottom
    csv_row = total_rows - 1 - layer_y
    grid[csv_row][col] = value


def _build_collision_grid(rooms, total_cols, total_rows, passages):
    grid = _new_grid(total_cols, total_rows, EMPTY_TILE)

    # Outer map edges are always solid.
    for col in range(total_cols):
        _set(grid, total_rows, col, 0, SOLID_TILE)
        _set(grid, total_rows, col, total_rows - 1, SOLID_TILE)
    for row in range(total_rows):
        _set(grid, total_rows, 0, row, SOLID_TILE)
        _set(grid, total_rows, total_cols - 1, row, SOLID_TILE)

    for room in rooms:
        for col in range(room.col_start, room.col_end + 1):
            _set(grid, total_rows, col, room.row_start, SOLID_TILE)  # ceiling
            _set(grid, total_rows, col, room.row_end, SOLID_TILE)  # floor
        for row in range(room.row_start, room.row_end + 1):
            _set(grid, total_rows, room.col_start, row, SOLID_TILE)  # left wall
            _set(grid, total_rows, room.col_end, row, SOLID_TILE)  # right wall

    # Carve the aligned passage tiles between neighboring rooms (on the shared wall
    # columns, i.e. room_a.col_end and room_b.col_start are adjacent columns).
    for room_a, room_b, passage_row in passages:
        _set(grid, total_rows, room_a.col_end, passage_row, PASSAGE_TILE)
        _set(grid, total_rows, room_b.col_start, passage_row, PASSAGE_TILE)

    return grid


def _build_background_grid(total_cols, total_rows):
    return _new_grid(total_cols, total_rows, BACKGROUND_TILE)


def _grid_to_csv(grid):
    lines = [",".join(row) + "," for row in grid[:-1]]
    lines.append(",".join(grid[-1]))
    return "\n" + "\n".join(lines) + "\n"


def _interior_floor_cols(room, passages):
    """Interior floor columns (excluding perimeter walls and passage columns)."""
    excluded = {room.col_start, room.col_end}
    for room_a, room_b, _ in passages:
        if room_a is room or room_b is room:
            excluded.add(room_a.col_end if room_a is room else room_b.col_start)
    cols = [c for c in range(room.col_start + 1, room.col_end) if c not in excluded]
    return cols


def _room_world_rect(room, total_rows):
    x = room.col_start * TILE_SIZE
    y = room.row_start * TILE_SIZE
    width = room.cols * TILE_SIZE
    height = room.rows * TILE_SIZE
    return x, y, width, height


def _floor_world_y(room):
    """World y (bottom-left origin) of the interior floor tile's top surface."""
    return (room.row_end - 1) * TILE_SIZE + TILE_SIZE // 2


def _build_objects(rooms, passages, rng):
    objects = []
    next_id = 1

    def add(name, obj_type, x, y, width, height, properties=None):
        nonlocal next_id
        objects.append({
            "id": next_id,
            "name": name,
            "type": obj_type,
            "x": x,
            "y": y,
            "width": width,
            "height": height,
            "properties": properties or {},
        })
        next_id += 1

    for room in rooms:
        interior_cols = _interior_floor_cols(room, passages)
        if not interior_cols:
            continue
        floor_y = _floor_world_y(room)

        if room.index == 0:
            x = interior_cols[0] * TILE_SIZE
            add("playerStart", "playerStart", x, floor_y - TILE_SIZE // 2, TILE_SIZE, TILE_SIZE)

        available = [c for c in interior_cols]
        rng.shuffle(available)

        enemy_count = rng.randint(0, 2)
        for _ in range(min(enemy_count, len(available))):
            col = available.pop()
            enemy_type = rng.choice(ENEMY_TYPES)
            x = col * TILE_SIZE
            props = {"enemyType": enemy_type} if enemy_type != "walker" else {}
            add(f"enemy_r{room.index}", "enemy", x, floor_y - TILE_SIZE // 2, TILE_SIZE, TILE_SIZE, props)

        item_count = rng.randint(0, 3)
        for _ in range(min(item_count, len(available))):
            col = available.pop()
            item_type = rng.choice(ITEM_TYPES)
            x = col * TILE_SIZE
            size = 8 if item_type == "coin" else TILE_SIZE
            add(f"{item_type}_r{room.index}", item_type, x, floor_y - size // 2, size, size)

    return objects


def _objects_xml(objects):
    parts = []
    for obj in objects:
        props = obj["properties"]
        if props:
            props_xml = "".join(
                f'<property name="{k}" value="{v}"/>' for k, v in props.items()
            )
            parts.append(
                f'  <object id="{obj["id"]}" name="{obj["name"]}" type="{obj["type"]}" '
                f'x="{obj["x"]}" y="{obj["y"]}" width="{obj["width"]}" height="{obj["height"]}">\n'
                f'   <properties>\n    {props_xml}\n   </properties>\n  </object>'
            )
        else:
            parts.append(
                f'  <object id="{obj["id"]}" name="{obj["name"]}" type="{obj["type"]}" '
                f'x="{obj["x"]}" y="{obj["y"]}" width="{obj["width"]}" height="{obj["height"]}"/>'
            )
    return "\n".join(parts)


def _rooms_xml(rooms, total_rows, start_id):
    parts = []
    obj_id = start_id
    for room in rooms:
        x, y, width, height = _room_world_rect(room, total_rows)
        parts.append(
            f'  <object id="{obj_id}" name="room{room.index}_{room.preset_name}" '
            f'x="{x}" y="{y}" width="{width}" height="{height}"/>'
        )
        obj_id += 1
    return "\n".join(parts)


def generate_map(output_path, room_count=3, seed=None):
    """Builds a linear chain of room_count rooms (preset sizes chosen at random),
    each perimeter-sealed except for one passage to its neighbor(s), scatters a
    playerStart in room 0 and a small random count of enemies/items per room,
    and writes a complete .tmx (background/collision/objects/Rooms layers) to
    output_path."""
    rng = random.Random(seed)
    random.seed(seed)

    rooms, total_cols, total_rows = _build_rooms(room_count, total_rows_hint=8)

    passages = []
    for i in range(len(rooms) - 1):
        room_a, room_b = rooms[i], rooms[i + 1]
        passages.append((room_a, room_b, _passage_row(room_a, room_b)))

    collision_grid = _build_collision_grid(rooms, total_cols, total_rows, passages)
    background_grid = _build_background_grid(total_cols, total_rows)
    objects = _build_objects(rooms, passages, rng)
    rooms_start_id = (objects[-1]["id"] + 1) if objects else 1

    background_csv = _grid_to_csv(background_grid)
    collision_csv = _grid_to_csv(collision_grid)
    objects_xml = _objects_xml(objects)
    rooms_xml = _rooms_xml(rooms, total_rows, rooms_start_id)
    next_object_id = rooms_start_id + len(rooms)

    tmx = f'''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.12.2" orientation="orthogonal" renderorder="right-down" width="{total_cols}" height="{total_rows}" tilewidth="{TILE_SIZE}" tileheight="{TILE_SIZE}" infinite="0" nextlayerid="5" nextobjectid="{next_object_id}">
 <tileset firstgid="1" name="brick_bg" tilewidth="{TILE_SIZE}" tileheight="{TILE_SIZE}" tilecount="1" columns="1">
  <image source="../gfx/brick_bg.png" width="{TILE_SIZE}" height="{TILE_SIZE}"/>
 </tileset>
 <tileset firstgid="2" name="collision_tile" tilewidth="{TILE_SIZE}" tileheight="{TILE_SIZE}" tilecount="1" columns="1">
  <image source="../gfx/tile.png" width="{TILE_SIZE}" height="{TILE_SIZE}"/>
  <tile id="0">
   <properties>
    <property name="solid" type="bool" value="true"/>
   </properties>
  </tile>
 </tileset>
 <tileset firstgid="3" name="passage_tile" tilewidth="{TILE_SIZE}" tileheight="{TILE_SIZE}" tilecount="1" columns="1">
  <image source="../gfx/passage.png" width="{TILE_SIZE}" height="{TILE_SIZE}"/>
  <tile id="0">
   <properties>
    <property name="solid" type="bool" value="false"/>
   </properties>
  </tile>
 </tileset>
 <layer id="1" name="background" width="{total_cols}" height="{total_rows}">
  <data encoding="csv">{background_csv}</data>
 </layer>
 <layer id="2" name="collision" width="{total_cols}" height="{total_rows}">
  <data encoding="csv">{collision_csv}</data>
 </layer>
 <objectgroup id="3" name="objects">
{objects_xml}
 </objectgroup>
 <objectgroup id="4" name="Rooms">
{rooms_xml}
 </objectgroup>
</map>
'''

    with open(output_path, "w") as f:
        f.write(tmx)

    problems = validate_map(output_path)
    if problems:
        raise RuntimeError("Generated map failed validation:\n" + "\n".join(problems))

    print(f"Generated {output_path}: {room_count} rooms "
          f"({', '.join(r.preset_name for r in rooms)}), "
          f"{len(passages)} passages, {len(objects)} objects.")
    return output_path


def validate_map(path):
    """Returns a list of problems found (e.g. perimeter holes, CSV shape mismatches);
    empty list means the map is safe to load."""
    problems = []
    tree = ET.parse(path)
    root = tree.getroot()

    width = int(root.get("width"))
    height = int(root.get("height"))

    collision_layer = None
    for layer in root.findall("layer"):
        if layer.get("name") == "collision":
            collision_layer = layer
            break
    if collision_layer is None:
        problems.append("Missing 'collision' layer")
        return problems

    data_text = collision_layer.find("data").text.strip()
    rows = [line.strip().rstrip(",") for line in data_text.split("\n") if line.strip() != ""]
    if len(rows) != height:
        problems.append(f"collision layer has {len(rows)} rows, expected {height}")
    grid = [row.split(",") for row in rows]
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

    for room_obj in rooms_layer.findall("object"):
        rx = int(float(room_obj.get("x")))
        ry = int(float(room_obj.get("y")))
        rw = int(float(room_obj.get("width")))
        rh = int(float(room_obj.get("height")))
        col_start = rx // TILE_SIZE
        col_end = col_start + rw // TILE_SIZE - 1
        row_from_bottom_start = ry // TILE_SIZE
        row_from_bottom_end = row_from_bottom_start + rh // TILE_SIZE - 1

        def cell(col, row_from_bottom):
            layer_y = row_from_bottom
            csv_row = height - 1 - layer_y
            if csv_row < 0 or csv_row >= len(grid) or col < 0 or col >= width:
                return None
            return grid[csv_row][col]

        # Floor / ceiling
        for col in range(col_start, col_end + 1):
            for row_from_bottom in (row_from_bottom_start, row_from_bottom_end):
                v = cell(col, row_from_bottom)
                if v not in (SOLID_TILE, PASSAGE_TILE):
                    problems.append(
                        f"{room_obj.get('name')}: hole at col {col}, row_from_bottom {row_from_bottom} (value={v})"
                    )
        # Left / right walls
        for row_from_bottom in range(row_from_bottom_start, row_from_bottom_end + 1):
            for col in (col_start, col_end):
                v = cell(col, row_from_bottom)
                if v not in (SOLID_TILE, PASSAGE_TILE):
                    problems.append(
                        f"{room_obj.get('name')}: hole at col {col}, row_from_bottom {row_from_bottom} (value={v})"
                    )

    return problems


def main():
    parser = argparse.ArgumentParser(description="Generate a prototype .tmx map for the platformer.")
    parser.add_argument("--rooms", type=int, default=3, help="Number of rooms in the chain (default: 3).")
    parser.add_argument("--out", type=str, required=True, help="Output .tmx path.")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducible output.")
    args = parser.parse_args()

    generate_map(args.out, room_count=args.rooms, seed=args.seed)


if __name__ == "__main__":
    main()
