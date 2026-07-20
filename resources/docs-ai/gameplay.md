# Agent Instructions: Gameplay Mechanics Implementation

This document outlines the architectural and technical requirements for implementing player traversal (double jump, wall climb) and combat (close-combat strike, ammo-gated shooting, dagger pickups, coin pickups, chest interaction) using Ashley ECS in libGDX.

---

## 1. State Management & Components

### A. Extended PlayerComponent
Track the advanced state variables required for these mechanics directly inside the `PlayerComponent`:
*   `int jumpCount` (Tracks current jumps executed; resets to 0 when grounded).
*   `int maxJumps = 2` (Allows for double jumping).
*   `boolean isWallClimbing` (Flag for wall attachment).
*   `int facingDirection` (-1 for left, 1 for right).
*   `int items` / `int maxItems = 30` (Dagger/shoot ammo count and cap; the HUD's item tracker displays this as `x NN/30`).
*   `float shootCooldownTimer` (Prevents ranged-shot spamming, independent of melee).
*   `float meleeCooldownTimer` (Prevents melee-strike spamming, independent of shooting).
*   `float meleeAttackTimer` (Counts down while the melee strike hitbox is active; `> 0` means a strike is in progress).
*   `boolean meleeHasHit` (Ensures a single swing damages at most one enemy).

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
*   `float disappearTimer = 0f` (Counts down after opening; on reaching 0 the chest is removed and drops coins).

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
2.  **Cooldown Check:** Only allow a strike if `meleeCooldownTimer <= 0`.
3.  **Trigger:** On a valid press, arm `meleeAttackTimer` (a short active window, e.g. ~0.2s), reset `meleeHasHit = false`, and start `meleeCooldownTimer` — no ammo is consumed and no projectile entity is spawned.
4.  **Hit Resolution (`MeleeAttackSystem`):** While `meleeAttackTimer > 0` and `!meleeHasHit`, build a short strike rectangle offset from the player's `CollisionComponent` bounds in `facingDirection`. Apply melee damage to any overlapping enemy entity exactly once; if no enemy is hit, check the same rectangle against unopened chests (see §F). Either way, set `meleeHasHit = true` so the same swing cannot hit twice, and count `meleeAttackTimer` down to 0.
5.  **Animation & Visual Feedback:** Trigger the `ATTACKING` animation state while `meleeAttackTimer > 0`, taking priority over movement-derived states (idle/run/jump/wall-climb); a distinct attack-pose texture is registered for this state so the strike is visibly obvious on-screen.
6.  **Facing Flip:** Every frame, the player's `transform.scale.x` is set to `Math.abs(transform.scale.x) * facingDirection`, mirroring the sprite horizontally to match the direction the player last moved/faced (the renderer already scales draw width by `transform.scale.x`, so a negative value flips the sprite for free).

### D. Ammo-Gated Shooting Logic (`PlayerInputSystem` & Spawning)
1.  **Input:** Listen for the Special button (**Y**, keyboard `K` or keyboard `Y`), independent of the melee button.
2.  **Cooldown & Ammo Check:** Only allow shooting if `shootCooldownTimer <= 0` **and** `items > 0`. If ammo is 0, the request is silently dropped: no bullet is spawned and `shootCooldownTimer` is left untouched (mirroring the cooldown-drop behavior).
3.  **Entity Creation:** On a successful shot, decrement `items` by 1 and instantiate a new projectile entity dynamically with:
    *   `TransformComponent`: Positioned at the player’s center, offset slightly forward based on `facingDirection`.
    *   `MovementComponent`: High horizontal velocity multiplied by `facingDirection` ($X \text{ velocity} \times \text{direction}$), with $0$ vertical velocity.
    *   `TextureComponent`: A small projectile/energy pixel sprite.
    *   `CollisionComponent`: Small bounding box for impact detection.
    *   `BulletComponent`: Set lifetime to roughly 1.5 seconds.

### E. Pickup Logic (`PickupSystem`)
1.  **Lookup:** Resolve the single player entity once when the system is added to the engine (mirroring `CollisionSystem`'s enemy lookup pattern).
2.  **Overlap Check:** For each pickup entity (`DaggerPickupComponent` or `CoinPickupComponent`, spawned from `"dagger"`/`"coin"` object-layer markers or chest drops), check AABB overlap between its `CollisionComponent` bounds and the player's.
3.  **On Overlap:** For a dagger pickup, increment `player.items` by the pickup's `amount`, capped at `maxItems` (no overflow past the cap). For a coin pickup, increment `player.coins` by the pickup's `amount` (uncapped, reflected immediately by the HUD's existing coin counter). Either way, remove the pickup entity from the engine.

### F. Chest Open/Disappear Logic (`MeleeAttackSystem` & `ChestSystem`)
1.  **Open Trigger:** When a melee strike rectangle overlaps an unopened chest (`ChestComponent.opened == false`), mark it `opened = true`, swap its texture to an "open" variant, and start `disappearTimer` (~0.3s). A chest that's already `opened` has no further reaction to being struck (no repeated coin drops).
2.  **Disappear & Coin Drop (`ChestSystem`):** For each opened chest, count `disappearTimer` down every frame; once it reaches 0, remove the chest entity and spawn a random number (`MathUtils.random(2, 6)`) of coin pickup entities at small random offsets around the chest's last position.

---

## 3. Collision & Cleanup Systems

### A. Projectile Collision Handling
Create or expand a `CollisionSystem` to handle bullet interactions:
*   **Wall Impact:** If a bullet entity's bounding box intersects a solid Tiled layer cell, immediately remove the bullet entity from the engine.
*   **Enemy Impact:** If a bullet intersects an enemy entity, apply damage to the enemy's health component and destroy the bullet.

### B. Memory Optimization
*   **Bullet Pooling:** Because projectiles are frequently created and destroyed, utilize libGDX’s `Pool` or Ashley's component pooling interfaces to avoid garbage collection stuttering during heavy combat sequences.

### C. Room-to-Room Passages (`MapLoader`)
The single multi-room `.tmx` map (see the flip-screen `CameraSystem` design) lays adjacent rooms out as room-sized blocks separated by ordinary wall tiles. Rooms are **never** connected via door objects/interactions; instead, a natural passageway is carved directly into the collision tile layer:
*   **Solid tile property:** Every tile in the collision layer's tilesets declares a boolean `solid` property (`true` for the regular wall tile, `false` for the dedicated `passage_tile`). `MapLoader.buildCollisionRects()` only emits a boundary `Rectangle` for cells whose tile is `solid` (defaulting to `true` if the property is absent), so `passage_tile` cells render as part of the wall/corridor art but never block movement.
*   **Authoring an opening:** To connect two adjacent rooms, replace the wall tiles on **both** sides of the shared border (each room contributes its own 1-tile-thick border) with `passage_tile` cells, spanning enough width/height for the player to walk through comfortably (e.g. 3-4 tiles).
*   **No new components/systems required:** Since passability is derived purely from the tile's `solid` property, traversal, `MovementSystem`'s AABB collision, and `CollisionSystem`'s bullet-vs-wall checks all treat a passage exactly like open floor — no special-casing needed outside `MapLoader`.
