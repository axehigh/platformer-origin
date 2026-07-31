# Agent Instructions: 2D Pixel-Art Platformer (libGDX)

You are an expert libGDX game developer. 
Your task is to build a retro 2D side-scrolling platformer 
utilizing the Ashley ECS framework and Tiled maps.

---

## 1. Core Architecture & Tech Stack
*   **Framework:** libGDX (Java) with **Ashley ECS (Entity Component System)**.
*   **Design Pattern:** ECS. Decouple data (Components) completely from logic (Systems).
*   **Physics/Movement:** Custom AABB grid-based collision handling inside a dedicated Ashley `MovementSystem`. 
*   **Resolution & Scaling:** The game camera targets a fixed virtual resolution (`VIRTUAL_WIDTH` x `VIRTUAL_HEIGHT`, 480x272), scaled by the tile-size factor and rendered through a `FitViewport` for crisp pixel-art. Set texture filtering to `TextureFilter.Nearest`.
*   **UI Resolution:** All Scene2D UI — menus, HUD, touch controls, and dialogs — renders at `SCREEN_WIDTH` x `SCREEN_HEIGHT` (1980x1080) through an `ExtendViewport`. Keep UI off the small game-camera resolution; menu-style assets (e.g. the 1102x755 `table` window panel) must be scaled uniformly, never squished, so they don't distort.

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
*   **Dialogs:** Pause and game-over dialogs render on the shared 1980x1080 UI stage (never the game-HUD stage) and are sized to a uniform scale of the `table` panel so the background is never distorted.

---

## 5. Coding Conventions & Best Practices
*   **Import** Never Use qualified imports, unless you have to.
*   **Asset Management:** Use `AssetManager` to load all `TextureAtlas`, `TiledMap`, and audio assets asynchronously.
*   **Memory Management:** Always explicitly `dispose()` of Textures, SpriteBatches, and TiledMaps when changing screens or shutting down to prevent memory leaks. Pool frequent ECS components if garbage collection spikes occur.
*   **Frame-Rate Independence:** Always use `Gdx.graphics.getDeltaTime()` inside your Ashley systems' `update` methods.
*   **Gameplay Documentation Sync:** Any change to gameplay mechanics (movement, combat, traversal abilities, enemy behavior, etc.) MUST be flected with a corresponding update to `resources/docs-ai/gameplay.md`, keeping it as the single source of truth for gameplay design.
*   **ECS Documentation Sync:** Any time an Ashley ECS `Component` or `System` is added, removed, renamed, or has its fields/family/priority/behavior changed, MUST be reflected with a corresponding update to `resources/docs-ai/ashley-ecs.md`, keeping it as the single source of truth for the ECS component/system breakdown.
*   **Enemy Documentation Sync:** Any time an enemy type is added, removed, renamed, or has its stats/sprite/behavior changed, MUST be reflected with a corresponding update to `resources/docs-ai/enemies.md`, keeping it as the single source of truth for the enemy catalog.
*   **Timer Convention:** For any new cooldown, countdown, attack-window, or grace-period effect, use the reusable `com.axehigh.platformer.util.Timer` helper (`start()`/`update()`/`isActive()`/`isDone()`) instead of hand-rolling a raw-`float` decrement, matching the existing usage in `PlayerComponent`/`ChestComponent`.
*   **Enum** Use enum if you can.
*   **Grill Before Building:** For any new feature request (new mechanic, enemy type, system, visual behavior, etc.), before implementing, "grill" the requester with focused clarifying/challenging questions about the ambiguous design decisions (e.g. exact motion/behavior shape, tunable defaults, edge cases, how it interacts with existing systems) rather than silently guessing. Only proceed with implementation once those decisions are confirmed.

## 6. Hybrid Flip-Screen / Dead-Zone Scroll Camera System
*   **Virtual Screen Dimensions:** Define explicit constants for `VIRTUAL_WIDTH` and `VIRTUAL_HEIGHT` (e.g., 480x270), scaled by the tile-size factor for the game viewport.
*   **Room Definitions:** Rooms come from the map's `Rooms` object layer (`MapLoader.getRooms()` → `Array<Room>`). A map with no `Rooms` layer is treated as one room covering the whole map. Rooms do NOT have to match the viewport size.
*   **Camera Modes (per axis):** `CameraSystem` frames the active room per axis. If the room is no bigger than the viewport on an axis — or is forced via the per-room `camera="flip"` Tiled property — the camera locks to the room's center (static flip-screen framing) and snaps instantly when the player enters the room. If the room is bigger than the viewport on an axis — or forced via `camera="scroll"` — the camera uses a dead zone: it stays still while the player roams more than `GameConstants.CAMERA_SCROLL_MARGIN` from a screen edge, and only scrolls once the player crosses that margin, clamped to the room's bounds.
*   **No Smooth Tracking:** Do NOT smoothly follow the player. Camera movement is either static (flip rooms) or dead-zone-triggered (scroll rooms); room-to-room transitions are instant snaps (no lerp, no input freeze).
*   **Start Framing:** On level start/swap, `CameraSystem.snapToRoom(...)` frames the starting room — never the player: flip rooms center, scroll rooms clamp the player-start into view.

## Debugging
Turn on and off collision debugging with SHIFT+D (desktop) or the "Collision Debug" button in the in-game Pause dialog (all platforms; the toggle persists across level reloads within a session).
Use ShapeRenderer for debugging (see `DebugRenderSystem`, which outlines every live `CollisionComponent` AABB plus the static map collision rects).
The Pause dialog also exposes a "Touch Debug" button that logs every touch to logcat under the `TouchDebug` tag (surface/viewport sizes, raw vs stage-mapped coords, and the hit actor), for diagnosing touch-input misalignment.
