# Agent Instructions: 2D Pixel-Art Platformer (libGDX)

You are an expert libGDX game developer. 
Your task is to build a retro 2D side-scrolling platformer based on the reference images provided, utilizing the Ashley ECS framework and Tiled maps.

---

## 1. Core Architecture & Tech Stack
*   **Framework:** libGDX (Java) with **Ashley ECS (Entity Component System)**.
*   **Design Pattern:** ECS. Decouple data (Components) completely from logic (Systems).
*   **Physics/Movement:** Custom AABB grid-based collision handling inside a dedicated Ashley `MovementSystem`. 
*   **Resolution & Scaling:** Target a fixed virtual resolution (e.g., 480x270 or 320x180) to maintain a crisp pixel-art style, scaled up via `FitViewport`. Set texture filtering to `TextureFilter.Nearest`.

---

## 2. Ashley ECS Component & System Breakdown

### Core Components
*   `TransformComponent`: Position (x, y, z for layering), scale, and rotation.
*   `TextureComponent`: Holds the `TextureRegion` to render.
*   `AnimationComponent`: Holds animation states (Idle, Running, Jumping, Attacking).
*   `MovementComponent`: Velocity, acceleration, and maximum speed limits.
*   `CollisionComponent`: Bounding box dimensions for AABB environment checks.
*   `PlayerComponent`: Flag component storing player-specific data (health, coins, states).

### Core Systems
*   `PlayerInputSystem`: Processes keyboard or mobile UI inputs and translates them into velocity changes on the `MovementComponent`.
*   `MovementSystem`: Updates positions based on velocity and handles tilemap collisions.
*   `AnimationSystem`: Updates texture regions based on current state timers.
*   `RenderSystem`: Sorted by Z-index to draw entities via `SpriteBatch`.

---

## 3. Map & Level Integration (Tiled)
*   **Format:** Parse `.tmx` maps using libGDX's built-in **Tiled map support** (`TmxMapLoader`).
*   **Rendering:** Use `OrthogonalTiledMapRenderer` integrated into the `RenderSystem` or a separate `TiledMapRenderSystem` to ensure correct background/foreground layering relative to entities.
*   **Layer Structures:**
    *   *Background Layers:* Dark blue brick walls, pillars, windows, and decorative chains/shields.
    *   *Collision Layer:* Object layer or dedicated tile layer containing solid brick walls, floors, and platforms. Read this layer at startup to build static collision boundaries.
    *   *Object Layers:* Spawners for chests, coins, lights/torches, start gates, and exit doors. Parse these to instantiate Ashley Entities dynamically.

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
*   **Gameplay Documentation Sync:** Any change to gameplay mechanics (movement, combat, traversal abilities, enemy behavior, etc.) MUST be reflected with a corresponding update to `resources/docs-ai/gameplay.md`, keeping it as the single source of truth for gameplay design.

### E. Flip-Screen (Room-Based) Camera System
*   **Virtual Screen Dimensions:** Define explicit constants for `VIRTUAL_WIDTH` and `VIRTUAL_HEIGHT` (e.g., 480x270).
*   **Camera Tracking:** Do NOT track the player smoothly. Instead, implement a `CameraSystem` that calculates the current room index based on the player's position:
    *   `int roomX = (int)(player.x / VIRTUAL_WIDTH);`
    *   `int roomY = (int)(player.y / VIRTUAL_HEIGHT);`
*   **Camera Position:** Set the camera's center position precisely to:
    *   `camera.position.set((roomX * VIRTUAL_WIDTH) + (VIRTUAL_WIDTH / 2f), (roomY * VIRTUAL_HEIGHT) + (VIRTUAL_HEIGHT / 2f), 0);`
*   **Transitions (Optional):** When `roomX` or `roomY` changes, freeze player input/physics for a split second and linearly interpolate (`lerp`) the camera to the new room center to create a smooth sliding screen transition.
