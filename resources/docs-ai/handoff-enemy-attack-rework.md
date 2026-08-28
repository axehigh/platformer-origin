# WIP: Enemy Melee Attack Rework — Handoff / Resumable Status

> Status: **FEATURE COMPLETE & VERIFIED (code, tests, docs) — NOT YET COMMITTED**
> Last updated: 2026-08-28 (context: continuation of issue #15 "enemy melee attacks")

This document is the single entry point for picking this feature back up. It captures
what exists, what was verified, and what is still open. The authoritative behavior specs
live in `resources/docs-ai/gameplay.md` §2.M, `resources/docs-ai/enemies.md`,
`resources/docs-ai/ashley-ecs.md` (all already updated to the final model).

---

## 1. Feature summary

Melee-capable enemies (walkers, flyers, knights — anything with `EnemyAttackComponent`)
now fight the player through **three distinct distances**:

1. **Detection** (debug overlay: **magenta**)
   — Centered box on the enemy's AABB center: `attackRange × 3 × unitScale` per horizontal
   side, `detectionHeight (= 1.25 tiles = 20u) × unitScale` tall total (so ±10u per side
   vertically — only same-floor standoffs aggro). Check is **live per frame**; player
   **center** inside counts. Detection ⇒ the enemy **chases** (steps 2 & 3 reachable).
   No memory/"awareness" latch exists anymore — if the player leaves the box, chasing and
   strike-commit stop (an in-flight telegraph still resolves).
2. **AttackRange** (debug overlay: **green**, commit trigger)
   — Front rectangle adjacent to the enemy's facing edge, `attackRange (= 24u) × unitScale`
   wide, collision-height tall. The strike commits only when ALL hold:
   `attackCooldown.isDone()` AND player detected AND this rect overlaps the player's
   `worldBounds`. The enemy never swings at a player behind it.
3. **Strike hitbox** (debug overlay: **red**, live only)
   — Actually the enemy's **own collision width × height**, adjacent to its facing edge.
   Active only during the **strike window** ("blade out", like the player's melee):
   `strikeWindow = 0.2s` opened when `windUp` completes. Multi-frame exposure via
   `EnemyAttackSystem.getActiveStrikeBounds()` (null unless live) so the debug renderer and
   future systems can read it.

### Attack sequence (per committed swing)
`detected + in attack range + cooldown due`
→ snap-face the player (`direction = sign(centerX diff)`)
→ `windUp` (0.4s telegraph; `EnemySystem` holds enemy stationary, facing locked)
→ **strike window** (0.2s; hitbox live, once per swing — player invulnerability dedupes,
  knocked back toward the enemy)
→ window ends → `recovery` (0.5s **stand-down**: stationary, facing locked — the enemy does
  NOT flip direction right after a swing; the recovery-end frame zeroes the wall-block turn
  check via `resumedFromAttack = wasRecovering`) → `attackCooldown` (2.0s) → chase resumes.

**Chase behavior** (`EnemySystem`, only for enemies with `EnemyAttackComponent`):
normal movement speed toward the player; `blockedByWall || atLedge || atHazard` holds the
enemy in place (velocity 0) but it never turn-arounds away from the player; flyers keep
their vertical bob (horizontal homing only — no vertical homing). When the player leaves
the detection box, normal patrol resumes.

**Config:** Tiled marker property renamed `meleeRange` → **`attackRange`** (float, world
units; default 24) with legacy **`meleeRange` alias** fallback in `EnemyFactory`. Tiled maps
in `assets/maps/world1/level_04/05/07/08/09.tmx` were already edited earlier — verify those
edits are intentional before you rely on them (they predate/unrelated to this feature; do
not assume they belong to this work).

---

## 2. Files touched (working tree, ALL UNCOMMITTED)

### Main code (this feature)
| File | Change |
|---|---|
| `core/.../ecs/components/EnemyAttackComponent.java` | `meleeRange`→`attackRange` (24f); added `detectionHeight` (20f = 1.25 tiles), `strikeWindow` (0.2f), `Timer strike`; **removed** `awareness` + `awarenessDuration` |
| `core/.../ecs/systems/EnemyAttackSystem.java` | Live detection box; cooldown always ticks; room gate unchanged; front attack-range commit; strike-window state machine (windUp → strike → recovery + cooldown restart); `getActiveStrikeBounds()`; strike hitbox = enemy collision W×H |
| `core/.../ecs/systems/EnemySystem.java` | Chase branch (players family cached in `addedToEngine`; detection calc; face/move toward player, hold at wall/ledge/hazard without turning; flyer bob; `return` skips patrol); keeps recovery stand + isAttacking pause + `resumedFromAttack` wall-block guard |
| `core/.../map/EnemyFactory.java` | Reads `attackRange`, falls back to legacy `meleeRange` |
| `core/.../ecs/systems/DebugRenderSystem.java` | Points magenta detection / green commit rect / red live strike via `enemyAttackSystem.getActiveStrikeBounds()` (resolved in `addedToEngine`, p8 < p40 so same-frame) |
| `core/.../ecs/GameSystems.java` | `enemySystem.setUnitScale(...)` + `enemyAttackSystem.setUnitScale(...)` (already wired; verify on resume) |

### Tests
| File | Change | Status |
|---|---|---|
| `core/.../systems/EnemyAttackSystemTest.java` | Rewritten to the new model (removed awareness tests; 3-step strike-window cadence; new hitbox-liveness/facing/elevation tests) | **16/16 PASS** |
| `core/.../systems/EnemySystemChaseTest.java` (NEW) | Chase toward / stops on leave / holds at wall without turning / flyer chase+bob | **4/4 PASS** |

### Docs (already synced — do NOT undo)
- `resources/docs-ai/gameplay.md` §2.M (incl. new **Chase** item), `resources/docs-ai/enemies.md` (damage contract), `resources/docs-ai/ashley-ecs.md` (component + EnemyAttackSystem/EnemySystem rows).

### Modified but NOT part of this feature (pre-existing working-tree edits)
- `assets/maps/world1/level_04/05/07/08/09.tmx`, `core/.../map/LevelCatalog.java`, `AGENTS.md` (AGENTS.md has the #15-era Debugging-text edit — harmless; tmx/LevelCatalog edits unknown provenance — inspect before relying on them).

---

## 3. Verified state (2026-08-28)

- `./gradlew :core:compileJava` → clean (only pre-existing obsolete source/target warnings).
- Full suite: `./gradlew :core:test` → **251 tests, 19 failed** — all 19 are the KNOWN
  PRE-EXISTING baseline failures, verified against a clean checkout. They live in:
  `MovementSystemTest` (4), `MeleeAttackSystemTest` (3), `EnemySystemTest` coin-drop (2),
  `PlayerInputPotionTest` (2), `PauseDialogTest` (2), `PotionEffectsTest` (6).
- Per-class: `..EnemyAttackSystemTest` 16/16; `..EnemySystemChaseTest` 4/4.
- Debug overlay verified at code level (magenta/green/red); visual confirmation still
  pending (see §5).

---

## 4. Current git state

- **Nothing committed.** Everything in §2 is uncommitted working-tree changes.
- Recommendation on resume: commit this snapshot first (`git add -A && git commit`), so the
  handoff is recoverable. Then build/run the desktop launcher (`./gradlew :lwjgl3:run`) and
  use **SHIFT+D** (collision debug) to see the three ranges on real enemies.

---

## 5. Known watch-outs / open decisions (flag for the requester)

1. **Visual check pending.** The three-color overlay was code-reviewed and unit-tested, but
   no human playtest. Priority on resume.
2. **Strike reach vs commit distance:** commits at 24u (AttackRange), hitbox is only the
   enemy width (~20u) — a player backpedaling during the 0.4s telegraph can dodge the swing
   (whiff). Behaviorally fine; intended "roll out of the way" counter.
3. **Flyer chase is horizontal-only** (+ bob): no vertical homing — flyers can be kited on
   staircases. Decide if vertical homing is wanted.
4. **Aggro stickiness:** detection is strictly live. A hit-and-run player can de-aggro and
   re-aggro repeatedly. If "annoying/grindy", reintroduce a short memory (was the old
   `awareness` latch; deliberately removed per design).
5. **Chase + room gate interplay untested visually** — enemies frozen by `RoomState` do not
   chase (correct same as attack); on room activation they immediately detect+chase.
6. `EnemySystemChaseTest` is a new file — if the test suite is ever pared down, keep it
   (it pins the chase rules).

---

## 6. Resume checklist (pick-up steps)

```bash
# 1. Read this file + gameplay.md §2.M + enemies.md melee contract
# 2. Inspect working tree diff before committing: git status && git diff --stat
# 3. Committing is mandatory before further edits (uncommitted feature snapshot):
#    git add -A && git commit -m "feat(enemies): three-distance melee attack rework (WIP handoff)"
# 4. Verify: ./gradlew :core:compileJava && ./gradlew :core:test --tests "com.axehigh.platformer.ecs.systems.EnemyAttackSystemTest" --tests "com.axehigh.platformer.ecs.systems.EnemySystemChaseTest"
# 5. Full suite: expect exactly the 19 pre-existing failures from §3.
# 6. Desktop run + SHIFT+D to visually confirm magenta/green/red overlay.
# 7. Address §5 open decisions with the requester.
```