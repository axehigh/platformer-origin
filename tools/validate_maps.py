#!/usr/bin/env python3
"""
TMX Map Validator for the libGDX Platformer.

Validates .tmx map files against the project's conventions:
- Required layers present (collision, objects, Rooms)
- Exactly one playerStart object per map
- At least one exitGate object
- exitGate objects have a nextLevel property
- Object type strings and their required property values
- secret tiles reference valid Room names
- TSX tileset files exist and are resolvable
- Tile properties on collision layer are well-formed

Usage:
    python tools/validate_maps.py                  # validate all maps
    python tools/validate_maps.py assets/maps/world1  # validate one world
    python tools/validate_maps.py path/to/map.tmx  # validate one map

Exit code 0 = all pass, 1 = errors found.
"""

import sys
import os
import xml.etree.ElementTree as ET
from pathlib import Path
from dataclasses import dataclass, field

# ---------------------------------------------------------------------------
# Constants — mirrors MapLoader.java / EntityFactory.java
# ---------------------------------------------------------------------------

REQUIRED_TILE_LAYERS = {"collision"}
REQUIRED_OBJ_LAYERS  = {"objects"}
RECOMMENDED_OBJ_LAYERS = {"Rooms", "enemies"}

KNOWN_OBJ_TYPES = {
    "playerStart",
    "coin", "chest", "torch", "exitGate",
    "dagger", "potion", "enemy", "platform", "trap",
}

# tileset source paths expected relative to maps/
KNOWN_TILESETS = {
    "../tileset/dungeon_tiles.tsx",
    "../tileset/items.tsx",
    "../tileset/enemy.tsx",
    "../tileset/bg.tsx",
    "../tileset/hazards.tsx",
    "../tileset/secret_wall.tsx",
    "../tileset/drop_platform.tsx",
}

VALID_ENEMY_TYPES  = {"walker", "flyer", "shooter", "knight"}
VALID_TRAP_TYPES   = {"acidDrop", "flame"}
VALID_POTION_TYPES = {"healing", "strength", "speed", "invulnerability"}

# Collision-layer tile property names the engine reads
COLLISION_BOOL_PROPS = {"solid", "hazard", "oneWay", "secret"}
COLLISION_STR_PROPS  = {"secretRoom", "effect"}

# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class Issue:
    severity: str   # "ERROR" | "WARN"
    message: str
    location: str   # e.g. "objects:12" or "collision tile gid=42"

@dataclass
class MapReport:
    path: str
    issues: list = field(default_factory=list)

    def error(self, msg, location=""):
        self.issues.append(Issue("ERROR", msg, location))

    def warn(self, msg, location=""):
        self.issues.append(Issue("WARN", msg, location))

    @property
    def has_errors(self):
        return any(i.severity == "ERROR" for i in self.issues)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _props_dict(elem):
    """Parse <properties> child into {name: value_str}."""
    out = {}
    props_el = elem.find("properties")
    if props_el is None:
        return out
    for p in props_el.findall("property"):
        out[p.get("name")] = p.get("value", "")
    return out


def _parse_csv_data(layer_elem):
    """Return set of non-zero tile GIDs from a <layer>'s CSV data."""
    data_el = layer_elem.find("data")
    if data_el is None or data_el.get("encoding") != "csv":
        return set()
    csv = data_el.text.strip().replace("\n", "").replace("\r", "")
    gids = set()
    for token in csv.split(","):
        token = token.strip()
        if token:
            gid = int(token)
            if gid > 0:
                gids.add(gid)
    return gids


def _gid_to_tileset_source(gid, tilesets):
    """Resolve a map-level GID to its tileset lookup key + local tile id.

    For external tilesets the key is the TSX source path.
    For inline tilesets the key is the tileset name or 'inline_<firstgid>'.
    """
    for firstgid, key, tilecount, is_inline in reversed(tilesets):
        if gid >= firstgid:
            return key, gid - firstgid
    return None, None


def _resolve_tsx_path(tmx_path, tsx_source):
    """Resolve a relative TSX path from the TMX file location."""
    tmx_dir = Path(tmx_path).parent
    return (tmx_dir / tsx_source).resolve()


def _load_tsx_tile_props(tsx_path):
    """Load a TSX and return {local_tile_id: {prop_name: prop_value_str}}.

    Reads both the tile's 'type' attribute and its <properties> children.
    """
    if not tsx_path.exists():
        return {}
    tree = ET.parse(str(tsx_path))
    root = tree.getroot()
    props_map = {}
    for tile_el in root.findall("tile"):
        tid = int(tile_el.get("id"))
        pd = _props_dict(tile_el)
        # Also capture the 'type' attribute on the <tile> element
        ttype = tile_el.get("type")
        if ttype:
            pd["type"] = ttype
        if pd:
            props_map[tid] = pd
    return props_map

# ---------------------------------------------------------------------------
# Per-map validation
# ---------------------------------------------------------------------------

def validate_map(tmx_path: str) -> MapReport:
    report = MapReport(path=tmx_path)

    try:
        tree = ET.parse(tmx_path)
    except ET.ParseError as e:
        report.error(f"XML parse error: {e}")
        return report

    root = tree.getroot()

    # -- Global map attributes --
    orientation = root.get("orientation")
    if orientation != "orthogonal":
        report.warn(f"Map orientation is '{orientation}', expected 'orthogonal'")
    renderorder = root.get("renderorder")
    if renderorder != "right-down":
        report.warn(f"Map renderorder is '{renderorder}', expected 'right-down'")
    if root.get("infinite") != "0":
        report.warn("Map has infinite=1; engine expects finite maps")

    tilewidth  = int(root.get("tilewidth",  "16"))
    tileheight = int(root.get("tileheight", "16"))

    # -- Parse tileset references --
    tilesets = []  # list of (firstgid, lookup_key, tilecount, is_inline)
    for ts_el in root.findall("tileset"):
        firstgid = int(ts_el.get("firstgid"))
        source   = ts_el.get("source", "")
        tc = ts_el.get("tilecount")
        tilecount = int(tc) if tc else 0
        is_inline = not source  # inline tileset (defined in the TMX itself)
        lookup_key = source if source else (ts_el.get("name") or f"inline_{firstgid}")
        tilesets.append((firstgid, lookup_key, tilecount, is_inline))

        if is_inline:
            ts_name = ts_el.get("name", "<unnamed>")
            report.warn(f"Inline tileset '{ts_name}' (firstgid={firstgid}) — "
                        f"consider moving to a .tsx file for reuse")
            continue

        # Check source is known
        if source not in KNOWN_TILESETS:
            report.warn(f"Unknown tileset source '{source}' in tileset declaration")

        # Check TSX file exists
        resolved = _resolve_tsx_path(tmx_path, source)
        if not resolved.exists():
            report.error(f"TSX file not found: {source} (resolved to {resolved})")

    tilesets.sort(key=lambda t: t[0])

    # -- Collect per-tileset tile properties --
    tsx_tile_props = {}  # lookup_key -> {local_id: {prop: val}}
    for _, lookup_key, _, is_inline in tilesets:
        if is_inline:
            continue  # handled separately below
        resolved = _resolve_tsx_path(tmx_path, lookup_key)
        tsx_tile_props[lookup_key] = _load_tsx_tile_props(resolved)

    # Also parse inline tileset tile properties directly from the TMX
    for ts_el in root.findall("tileset"):
        if ts_el.get("source"):
            continue
        for tile_el in ts_el.findall("tile"):
            tid = int(tile_el.get("id"))
            pd = _props_dict(tile_el)
            ttype = tile_el.get("type")
            if ttype:
                pd["type"] = ttype
            if pd:
                ts_key = ts_el.get("name") or f"inline_{ts_el.get('firstgid')}"
                tsx_tile_props.setdefault(ts_key, {})[tid] = pd

    # -- Separate layers --
    tile_layers   = {}   # name -> element
    obj_layers    = {}   # name -> element
    for child in root:
        if child.tag == "layer":
            tile_layers[child.get("name")] = child
        elif child.tag == "objectgroup":
            obj_layers[child.get("name")] = child

    # -- Required tile layers --
    for req in REQUIRED_TILE_LAYERS:
        if req not in tile_layers:
            report.error(f"Required tile layer '{req}' missing")

    # -- Collision layer deep checks --
    collision_layer = tile_layers.get("collision")
    if collision_layer is not None:
        gids = _parse_csv_data(collision_layer)
        for gid in gids:
            source, local_id = _gid_to_tileset_source(gid, tilesets)
            if source is None:
                report.error(f"Collision tile gid={gid} cannot be resolved to any tileset",
                             location="collision")
                continue
            local_props = tsx_tile_props.get(source, {}).get(local_id, {})
            # Warn if a tile on collision layer has no meaningful properties
            if not local_props:
                # GID 0 = empty; some tiles are just visual solid (default solid=true)
                # Only warn if tile has suspicious-looking properties
                pass
            # Check secret tiles have secretRoom
            if local_props.get("secret") == "true" and "secretRoom" not in local_props:
                report.warn(
                    f"Collision tile gid={gid} has secret=true but no secretRoom property "
                    f"— secret wall won't be grouped with a room",
                    location="collision"
                )

    # -- Required object layers --
    for req in REQUIRED_OBJ_LAYERS:
        if req not in obj_layers:
            report.error(f"Required object layer '{req}' missing")

    if "enemies" not in obj_layers:
        report.warn("Recommended object layer 'enemies' missing")

    # -- Objects validation --
    objects_layer = obj_layers.get("objects")
    player_start_count = 0
    has_exit_gate = False
    room_names = set()

    # Collect room names first
    rooms_layer = obj_layers.get("Rooms")
    if rooms_layer is not None:
        for obj in rooms_layer.findall("object"):
            name = obj.get("name", "")
            if name:
                room_names.add(name)

    if objects_layer is not None:
        for idx, obj in enumerate(objects_layer.findall("object")):
            obj_type = obj.get("type", "")
            obj_id   = obj.get("id", str(idx))
            loc = f"objects:id={obj_id}"
            props = _props_dict(obj)

            # If type is missing but has gid, try to resolve from tileset
            if not obj_type:
                gid = obj.get("gid")
                if gid:
                    source, local_id = _gid_to_tileset_source(int(gid), tilesets)
                    if source:
                        tile_def = tsx_tile_props.get(source, {}).get(local_id, {})
                        obj_type = tile_def.get("type", "")
                        # Merge tile-level properties
                        for k, v in tile_def.items():
                            if k not in props:
                                props[k] = v
                        # Infer type from tile properties if no explicit type
                        if not obj_type:
                            if "enemyType" in tile_def:
                                obj_type = "enemy"
                            elif tile_def.get("effect"):
                                obj_type = "torch"  # light/effect tiles

            if not obj_type:
                # Object with no type — check for legacy TileEnum property
                tileenum = props.get("type", "")
                if tileenum:
                    report.warn(
                        f"Object uses TileEnum property '{tileenum}' instead of "
                        f"object type attribute — consider updating",
                        location=loc
                    )
                    continue
                gid_str = obj.get("gid")
                if gid_str:
                    report.warn(
                        f"Object gid={gid_str} resolves to a tile with no type "
                        f"— will be ignored by engine",
                        location=loc
                    )
                else:
                    report.warn(
                        "Object has no type and no tile gid — will be ignored by engine",
                        location=loc
                    )
                continue

            if obj_type not in KNOWN_OBJ_TYPES:
                report.warn(
                    f"Unknown object type '{obj_type}'",
                    location=loc
                )

            # -- playerStart --
            if obj_type == "playerStart":
                player_start_count += 1

            # -- exitGate --
            elif obj_type == "exitGate":
                has_exit_gate = True
                next_level = props.get("nextLevel", "")
                if not next_level:
                    report.error(
                        "exitGate missing required 'nextLevel' property",
                        location=loc
                    )
                elif not next_level.endswith(".tmx"):
                    report.warn(
                        f"exitGate nextLevel '{next_level}' doesn't end with .tmx",
                        location=loc
                    )

            # -- enemy --
            elif obj_type == "enemy":
                etype = props.get("enemyType", "walker")
                if etype not in VALID_ENEMY_TYPES:
                    report.error(
                        f"Invalid enemyType '{etype}' — valid: {sorted(VALID_ENEMY_TYPES)}",
                        location=loc
                    )

            # -- trap --
            elif obj_type == "trap":
                ttype = props.get("trapType", "acidDrop")
                if ttype not in VALID_TRAP_TYPES:
                    report.error(
                        f"Invalid trapType '{ttype}' — valid: {sorted(VALID_TRAP_TYPES)}",
                        location=loc
                    )

            # -- potion --
            elif obj_type == "potion":
                ptype = props.get("potionType", "healing")
                if ptype not in VALID_POTION_TYPES:
                    report.error(
                        f"Invalid potionType '{ptype}' — valid: {sorted(VALID_POTION_TYPES)}",
                        location=loc
                    )

            # -- platform --
            elif obj_type == "platform":
                axis = props.get("axis", "")
                if axis and axis not in {"x", "y", "both"}:
                    report.error(
                        f"Invalid platform axis '{axis}' — valid: x, y, both",
                        location=loc
                    )

    # -- playerStart count --
    if player_start_count == 0:
        report.error("No playerStart object found in objects layer")
    elif player_start_count > 1:
        report.error(f"Expected exactly 1 playerStart, found {player_start_count}")

    # -- exitGate check --
    if not has_exit_gate:
        report.warn("No exitGate object found — level has no exit")

    # -- secret tile ↔ Room cross-reference --
    secret_tile_rooms = set()
    for layer_name, layer_el in tile_layers.items():
        gids = _parse_csv_data(layer_el)
        for gid in gids:
            source, local_id = _gid_to_tileset_source(gid, tilesets)
            if source is None:
                continue
            local_props = tsx_tile_props.get(source, {}).get(local_id, {})
            sr = local_props.get("secretRoom")
            if sr:
                secret_tile_rooms.add(sr)

    # Also check objects with secretRoom property
    for layer_el in obj_layers.values():
        for obj in layer_el.findall("object"):
            props = _props_dict(obj)
            sr = props.get("secretRoom")
            if sr:
                secret_tile_rooms.add(sr)

    for sr_name in secret_tile_rooms:
        if sr_name not in room_names:
            report.warn(
                f"secretRoom='{sr_name}' references a room name not found in the "
                f"Rooms layer (available rooms: {sorted(room_names) or 'none'})"
            )

    # -- Rooms layer checks --
    if rooms_layer is not None:
        room_objs = rooms_layer.findall("object")
        unnamed = [o for o in room_objs if not o.get("name")]
        if unnamed:
            report.warn(
                f"{len(unnamed)} room(s) in Rooms layer have no name — "
                f"engine will use fallback naming"
            )
        for obj in room_objs:
            props = _props_dict(obj)
            cam = props.get("camera", "")
            if cam and cam not in {"flip", "scroll"}:
                report.warn(
                    f"Room '{obj.get('name', '<unnamed>')}' has camera='{cam}' "
                    f"— valid values: flip, scroll (or omit for AUTO)",
                    location="Rooms"
                )

    # -- Orientation / size sanity --
    w = int(root.get("width", 0))
    h = int(root.get("height", 0))
    if w == 0 or h == 0:
        report.error(f"Map dimensions are {w}x{h} — must be > 0")

    # -- Rooms layer needed for large maps --
    MAP_NEEDS_ROOMS_THRESHOLD = (30, 17)
    if "Rooms" not in obj_layers:
        if (w, h) > MAP_NEEDS_ROOMS_THRESHOLD:
            report.warn(f"Map is {w}×{h} tiles (>{MAP_NEEDS_ROOMS_THRESHOLD[0]}×"
                        f"{MAP_NEEDS_ROOMS_THRESHOLD[1]}) but has no Rooms layer")

    return report

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

SKIP_DIRS = {"world_template"}

def find_tmx_files(path):
    """Recursively find all .tmx files under path, skipping SKIP_DIRS."""
    p = Path(path)
    if p.is_file() and p.suffix == ".tmx":
        return [p]
    return sorted(
        f for f in p.rglob("*.tmx")
        if not any(part in SKIP_DIRS for part in f.parts)
    )


def main():
    map_root = sys.argv[1] if len(sys.argv) > 1 else "assets/maps"

    if not os.path.exists(map_root):
        print(f"ERROR: path '{map_root}' does not exist")
        sys.exit(2)

    tmx_files = find_tmx_files(map_root)
    if not tmx_files:
        print(f"No .tmx files found under '{map_root}'")
        sys.exit(0)

    total_errors = 0
    total_warns  = 0

    for tmx in tmx_files:
        rel = str(tmx).replace("\\", "/")
        report = validate_map(str(tmx))

        if not report.issues:
            print(f"  OK  {rel}")
            continue

        errors = [i for i in report.issues if i.severity == "ERROR"]
        warns  = [i for i in report.issues if i.severity == "WARN"]
        total_errors += len(errors)
        total_warns  += len(warns)

        print(f"\n{'ERROR' if errors else 'WARN '}  {rel}")
        for i in report.issues:
            loc = f"  [{i.location}]" if i.location else ""
            print(f"    {i.severity}: {i.message}{loc}")

    print(f"\n{'='*60}")
    print(f"Scanned {len(tmx_files)} map(s): {total_errors} error(s), {total_warns} warning(s)")

    sys.exit(1 if total_errors > 0 else 0)


if __name__ == "__main__":
    main()
