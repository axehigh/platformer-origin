# Asset Atlas Reference

## Overview

This project uses two texture atlases plus a few standalone PNGs:

| Atlas | Location | Purpose |
|-------|----------|---------|
| `origin-game.atlas` | `assets/gfx/` | In-game sprites (enemies, items, effects) |
| `uiskin.atlas` | `assets/ui/` | UI widgets (buttons, dialogs, D-pad, touch controls) |
| `gfx/old/*.png` | `assets/gfx/old/` | Legacy standalone PNGs (being phased out) |
| `gfx/acid_drop.png` | `assets/gfx/` | Standalone trap sprite (should be in atlas) |
| `gfx/lava_drop.png` | `assets/gfx/` | Standalone trap sprite (should be in atlas) |

---

## Atlas Contents

### `origin-game.atlas` — In-Game Sprites

| Region | Used by | Notes |
|--------|---------|-------|
| `Coin_01..06` | `EntityFactory` — animated coin pickup | Looping animation |
| `Chest_01_Locked` | `EntityFactory` — chest closed state | |
| `Chest_01_Unlocked` | `EntityFactory` — chest open state | |
| `Key_01`, `Key_02` | `EntityFactory` — key pickup | |
| `Diamond` | `EntityFactory` — diamond pickup | |
| `Life` | `EntityFactory` — extra life pickup | |
| `fire1..10` | `EntityFactory` — flame trap animation | 256×256, used for pulsing fire |
| `blade1..7` | `EntityFactory` — blade trap animation | |
| `lightning1..9` | `EntityFactory` — lightning trap animation | |
| `goblin_*` | `EntityFactory` — goblin enemy states | walk, attack, death, hurt, idle |
| `spider_*` | `EntityFactory` — spider enemy states | walk, attack, death, hurt, idle, web |
| `mosquito_*` | `EntityFactory` — mosquito enemy states | flight, attack, death, hurt, idle |
| `potion_healing` | `PotionType.HEALING` | Region key shared with `uiskin.atlas` |
| `potion_invulnerability` | `PotionType.INVULNERABILITY` | Region key shared with `uiskin.atlas` |
| `potion_speed` | `PotionType.SPEED` | Region key shared with `uiskin.atlas` |
| `potion_strength` | `PotionType.STRENGTH` | Region key shared with `uiskin.atlas` |

### `uiskin.atlas` — UI Widgets

| Region | Used by | Notes |
|--------|---------|-------|
| `button` | `GameScreen` — touch controls | |
| `button_empty` | `GameScreen` — touch controls | |
| `button_off` | `GameScreen` — touch controls | |
| `button_on` | `GameScreen` — touch controls | |
| `flatAttack` | `GameScreen` — A button icon | |
| `flatDown` | `GameScreen` — D-pad down | |
| `flatFlame` | `GameScreen` — Y button icon | |
| `flatFly` | `GameScreen` — special icon | |
| `flatLeft` | `GameScreen` — D-pad left | |
| `flatRight` | `GameScreen` — D-pad right | |
| `flatUp` | `GameScreen` — D-pad up | |
| `bag` | `GameScreen` — inventory button | |
| `potion_healing` | `InventoryBarStage`, `HudStage` | Region key shared with `origin-game.atlas` |
| `potion_invulnerability` | `InventoryBarStage`, `HudStage` | Region key shared with `origin-game.atlas` |
| `potion_speed` | `InventoryBarStage`, `HudStage` | Region key shared with `origin-game.atlas` |
| `potion_strength` | `InventoryBarStage`, `HudStage` | Region key shared with `origin-game.atlas` |
| `rope_big` | UI decorations | |
| `rope_big_horisontal` | UI decorations | |
| `star` | UI decorations | |
| `scroll_info` | Dialog backgrounds | |
| `scroll_large` | Dialog backgrounds | |
| `table` | Dialog panel background | 1102×755, scaled uniformly |

---

## Legacy Standalone PNGs (`gfx/old/`)

| File | Used by | Status | Recommendation |
|------|---------|--------|----------------|
| `coin.png` | `HudStage` — coin counter icon | **ACTIVE** | Move to `origin-game.atlas` |
| `torch.png` | `EntityFactory` — torch decoration | **ACTIVE** | Move to `origin-game.atlas` |
| `heart.png` | `HudStage` — health hearts | **ACTIVE** | Move to `origin-game.atlas` |
| `bullet.png` | `EnemyShootSystem`, `PlayerInputSystem` — projectiles | **ACTIVE** | Move to `origin-game.atlas` |
| `dagger.png` | `HudStage` — item tracker; `EntityFactory` — dagger pickup | **ACTIVE** | Move to `origin-game.atlas` |
| `platform.png` | `EntityFactory` — moving platform fallback | **ACTIVE** | Move to `origin-game.atlas` |
| ~~`brick_bg.png`~~ | — | **REMOVED** | Deleted |
| ~~`chest_open.png`~~ | — | **REMOVED** | Deleted |
| ~~`chest.png`~~ | — | **REMOVED** | Deleted |
| ~~`enemy_flyer.png`~~ | — | **REMOVED** | Deleted |
| ~~`enemy_shooter.png`~~ | — | **REMOVED** | Deleted |
| ~~`enemy.png`~~ | — | **REMOVED** | Deleted |
| ~~`passage.png`~~ | — | **REMOVED** | Deleted |
| ~~`player_attack.png`~~ | — | **REMOVED** | Deleted |
| ~~`sword.png`~~ | — | **REMOVED** | Deleted |
| ~~`tile.png`~~ | — | **REMOVED** | Deleted |
| ~~`white.png`~~ | — | **REMOVED** | Deleted |
| ~~`inventory_backpack.png`~~ | — | **REMOVED** | Deleted |
| ~~`potion_healing.png`~~ | — | **REMOVED** | Deleted |
| ~~`potion_invulnerability.png`~~ | — | **REMOVED** | Deleted |
| ~~`potion_speed.png`~~ | — | **REMOVED** | Deleted |
| ~~`potion_strength.png`~~ | — | **REMOVED** | Deleted |
| ~~`potion_swap.png`~~ | — | **REMOVED** | Deleted |

---

## Standalone Trap PNGs

| File | Used by | Status | Recommendation |
|------|---------|--------|----------------|
| `gfx/acid_drop.png` | `TrapSystem` — acid drop projectile | **ACTIVE** | Move to `origin-game.atlas` |
| `gfx/lava_drop.png` | `TrapSystem` — lava drop projectile | **ACTIVE** | Move to `origin-game.atlas` |

---

## Recommendations

### Immediate Cleanup

1. ~~**Remove 14 unused files** from `gfx/old/`~~ **DONE** — 17 files deleted

2. **Move 8 active PNGs** to `origin-game.atlas`:
   - From `gfx/old/`: `coin.png`, `torch.png`, `heart.png`, `bullet.png`, `dagger.png`, `platform.png`
   - From `gfx/`: `acid_drop.png`, `lava_drop.png`

3. **Remove `gfx/old/` directory** after migration (all files either deleted or moved to atlas).

### Migration Steps

1. Rebuild `origin-game.atlas` using TexturePacker to include the 8 active PNGs
2. Update `GameAssetRegistry.java` to remove `gfx/old/` loads
3. Update all Java files to use `originAtlas.findRegion(...)` instead of `assetManager.get("gfx/old/...")`
4. Delete `gfx/old/` directory and remaining unused files

### Asset Guidelines

- **In-game sprites** (enemies, items, effects, trap projectiles) → `origin-game.atlas`
- **UI widgets** (buttons, dialogs, touch controls) → `uiskin.atlas`
- **Shared regions** (potions) exist in both atlases with identical region names
- **Standalone PNGs** should be avoided — use atlases for texture atlas packing efficiency
