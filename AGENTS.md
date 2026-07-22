# Agent Instructions: 2D Pixel-Art Platformer (libGDX)

You are an expert libGDX game developer. 
Your task is to build a retro 2D side-scrolling platformer 
utilizing the Ashley ECS framework and Tiled maps.

---

## 1. Core Architecture & Tech Stack
*   **Framework:** libGDX (Java) with **Ashley ECS (Entity Component System)**.
*   **Design Pattern:** ECS. Decouple data (Components) completely from logic (Systems).
*   **Physics/Movement:** Custom AABB grid-based collision handling inside a dedicated Ashley `MovementSystem`. 
*   **Resolution & Scaling:** Target a fixed virtual resolution (e.g., 480x270 or 320x180) to maintain a crisp pixel-art style, scaled up via `FitViewport`. Set texture filtering to `TextureFilter.Nearest`.

---

## 2. Ashley ECS Component & System Breakdown
See @resources/docs-ai/ashley-ecs.md for the full, AI-usable overview of every ECS `Component` and `System`, their fields/family/priority, and how they're wired together in `GameScreen`.

---

## 3. Map & Level Integration (Tiled)
*   **Format:** Parse `.tmx` maps using libGDX's built-in **Tiled map support** (`TmxMapLoader`).
*   **Rendering:** Use `OrthogonalTiledMapRenderer` integrated into the `RenderSystem` or a separate `TiledMapRenderSystem` to ensure correct background/foreground layering relative to entities.
*   **Layer Structures:**
    *   *Background Layers:* Dark blue brick walls, pillars, windows, and decorative chains/shields.
    *   *Collision Layer:* Object layer or dedicated tile layer containing solid brick walls, floors, and platforms. Read this layer at startup to build static collision boundaries.
    *   *Object Layers:* Spawners for chests, coins, lights/torches, start gates, exit doors, and enemies. Parse these to instantiate Ashley Entities dynamically. See @resources/docs-ai/enemies.md for the enemy catalog (current types, stats, spawning, and how to add new ones).

---

## 4. Visual Style & UI Layout
*   **Art Style:** 8-bit/16-bit medieval dungeon theme. Heavy use of tiled stone walls, brick backgrounds, torches, and wooden platforms.
*   **HUD (Top Overlay):**
    *   **Top Left:** Player avatar preview with a health counter using heart icons.
    *   **Top Center:** Coin counter (`Coin Icon x 0000`).
    *   **Top Right:** Level-specific item tracker (e.g., `Sword Icon x 02/30`) and Pause button.
*   **On-Screen Touch Controls (Mobile Overlay via Scene2D.ui):**
    *   **Bottom Left:** Left/Right D-pad arrows.
    *   **Contextual Action Button:** Up arrow (used near interactable doors/exits).
    *   **Bottom Right:** Action buttons labeled **A**, **B**, and **Y** (Jump, Attack, Special).

---

## 5. Coding Conventions & Best Practices
*   **Asset Management:** Use `AssetManager` to load all `TextureAtlas`, `TiledMap`, and audio assets asynchronously.
*   **Memory Management:** Always explicitly `dispose()` of Textures, SpriteBatches, and TiledMaps when changing screens or shutting down to prevent memory leaks. Pool frequent ECS components if garbage collection spikes occur.
*   **Frame-Rate Independence:** Always use `Gdx.graphics.getDeltaTime()` inside your Ashley systems' `update` methods.
*   **Gameplay Documentation Sync:** Any change to gameplay mechanics (movement, combat, traversal abilities, enemy behavior, etc.) MUST be flected with a corresponding update to `resources/docs-ai/gameplay.md`, keeping it as the single source of truth for gameplay design.
*   **ECS Documentation Sync:** Any time an Ashley ECS `Component` or `System` is added, removed, renamed, or has its fields/family/priority/behavior changed, MUST be reflected with a corresponding update to `resources/docs-ai/ashley-ecs.md`, keeping it as the single source of truth for the ECS component/system breakdown.
*   **Enemy Documentation Sync:** Any time an enemy type is added, removed, renamed, or has its stats/sprite/behavior changed, MUST be reflected with a corresponding update to `resources/docs-ai/enemies.md`, keeping it as the single source of truth for the enemy catalog.
*   **Timer Convention:** For any new cooldown, countdown, attack-window, or grace-period effect, use the reusable `com.axehigh.platformer.util.Timer` helper (`start()`/`update()`/`isActive()`/`isDone()`) instead of hand-rolling a raw-`float` decrement, matching the existing usage in `PlayerComponent`/`ChestComponent`.
*   **Grill Before Building:** For any new feature request (new mechanic, enemy type, system, visual behavior, etc.), before implementing, "grill" the requester with focused clarifying/challenging questions about the ambiguous design decisions (e.g. exact motion/behavior shape, tunable defaults, edge cases, how it interacts with existing systems) rather than silently guessing. Only proceed with implementation once those decisions are confirmed.

## 6. Flip-Screen (Room-Based) Camera System
*   **Virtual Screen Dimensions:** Define explicit constants for `VIRTUAL_WIDTH` and `VIRTUAL_HEIGHT` (e.g., 480x270).
*   **Camera Tracking:** Do NOT track the player smoothly. Instead, implement a `CameraSystem` that calculates the current room index based on the player's position:
    *   `int roomX = (int)(player.x / VIRTUAL_WIDTH);`
    *   `int roomY = (int)(player.y / VIRTUAL_HEIGHT);`
*   **Camera Position:** Set the camera's center position precisely to:
    *   `camera.position.set((roomX * VIRTUAL_WIDTH) + (VIRTUAL_WIDTH / 2f), (roomY * VIRTUAL_HEIGHT) + (VIRTUAL_HEIGHT / 2f), 0);`
*   **Transitions (Optional):** When `roomX` or `roomY` changes, freeze player input/physics for a split second and linearly interpolate (`lerp`) the camera to the new room center to create a smooth sliding screen transition.

## Debugging
Turn on and off debugging with SHIFT+D.
Use ShapeRenderer for debugging (see `DebugRenderSystem`, which outlines every live `CollisionComponent` AABB plus the static map collision rects).
