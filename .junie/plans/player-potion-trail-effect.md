---
sessionId: session-261011-154910-1lqr
---

# Requirements

### Overview & Goals
Implement a trailing shadow (afterimage ghost) effect for the player when speed or jump potions are active. This gives visual feedback similar to high-speed action platformers, enhancing the hack-and-slash medieval action feel when boosted by potions.

### Scope
- **In Scope:**
  - Spawning fading afterimage ghost entities for the player when speed or jump potion buffs are active.
  - Reusing the existing `TrailComponent` and `TrailSystem` infrastructure.
  - Adding configuration constants for player trail frequency and lifetime.
  - Documentation updates (`gameplay.md` and ECS docs).
- **Out of Scope:**
  - Changes to player movement physics or buff durations.
  - Trailing shadows for enemies or regular unbuffed player movement.

### User Stories
- As a player, when I consume a speed or jump potion, I want to see a trailing shadow effect behind my character so that my powered-up state feels fast, dynamic, and visually impactful.

### Functional Requirements
- **Buff Gating:** Player trail ghosts are spawned only when either the `speed` buff or `jump` buff is active on `BuffComponent`.
- **Ghost Properties:** Trail entities consist of `TransformComponent`, `TextureComponent`, and `TrailComponent`. They inherit the player's position, scale, Z-index, and current animation frame.
- **Fade Out & Cleanup:** Trail entities age over their lifespan (`lifeTime`), with alpha decreasing linearly via `TrailSystem`, and are automatically removed from the Ashley engine upon expiration.


# Technical Design

### Current Implementation
- `TrailComponent` and `TrailSystem` currently handle bullet trail ghosts (`BulletComponent` / `EnemyBulletCollisionSystem` / `PlayerBulletSystem`).
- `RenderSystem` supports `transform.alpha` for fading entities and correctly handles character anchoring using `CollisionComponent` and `AtlasRegion` offsets.
- `BuffComponent` tracks active `speed` and `jump` timers.

### Key Decisions
- **Reuse Existing Infrastructure:** Leverage the existing `TrailComponent` and `TrailSystem` rather than creating duplicate rendering logic, ensuring consistency with bullet trails.
- **Collision-Aware Spawning:** Because player sprites use atlas regions with trimming and directional offsets, player trail spawning will capture the current sprite frame and transform accurately.

### Proposed Changes
1. **PlayerConfig:** Add constants for player trail emission interval and lifetime (`PLAYER_TRAIL_INTERVAL`, `PLAYER_TRAIL_LIFETIME`).
2. **TrailSystem:** Add a static `spawnPlayerTrail(Engine engine, TransformComponent transform, TextureComponent texture, CollisionComponent collision)` method to instantiate player afterimage ghosts.
3. **Trail Trigger:** Implement periodic trail spawning (e.g. in `BuffSystem` or a dedicated update method when speed/jump buffs are active and the player is moving/jumping).

### File Structure
- `core/src/main/java/com/axehigh/platformer/PlayerConfig.java` (modified: add trail constants)
- `core/src/main/java/com/axehigh/platformer/ecs/systems/TrailSystem.java` (modified: add player trail support)
- `core/src/main/java/com/axehigh/platformer/ecs/systems/BuffSystem.java` (modified: trigger player trail spawning during speed/jump buffs)
- `resources/docs-ai/gameplay.md` & `resources/docs-ai/ashley-ecs-systems.md` (modified: document player potion trail feature)


# Delivery Steps

###   ✓ Step 1: Add player trail configuration and TrailSystem support
Player trail configuration constants are available in PlayerConfig.
- Add `PLAYER_TRAIL_INTERVAL` and `PLAYER_TRAIL_LIFETIME` constants to `PlayerConfig`.
- Extend `TrailSystem` with a static `spawnPlayerTrail` method that correctly captures the player's transform, texture, and collision-anchored drawing coordinates.

###   ✓ Step 2: Implement player trail spawning logic for speed and jump buffs
Player trail ghosts spawn automatically during active speed or jump potion buffs.
- Implement trail spawning logic triggered when the player's `BuffComponent` has active speed or jump buffs.
- Ensure trails spawn at regular intervals while moving or jumping with speed/jump buffs active, creating a smooth afterimage trail.

###   ✓ Step 3: Update gameplay and ECS documentation
Gameplay and ECS documentation are updated to reflect the new player potion trail feature.
- Update `resources/docs-ai/gameplay.md` to document the player trailing shadow effect for speed and jump potions.
- Update `resources/docs-ai/ashley-ecs-systems.md` / `ashley-ecs-components.md` to document the trail generation mechanism.
