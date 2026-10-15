#!/usr/bin/env python3
"""Find image assets under assets/maps/gfx that are not referenced anywhere.

Reference model
---------------
A gfx file is considered IN USE when its path appears in a text file in the repo
(Tiled .tsx/.tmx, Java, atlas, docs, ...). Paths are resolved relative to the
referencing file, the repo root, and the assets/ root, so both Tiled-relative
("..\\gfx\\tiles\\...png") and classpath-relative ("maps/gfx/...png")
references are detected.

Because the project's tilesets are separate .tsx files that maps pull in via
<tmx> <tileset source="...">, a tile can be referenced by a .tsx that no map
actually loads. Those are reported separately as WEAK (referenced only by a
tileset that no map references) so a real "never used anywhere" list stays
clean.

Usage:
    py assets/maps/find_unused_gfx.py

Writes the report to assets/maps/gfx/UNUSED_GFX.txt
"""
from __future__ import annotations

import re
from pathlib import Path

SCRIPT = Path(__file__).resolve()
REPO_ROOT = SCRIPT.parents[2]
ASSETS = REPO_ROOT / "assets"
GFX_ROOT = ASSETS / "maps" / "gfx"
TILESET_DIR = ASSETS / "maps" / "tileset"
REPORT = GFX_ROOT / "UNUSED_GFX.txt"

IMG_EXT = {".png", ".jpg", ".jpeg"}
SKIP_DIRS = {
    ".git", ".gradle", ".idea", "build", "out", "node_modules",
    ".vscode", "__pycache__",
}
TEXT_EXT = {
    ".tsx", ".tmx", ".xml", ".java", ".json", ".atlas", ".md", ".txt",
    ".properties", ".sh", ".gradle", ".p", ".csv", ".frag", ".vert",
    ".gwt", ".session",
}
SKIP_FILES = {".tiled-session", "assets.txt"}

TOKEN_RE = re.compile(r"[^\"'<>:\n\r\t]*\.(?:png|jpg|jpeg)", re.IGNORECASE)
TSX_RE = re.compile(r"[^\"'<>:\n\r\t]*\.tsx", re.IGNORECASE)


def is_skipped_dir(path: Path) -> bool:
    return any(part in SKIP_DIRS for part in path.parts)


def collect_gfx_files() -> list[Path]:
    return sorted(
        p.resolve()
        for p in GFX_ROOT.rglob("*")
        if p.is_file() and p.suffix.lower() in IMG_EXT
    )


def iter_text_files():
    for p in REPO_ROOT.rglob("*"):
        if not p.is_file() or is_skipped_dir(p):
            continue
        if p.name in SKIP_FILES or p.name.endswith(".tiled-session") or p == REPORT:
            continue
        if p.suffix.lower() in TEXT_EXT or p.name.endswith(".gwt.xml"):
            yield p


def resolve_token(token: str, base: Path) -> Path | None:
    token = token.strip().strip('"').strip("'")
    if not token:
        return None
    token = token.replace("\\", "/")
    if token.startswith("./"):
        token = token[2:]
    raw = Path(token)
    try:
        return raw.resolve() if raw.is_absolute() else (base / raw).resolve()
    except OSError:
        return None


def references_in(f: Path):
    """Yield (referrer_path, resolved_target) for every resolvable path token."""
    try:
        text = f.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return
    is_tmx = f.suffix.lower() == ".tmx"
    patterns = [TOKEN_RE] + ([TSX_RE] if is_tmx else [])
    for pattern in patterns:
        for match in pattern.findall(text):
            for base in (f.parent, REPO_ROOT, ASSETS):
                resolved = resolve_token(match, base)
                if resolved is not None and resolved.exists():
                    yield f.resolve(), resolved
                    break


def main() -> int:
    gfx_files = collect_gfx_files()
    gfx_set = set(gfx_files)

    # referrer -> set of gfx paths it references; and reverse map
    refs: dict[Path, set[str]] = {}
    all_tsx: set[Path] = set(TILESET_DIR.glob("*.tsx")) if TILESET_DIR.is_dir() else set()
    tsx_referenced_by_map: set[Path] = set()

    for f in iter_text_files():
        for referrer, target in references_in(f):
            if target in gfx_set:
                refs.setdefault(target, set()).add(
                    referrer.relative_to(REPO_ROOT).as_posix()
                )
            if referrer.suffix.lower() == ".tmx" and target in all_tsx:
                tsx_referenced_by_map.add(target)

    def is_active_tsx(ref: str) -> bool:
        return (REPO_ROOT / ref).resolve() in tsx_referenced_by_map

    used: dict[Path, set[str]] = {}
    weak: dict[Path, set[str]] = {}
    for target, referrers in refs.items():
        strong = {r for r in referrers if not r.endswith(".tsx") or is_active_tsx(r)}
        if strong:
            used[target] = referrers
        else:
            weak[target] = referrers

    unused = [p for p in gfx_files if p not in refs]

    def rel(p: Path) -> str:
        return p.relative_to(REPO_ROOT).as_posix()

    lines: list[str] = []
    lines.append("Unused gfx report")
    lines.append("=" * 60)
    lines.append(f"Scanned gfx files : {len(gfx_files)}")
    lines.append(f"In use            : {len(used)}")
    lines.append(f"Weak (tileset unused by any map) : {len(weak)}")
    lines.append(f"Not in use        : {len(unused)}")
    lines.append("")
    lines.append("Notes: 'assets/assets.txt' (generated asset manifest) and Tiled")
    lines.append("       *.tiled-session editor state files are ignored.")
    lines.append("")

    def dump_grouped(title: str, items: list[Path]) -> None:
        lines.append(title)
        lines.append("-" * 60)
        if not items:
            lines.append("(none)")
            lines.append("")
            return
        by_dir: dict[str, list[Path]] = {}
        for p in items:
            by_dir.setdefault(p.parent.relative_to(GFX_ROOT).as_posix(), []).append(p)
        for key in sorted(by_dir):
            lines.append(f"[{key}]")
            for p in by_dir[key]:
                lines.append(f"  {p.name}")
            lines.append("")

    dump_grouped("NOT IN USE", unused)
    dump_grouped("WEAK - only referenced by a tileset no map loads", list(weak))

    lines.append("=" * 60)
    lines.append("FULL PATHS NOT IN USE")
    lines.append("-" * 60)
    for p in unused:
        lines.append(rel(p))
    lines.append("")

    lines.append("=" * 60)
    lines.append("FULL PATHS WEAK (unused tileset only)")
    lines.append("-" * 60)
    for p in gfx_files:
        if p in weak:
            lines.append(f"{rel(p)}  <-  {', '.join(sorted(weak[p]))}")
    lines.append("")

    lines.append("=" * 60)
    lines.append("FULL PATHS IN USE")
    lines.append("-" * 60)
    for p in gfx_files:
        if p in used:
            lines.append(f"{rel(p)}  <-  {', '.join(sorted(used[p]))}")

    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {REPORT}")
    print(
        f"gfx files: {len(gfx_files)}  in use: {len(used)}  "
        f"weak: {len(weak)}  unused: {len(unused)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
