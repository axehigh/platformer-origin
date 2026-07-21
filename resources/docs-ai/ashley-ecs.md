# Ashley ECS Reference

This document is the **single source of truth for the ECS layer** (`com.axehigh.platformer.ecs`): every `Component` and every `System` currently implemented, what data/behavior it owns, and how systems are wired together at runtime. It is written to be consumed by an AI agent before making any ECS change, so keep it terse, structured, and exhaustive rather than narrative.

> **Maintenance rule:** Any time a `Component` or `System` is added, removed, renamed, or has its fields/behavior/family/priority changed, this file MUST be updated in the same change. This is the ECS counterpart to the `gameplay.md` sync rule in `AGENTS.md`.

---

## 1. Components (`com.axehigh.platformer.ecs.components`)

| Component | Poolable | Fields | Purpose |
|---|---|---|---|
| `TransformComponent` | Yes | `Vector2 position`, `Vector2 scale = (1,1)`, `float rotation`, `float z` | World position, scale (also used to flip sprites via negative `scale.x`), rotation, and z-layer draw order. |
| `TextureComponent` | Yes | `TextureRegion region` | The currently visible texture region for an entity; `null` region is skipped by `RenderSystem`. |
| `AnimationComponent` | No | `ObjectMap<State, Animation<TextureRegion>> animations`, `State currentState/previousState = IDLE`, `float stateTime` | Per-state animation clips and playback timer. `State` enum: `IDLE, RUNNING, JUMPING, DOUBLE_JUMPING, WALL_CLIMBING, ATTACKING`. |
| `MovementComponent` | Yes | `Vector2 velocity`, `Vector2 acceleration`, `float maxSpeedX = 100`, `float maxSpeedY = 400`, `boolean grounded` | Velocity/acceleration and speed clamps consumed by `MovementSystem` (or `CollisionSystem` for bullets). |
| `CollisionComponent` | Yes | `Rectangle bounds` | AABB bounding box, relative-sized (positioned each frame from `TransformComponent.position`). |
| `PlayerComponent` | No | `int health/maxHealth = 3`, `int coins = 0`, `int items/maxItems = 0/30`, `int facingDirection = 1` (-1 left, 1 right), `int jumpCount`, `int maxJumps = 2`, `boolean isWallClimbing`, `float shootCooldownTimer`, `float meleeCooldownTimer`, `float meleeAttackTimer`, `boolean meleeHasHit` | Flag + state component for the single player entity: health, currency, ammo, traversal (double jump, wall climb), and combat (melee/ranged) state. |
| `BulletComponent` | Yes | `float damage`, `float lifetime` | Marks a spawned projectile entity; despawns once `lifetime` elapses without a hit. |
| `EnemyComponent` | No | `float health = 1`, `float speed = 20`, `int direction = 1` (1=right, -1=left), `float patrolRange = 32`, `float originX` | Damageable enemy with simple back-and-forth patrol movement resolved by `EnemySystem` (direction/velocity are stored here so the system stays stateless between entities); damage is applied externally by `CollisionSystem`/`MeleeAttackSystem`. |
| `CoinPickupComponent` | No | `int amount = 1` | Marker for a coin pickup; grants `amount` to `PlayerComponent.coins` (uncapped) on pickup. |
| `DaggerPickupComponent` | No | `int amount = 5` | Marker for a dagger pickup; grants `amount` to `PlayerComponent.items`, capped at `maxItems`. |
| `ChestComponent` | No | `boolean opened = false`, `float disappearTimer = 0` | Tracks a chest's open/disappear state after being melee-struck; drops coins on disappear. |

### `Mappers` (`com.axehigh.platformer.ecs.components.Mappers`)
Central holder of shared `ComponentMapper<T>` instances (one static field per component above: `TRANSFORM`, `TEXTURE`, `ANIMATION`, `MOVEMENT`, `COLLISION`, `PLAYER`, `BULLET`, `ENEMY`, `DAGGER_PICKUP`, `COIN_PICKUP`, `CHEST`). **Every system must read/write components through these mappers** instead of creating new `ComponentMapper.getFor(...)` calls. Adding a new component requires adding its mapper here.

---

## 2. Systems (`com.axehigh.platformer.ecs.systems`)

Each row lists the Ashley base class, the entity `Family` it processes (as configured in its constructor), and its responsibility. "Priority" refers to the value passed in by `GameScreen` (lower runs first); systems are also usable with priority `0` via their no-arg/short constructors for tests or other screens.

| System | Base class | Family | Responsibility |
|---|---|---|---|
| `PlayerInputSystem` | `IteratingSystem` | all: `PlayerComponent`, `MovementComponent`, `TransformComponent`, `CollisionComponent` | Reads keyboard **and** touch-UI input (state pushed in via `setTouchLeft/Right`, `requestTouchJump/Melee/Shoot`) and converts it into: horizontal velocity + `facingDirection`; jump velocity (gated by `jumpCount < maxJumps`); melee trigger (gated by `meleeCooldownTimer`, arms `meleeAttackTimer`); ranged shot (gated by `shootCooldownTimer` and `items > 0`, spawns a bullet entity via `PooledEngine`). Touch-request flags are reset every `update()` after processing. |
| `EnemySystem` | `IteratingSystem` | all: `EnemyComponent`, `MovementComponent`, `TransformComponent` | Simple back-and-forth patrol AI: sets `movement.velocity.x = enemy.speed * enemy.direction`; flips `direction` once the enemy strays `patrolRange` past `originX` in either direction, or immediately if it's `grounded` with `velocity.x == 0` (i.e. `MovementSystem` zeroed it after a wall hit last frame). Runs **before** `MovementSystem` so the velocity it sets is integrated the same frame; gravity/wall collision for enemies come for free from `MovementSystem` since Transform+Movement+Collision (non-bullet) entities already match its family. |
| `MovementSystem` | `IteratingSystem` | all: `TransformComponent`, `MovementComponent`, `CollisionComponent`; **excludes** `BulletComponent` | Applies gravity (or reduced wall-slide gravity while `isWallClimbing`), clamps velocity to `maxSpeedX/Y`, integrates position on X then Y separately with AABB resolution against the static `collisionRects` set (from `MapLoader`). Drives player-only side effects: resets `jumpCount` when grounded, sets `isWallClimbing`/`jumpCount = 1` on a horizontal wall hit while airborne, and clears `isWallClimbing` on release. |
| `CollisionSystem` | `IteratingSystem` | all: `BulletComponent`, `TransformComponent`, `MovementComponent`, `CollisionComponent` | Owns **bullet-only** movement integration (bullets are excluded from `MovementSystem`) and resolution: counts down `lifetime` (removes on expiry), moves the bullet, removes it on wall overlap (`collisionRects`), and on enemy overlap applies `bullet.damage` to `EnemyComponent.health` (removing the enemy if `<= 0`) and always removes the bullet. Looks up the enemy family once in `addedToEngine`. |
| `MeleeAttackSystem` | `IteratingSystem` | all: `PlayerComponent`, `TransformComponent`, `CollisionComponent` | While `meleeAttackTimer > 0` and `!meleeHasHit`, builds a short strike `Rectangle` offset from the player's bounds in `facingDirection` (width `STRIKE_WIDTH = 10`) and checks it against the enemy family first, then the chest family (looked up once in `addedToEngine`). Applies melee damage to an enemy (removing it if health `<= 0`) **or** opens an unopened chest (swaps texture to `gfx/chest_open.png`, starts `disappearTimer`); either way sets `meleeHasHit = true` so one swing hits at most once. Counts `meleeAttackTimer` down every frame it runs. |
| `PickupSystem` | `IteratingSystem` | one of: `DaggerPickupComponent`, `CoinPickupComponent` | Resolves the single player entity once in `addedToEngine`, then per pickup entity checks AABB overlap against the player. On overlap: dagger pickups increment `player.items` (capped at `maxItems`), coin pickups increment `player.coins` (uncapped); either way removes the pickup entity. |
| `ChestSystem` | `IteratingSystem` | all: `ChestComponent`, `TransformComponent` | Owns the opened-chest lifecycle: for chests with `opened == true`, counts `disappearTimer` down; on reaching `<= 0`, removes the chest and spawns a random number (`MathUtils.random(2, 6)`) of coin pickups (via `EntityFactory.createCoinPickup`) scattered within `SCATTER_RANGE = 12` of the chest's last position. |
| `CameraSystem` | `EntitySystem` (not iterating; single-purpose) | all: `PlayerComponent`, `TransformComponent` (resolved once in `addedToEngine`) | Flip-screen room camera (see `AGENTS.md` §E): each frame computes `roomX = (int)(player.x / GameConstants.VIRTUAL_WIDTH)`, `roomY = (int)(player.y / GameConstants.VIRTUAL_HEIGHT)` and snaps `camera.position` to that room's center — no smooth tracking/lerp. |
| `AnimationSystem` | `IteratingSystem` | all: `AnimationComponent`, `TextureComponent` | If the entity also has `PlayerComponent` + `MovementComponent`, resolves `currentState` from player/movement state (`ATTACKING` > `WALL_CLIMBING` > `JUMPING`/`DOUBLE_JUMPING` > `RUNNING` > `IDLE`) and mirrors the sprite by setting `transform.scale.x = abs(scale.x) * facingDirection`. Resets `stateTime` on state change, otherwise accumulates it, then writes the resolved animation's key frame into `TextureComponent.region`. |
| `TiledMapRenderSystem` | `EntitySystem` (not iterating), `Disposable` | n/a (holds the `TiledMap`/`OrthogonalTiledMapRenderer` directly, no entity family) | Renders the Tiled map's background/collision tile layers each frame via `OrthogonalTiledMapRenderer.setView(camera)` + `render()`. Must be `dispose()`d when the screen changes. |
| `RenderSystem` | `SortedIteratingSystem` (sorted by a `ZComparator` on `TransformComponent.z`, ascending) | all: `TransformComponent`, `TextureComponent` | Draws every renderable entity via the shared `SpriteBatch`, sized from the texture region times `transform.scale`, centered on `transform.position`, honoring `transform.rotation`. Wraps `batch.begin()/end()` around the sorted iteration in its own `update()` override. |

### System wiring & priority order (`GameScreen.show()`)
Systems are added to the `PooledEngine` in this priority order (lower runs first each frame); `MeleeAttackSystem`, `PickupSystem`, and `ChestSystem` share the same priority (`7`) and their relative order among themselves is not meaningful:

1. `PlayerInputSystem` — priority `0`
2. `EnemySystem` — priority `4`
3. `MovementSystem` — priority `5`
4. `CollisionSystem` — priority `6`
5. `MeleeAttackSystem` — priority `7`
6. `PickupSystem` — priority `7`
7. `ChestSystem` — priority `7`
8. `CameraSystem` — priority `8`
9. `AnimationSystem` — priority `10`
10. `TiledMapRenderSystem` — priority `20`
11. `RenderSystem` — priority `30`

This ordering matters: input must run before movement/collision resolve it the same frame; enemy AI must set patrol velocity before `MovementSystem` integrates it; melee/pickup/chest must run after collision-affecting movement but before the camera snaps and animation/rendering read the resulting state; map rendering must happen before entity rendering so entities draw on top.

---

## 3. Adding/removing/changing an ECS element — checklist
When you add, remove, or edit a `Component` or `System`:
1. Update the relevant table row(s) above (fields, family, priority, responsibility).
2. If the change affects gameplay-visible mechanics (movement, combat, traversal, enemy behavior, pickups, etc.), also update `resources/docs-ai/gameplay.md` per the `AGENTS.md` "Gameplay Documentation Sync" rule — the two docs are complementary: this file describes the ECS *shape*, `gameplay.md` describes the *mechanics/logic* built on top of it.
3. If a new component needs cross-system lookups, add its `ComponentMapper` to `Mappers`.
4. If a new system is registered, add it to `GameScreen.show()` with an explicit priority constant and update the "System wiring & priority order" list above.
