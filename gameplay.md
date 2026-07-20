# Agent Instructions: Gameplay Mechanics Implementation

This document outlines the architectural and technical requirements for implementing player traversal (double jump, wall climb) and combat (shooting) using Ashley ECS in libGDX.

---

## 1. State Management & Components

### A. Extended PlayerComponent
Track the advanced state variables required for these mechanics directly inside the `PlayerComponent`:
*   `int jumpCount` (Tracks current jumps executed; resets to 0 when grounded).
*   `int maxJumps = 2` (Allows for double jumping).
*   `boolean isWallClimbing` (Flag for wall attachment).
*   `int facingDirection` (-1 for left, 1 for right).
*   `float shootCooldownTimer` (Prevents bullet spamming).

### B. BulletComponent (New)
Applied to spawned projectile entities:
*   `float damage`
*   `float lifetime` (Despawns the bullet after a set time if it doesn't hit anything).

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

### C. Shooting Logic (`PlayerInputSystem` & Spawning)
1.  **Input:** Listen for the Attack/Special button (**B** or **Y**).
2.  **Cooldown Check:** Only allow shooting if `shootCooldownTimer <= 0`.
3.  **Entity Creation:** Instantiate a new projectile entity dynamically with:
    *   `TransformComponent`: Positioned at the player’s center, offset slightly forward based on `facingDirection`.
    *   `MovementComponent`: High horizontal velocity multiplied by `facingDirection` ($X \text{ velocity} \times \text{direction}$), with $0$ vertical velocity.
    *   `TextureComponent`: A small projectile/energy pixel sprite.
    *   `CollisionComponent`: Small bounding box for impact detection.
    *   `BulletComponent`: Set lifetime to roughly 1.5 seconds.

---

## 3. Collision & Cleanup Systems

### A. Projectile Collision Handling
Create or expand a `CollisionSystem` to handle bullet interactions:
*   **Wall Impact:** If a bullet entity's bounding box intersects a solid Tiled layer cell, immediately remove the bullet entity from the engine.
*   **Enemy Impact:** If a bullet intersects an enemy entity, apply damage to the enemy's health component and destroy the bullet.

### B. Memory Optimization
*   **Bullet Pooling:** Because projectiles are frequently created and destroyed, utilize libGDX’s `Pool` or Ashley's component pooling interfaces to avoid garbage collection stuttering during heavy combat sequences.
