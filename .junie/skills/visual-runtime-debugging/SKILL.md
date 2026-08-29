---
name: visual-runtime-debugging
description: Use when debugging a runtime/visual bug in this libgdx platformer — "sprite does not show", "entity invisible", "bullet/projectile missing", "wrong position", "stuck behind wall", or any behavior you must see in the running game to understand. Covers a reproducible recipe: a temporary launcher that jumps straight into a specific level, temporary System.out.println instrumentation in ECS systems, screenshot capture + pixel-color analysis when images cannot be viewed, and .tmx collision-layer verification. Also use when a spawned entity never appears on screen or is removed before it renders.
---

# Visual / Runtime Debugging (libGDX Platformer)

This game renders through a `FitViewport` (world = `VIRTUAL_WIDTH*scale` x `VIRTUAL_HEIGHT*scale`,
window 1280x720) and runs a `PooledEngine` where **every system executes every frame in priority
order**. Both facts drive how you debug a "thing doesn't show up" bug. The method below is the
three-layer loop: log it, check the geometry, then look at the pixels.

## 0. The #1 gotcha: same-frame despawn before render

ECS does not render an entity the instant it is spawned. Within a single frame the engine runs
every system in priority order (`PRIORITY_*` in `GameScreen`). So an entity can be created
(priority 0, e.g. `PlayerInputSystem.spawnBullet`) and **removed in the same frame** (priority 7,
e.g. `PlayerBulletSystem` wall/enemy hit, `EnemyBulletCollisionSystem`), before `RenderSystem`
(priority 30) ever draws it. Symptom: ammo/HP/logic proves the entity spawned, but no sprite is
ever visible. Bullets that spawn overlapping a wall or enemy die frame 1 and never draw. Before
blaming rendering, **prove the entity survives past its first update**.

## 1. Reproduce deterministically: temporary launcher

Don't click through Splash → Menu → Level. Create a throwaway `Game` subclass that opens the exact
level, and point `Lwjgl3Launcher` at it.

`lwjgl3/src/main/java/com/axehigh/platformer/lwjgl3/TempDebugLauncher.java`:

```java
package com.axehigh.platformer.lwjgl3;

import com.axehigh.platformer.screens.GameScreen;
import com.badlogic.gdx.Game;

/** TEMPORARY: jump straight into a level for debugging. */
public class TempDebugLauncher extends Game {
    @Override
    public void create() {
        setScreen(new GameScreen(this, "maps/level1/generated_room.tmx"));
    }
}
```

Temporarily edit `Lwjgl3Launcher.createApplication()`:

```java
return new Lwjgl3Application(new TempDebugLauncher(), getDefaultConfiguration());
```

Run in the background, logging to a file:

```powershell
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "gradlew.bat :lwjgl3:run --console=plain > C:\Users\pt184\AppData\Local\Temp\opencode\run.log 2>&1" -WorkingDirectory "C:\skuld\dev_olona\libgdx\platformer-origin" -WindowStyle Hidden
```

Wait for the window (title `origin`) before interacting. The window is 1280x720; monitor >= 1920x1080.

## 2. Instrument the systems with System.out.println

`GameScreen.show()` does `assetManager.finishLoading()` synchronously, and the render loop prints
nothing — so **add temporary `System.out.println` to the systems** involved (spawn, per-frame
position, despawn reasons). They land in `run.log`. Example, `EnemyShootSystem.spawnBullet`:

```java
System.out.println("BULLET-SPAWN x=" + spawnX + " y=" + centerY + " size=" + bulletSize + " dir=" + direction);
```

and in `EnemyBulletCollisionSystem.processEntity` / `CollisionSystem.processEntity`:

```java
System.out.println("BULLET-UPDATE x=" + transform.position.x + " life=" + bullet.lifetime + " wall=" + hitsWall(collision.worldBounds));
```

If you see SPAWN but never a surviving UPDATE past frame 1, it is a same-frame despawn (see §0).
If UPDATE keeps printing but nothing renders, the bug is in `RenderSystem`.

**Enlarge/slow the object to make it observable.** Temporarily crank `BULLET_SIZE`, set
`transform.scale.set(unitScale * 4f, ...)`, raise `BULLET_LIFETIME` to 30, drop `BULLET_SPEED` to a
crawl. A giant slow target is trivially detectable in screenshots. Always revert after.

## 3. Geometry verification from the .tmx (no game needed)

To check "does the spawn point overlap a wall?" parse the collision layer directly. Gotchas:
- Tiled **flip flags** make gids like `2147483681` (2^31 + gid) — treat **any nonzero** as solid.
- Data lines often end with a **trailing comma** — trim it before splitting.
- The collision layer is a fixed width (here 90 cols); rows after the last one are implicit 0.

```powershell
$lines = Get-Content "assets\maps\level1\generated_room.tmx"
$rows = @()
foreach ($l in $lines) {
  $t = $l.Trim().TrimEnd(',')
  if ($t -match '^[0-9, ]+$') {
    $toks = $t -split ','
    if ($toks.Count -eq 90) { $rows += ,($toks | ForEach-Object { if ($_.Trim() -eq '0') { 0 } else { 1 } }) }
  }
}
# collision layer is the 2nd data block (rows 17..33 when 3 layers); verify per-layer.
```

Solid test for a rect `(x, y, w, h)`, tile size 128: check each overlapped cell
`rows[floor(r/128)][floor(c/128)]` for nonzero.

## 4. Pixel analysis when you cannot view images

This model cannot look at PNGs — but screenshots are still the ground truth for rendering bugs.
Capture the window and scan for the object's distinctive color at its predicted screen position.

World → window for this game's FitViewport (world `Ww x Wh`, window `1280x720`):
- `scale = min(720/Wh, 1280/Ww)` (fit), here world is `3840 x 2176` → `scale ≈ 0.331`, letterbox X ≈ 5.
- `screenX = worldX * scale + letterboxX`, `screenY = 720 - (worldY + h) * scale`.

Capture (PowerShell, `System.Drawing`):

```powershell
Add-Type -AssemblyName System.Drawing
# SetForegroundWindow the 'origin' window, SetWindowPos to (20,20,1280,720), then:
$bmp = New-Object System.Drawing.Bitmap(1280, 720)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen(20, 20, 0, 0, $bmp.Size)
$bmp.Save("shot.png", [System.Drawing.Imaging.ImageFormat]::Png)
```

Scan for the object's color (e.g. bullet texture `FFDC50` → r>235, g 195–235, b 55–120) in the
band where it should be; **look for a large contiguous cluster**, not a few pixels (torch flames
and coins are the same amber family — exclude their known positions). A handful of pixels at a
fixed spot is static decoration, not your entity.

## 5. Built-in debug aids

- **Collision Debug**: `SHIFT+D` (desktop) or the Pause dialog button — `DebugRenderSystem`
  outlines every live `CollisionComponent` AABB and the static map rects. Shows whether an entity
  is alive (outlined) even when its sprite is missing.
- **Touch Debug**: Pause dialog button, logs touches under the `TouchDebug` tag.
- Screenshot with collision debug ON to confirm an entity exists but renders wrong, vs never spawns.

## 6. Revert checklist (always)

1. Restore `Lwjgl3Launcher` to `new Main()`.
2. Delete the temp launcher.
3. Revert temp constant/println edits in the systems.
4. `git status --short` — only intended changes may remain; stash/revert the rest.
5. `.\gradlew.bat :core:compileJava` and `:core:test` to confirm you left the tree buildable.
