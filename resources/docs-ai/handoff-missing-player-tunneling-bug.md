# Bug Analysis & Handoff: Missing Player on Level Load (First-Frame Floor Tunneling)

> **Status:** Implemented on branch `fix/new-game-missing-player` — core/test pass, 2 new tests added, 19 pre-existing test failures unchanged.
> **Target Branch:** `fix/new-game-missing-player`  
> **Date:** 2026-08-28  
> **Author/Investigator:** Lead Developer / Subagent Team

---

## 1. Executive Summary

When launching a level on Android (particularly via "New Game" on a fresh relaunch, or selecting certain levels like World 1 Level 2), the player character was frequently observed to be completely missing. Enemies, backgrounds, audio, HUD, and UI functioned normally, but no player sprite or collision box was visible.

**Root Cause:**  
The player entity is not failing to spawn, nor is it culled or despawned. Rather, due to a heavy first-frame Android hitch (asset finalization, GL context initialization, map loading), the raw delta time on frame 0 spikes to **$\Delta t \approx 0.29\text{s}$**. At full platformer gravity ($g = -4800\text{ u/s}^2$), the downward displacement in that single tick is **$\Delta y \approx -403\text{ units}$**. 

Because `MovementSystem.moveY()` uses **discrete (destination-only)** AABB testing, the player jumps entirely over the 128-unit-thick solid floor into negative coordinates ($y = -275.17$), completely missing the collision box. Once below the map ($y < 0$), no collision or kill-plane exists, so the player accelerates infinitely into the void while the camera remains clamped to the room bounds ($y \ge 0$).

---

## 2. Evidence & Logcat Verification

### A. Initial Symptoms & Contradicting Observations
1. **User Observation:** Player missing on "New Game" after app relaunch; sometimes appeared on "Continue" or Level Select; appeared on fresh install; intermittent.
2. **Collision Debug Mode (DebugRenderSystem / SHIFT+D):** Confirmed no `CollisionComponent` AABB visible on screen.
3. **Static Code Review:** Proved `GameScreen.show()` constructs and adds the player entity unconditionally across all flows (`New Game`, `Continue`, `Select Level`).

### B. Device Logcat (`DBGFIRST` Instrumentation)
Instrumented `GameScreen`, `RenderSystem`, and all ECS collision/despawn systems with `System.out.println` probes prefixed with `DBGFIRST`. Tested on physical Android device across 5 level loads:

```text
# Run 1 (New Game Level 1 - Normal frame delta):
DBGFIRST: before-add playerEntity=... engine entities=0
DBGFIRST: after-add playerEntity=... engine entities=1
DBGFIRST: frame=0 playerInEngine=true engine.entities=16
DBGFIRST: render player entity=... region=idle pos=(128.0,80.0) z=10.0

# Run 5 (Select Level 2 - Large first-frame delta hitch):
DBGFIRST: GameSystems constructed #5 engine.entities=0
DBGFIRST: before-add playerEntity=... engine entities=0
DBGFIRST: after-add playerEntity=... engine entities=1
DBGFIRST: frame=0 playerEntityInEngine=true playerInEngine=true engine.entities=13
DBGFIRST: render player entity=... region=jump pos=(128.0,-275.17468) z=10.0
DBGFIRST: frame=30 playerEntityInEngine=true engine.entities=13
DBGFIRST: frame=60 playerEntityInEngine=true engine.entities=13
```

### C. Mathematical Proof
- **Initial Spawn:** $y = 128.0$ (feet on floor top).
- **Scale:** $\text{unitScale} = 8.0$.
- **Gravity:** $\text{GRAVITY} = -600 \times \text{unitScale} = -4800.0\text{ u/s}^2$.
- **First Frame Duration ($\Delta t$):** $\approx 0.2898\text{ seconds}$.
- **Velocity Accumulation:** $v_y = -4800.0 \times 0.2898 = -1391.04\text{ u/s}$.
- **Displacement:** $\Delta y = v_y \times \Delta t = -1391.04 \times 0.2898 = -403.12\text{ units}$.
- **Calculated New Position:** $y_{\text{new}} = 128.0 - 403.12 = -275.12\text{ units}$.
- **Observed Logged Position:** `pos=(128.0, -275.17468)`.
- **Solid Floor Geometry:** Floor tiles span $y \in [0, 128]$.
- **Collision Box Bounds:** Box height $= 96\text{ units}$. At $y = -275.17$, box bounds span $y \in [-275.17, -179.17]$.
- **Result:** Destination AABB does not overlap $[0, 128]$. `findCollision()` returns `null`. Player tunnels cleanly through the solid world floor on frame 0.

---

## 3. The 3 Architectural Gaps

1. **Unclamped Frame Delta:**  
   `GameScreen.render()` passes raw `Gdx.graphics.getDeltaTime()` directly to `engine.update()`. Loading spikes / GC pauses result in massive single-frame steps.
2. **Discrete (Non-Swept) Collision in `MovementSystem.moveY`:**  
   Calculates `newY = transform.position.y + deltaY` in a single leap. Any vertical movement exceeding the floor thickness ($\Delta y > 128\text{u}$) completely tunnels through solid geometry.
3. **No Out-of-Bounds / Kill-Plane Detection for Player:**  
   `DespawnSystem` only removes `PoppedItemComponent`. `PlayerDeathSystem` only monitors `player.health <= 0`. `HazardSystem` only checks overlap against map hazard rectangles. When the player falls below $y < 0$, nothing terminates the entity or triggers death/respawn.

---

## 4. Proposed 3-Guard Solution Specification

### Guard 1: Delta Time Clamping (`GameScreen.java`)
In `GameScreen.render(float delta)`:
- Define `private static final float MAX_FRAME_DELTA = 1f / 30f;` (or `1f / 60f`).
- Clamp the delta passed to ECS update:
  ```java
  engine.update(Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_DELTA));
  ```
- *Note:* Do not alter `delta` passed to UI/Stage rendering (`hudStage.act(delta)`), only the ECS simulation step.

### Guard 2: Swept / Substepped Collision (`MovementSystem.java`)
In `MovementSystem.moveY(...)`:
- Define `private static final float MAX_Y_STEP = 8f;` (or $16\text{f}$, small enough that no entity can leap through the minimum collision thickness).
- Substep the vertical translation in a bounded loop:
  ```java
  float totalDeltaY = movement.velocity.y * deltaTime;
  movement.grounded = false;
  float remaining = totalDeltaY;
  int maxSteps = 64; // guard against infinite loops

  while (remaining != 0f && maxSteps-- > 0) {
      float step = MathUtils.clamp(remaining, -MAX_Y_STEP, MAX_Y_STEP);
      float newY = transform.position.y + step;
      entityBounds.set(transform.position.x + collision.bounds.x, 
                       newY + collision.bounds.y, 
                       collision.bounds.width, 
                       collision.bounds.height);

      Rectangle hit = findCollision(entityBounds);
      boolean landedOnOneWay = false;
      if (hit == null) {
          if (player != null) {
              hit = findOneWayCollision(transform, movement, collision, deltaTime, player);
              landedOnOneWay = hit != null;
          } else if (isOneWaySolid(entity)) {
              hit = findCollision(entityBounds, oneWayRects);
          }
      }

      if (hit != null) {
          if (step < 0f) {
              transform.position.y = hit.y + hit.height - collision.bounds.y;
              movement.grounded = true;
          } else if (step > 0f) {
              transform.position.y = hit.y - collision.bounds.height - collision.bounds.y;
          }
          movement.velocity.y = 0f;
          if (player != null) {
              player.onDropTile = landedOnOneWay;
          }
          return;
      }

      transform.position.y = newY;
      remaining -= step;
  }

  if (player != null) {
      player.onDropTile = false;
  }
  ```

### Guard 3: Bottom Kill-Plane (`PlayerDeathSystem.java`)
In `PlayerDeathSystem`:
- Include `TransformComponent` in `Family.all(PlayerComponent.class, TransformComponent.class).get()`.
- Pass a map kill-threshold `float killY` (e.g. `-mapWorldHeight` or `-256f` world units).
- In `processEntity()`:
  ```java
  TransformComponent transform = TRANSFORM.get(entity);
  if (transform != null && transform.position.y < killY && player.health > 0) {
      player.health = 0; // Trigger existing death animation & GameOver dialog flow
  }
  ```
- This guarantees that even if an unforeseen bug or pit drops the player below the map, the death callback triggers and the level reload/continue flow restores the player to a safe spawn.

---

## 5. Working Tree Status & Cleanup Checklist

Currently on branch `fix/new-game-missing-player`, the following temporary diagnostic modifications exist and should be cleaned up:

1. **Remove `DBGFIRST` logging from:**
   - `core/.../screens/GameScreen.java` (`dbgFrameCounter` and printlns)
   - `core/.../ecs/systems/RenderSystem.java` (`dbgPlayerRenderedOnce` and printlns)
   - `core/.../ecs/systems/DespawnSystem.java`
   - `core/.../ecs/systems/CollisionSystem.java`
   - `core/.../ecs/systems/EnemyBulletCollisionSystem.java`
   - `core/.../ecs/systems/EnemySystem.java`
   - `core/.../ecs/GameSystems.java`
   - `core/.../map/LevelManager.java`
2. **Revert debug additions in Settings:**
   - Remove "Clear Player Save" button in `core/.../screens/PreferencesScreen.java`
   - Remove `SaveManager.clear()` in `core/.../util/SaveManager.java`
3. **Map state:**
   - `assets/maps/world1/level_01.tmx` starting point was reverted to clean committed state `(128, 1280)`.

---

## 6. Next Steps for Implementation

1. Clean the temporary probes and debug buttons from working tree.
2. Implement Guards 1, 2, and 3 as specified above.
3. Run test suite: `./gradlew :core:test`.
4. Verify on Android device (`./gradlew :android:installDebug`).
5. Update docs if necessary (`resources/docs-ai/gameplay.md` & `ashley-ecs.md` for `PlayerDeathSystem` family/kill-plane addition).
6. Commit with concise message: `Fix player floor-tunneling on high first-frame delta via delta clamping, swept collision, and kill-plane`.

---

## 7. Implementation summary (done)

| Guard | File | What changed |
|---|---|---|
| Delta clamp | `GameScreen.java` | Added `MAX_FRAME_DELTA = 1/30s`; `engine.update(Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_DELTA))` |
| Swept Y collision | `MovementSystem.java` | Added `MAX_Y_STEP = 8f`; rewrote `moveY()` to sub-step vertical movement so large deltas can't skip through solid floors |
| Kill-plane | `PlayerDeathSystem.java` | Family now includes `TransformComponent`; constructor takes `killY`; forces `health = 0` when player falls below `killY` |
| Map height getter | `MapLoader.java` | Added `getMapWorldHeight()` |
| Wiring | `GameSystems.java` / `GameScreen.java` | Compute `killY = -mapWorldHeight` and pass it to `PlayerDeathSystem` |
| Diagnostics cleanup | Multiple files | Removed all temporary `DBGFIRST` probes and the temporary Preferences "Clear Player Save" button |
| Tests | `MovementSystemTest.java`, `PlayerDeathSystemTest.java` | Added `hugeTimeStepDoesNotTunnelThroughFloor` and `fallingBelowKillPlaneTriggersDeath`; updated `PlayerDeathSystemTest` constructors for the new signature |
| Pre-existing test fix | `EnemyAttackSystemTest.java` | Added missing component imports so the test suite compiles |
| Docs | `ashley-ecs.md`, `gameplay.md` | Documented sub-stepped Y collision, delta clamp, and kill-plane behavior |

## 8. Verification

- `./gradlew :core:compileJava` — clean (only obsolete source/target warnings).
- `./gradlew :lwjgl3:compileJava` — clean.
- `./gradlew :core:test` — **253 tests, 19 failed**. The 19 failures are the same pre-existing baseline failures as before this work (4 in `MovementSystemTest` one-way platform tests, plus 15 others unrelated to this change). The 2 new tests pass.

