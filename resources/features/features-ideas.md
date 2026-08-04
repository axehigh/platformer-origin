# Features & Fixes Ideas

Status legend: ✅ done · 🔶 in progress · 🧊 backlog · ⬜ not started · 🐛 bug · ❓ open question

## P0 — Bugs
- ✅ **Chest opening keeps entity**: chest no longer disappears; it stays and pops coins once (via `ChestComponent.coinsDropped`, `ChestSystem`).
- 🐛 **Resume → all sprites disappear**: window-focus resume drops entities from render. Root cause still unconfirmed (pause dialog vs window focus); needs repro + fix. Blocked on investigation.
- ✅ **Coins pop from chest as proper coins with velocity** (arc + scatter via `createPoppedCoinPickup`, `unitScale`-scaled).

## P1 — Juice & Readability
- ✅ **Chest-opening smoke/particle effect**: `MeleeAttackSystem` spawns smoke on chest open; sparks on enemy hit (`EnemyDamageResolver` spark overload).
- ✅ **Coin-pickup sparkle**: `PickupSystem` spawns SPARKS at picked-up coin center; sparkle auto-expires via `ParticleComponent.maxLifetime` (`COIN_SPARK_MAX_LIFETIME = 1.5s`) so it never lingers.
- ✅ **Landing-dust height gate**: landing puff only when the fall was > 1 tile (`MovementSystem.LANDING_DUST_MIN_FALL`, `16f * unitScale`), driven by new `PlayerComponent.inAir`/`maxAirHeight` airborne tracking; short hops/ledge rides settle silently. Shared `MovementSystem.onLanding(...)` helper used by ground and moving-platform landings alike.
- ✅ **Attack animation 1.5× faster**: `GameScreen` `ATTACKING` clip at `0.066s`/frame (was `0.1s`); tighter strike window + cooldown for free.
- ✅ **Jump-down squash & stretch**: new `SquashSystem` (landing squash, exponential decay; **jump stretch path removed** for feel, retained unused via `squashIsStretch`); triggered only on jump landings from the shared `MovementSystem.onLanding(...)` (ground + platforms); reset in `LevelManager`. **Disabled by default** (look not final): trigger gated on `FeatureFlags.isSquashEnabled()` (persisted pref, default `false`) — flip the pref to re-enable.
- ✅ **HUD text contrast**: new `ShadowLabel` widget (drop-shadow pass; no `BitmapFontData.setShadow` in this libGDX fork); heart icons tightened (28px / 6px pad).
- ✅ **Coin tile-center snap**: tile-based coins center on their tile cell in `EntityFactory`.
- ⬜ **Restart-run option** in game-over dialog when `triesRemaining <= 0` (continue-only currently).
- 🔶 **Resume-bug / input**: see P0.

## P2 — Code Quality
- 🔶 **java.util removal** (`core` Java 8 target): `ShopItem`/`ShopManager` done (libGDX `Array` + custom functional interfaces). `RenderSystem` (`Comparator` → libGDX sort) still pending.
- 🧊 **Collision granularity**: half-tiles / non-square collision design — ❓ how best (see `CollisionSystem`).
- 🧊 **Component field style**: public fields vs constants — ❓ decide convention (see `GameConstants`).

## Game Loop
- ⬜ **Lives/tries model**: 3 tries × 3 base lives; death resets to level start.
- ⬜ **Level-complete flow**: reaching exit → next level / level-select.

## Level & Tiled Map
- ❓ Collision layer modeling for non-square tiles.

## Terrain / Interactables
- ⬜ Keys and doors.
- ⬜ Levers that open doors.
- ⬜ Teleporters.
- ⬜ Traps.
- ⬜ Crumbling walls.

## UI Polish
- ⬜ Improve button visuals.

## Docs Sync (AGENTS.md requirement)
- ✅ `resources/docs-ai/ashley-ecs.md`: `SquashSystem`, `ParticleComponent` `lifeTimer`/`maxLifetime`, `ParticleSystem` lifetime-cap removal, new `PlayerComponent` airborne/squash fields, `ChestComponent.coinsDropped`, `MovingPlatformSystem` `unitScale`, landing-dust `onLanding` wording, priority list.
- ✅ `resources/docs-ai/gameplay.md`: squash, height-gated landing dust, coin sparkle max-lifetime, faster attack clip, moving-platform landing wording.
- ⬜ `resources/docs-ai/enemies.md`: if any enemy behavior changed.
