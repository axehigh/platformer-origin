---
name: pixel-art-asset-generator
description: Use when creating a new placeholder pixel-art icon/sprite for this prototype's assets/gfx/ folder (items, pickups, enemies, gates/doors, projectiles, decorations, tiles) or regenerating an existing one, so it visually fits the project's 8-bit/16-bit medieval-dungeon theme. Also use when no image-editing tool (Pillow, ImageMagick) is available and a PNG must be hand-built.
---

# Pixel-Art Asset Generator (Prototype Icons)

This project's `assets/gfx/*.png` sprites are throwaway prototype art — they exist so gameplay
is readable, and are expected to be replaced with real art later (see `AGENTS.md` §4 "Visual
Style"). They do **not** need to look polished or exact. They DO need to:
1. Read clearly at tiny sizes (8x8–16x24) against the game's dark stone/brick background.
2. Share the same flat, low-color, high-contrast style as the existing sprites, so a new icon
   doesn't visually clash with the rest of the HUD/world.
3. Be generated **without Pillow/ImageMagick** — neither is installed in this sandbox. Every
   existing sprite (`chest.png`, `torch.png`, `dagger.png`, `exit_gate.png`, ...) was produced by
   hand-encoding raw RGBA pixel rows directly into PNG bytes with only `struct` + `zlib` (both
   stdlib). Use the same approach for new icons via `scripts/generate_png.py`.

## Style Rules (match the existing sprites)

- **Flat colors only, no gradients/anti-aliasing/dithering.** Existing sprites use 2-6 distinct
  RGBA colors total (`chest.png` has 3, `dagger.png` has 5, `coin.png` has 2).
- **Fully transparent background** (`(0, 0, 0, 0)`), usually with a 1px transparent border around
  the whole icon so it doesn't look clipped against tile edges.
- **Muted, desaturated base tones** (grays/browns/dark-blues for stone, wood, metal) plus **one
  brighter accent color** for the "important" part (gold/orange glow, a gem, a blade highlight) —
  e.g. `torch.png` is mostly dark brown/stone with a bright orange `(255, 140, 0, 255)` flame;
  `exit_gate.png` is a stone frame + dark portal with a small gold `(255, 200, 60, 255)` glow.
- **Silhouette-first:** since there's no shading, the icon must be recognizable from its outline
  alone (a chest is a rectangle with a banded lid, a dagger is a blade + hilt cross-shape, a gate
  is a framed rectangle with a darker interior).
- **Pixel-perfect symmetry** where it makes sense (most icons here are left-right symmetric) —
  makes them easier to reason about as an ASCII grid and looks more deliberate at low res.

## Standard Sizes (pick to match how the sprite is used in code)

| Size | Used for | Example |
|---|---|---|
| 8x8 | Small HUD/pickup icons | `coin.png`, `heart.png` |
| 16x16 | Most world objects/enemies/tiles | `chest.png`, `torch.png`, `dagger.png`, `exit_gate.png`, `enemy.png`, `tile.png` |
| 16x24 | Player-sized entities | `player.png`, `player_attack.png` |
| Small/misc | Tiny effects | `bullet.png` (6x4), `white.png` (4x4, a 1x1-scaled solid pixel) |

The chosen pixel size must match whatever `Texture.getWidth()/getHeight()` (or a hardcoded
`CollisionComponent.bounds` size) the spawning code expects — check `EntityFactory` for how the
sprite's entity is built before picking dimensions, so the collision box and the art agree.

## Workflow

1. **Check where the sprite is used** in `EntityFactory`/`Mappers`/the relevant `.tmx` object to
   confirm the exact pixel size expected, and skim 1-2 existing same-category sprites (e.g. other
   pickups, other enemies) for a palette/style reference point.
2. **Sketch the icon as an ASCII grid on paper/in your head first** — rows of characters, each
   character mapped to one RGBA color, respecting the pixel size from step 1. Keep it simple:
   a border/background of transparency (`T`), a base color for the bulk of the shape, and (at
   most) one or two accent colors.
3. **Write the PNG** using `scripts/generate_png.py`'s `write_png(path, rows)`:
   ```python
   import sys
   sys.path.insert(0, "/absolute/path/to/.junie/skills/pixel-art-asset-generator/scripts")
   from generate_png import write_png, T

   FRAME = (90, 90, 100, 255)   # stone frame
   GLOW = (255, 200, 60, 255)   # accent

   rows = [
       [T]*16,
       [T, T] + [FRAME]*12 + [T, T],
       # ... one row per pixel row, each row a list of 16 RGBA tuples ...
       [T]*16,
   ]
   write_png("assets/gfx/my_new_icon.png", rows)
   ```
   Run this inline via a `python3 -c "..."` command (matching how every existing sprite in this
   repo was generated) rather than leaving one-off generator scripts lying around in the repo.
4. **Verify the result** with `preview_png(path)` from the same module (or run
   `python3 scripts/generate_png.py assets/gfx/my_new_icon.png`) — it prints the ASCII shape back
   plus a color legend, so you can sanity-check the silhouette and palette size before wiring it
   into the game. Also double check `file assets/gfx/my_new_icon.png` reports the expected
   dimensions/PNG format.
5. **Wire it up** exactly like existing sprites: add an `assetManager.load("gfx/my_new_icon.png",
   Texture.class);` line alongside the others in `GameScreen.show()`, then reference the same path
   from wherever `EntityFactory` builds that entity (it fetches the already-loaded texture via
   `EntityFactory.getTexture(...)`).
6. This is placeholder art — don't over-invest in detail. If the silhouette reads correctly in the
   ASCII preview and it uses the established palette conventions, it's done.

## Notes

- `scripts/generate_png.py` has no dependencies beyond the Python standard library (`struct`,
  `zlib`) — it works in this sandbox exactly as-is, unlike Pillow/ImageMagick-based approaches.
- Every row must have exactly `width` entries and every icon must have exactly `height` rows —
  `write_png` asserts this, so mismatched dimensions fail loudly rather than silently corrupting
  the PNG.
- Per `AGENTS.md`'s "Grill Before Building" convention, if a requested new asset's exact shape,
  color, or theme is ambiguous (e.g. a brand-new enemy type with no obvious existing analog),
  clarify with the requester before generating — but minor stylistic choices for a straightforward
  reskin/variant of an existing category don't need grilling.
