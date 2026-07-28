---
sessionId: session-260728-201613-skqb
---

# Requirements

### Overview & Goals
Transition the game's graphics from individual PNG textures to a centralized `TextureAtlas` (`origin-game.atlas`), specifically focusing on enemies. The player character will continue to use its dedicated `knight2.atlas`.

### Scope
- **In Scope**:
    - Loading `origin-game.atlas` via `AssetManager`.
    - Replacing `enemy.png`, `enemy_flyer.png`, and `enemy_shooter.png` with regions from the atlas.
    - Implementing animations for the new enemy types (`goblin`, `mosquito`, `spider`).
    - Updating `EntityFactory` and `AnimationSystem` to support these changes.
- **Out of Scope**:
    - Moving items like `coin`, `chest`, `torch`, and `exit_gate` to the atlas (they are currently missing from `origin-game.atlas`).
    - Modifying the hero's graphics or atlas.
    - Changing map files (the code will handle mapping existing types to new graphics).

# Technical Design

### Current Implementation
Currently, enemies are loaded as individual static textures (`enemy.png`, `enemy_flyer.png`, `enemy_shooter.png`) and assigned to a `TextureComponent`. They do not have an `AnimationComponent` or multi-frame animations.

### Key Decisions
- **Atlas Usage**: Use `origin-game.atlas` for all enemy-related graphics.
- **Enemy Mapping**:
    - Generic/`walker` -> `goblin`
    - `flyer` -> `mosquito`
    - `shooter` -> `spider`
- **Animation Support**: Enemies will now use `AnimationComponent` to play idle, walk, attack, hurt, and death animations where available in the atlas.
- **Sprite Flipping**: Sprite flipping will be driven by `EnemyComponent.direction` within the `AnimationSystem`.

### Proposed Changes

#### GameAssetRegistry.java
- Register `gfx/origin-game.atlas` for loading.
- Remove loads for `enemy.png`, `enemy_flyer.png`, and `enemy_shooter.png`.

#### EntityFactory.java
- Update `createEnemy` to:
    - Retrieve `origin-game.atlas` from `AssetManager`.
    - Map `enemyType` to atlas region prefixes (`goblin`, `mosquito`, `spider`).
    - Create `Animation` objects for `IDLE`, `WALKING`, `ATTACKING`, `HURT`, and `DEATH`.
    - Add `AnimationComponent` to the enemy entity.
    - Set the initial `TextureComponent.region` to the first frame of the idle animation.

#### AnimationSystem.java
- Update `processEntity` to handle `EnemyComponent`:
    - Resolve the current state (Idle/Walking/Hurt).
    - Flip the horizontal scale based on `enemy.direction`.
- Implement `resolveEnemyState(EnemyComponent enemy, MovementComponent movement)`:
    - Return `HURT` if `enemy.hitStun.isActive()`.
    - Return `WALKING` if `Math.abs(movement.velocity.x) > 0.01f`.
    - Otherwise, return `IDLE`.

### Data Models / Contracts

#### Enemy Mapping Table
 Internal Type | Atlas Prefix | Region Names Example |
---|---|---|
 `walker` (default) | `goblin` | `goblin_idle1`, `goblin_walk1` |
 `flyer` | `mosquito` | `mosquito_idle1`, `mosquito_flight1` |
 `shooter` | `spider` | `spider_idle1`, `spider_walk1` |

### Risks
- **Missing Regions**: If a requested region is missing from the atlas, `findRegions` will return an empty array, which might lead to crashes or invisible entities. I will ensure fallbacks or careful mapping.
- **Animation Timing**: Frame durations for enemy animations will be set to a default (e.g., 0.1s) which may need tuning for visual polish.

# Delivery Steps

### ✓ Step 1: Update GameAssetRegistry to load the new atlas and remove old enemy textures.
- Load `gfx/origin-game.atlas` using `assetManager.load()`.
- Remove redundant individual texture loads for `enemy.png`, `enemy_flyer.png`, and `enemy_shooter.png` from `GameAssetRegistry.loadAssets()`.

### ✓ Step 2: Update EntityFactory to use the atlas and animations for enemies.
- In `EntityFactory`, add a `TextureAtlas originAtlas` field and initialize it from `AssetManager`.
- Update `createEnemy` to use `originAtlas.findRegions()` to build animations for `goblin`, `mosquito`, and `spider` based on the requested mapping:
    - Default/`walker` -> `goblin`
    - `flyer` -> `mosquito`
    - `shooter` -> `spider`
- Attach `AnimationComponent` to enemies with `IDLE`, `WALKING`, `ATTACKING`, `HURT`, and `DEATH` states.
- Ensure `TextureComponent` is initialized with the first frame of the idle animation.

### * Step 3: Update AnimationSystem to support enemy animations and sprite flipping.
- Modify `AnimationSystem.processEntity()` to resolve animation states for entities with `EnemyComponent`.
- Implement `resolveEnemyState(EnemyComponent, MovementComponent)` to handle `HURT` (based on `hitStun.isActive()`) and `WALKING` (based on `velocity.x`).
- Add logic to flip `transform.scale.x` based on `enemy.direction` for enemies, similar to the existing player logic.