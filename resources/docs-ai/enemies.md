# Enemy Catalog & Design Reference

This document is the **single source of truth for enemy design**: every enemy type currently in the game, its stats/behavior, how it's spawned from Tiled, and how to add a new enemy type. It complements — but does not replace — `resources/docs-ai/ashley-ecs.md` (ECS shape: `EnemyComponent`/`EnemySystem`) and `resources/docs-ai/gameplay.md` §2.G (patrol movement logic); this file is where the actual *catalog* of enemy types and their tuning lives.

> **Maintenance rule:** Any time an enemy type is added, removed, renamed, or has its stats/sprite/behavior changed, this file MUST be updated in the same change (see `AGENTS.md` "Enemy Documentation Sync"). If the change also alters `EnemyComponent`/`EnemySystem` shape or introduces a new AI behavior, `ashley-ecs.md` and `gameplay.md` must be updated too, per their own sync rules.

---

## 1. How enemies are built

*   **Data:** `EnemyComponent` (`core/.../ecs/components/EnemyComponent.java`) — `float health`, `float speed`, `int direction` (`1` right / `-1` left), `float patrolRange`, `float originX`, `Timer hitStun` (post-hit grace period).
*   **Logic:** `EnemySystem` (`core/.../ecs/systems/EnemySystem.java`) — every frame ticks `hitStun` and, while it's active, skips patrol entirely so a knockback pop can play out. Otherwise sets `movement.velocity.x = enemy.speed * enemy.direction`; flips `direction` when the enemy strays `patrolRange` past `originX`, when blocked by a wall (`grounded && velocity.x == 0` after `MovementSystem` ran the previous frame), or when a small ground-sensor probe just past its leading foot finds no ground ahead (ledge/platform-edge detection — see "Platform-edge awareness" below). Runs at priority `4`, before `MovementSystem` (`5`).
*   **Physics:** Enemies are ordinary `MovementSystem` family members (Transform+Movement+Collision, non-bullet), so gravity, ground snapping, and AABB wall collision apply automatically — `EnemySystem` only ever touches horizontal (and, during knockback, vertical) velocity.
*   **Damage taken:** Applied externally via the shared `EnemyDamageResolver.applyHit(...)` helper, called from both `CollisionSystem` (bullet hits, `10` damage) and `MeleeAttackSystem` (melee strikes, `5` damage). If `enemy.hitStun` is active, the hit is ignored entirely; otherwise damage is subtracted (removing the entity at `<= 0`), and on a surviving hit the enemy gets a knockback pop (`90` u/s horizontal away from the attacker, `140` u/s vertical hop) plus a fresh `0.3s` `hitStun` grace period — see "Hit reaction: stun + knockback" below.
*   **Damage dealt:** `EnemyContactSystem` resolves the reverse direction — on AABB overlap with the player, while `player.hitInvulnerability` (a `com.axehigh.platformer.util.Timer`) is done, it decrements `player.health` by `1` and starts a `1.0s` grace period, so touching an enemy costs one life at most once per second regardless of how many enemies are overlapping.
*   **Spawning:** `EntityFactory.createEnemy(x, y)` builds the entity (`TransformComponent`, `TextureComponent`, `MovementComponent`, `CollisionComponent` sized to the texture, `EnemyComponent` with `originX = x`), wired to the `"enemy"` case in `EntityFactory.spawnObjects`. Placed on the map as a Tiled object-layer marker with `type="enemy"` (see `assets/maps/demo_room.tmx`, objects `enemy1`/`enemy2`).

### Hit reaction: stun + knockback
When a melee or bullet hit lands on an enemy that **isn't** already stunned, `EnemyDamageResolver` applies the damage and, if the enemy survives, gives it a brief "pop" — a `90` u/s horizontal kick away from the attacker (melee: opposite `player.facingDirection`; bullet: the bullet's own travel direction) plus a `140` u/s upward hop — then starts a `0.3s` `hitStun` timer. While `hitStun` is active: `EnemySystem` skips patrol AI (so the knockback velocity isn't immediately overwritten) and the enemy is fully immune to further damage/knockback from either weapon, so a single swing/bullet can't multi-trigger the pop. Gravity/wall collision for the knockback itself come for free from `MovementSystem`, since enemies already match its family.

### Platform-edge awareness
Enemies no longer rely solely on the manually-tuned `patrolRange` to avoid falling off a platform: `EnemySystem` also runs a small **ground-sensor probe** every frame — a tiny rectangle positioned just past the enemy's leading foot (in its current travel direction) and just below its feet, checked against the same static `collisionRects` set `MovementSystem` uses for wall/floor collision. If that probe finds no solid ground, the enemy turns around immediately, before it steps off the edge. This works for platforms of any width/shape without per-object tuning. `patrolRange`/`originX` still apply on top of this as an optional secondary cap (e.g. to keep an enemy patrolling only part of a long platform) — the two mechanisms are independent and whichever triggers first wins.

---

## 2. Current enemy catalog

Only one enemy type exists today. All fields below are the hardcoded defaults set by `EnemyComponent`'s field initializers — Tiled object markers do **not** currently override any of them (every `"enemy"` marker spawns an identical patroller); see §3 for how to add per-object overrides.

| Type | Sprite | Health | Speed (u/s) | Patrol Range | Hit Stun | Melee Dmg Taken | Bullet Dmg Taken | Contact Dmg Dealt | Behavior | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| **Patrol Enemy** (generic, no distinct name yet) | `assets/gfx/enemy.png` (16x16, placeholder red creature) | `10` | `20` | `32` | `0.3s` | `5` (dies in 2 hits) | `10` (dies in 1 hit) | `1` player life per contact, then a `1.0s` invulnerability grace period on the player (see `gameplay.md` §2.J) | Walks back and forth `patrolRange` units from its spawn X (`originX`), turning around at the range limit, when blocked by a wall, or when a ground-sensor probe detects no platform ahead (see "Platform-edge awareness" above). Falls/rests on floor via normal gravity+collision. On a surviving hit, gets a knockback pop + `0.3s` hit-stun (see "Hit reaction" above). No aggro/detection of the player — damage is purely resolved by AABB touch/collision (against the player via `EnemyContactSystem`, or from a bullet/melee strike via `CollisionSystem`/`MeleeAttackSystem`). | The only enemy type in `demo_room.tmx` (`enemy1` at world (160,240), `enemy2` at world (650,240)). |

---

## 3. Adding a new enemy type

There is currently **no per-type discriminator** (no `EnemyType` enum, no Tiled custom-property overrides) — every `"enemy"` marker produces the exact same stats/sprite. When introducing a second enemy type, follow this pattern and update this table:

1.  **Decide what varies:** stats only (health/speed/patrolRange), sprite only, or new behavior (e.g. flying, shooting, chasing the player, dealing contact damage to the player)?
2.  **Stats/sprite-only variants:** add a Tiled custom `string` property (e.g. `enemyType`) on the object marker, read it in `EntityFactory.createEnemy`/`spawnObjects`, and branch on it to pick the sprite and to set `EnemyComponent.health/speed/patrolRange` accordingly (a small `switch` or a lookup table in `EntityFactory` is enough — no new `Component` needed for this case).
3.  **New behavior:** if the enemy needs logic `EnemySystem`'s simple patrol can't express (ranged attacks, flying/no-gravity movement, player detection/aggro, contact damage to the player), prefer a **new marker component** (e.g. `ShooterEnemyComponent`, `FlyingEnemyComponent`) layered on top of the base `EnemyComponent`, plus either an extension of `EnemySystem` or a dedicated new `IteratingSystem` with a `Family` that includes the new marker. Keep `EnemyComponent` itself (health + patrol fields) as the common base every enemy shares, since damage systems (`CollisionSystem`/`MeleeAttackSystem`) key off it.
4.  **Update docs together:** add the new type's row to the table in §2 here; if `EnemyComponent`/`EnemySystem` (or a new system) changed shape, update `ashley-ecs.md`'s Components/Systems tables and priority list; if the *mechanic* is gameplay-visible, add/extend a subsection under `gameplay.md` §2 alongside the existing "G. Enemy Patrol Movement Logic".
5.  **Placement:** new enemy types are still placed as Tiled object-layer markers (same `type="enemy"` convention, or a distinct `type` value per new archetype if that's simpler for `EntityFactory.spawnObjects`'s `switch`) — no change to the map-parsing pipeline itself is required.

---

## 4. Open design questions (not yet decided)

These are intentionally left unresolved until a concrete need arises — don't design around them speculatively:

*   Whether future enemy types will share `"enemy"` as the Tiled object `type` (disambiguated by a custom property) or get their own `type` value (e.g. `"enemy_flyer"`).
*   Whether enemies will ever detect/react to the player (aggro range, chase behavior) — today they are non-reactive patrollers.
*   ~~Whether enemies will deal contact damage to the player on overlap~~ — **resolved:** `EnemyContactSystem` now applies `1` life of contact damage per enemy touch, gated by `player.hitInvulnerability` (see §1 and `gameplay.md` §2.J). Still open: whether future enemy types will deal *different* amounts of contact damage, or trigger a different grace-period duration.
*   ~~Whether enemies get any hit reaction when damaged~~ — **resolved:** every surviving hit now triggers a knockback pop + `0.3s` stun via `EnemyDamageResolver` (see §1 and `gameplay.md` §2.I). Still open: whether future enemy types will use a different knockback strength/stun duration.
