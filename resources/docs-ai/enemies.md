# Enemy Catalog & Design Reference

This document is the **single source of truth for enemy design**: every enemy type currently in the game, its stats/behavior, how it's spawned from Tiled, and how to add a new enemy type. It complements — but does not replace — `resources/docs-ai/ashley-ecs.md` (ECS shape: `EnemyComponent`/`EnemySystem`) and `resources/docs-ai/gameplay.md` §2.G (patrol movement logic); this file is where the actual *catalog* of enemy types and their tuning lives.

> **Maintenance rule:** Any time an enemy type is added, removed, renamed, or has its stats/sprite/behavior changed, this file MUST be updated in the same change (see `AGENTS.md` "Enemy Documentation Sync"). If the change also alters `EnemyComponent`/`EnemySystem` shape or introduces a new AI behavior, `ashley-ecs.md` and `gameplay.md` must be updated too, per their own sync rules.

---

## 1. How enemies are built

*   **Data:** `EnemyComponent` (`core/.../ecs/components/EnemyComponent.java`) — `float health`, `float speed`, `int direction` (`1` right / `-1` left), `float patrolRange`, `float originX`.
*   **Logic:** `EnemySystem` (`core/.../ecs/systems/EnemySystem.java`) — every frame sets `movement.velocity.x = enemy.speed * enemy.direction`; flips `direction` when the enemy strays `patrolRange` past `originX`, or immediately if blocked by a wall (`grounded && velocity.x == 0` after `MovementSystem` ran the previous frame). Runs at priority `4`, before `MovementSystem` (`5`).
*   **Physics:** Enemies are ordinary `MovementSystem` family members (Transform+Movement+Collision, non-bullet), so gravity, ground snapping, and AABB wall collision apply automatically — `EnemySystem` only ever touches horizontal velocity.
*   **Damage:** Applied externally — `CollisionSystem` (bullet hits) and `MeleeAttackSystem` (melee strikes) both subtract from `EnemyComponent.health` directly and remove the entity at `<= 0`. `EnemyComponent` itself has no damage/AI branching of its own.
*   **Spawning:** `EntityFactory.createEnemy(x, y)` builds the entity (`TransformComponent`, `TextureComponent`, `MovementComponent`, `CollisionComponent` sized to the texture, `EnemyComponent` with `originX = x`), wired to the `"enemy"` case in `EntityFactory.spawnObjects`. Placed on the map as a Tiled object-layer marker with `type="enemy"` (see `assets/maps/demo_room.tmx`, objects `enemy1`/`enemy2`).

---

## 2. Current enemy catalog

Only one enemy type exists today. All fields below are the hardcoded defaults set by `EnemyComponent`'s field initializers — Tiled object markers do **not** currently override any of them (every `"enemy"` marker spawns an identical patroller); see §3 for how to add per-object overrides.

| Type | Sprite | Health | Speed (u/s) | Patrol Range | Behavior | Notes |
|---|---|---|---|---|---|---|
| **Patrol Enemy** (generic, no distinct name yet) | `assets/gfx/enemy.png` (16x16, placeholder red creature) | `1` | `20` | `32` | Walks back and forth `patrolRange` units from its spawn X (`originX`), turning around at the range limit or when blocked by a wall. Falls/rests on floor via normal gravity+collision. No attack, no aggro/detection of the player — purely a touch/collision hazard resolved by whatever hits it (bullet or melee). | The only enemy type in `demo_room.tmx` (`enemy1` at world (160,240), `enemy2` at world (650,240)). |

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
*   Whether enemies will deal contact damage to the player on overlap — today only the player damages enemies (via bullets/melee); the reverse has no implementation.
