# Agent Instructions: Gameplay Mechanics Implementation

This document outlines the architectural and technical requirements for implementing player traversal (double jump, wall climb), combat (close-combat strike, ammo-gated shooting, dagger pickups, coin pickups, chest interaction), and enemy patrol movement using Ashley ECS in libGDX.

---

## 1. State Management & Components

### A. Extended PlayerComponent
Track the advanced state variables required for these mechanics directly inside the `PlayerComponent`:
*   `int jumpCount` (Tracks current jumps executed; resets to 0 when grounded).
*   `int maxJumps = 2` (Allows for double jumping).
*   `boolean isWallClimbing` (Flag for wall attachment).
*   `int facingDirection` (-1 for left, 1 for right).
*   `int items` / `int maxItems = 30` (Dagger/shoot ammo count and cap; the HUD's item tracker displays this as `x NN/30`).
*   `int coins` (Currency spent in the Shop/Upgrade mechanic — see §2.O).
*   `int swordDamage = 5` (Melee damage per hit, read dynamically by `MeleeAttackSystem` on every strike; raised to `8` by the "Sharp Edge" shop upgrade — see §2.O).
*   `boolean sharpEdgePurchased`, `boolean daggerBandolierPurchased`, `int ironHeartCount` (One-time-purchase flags and repeat-purchase counter for the three shop upgrades — see §2.O).
*   `Timer shootCooldown` (Prevents ranged-shot spamming, independent of melee).
*   `Timer meleeCooldown` (Prevents melee-strike spamming, independent of shooting).
*   `Timer meleeAttack` (Counts down while the melee strike hitbox is active; active means a strike is in progress).
*   `boolean meleeHasHit` (Ensures a single swing damages at most one enemy).
*   `Timer hitInvulnerability` (Grace period after being hit by an enemy; while active, further enemy contact does not reduce health again — see §2.J).
*   `boolean interactPressed` (One-shot: true only during the frame the interact key/touch button was pressed; see §2.M).
*   `boolean nearExit` (True while inside any exit gate's proximity sensor; drives the touch UI prompt — see §2.M).

All of the countdown fields above (`shootCooldown`, `meleeCooldown`, `meleeAttack`, `hitInvulnerability`) are instances of the reusable `com.axehigh.platformer.util.Timer` helper (`start()`/`update()`/`isActive()`/`isDone()`), not raw `float`s; use it for any new cooldown/countdown/grace-period field instead of hand-rolling a decrement.

### B. BulletComponent (New)
Applied to spawned projectile entities:
*   `float damage`
*   `float lifetime` (Despawns the bullet after a set time if it doesn't hit anything).

### C. DaggerPickupComponent (New)
Marker component applied to object-layer pickup entities that replenish shoot ammo:
*   `int amount = 5` (Ammo granted to `PlayerComponent.items` on pickup, capped at `maxItems`).

### D. CoinPickupComponent (New)
Marker component applied to coin pickup entities:
*   `int amount = 1` (Coins granted to `PlayerComponent.coins` on pickup, uncapped; the HUD's existing coin counter reflects this immediately).

### E. ChestComponent (New)
Tracks a chest entity's open/disappear state after being melee-struck:
*   `boolean opened = false`
*   `Timer disappearTimer` (Counts down after opening; on becoming done the chest is removed and drops coins).

### E.1. PoppedItemComponent (New)
Empty marker component applied to a pickup entity that was launched with an initial `MovementComponent.velocity` (currently only chest-dropped coins, see §2.F). Checked by `MovementSystem`: the instant the entity's `grounded` flag first becomes `true`, its horizontal velocity is also zeroed so it comes to a dead stop exactly where it lands, instead of sliding indefinitely (there is no ground friction elsewhere in the system).

### E.2. LevelExitComponent (New)
Marks an exit-gate entity as an actual level-transition trigger (as opposed to a purely decorative gate):
*   `String nextLevelPath = ""` (Target `.tmx` asset path, e.g. `maps/demo_room.tmx`; read from the gate object's `nextLevel` custom Tiled property by `EntityFactory.createExitGate(...)`. An exit-gate object with no `nextLevel` property gets no `LevelExitComponent` and is purely decorative.)

See §2.M for the full multi-level progression mechanic built on top of this component.

### F. Extended EnemyComponent
Adds simple back-and-forth patrol state on top of the existing `float health = 10`:
*   `float speed = 20` (Horizontal patrol speed, world units/second).
*   `int direction = 1` (Current patrol direction: `1` right, `-1` left).
*   `float patrolRange = 32` (Max distance walked away from `originX` in either direction before turning around; still applies as an optional secondary cap alongside ledge detection — see §2.G).
*   `float originX` (World-space X the enemy was spawned at, set once by `EntityFactory`; the center of its patrol path).
*   `Timer hitStun` (Grace period after taking damage; while active, patrol AI pauses and further hits are ignored so a knockback pop can play out — see §2.G.5).
*   `int roomIndex = -1` (Index into `RoomState.rooms` of the Room rectangle this enemy was spawned inside of, set once by `EntityFactory` from the spawn point's containment; `-1` if none. Drives the Room-Based Entity activation described in §2.N.)

See `resources/docs-ai/enemies.md` for the full enemy catalog (current type(s), stats, sprite, and how to add new enemy types) — this section only covers the shared `EnemyComponent` fields/mechanic, not per-type tuning.

### G. FlyingEnemyComponent (New)
Marker component layered on top of `EnemyComponent`, tagging an enemy as immune to gravity; checked by `MovementSystem`, which skips its gravity/wall-slide velocity-Y update entirely when present, so the entity never falls under gravity on its own. Also carries the vertical bob-wave tuning `EnemySystem` uses to give it a flapping motion (see §2.K):
*   `float bobAmplitude = 8` (World units the bob swings above/below the height it was at when patrol last resumed.)
*   `float bobFrequency = π` (Angular frequency of the bob wave, radians/second; default is roughly a 2 second period.)
*   `float bobTime = 0` (Elapsed-time accumulator driving the wave's phase; frozen while patrol AI is paused.)

### H. EnemyShooterComponent (New)
Marker component layered on top of `EnemyComponent`:
*   `Timer shootCooldown` (Counts down between shots; once done, `EnemyShootSystem` fires and restarts it.)
*   `float shootInterval = 5` (Seconds between shots.)

See §2.L and §2.N.

### I. EnemyBulletComponent (New)
Empty marker component (`Poolable`), no fields. Tags a bullet entity as enemy-fired (as opposed to the player's own bullets), attached alongside `BulletComponent` by `EnemyShootSystem`. See §2.L.

---

## 2. Mechanics & Logic Breakdown

### A. Double Jump Logic (`PlayerInputSystem` & `MovementSystem`)
1.  **Grounded Reset:** When the physics/collision system detects the player is standing on a solid tile, reset `jumpCount` to `0`.
2.  **Jump Trigger:** When the Jump button (**A**) is pressed:
    *   If `jumpCount < maxJumps`, allow the jump by setting the upward vertical velocity.
    *   Increment `jumpCount` by 1.
3.  **Animation State:** Trigger a distinct flipping or spinning animation frame if `jumpCount == 2`.

### B. Wall Climbing Logic (`MovementSystem`)
1.  **Wall Detection:** Perform a horizontal AABB collision check slightly ahead of the player in their `facingDirection`.
2.  **Latch Condition:** If the player is in mid-air, moving towards a solid wall tile, and holding the Directional D-Pad toward that wall:
    *   Set `isWallClimbing = true`.
    *   Zero out or significantly reduce downward vertical velocity (slight sliding effect).
    *   Reset `jumpCount = 1` to allow a "Wall Jump" away from the surface.
3.  **Release Condition:** Detach if the player presses the opposite direction or falls past the bottom of the wall tile.

### C. Close-Combat Strike Logic (`PlayerInputSystem` & `MeleeAttackSystem`)
1.  **Input:** Listen for the Attack button (**B**, keyboard `J` or keyboard `B`), independent of the shoot button.
2.  **Cooldown Check:** Only allow a strike if `meleeCooldown.isDone()`.
3.  **Trigger:** On a valid press, `start()` `meleeAttack` (a short active window, e.g. ~0.2s), reset `meleeHasHit = false`, and `start()` `meleeCooldown` — no ammo is consumed and no projectile entity is spawned.
4.  **Hit Resolution (`MeleeAttackSystem`):** While `meleeAttack.isActive()` and `!meleeHasHit`, build a short strike rectangle offset from the player's `CollisionComponent` bounds in `facingDirection`. Apply melee damage read dynamically from `player.swordDamage` (base `5`, or `8` once the "Sharp Edge" shop upgrade is purchased — see §2.O) to any overlapping enemy entity exactly once; if no enemy is hit, check the same rectangle against unopened chests (see §F). Either way, set `meleeHasHit = true` so the same swing cannot hit twice, and `update()` `meleeAttack` every frame it runs.
5.  **Animation & Visual Feedback:** Trigger the `ATTACKING` animation state while `meleeAttack.isActive()`, taking priority over movement-derived states (idle/run/jump/wall-climb); a distinct attack-pose texture is registered for this state so the strike is visibly obvious on-screen.
6.  **Facing Flip:** Every frame, the player's `transform.scale.x` is set to `Math.abs(transform.scale.x) * facingDirection`, mirroring the sprite horizontally to match the direction the player last moved/faced (the renderer already scales draw width by `transform.scale.x`, so a negative value flips the sprite for free).

### D. Ammo-Gated Shooting Logic (`PlayerInputSystem` & Spawning)
1.  **Input:** Listen for the Special button (**Y**, keyboard `K` or keyboard `Y`), independent of the melee button.
2.  **Cooldown & Ammo Check:** Only allow shooting if `shootCooldown.isDone()` **and** `items > 0`. If ammo is 0, the request is silently dropped: no bullet is spawned and `shootCooldown` is left untouched (mirroring the cooldown-drop behavior).
3.  **Entity Creation:** On a successful shot, decrement `items` by 1 and instantiate a new projectile entity dynamically with:
    *   `TransformComponent`: Positioned at the player’s center, offset slightly forward based on `facingDirection`.
    *   `MovementComponent`: High horizontal velocity multiplied by `facingDirection` ($X \text{ velocity} \times \text{direction}$), with $0$ vertical velocity.
    *   `TextureComponent`: A small projectile/energy pixel sprite.
    *   `CollisionComponent`: Small bounding box for impact detection.
    *   `BulletComponent`: Set lifetime to roughly 1.5 seconds and `damage = 10` (`BULLET_DAMAGE`).

### E. Pickup Logic (`PickupSystem`)
1.  **Lookup:** Resolve the single player entity once when the system is added to the engine (mirroring `CollisionSystem`'s enemy lookup pattern).
2.  **Overlap Check:** For each pickup entity (`DaggerPickupComponent` or `CoinPickupComponent`, spawned from `"dagger"`/`"coin"` object-layer markers or chest drops), check AABB overlap between its `CollisionComponent` bounds and the player's.
3.  **On Overlap:** For a dagger pickup, increment `player.items` by the pickup's `amount`, capped at `maxItems` (no overflow past the cap). For a coin pickup, increment `player.coins` by the pickup's `amount` (uncapped, reflected immediately by the HUD's existing coin counter). Either way, remove the pickup entity from the engine.

### F. Chest Open/Disappear Logic (`MeleeAttackSystem` & `ChestSystem`)
1.  **Open Trigger:** When a melee strike rectangle overlaps an unopened chest (`ChestComponent.opened == false`), mark it `opened = true`, swap its texture to an "open" variant, and `start()` `disappearTimer` (~0.3s). A chest that's already `opened` has no further reaction to being struck (no repeated coin drops).
2.  **Disappear & Coin Pop (`ChestSystem`):** For each opened chest, `update()` `disappearTimer` every frame; once `isDone()`, remove the chest entity and spawn a random number (`MathUtils.random(2, 6)`) of coin pickup entities at the chest's last position via `EntityFactory.createPoppedCoinPickup`, each given a random initial velocity: `80`-`140` u/s upward and `±40` u/s horizontal. Because these coins carry a `MovementComponent` (and a `PoppedItemComponent` marker), `MovementSystem` picks them up automatically the next frame: gravity arcs them up and back down, AABB collision keeps them from passing through floors/walls, and — since they have `PoppedItemComponent` — their horizontal velocity is zeroed the instant they first touch ground (see §1.E.1), so each coin visibly pops up and scatters outward before coming to rest in place rather than either sitting still or sliding forever.
3.  **Scope:** This pop-then-fall treatment is chest-drop-only; coin/dagger pickups placed directly on the map via object-layer markers are still built with the plain `EntityFactory.createCoinPickup`/`createDaggerPickup` (no `MovementComponent`), so they remain static.

### G. Enemy Patrol Movement Logic (`EnemySystem`)
1.  **Spawn:** Enemies are placed on the map via `"enemy"` object-layer markers, instantiated by `EntityFactory.createEnemy`, which gives them a `TransformComponent`, `MovementComponent`, `CollisionComponent`, and an `EnemyComponent` whose `originX` is set to the spawn X.
2.  **Hit-Stun Pause:** Every frame, `EnemySystem` first `update()`s `enemy.hitStun`; while it's `isActive()`, patrol AI is skipped entirely for that frame so a knockback pop (see §2.I) can play out via `MovementSystem` uninterrupted.
3.  **Velocity Drive:** Otherwise, before `MovementSystem` integrates positions, `EnemySystem` sets `movement.velocity.x = enemy.speed * enemy.direction`.
4.  **Turn-Around Conditions:** `enemy.direction` flips when any of the following is true (checked in this order, first match wins): (a) the enemy is `grounded` and its `velocity.x` was zeroed (meaning `MovementSystem` stopped it against a wall the previous frame); (b) a **ledge/platform-edge probe** finds no solid ground just past the enemy's leading foot (see "Platform-Edge Detection" below); (c) `transform.position.x` reaches `originX ± patrolRange`.
5.  **Platform-Edge Detection:** While `grounded`, `EnemySystem` checks a small rectangle positioned just past the enemy's leading foot (in its current travel `direction`) and just below its feet against the same static `collisionRects` set `MovementSystem` uses for wall/floor collision. If nothing overlaps it, there's no platform ahead, so the enemy turns around immediately rather than walking off the edge — this makes it safe to place an enemy on a platform narrower than `patrolRange` without any manual tuning; `patrolRange` still works as an optional secondary cap on top of this (e.g. to keep an enemy patrolling only part of a long platform).
6.  **Physics for free:** Enemies are **not** excluded from `MovementSystem`'s family (only bullets are), so gravity, ground snapping, and AABB wall collision against `collisionRects` apply to them automatically; `EnemySystem` only ever touches horizontal (and, during knockback, vertical) velocity.
7.  **Damage:** Enemies start with `10` hit points (`EnemyComponent.health`) by default, though the exact value varies by tier (`5`/`10`/`15` HP — see "Stat-threshold combat balance tiers" in `resources/docs-ai/enemies.md` §2) and remain damageable via `CollisionSystem` (bullet hits, `10` damage) and `MeleeAttackSystem` (melee strikes, `player.swordDamage` — base `5`, or `8` post-"Sharp Edge", see §2.O), both routed through the shared `EnemyDamageResolver.applyHit(...)` helper (see §2.I).
8.  **Enemy catalog:** This section describes the shared patrol/hit-reaction mechanics; for the concrete enemy type(s) built on top of them (sprite, stats, and guidance for adding more types), see `resources/docs-ai/enemies.md`.

### I. Enemy Hit Reaction: Stun + Knockback (`EnemyDamageResolver`)
1.  **Shared resolver:** Both `CollisionSystem` (bullet hits) and `MeleeAttackSystem` (melee hits) route enemy damage through a shared `EnemyDamageResolver.applyHit(enemy, movement, damage, knockbackDirection, isFlying)` helper, so both damage sources react identically. `isFlying` is computed at each call site from whether the hit enemy has a `FlyingEnemyComponent`.
2.  **Immunity while stunned:** If `enemy.hitStun.isActive()`, the hit is ignored completely — no damage, no knockback — preventing a single swing/bullet from re-triggering the pop on an enemy still mid-knockback.
3.  **Damage & knockback:** Otherwise, `damage` is subtracted from `enemy.health` (removing the entity at `<= 0`, no knockback needed). If the enemy survives, its `MovementComponent.velocity.x` is set to a knockback pop: `90` u/s horizontally away from the attacker (melee: opposite the player's `facingDirection`; bullet: the bullet's own travel direction, via `knockbackDirection`). Unless the enemy has a `FlyingEnemyComponent` (see §2.K), a `140` u/s upward hop is also applied to `velocity.y`; for a flying enemy this vertical hop is skipped entirely (it has no gravity to pull it back down, so it would otherwise drift upward forever) while the horizontal pop still applies.
4.  **Grace period:** A surviving hit also `start()`s `enemy.hitStun` for `0.3` seconds; while active, `EnemySystem` pauses patrol AI (§2.G.2) so the knockback isn't immediately overwritten, and the enemy can't take further damage until it elapses.

### J. Enemy-Contact Damage & Hit-Invulnerability Grace Period (`EnemyContactSystem`)
1.  **Overlap Detection:** `EnemyContactSystem` resolves the single player entity once in `addedToEngine` (mirroring `PickupSystem`), then for each enemy checks AABB overlap between its `CollisionComponent` bounds and the player's.
2.  **Damage & Grace Period:** On overlap, if `player.hitInvulnerability.isDone()`, decrement `player.health` by `1` (clamped at `0` via `Math.max`, never negative) and `start()` `hitInvulnerability` for `1.0` second. Reaching `0` triggers game-over handling — see §2.P.
3.  **Single-Tick Timer:** The grace-period countdown is ticked exactly **once per frame** in `EnemyContactSystem.update()` (before iterating enemies), not once per enemy in `processEntity` — this ensures multiple enemies overlapping the player in the same frame only cost **one** life, not one per enemy.
4.  **No Visual Feedback (yet):** The grace period only prevents repeated damage; it does not currently flicker/tint the player sprite.

### K. Flying Enemy Movement (`MovementSystem`, `EnemySystem`, `FlyingEnemyComponent`)
1.  **Gravity Opt-Out:** Every frame, `MovementSystem` checks whether the entity has a `FlyingEnemyComponent`; if so, the gravity/wall-slide velocity-Y update is skipped entirely, so the enemy never falls under gravity on its own.
2.  **Vertical Bob Wave:** Every frame patrol AI isn't paused (i.e. `enemy.hitStun` is not active), `EnemySystem` also accumulates `flying.bobTime += deltaTime` and sets `movement.velocity.y = flying.bobAmplitude * flying.bobFrequency * cos(flying.bobTime * flying.bobFrequency)`. Since gravity never touches `velocity.y` for a flyer, `MovementSystem`'s normal integration turns this into a smooth sine oscillation of height, swinging ±`bobAmplitude` (default `8` units) around the height the enemy was at when patrol last resumed, at `bobFrequency` (default `π` rad/s, ≈ 2 second period) — giving the enemy a visible flap/hover instead of flying in a perfectly flat line.
3.  **Horizontal Patrol Unchanged:** `EnemySystem`'s existing horizontal patrol logic (§2.G) needs no changes for a flyer: since it's never `grounded`, its wall-block and ledge-probe turn-around checks (both gated on `movement.grounded`) simply never fire, so it turns around purely on the `originX ± patrolRange` bounds check.
4.  **Level-Design Constraint:** A flying enemy has no wall-block fallback, so its `patrolRange` must be placed clear of walls by the level designer (there's no automatic edge/wall avoidance for flyers, unlike grounded patrol enemies); it should also be placed with enough vertical clearance for the bob wave.
5.  **Knockback & Bob Pause:** See §2.I.5 — a flyer's vertical knockback hop is skipped so it doesn't drift upward forever; since patrol AI (and therefore the bob) is paused for the same `hitStun` duration, `bobTime` simply freezes and resumes smoothly once the stun ends.
6.  **Catalog:** See `resources/docs-ai/enemies.md` for the Flying Enemy's stats (5 HP).

### L. Enemy Shooting & Enemy Bullet Damage (`EnemyShootSystem`, `EnemyBulletCollisionSystem`)
1.  **Firing Cadence:** A shooter enemy (`EnemyShooterComponent`) still patrols exactly like a base enemy (driven by `EnemySystem`, since it matches its family too). Every frame, `EnemyShootSystem` ticks `shooter.shootCooldown`; while `enemy.hitStun.isActive()` (§2.I), firing is skipped entirely, mirroring `EnemySystem`'s own stun-pause.
2.  **Same-Room Gating:** Once `shootCooldown.isDone()`, `EnemyShootSystem` additionally checks whether the shooter's `EnemyComponent.roomIndex` is either `-1` or equal to `RoomState.activeRoomIndex` (see §2.N), and only proceeds to fire if so. If the shooter's room isn't the active one, it simply doesn't fire that frame and leaves `shootCooldown` at `isDone()` (never restarted), so it fires immediately once its room becomes active again instead of firing blind into a room the player isn't even in.
3.  **Spawn:** When both the cooldown and the room check pass, a bullet is spawned at the enemy's leading edge, traveling horizontally in `enemy.direction` — there is **no** player-aiming/aggro, the shot always fires in whatever direction the enemy is currently facing/patrolling. The cooldown then restarts via `shootInterval` (`5` seconds by default).
4.  **Enemy Bullet Tagging:** The spawned bullet gets both `BulletComponent` (`damage = 0`, unused for enemy bullets) and `EnemyBulletComponent`, a marker that makes `CollisionSystem` ignore it (excluded from its family) so it's instead processed by `EnemyBulletCollisionSystem`.
5.  **Hit Resolution (`EnemyBulletCollisionSystem`):** Mirrors `CollisionSystem`'s bullet-vs-wall/lifetime handling, but resolves the hit case against the cached player entity instead of an enemy. On overlap, the bullet is always removed; additionally, if `player.hitInvulnerability.isDone()`, `player.health` is decremented by `1` (clamped at `0`) and `hitInvulnerability` is restarted for `1.0` second. Bullets already in flight are unaffected by the room check — only new shots are gated.
6.  **Shared Grace Period:** This is the **same** `player.hitInvulnerability` timer `EnemyContactSystem` (§2.J) uses, so a shot landing right after a contact hit (or vice versa) within the same window doesn't stack — a shot costs a life "similar to being hit by an enemy," per design.
7.  **Catalog:** See `resources/docs-ai/enemies.md` for the Shooting Enemy's stats (10 HP, 5s shot interval).

---

## 3. Collision & Cleanup Systems

### A. Projectile Collision Handling
Create or expand a `CollisionSystem` to handle bullet interactions:
*   **Wall Impact:** If a bullet entity's bounding box intersects a solid Tiled layer cell, immediately remove the bullet entity from the engine.
*   **Enemy Impact:** If a bullet intersects an enemy entity, apply damage to the enemy's health component and destroy the bullet.

### B. Memory Optimization
*   **Bullet Pooling:** Because projectiles are frequently created and destroyed, utilize libGDX’s `Pool` or Ashley's component pooling interfaces to avoid garbage collection stuttering during heavy combat sequences.

### C. Room-to-Room Passages (`MapLoader`)
A multi-room `.tmx` map (see the Room-Based Camera design, §2.N) lays adjacent rooms out as room-sized (or larger/smaller) blocks separated by ordinary wall tiles. Rooms are **never** connected via door objects/interactions; instead, a natural passageway is carved directly into the collision tile layer:
*   **Solid tile property:** Every tile in the collision layer's tilesets declares a boolean `solid` property (`true` for the regular wall tile, `false` for the dedicated `passage_tile`). `MapLoader.buildCollisionRects()` only emits a boundary `Rectangle` for cells whose tile is `solid` (defaulting to `true` if the property is absent), so `passage_tile` cells render as part of the wall/corridor art but never block movement.
*   **Authoring an opening:** To connect two adjacent rooms, replace the wall tiles on **both** sides of the shared border (each room contributes its own 1-tile-thick border) with `passage_tile` cells, spanning enough width/height for the player to walk through comfortably (e.g. 3-4 tiles).
*   **No new components/systems required:** Since passability is derived purely from the tile's `solid` property, traversal, `MovementSystem`'s AABB collision, and `CollisionSystem`'s bullet-vs-wall checks all treat a passage exactly like open floor — no special-casing needed outside `MapLoader`.

### D. Debug Collision Overlay (`DebugRenderSystem`)
A dedicated `DebugRenderSystem` (see `resources/docs-ai/ashley-ecs.md`) draws every live `CollisionComponent` AABB (lime), the static map `collisionRects` (yellow), and every `RoomState.rooms` rectangle (cyan, with whichever one is currently active in orange — see §2.N) via a `ShapeRenderer`, toggled on/off with **SHIFT+D** (see `AGENTS.md` "Debugging"). It is a pure visualization aid — disabled by default, no gameplay effect, and skips all drawing while off.

### M. Multi-Level Progression & Exit Gates (`LevelExitSystem`, `LevelManager`, `PlayerInputSystem`)
1.  **Level chain:** The game ships 3 levels in a strict, hardcoded-by-content (not hardcoded-in-code) sequence: `demo_room_start.tmx` (level 1, where `GameScreen` now boots into) → `demo_room.tmx` (level 2) → `demo_room_final.tmx` (level 3, a dead end with no exit gate — reaching it ends traversal with no win screen; there is still no explicit "win" state, only game-over on death, see §2.P). Each map's exit gate points at the next one via its own `nextLevel` Tiled property (§1.E.2) rather than any level-order list, so the chain is purely data-driven and could become non-linear later without code changes.
2.  **Proximity sensor, not walk-through:** Unlike coin/dagger pickups, an exit gate never auto-triggers on overlap. `LevelExitSystem` builds a sensor `Rectangle` from the gate's own `CollisionComponent.bounds` inflated by `SENSOR_PADDING = 6` world units on every side (so it feels like walking up to a door, not requiring pixel-perfect contact) and checks it against the player's AABB every frame.
3.  **Interact input:** Pressing **E** (keyboard) or the new contextual up-arrow touch button sets `player.interactPressed` for exactly one frame (`PlayerInputSystem`, mirroring the existing `requestTouchJump`/`touchJumpRequested` one-shot pattern via `requestTouchInteract()`/`touchInteractRequested`). A level transition only fires when the player is inside a gate's sensor **and** `interactPressed` is `true` that same frame; being near the gate alone only sets `player.nearExit = true` (driving the UI prompt) without firing anything.
4.  **Contextual UI prompt:** `TouchControlsStage` exposes `setInteractVisible(boolean)`, called every frame from `GameScreen.render()` with `playerComponent.nearExit`, showing/hiding a small up-arrow touch button (per the "Contextual Action Button" UI guideline) only while the player is near a gate.
5.  **In-place level swap (`LevelManager`):** Firing a gate calls `LevelManager.loadLevel(nextLevelPath, playerEntity)`, which loads the new `.tmx`, swaps `TiledMapRenderSystem`'s wrapped map, clears+refills the shared `collisionRects` array **and** the shared `RoomState.rooms` array in place (every map-dependent system already holds one of those same references, so no re-wiring is needed), removes every engine entity **except** the player, disposes the old map, respawns the new level's objects (`EntityFactory.spawnObjects`, which also assigns each spawned enemy's `roomIndex` — see §2.N), repositions the player at the new map's `playerStart`, and calls `CameraSystem.snapToRoom(...)` to place the camera in the new starting room. The `PooledEngine` and the player `Entity` are never destroyed — it's the exact same `PlayerComponent` instance before and after the swap.
6.  **Stat persistence guarantee:** Because the player entity survives the swap, `health`, `maxHealth`, `coins`, `items`, `maxItems`, and every active cooldown `Timer` (shoot/melee/hit-invulnerability) carry over into the new level completely untouched — there is nothing to serialize or restore. Only *transient* state is explicitly reset by `LevelManager`: `MovementComponent.velocity`/`grounded`, and `PlayerComponent.jumpCount`/`isWallClimbing`/`interactPressed`/`nearExit`, so the player doesn't, say, arrive already mid-jump or wall-climbing in the new room.
7.  **Fresh object population:** Every non-player entity from the old level (enemies, pickups, chests, the old exit gate, etc.) is gone after a transition; the new level's enemies/pickups/gate are spawned fresh from its own object layer, exactly as if the level had just been loaded from scratch.
8.  **Cross-session persistence:** Passing through any exit gate autosaves a `SaveData` snapshot (level path, core stats, `completedLevelIds`, `triesRemaining`) via `SaveManager`/libGDX `Preferences`, so `MainMenuScreen`'s Continue button can resume the run across app restarts — see `resources/docs-ai/main-menu-screens.md`-equivalent plan notes and §2.P for the tries counter specifically.
9.  **Debug overlay survives transitions:** `DebugRenderSystem`'s SHIFT+D toggle state is untouched by a level swap, since the system itself is never removed/recreated — only its `collisionRects`/`RoomState.rooms` references are refilled in place by `LevelManager`.

### N. Room-Based Camera & Entity Management (`CameraSystem`, `RoomState`, `EnemySystem`, `EnemyShootSystem`)
1.  **"Rooms" object layer:** Any map may define an object layer literally named `Rooms`, containing one or more plain rectangle objects (no `type`/properties needed) — each one is a distinct room zone of whatever size the level designer wants (does not have to match the fixed `VIRTUAL_WIDTH`/`VIRTUAL_HEIGHT` viewport). `MapLoader.getRooms()` parses these into a plain `Array<Rectangle>`; a map with no such layer simply has zero rooms.
2.  **`RoomState` (shared, live state):** A single `RoomState` instance (created once in `GameScreen.show()`, same sharing pattern as `collisionRects`) holds `rooms` (the current level's parsed rectangles) and `activeRoomIndex` (which one currently contains the player, `-1` if none). It's passed to `CameraSystem`, `EnemySystem`, `EnemyShootSystem`, `DebugRenderSystem`, `EntityFactory.spawnObjects(...)`, and `LevelManager`, which refills `rooms` in place on every level transition (§2.M.5) — no system ever needs re-wiring across a level swap.
3.  **Camera tracking & clamping (`CameraSystem`):** Every frame, `CameraSystem` finds which room rectangle contains the player's position (updating `RoomState.activeRoomIndex`), then positions the camera per axis: if the room is no bigger than the fixed `VIRTUAL_WIDTH`/`VIRTUAL_HEIGHT` viewport on that axis, the camera centers on the room and stays perfectly static on that axis (the viewport is allowed to show a sliver beyond the room's edges rather than trying to clamp into an impossible range); otherwise, the camera continuously follows the player's position on that axis, clamped so the viewport never shows anything past the room's own edges. There is no smooth lerp/easing on a room-to-room transition — the clamp bounds change instantly the moment the player's active room changes, exactly like the previous flip-screen camera's snap behavior. This **replaces** the old fixed-grid flip-screen `CameraSystem` entirely.
4.  **Enemy Room activation (`EnemySystem`/`EnemyShootSystem`):** Each enemy is assigned a `roomIndex` at spawn time (`EnemyComponent.roomIndex`, §1.F) by testing its spawn point against `RoomState.rooms` — no extra Tiled authoring needed. Every frame, both `EnemySystem` and `EnemyShootSystem` check whether that enemy's room is the currently active one (`roomIndex == RoomState.activeRoomIndex`, or always-active if `roomIndex == -1`, e.g. a map with no "Rooms" layer at all). An enemy in an inactive room is **frozen, not despawned**: its horizontal velocity (and, for a `FlyingEnemyComponent`, its bob-driven vertical velocity) is zeroed and all patrol/bob/firing logic is skipped, but the entity itself, its `health`, and its `hitStun` state remain exactly as they were — so leaving mid-fight and coming back later resumes from where you left it, rather than resetting or despawning the enemy.
5.  **Multi-room single level example (`demo_room.tmx`):** The level-2 map defines 6 rooms: the original 2x2 flip-screen grid (each exactly `VIRTUAL_WIDTH`x`VIRTUAL_HEIGHT`, so the camera is static within each) plus two new rooms stacked above it, connected by aligned vertical passages (§3.C) — a `largeRoom` (`960x320`, bigger than the viewport on both axes, so the camera continuously scrolls/clamps while exploring it) and a `smallRoom` (`160x96`, smaller than the viewport on both axes, so the camera simply centers on it without scrolling).
6.  **No effect on collision/movement:** Room rectangles are purely a camera/entity-activation concern; they never participate in AABB collision (that's still `collisionRects`, §3.C) and a room boundary is not itself a wall — the player can walk freely between overlapping/adjacent rooms exactly as the tile geometry allows.

### O. Shop/Upgrade Mechanic (`shop.ShopManager`, `shop.ShopItem`)
1.  **Scope today:** Only the data model and transaction logic exist so far — a standalone, non-ECS `ShopManager`/`ShopItem` pair under `core/.../shop/`. There is no vendor NPC entity, shop map, or interaction UI yet; a future vendor entity/interact prompt is expected to call `ShopManager.purchase(playerComponent, item)` directly, the same way `LevelExitSystem`'s interact prompt already drives a state transition today (§2.M).
2.  **Catalog:** `ShopManager` exposes a fixed 3-item catalog via `getCatalog()`:
    *   **"Sharp Edge"** (`100` gold, one-time): sets `player.swordDamage = 8` and `player.sharpEdgePurchased = true`.
    *   **"Dagger Bandolier"** (`75` gold, one-time): sets `player.maxItems = 60` (doubling the `30` base) and `player.daggerBandolierPurchased = true`.
    *   **"Iron Heart"** (`150` gold, repeatable): increments both `player.maxHealth` and `player.health` by `1`, and increments `player.ironHeartCount`.
3.  **Transaction (`purchase(PlayerComponent, ShopItem)`):** Rejects the purchase with no state change if `player.coins < item.cost`, or if a one-time item (`Sharp Edge`/`Dagger Bandolier`) has already been purchased. Otherwise, deducts `item.cost` from `player.coins`, applies the item's effect, marks the purchased flag (or increments `ironHeartCount` for the repeatable Iron Heart), and returns `true`.
4.  **Combat balance tie-in:** `swordDamage` (base `5`, `8` post-"Sharp Edge") is the same dynamic stat `MeleeAttackSystem` reads on every strike (§2.C.4, §2.G.7); combined with the enemy stat-threshold tiers in `resources/docs-ai/enemies.md` §2 (Grunt `5` HP / Elite `10` HP / Knight `15` HP), a Knight drops from `3` melee hits to `2` once "Sharp Edge" is purchased, while Grunt/Elite hit counts are unchanged by the upgrade.
5.  **Persistence:** In-memory only, matching every other `PlayerComponent` field — upgrades survive level transitions (§2.M.6) for the current play session, but there is no save/load across app restarts.

### P. Player Death, Tries, and Game Over (`PlayerDeathSystem`, `GameScreen`, `SaveData`)
1.  **Detection:** `PlayerDeathSystem` checks `player.health <= 0` every frame; the very first frame this becomes true, an internal `triggered` guard flips and a one-time `Runnable` callback fires into `GameScreen` — subsequent frames while still at `0` health do nothing further (no repeated triggers while the dialog is open).
2.  **Pause on death:** The callback sets a `gameOverActive` flag on `GameScreen` and immediately shows a Scene2D `Dialog` (titled "Game Over") on the existing `hudStage`. While `gameOverActive` is `true`, `render()` skips `engine.update(...)` entirely — no movement, AI, physics, or damage resolves — but `hudStage`'s own `act()`/`draw()` (and therefore the dialog's buttons) keep running.
3.  **Tries system:** `SaveData` carries `triesRemaining` (default `3`). A brand-new run (`MainMenuScreen`'s New Game) always seeds a fresh `SaveData` with `triesRemaining = 3`; Continue-from-menu resumes whatever value was last persisted. Tries are **not** refilled by level-exit checkpoints — they only ever go down within a run, until New Game restarts the counter.
4.  **Continue (uses 1 try):** Shown only while the loaded/in-memory `SaveData.triesRemaining > 0`. Clicking it decrements `triesRemaining`, persists it immediately via `SaveManager.save(...)`, reloads the **current** level from scratch via `LevelManager.loadLevel(currentLevelPath, playerEntity)` (coins/items/upgrades untouched, since the player entity itself is never destroyed — same guarantee as §2.M.6), resets `player.health = player.maxHealth`, hides the dialog, and clears `gameOverActive` so the engine resumes updating next frame. This does **not** roll back to the last exit-gate autosave — it restarts the level currently in progress.
5.  **Tries exhausted:** Once `triesRemaining == 0` at the moment of death, the dialog is built with **no** Continue option at all (not merely disabled) — only "Exit to Main Menu" is shown.
6.  **Exit to Main Menu:** Always available regardless of tries remaining; calls `game.setScreen(new MainMenuScreen(game))`, abandoning the current in-progress level (any stats from the last exit-gate autosave remain on disk, untouched).
7.  **No death animation/sound:** This mechanic only covers detection + pause + dialog + retry/exit; there is no visual/audio feedback on the player entity itself when health reaches 0.

### Q. Player Scaling (EntityFactory)
1. Scaling Strategy: Automatic. Player scale is derived from tile size to maintain relative size.
2. Atlas Usage: Uses `knight2.atlas` instead of `gfx/player.png`.
3. Implementation: Player `TransformComponent.scale` is set to `unitScale * GameConstants.PlayerScale`. `CollisionComponent.bounds` is set to a tighter 40x80 pixel box (scaled by `finalScale`) for more responsive collision handling.

### R. Dynamic Collision Offset & Sprite Alignment (`MovementSystem`, `RenderSystem`)
1. **Alignment & Reach:** The player's collision box can be offset from its visual center using `PlayerOffsetRight`, `PlayerOffsetLeft`, and `PlayerOffsetY` in `GameConstants`. These are used to correct sprite misalignment in the atlas or to provide directional reach.
2. **Locked Movement:** The collision box target offset is chosen based on `facingDirection` (`PlayerOffsetRight` when facing right, `PlayerOffsetLeft` when facing left). Vertical offset `PlayerOffsetY` is applied constantly.
3. **Smoothing:** `MovementSystem` smoothly interpolates `CollisionComponent.currentOffsetX` towards the target offset using a lerp, preventing position snaps when turning against walls.
4. **Visual Anchoring:** `RenderSystem` anchors the sprite such that the character's center remains locked to the collision box center (compensated for offsets). To prevent the character from "jumping" in world space when flipping off-center frames, the system dynamically compensates by flipping the sign of the directional offsets (`currentOffsetX/Y`) when calculating the sprite's `frameCenterX/Y`. This ensures that both the character and its collision box remain stable and aligned during turns, even if the character is not centered in the sprite frame.
