#!/usr/bin/env python3
"""Convert map-design-for-tiled.md to a styled HTML page.

Usage:
    python3 tools/generate_html.py [input.md] [output.html]

Defaults to:
    input  = resources/docs-ai/map-design-for-tiled.md
    output = resources/docs-ai/map-design-for-tiled.html

The HTML is never edited by hand — always regenerated from the markdown.
"""

import mistune
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DEFAULT_INPUT = os.path.join(PROJECT_ROOT, "resources", "docs-ai", "map-design-for-tiled.md")
DEFAULT_OUTPUT = os.path.join(PROJECT_ROOT, "resources", "docs-ai", "map-design-for-tiled.html")

# --- Dark theme CSS (single source of truth for styling) ---
CSS = """
:root {
  --bg: #1a1a2e;
  --surface: #16213e;
  --border: #0f3460;
  --text: #e0e0e0;
  --text-muted: #a0a0b0;
  --accent: #e94560;
  --accent2: #533483;
  --link: #5dade2;
  --code-bg: #0d1b2a;
  --table-header: #0f3460;
  --table-row-alt: rgba(15,52,96,0.25);
  --warning-bg: #3d2b00;
  --warning-border: #e9a100;
  --tip-bg: #003d2b;
  --tip-border: #00e9a1;
  --info-bg: #002b3d;
  --info-border: #00a1e9;
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Inter', 'Segoe UI', system-ui, -apple-system, sans-serif;
  background: var(--bg); color: var(--text); line-height: 1.7; padding: 0;
}
.layout { display: flex; max-width: 1400px; margin: 0 auto; }
nav.toc {
  position: sticky; top: 0; height: 100vh; width: 280px; min-width: 280px;
  overflow-y: auto; padding: 24px 16px; background: var(--surface);
  border-right: 1px solid var(--border); font-size: 0.82rem;
}
nav.toc h2 {
  font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.1em;
  color: var(--accent); margin-bottom: 12px;
}
nav.toc ul { list-style: none; padding: 0; }
nav.toc li { margin-bottom: 4px; }
nav.toc a {
  color: var(--text-muted); text-decoration: none; display: block;
  padding: 3px 8px; border-radius: 4px; transition: all 0.15s;
}
nav.toc a:hover { color: var(--text); background: rgba(233,69,96,0.1); }
nav.toc .indent { padding-left: 20px; }
nav.toc .indent2 { padding-left: 36px; }
main { flex: 1; min-width: 0; padding: 40px 56px; max-width: 900px; }
h1 {
  font-size: 2rem; color: var(--accent); margin-bottom: 8px;
  border-bottom: 2px solid var(--accent); padding-bottom: 12px;
}
h2 {
  font-size: 1.5rem; color: var(--accent); margin-top: 48px; margin-bottom: 16px;
  border-bottom: 1px solid var(--border); padding-bottom: 8px;
}
h3 { font-size: 1.15rem; color: #c0c0d0; margin-top: 32px; margin-bottom: 12px; }
h4 { font-size: 1rem; color: #a0a0c0; margin-top: 24px; margin-bottom: 8px; }
p { margin-bottom: 14px; }
a { color: var(--link); }
code {
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  background: var(--code-bg); padding: 2px 6px; border-radius: 3px;
  font-size: 0.88em; color: #f0c674;
}
pre {
  background: var(--code-bg); border: 1px solid var(--border); border-radius: 6px;
  padding: 16px 20px; overflow-x: auto; margin: 16px 0; font-size: 0.85rem; line-height: 1.5;
}
pre code { background: none; padding: 0; color: var(--text); }
strong { color: #fff; }
em { color: var(--text-muted); font-style: italic; }
ul, ol { margin: 8px 0 16px 24px; }
li { margin-bottom: 4px; }
hr { border: none; border-top: 1px solid var(--border); margin: 40px 0; }
blockquote {
  border-left: 3px solid var(--accent); background: var(--warning-bg);
  padding: 12px 16px; margin: 16px 0; border-radius: 0 6px 6px 0; font-size: 0.92em;
}
blockquote p { margin-bottom: 0; }
table { width: 100%; border-collapse: collapse; margin: 16px 0 24px; font-size: 0.9rem; }
thead th {
  background: var(--table-header); color: #fff; font-weight: 600;
  text-align: left; padding: 10px 12px; border-bottom: 2px solid var(--accent); white-space: nowrap;
}
tbody td { padding: 8px 12px; border-bottom: 1px solid var(--border); vertical-align: top; }
tbody tr:nth-child(even) { background: var(--table-row-alt); }
tbody tr:hover { background: rgba(233,69,96,0.08); }
@media print {
  nav.toc { display: none; }
  main { padding: 20px; max-width: 100%; }
  body { background: #fff; color: #111; }
  table { border: 1px solid #ccc; }
  thead th { background: #eee; color: #111; }
}
@media (max-width: 900px) {
  nav.toc { display: none; }
  main { padding: 20px; }
}
"""

# --- TOC structure (hardcoded to match the markdown headings) ---
# IDs are auto-generated from heading text: lowercased, non-alphanumeric → dash
TOC_ITEMS = [
    ("#1-the-big-picture", "1. The big picture", 0),
    ("#units-scale-and-coordinates-important", "Units, scale & coordinates", 1),
    ("#2-layers", "2. Layers", 0),
    ("#3-the-collision-layer-the-tile-language", "3. The collision layer", 0),
    ("#3-1-the-passage-rule", "3.1 Passage rule", 1),
    ("#3-2-hazards-spikes-lava", "3.2 Hazards", 1),
    ("#3-3-drop-through-platforms-one-way", "3.3 Drop-through platforms", 1),
    ("#3-4-secret-walls-breakable", "3.4 Secret walls", 1),
    ("#3-5-secret-rooms-hidden-until-the-wall-breaks", "3.5 Secret rooms", 1),
    ("#3-6-vertical-room-links-platform-shafts", "3.6 Vertical room links", 1),
    ("#4-the-objects-enemies-layers-markers", "4. Objects / Enemies layers", 0),
    ("#marker-type-reference", "Marker type reference", 1),
    ("#traps-tile-hazards-spawned-trap-entities", "Traps", 1),
    ("#4-5-effect-property-effect-property", "4.5 Effect property", 1),
    ("#5-custom-properties-the-full-reference", "5. Custom properties reference", 0),
    ("#6-the-rooms-layer-camera", "6. Rooms layer & camera", 0),
    ("#7-step-by-step-build-a-new-level", "7. Step-by-step: new level", 0),
    ("#8-common-pitfalls-checklist-when-something-feels-wrong", "8. Common pitfalls", 0),
    ("#9-related-tooling-docs", "9. Related tooling & docs", 0),
]


def build_toc():
    items = []
    for href, label, depth in TOC_ITEMS:
        cls = ' class="indent"' if depth == 1 else ""
        items.append(f'<li><a href="{href}"{cls}>{label}</a></li>')
    return "\n".join(items)


def md_to_html(md_text):
    """Convert markdown to HTML, adding id attributes to headings."""
    md = mistune.create_markdown(escape=False)

    # Add anchor IDs to headings so the TOC works
    import re

    def add_heading_ids(html):
        def replacer(m):
            full = m.group(0)
            level = m.group(1)
            # Extract text content, stripping any inner HTML tags for slug generation
            inner = re.sub(r"<[^>]+>", "", m.group(2))
            # Decode common HTML entities
            inner = inner.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            inner = inner.replace("&#39;", "'").replace("&quot;", '"')
            slug = re.sub(r"[^a-z0-9]+", "-", inner.lower()).strip("-")
            slug = re.sub(r"-{2,}", "-", slug)  # collapse multiple dashes
            # Replace the original tag with one that has an id
            return full.replace(f"<h{level}>", f'<h{level} id="{slug}">', 1)

        return re.sub(r"<h([1-6])>(.+?)</h\1>", replacer, html, flags=re.DOTALL)

    body = md(md_text)
    body = add_heading_ids(body)
    return body


def generate(md_path, html_path):
    with open(md_path, "r", encoding="utf-8") as f:
        md_text = f.read()

    body_html = md_to_html(md_text)
    toc_html = build_toc()

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Map Design for Tiled — Level Authoring Guide</title>
<style>{CSS}</style>
</head>
<body>
<div class="layout">
<nav class="toc">
  <h2>Contents</h2>
  <ul>
{toc_html}
  </ul>
</nav>
<main>
{body_html}
<hr>
<p><em>Auto-generated from <code>map-design-for-tiled.md</code> — do not edit this file directly.</em></p>
</main>
</div>
</body>
</html>
"""

    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)

    print(f"Generated: {html_path}")
    print(f"  Source:   {md_path}")
    print(f"  Size:     {os.path.getsize(html_path):,} bytes")


if __name__ == "__main__":
    md_file = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_INPUT
    html_file = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUTPUT
    generate(md_file, html_file)
