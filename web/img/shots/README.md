# Screenshots for the ORIGIN site

Everything in this folder is optional. `web/js/main.js` holds a `SHOTS` array
that lists the files the gallery expects; a file that is not here renders as a
labelled "Screenshot pending" placeholder instead of a broken image.

## How to produce one

1. Run ORIGIN (desktop, Android or the browser build).
2. Press **P** in game, or use **Take Screenshot** in the pause menu.
3. The game writes a PNG to a `screenshots/` folder next to the app:
   - desktop: `screenshots/` next to the `.jar` / launch directory
   - Android: `Gdx.files.external("screenshots")` — usually
     `Android/data/<package>/files/screenshots/`
   - web: your browser's download folder (`origin_<level>_<counter>.png`)
4. Copy the PNG here and rename it to the file name in the gallery.
5. Reload the page. Nothing else to do — no build step.

The capture is the exact frame you were looking at: world, HUD, touch overlay
and all, minus the pause menu.

## Already captured

| File | Level |
| --- | --- |
| `origin_tutorial_level_1_000.png` | Tutorial, Level 1 |
| `origin_world_2_level_1b_000.png` | Dungeon 2, Level 1b |

These two came straight out of the game with the in-game **P** key. The rest are
still pending.

## Suggested captures

| File | What to show |
| --- | --- |
| `origin_dungeon-1_1.png` | Dungeon 1, Level 1 — the first real room |
| `origin_dungeon-1_4.png` | Mid-sword-fight with a goblin |
| `origin_dungeon-3_3.png` | Dungeon 3 — the last room |
| `origin_menu.png` | The main menu |
| `origin_phone-1.png` | Any room, on a phone |
| `origin_phone-2.png` | The on-screen touch controls in action |
| `origin_phone-3.png` | A portrait run with the HUD and potion bar |

Keep shots at native resolution. The site scales them with
`image-rendering: pixelated`, so anything other than an integer scale will look
soft — if a shot looks blurry, that is the site's fault, not yours.
