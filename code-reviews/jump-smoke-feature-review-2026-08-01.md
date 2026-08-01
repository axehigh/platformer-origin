# Jump smoke feature review

No CRITICAL findings.

## WARNING — Delayed particle effects start one frame late
- **Why it matters:** correctness / visual timing
- **Where:** `core/src/main/java/com/axehigh/platformer/ecs/systems/ParticleSystem.java:41-47`
- **Evidence:** When `particle.delay` is still positive, the system subtracts `deltaTime` and returns immediately. If the remaining delay is smaller than the current frame delta, the effect still waits until the *next* frame to call `start()`, adding an extra frame of latency to delayed explosions/smoke.
- **Fix:** After subtracting `deltaTime`, start the effect as soon as `delay <= 0` instead of unconditionally returning; ideally consume the overshoot on the first update.

## WARNING — Static particle templates outlive the screen-scoped AssetManager
- **Why it matters:** resource lifetime / stale references
- **Where:** `core/src/main/java/com/axehigh/platformer/particles/ParticleHelper.java:18-37,218-220`; `core/src/main/java/com/axehigh/platformer/screens/GameScreen.java:495-499`
- **Evidence:** `ParticleHelper.templates` is static and keeps `ParticleEffect` templates fetched from `GameScreen`'s private `AssetManager`, but `GameScreen.dispose()` disposes the manager without ever calling `ParticleHelper.dispose()`. That leaves the helper holding disposed template instances until some later `load()` overwrites them.
- **Fix:** Call `ParticleHelper.dispose()` during `GameScreen.dispose()` (before or alongside `assetManager.dispose()`), or remove the static registry in favor of a screen-owned particle service.

## WARNING — The new headless tests do not exercise the real runtime particle path
- **Why it matters:** test coverage / false positives
- **Where:** `core/src/main/java/com/axehigh/platformer/particles/ParticleHelper.java:56-66`; `core/src/test/java/com/axehigh/platformer/ecs/systems/PlayerJumpSmokeTest.java:68-88`
- **Evidence:** In headless mode `spawnParticle()` creates a dummy entity with only `ParticleComponent`; production creates `ParticleComponent + TransformComponent + ParticleEffect`. The test only counts entities carrying `ParticleComponent`, so it cannot catch broken spawn coordinates, family mismatches with `ParticleSystem`, or completion/removal regressions.
- **Fix:** Make the headless branch mirror the production entity shape (at least add `TransformComponent` with the spawn coordinates), and/or add a GL-backed integration test that runs the real particle lifecycle.

## WARNING — Every jump smoke puff allocates a fresh ParticleEffect clone
- **Why it matters:** performance / GC churn on a frequently used effect
- **Where:** `core/src/main/java/com/axehigh/platformer/particles/ParticleHelper.java:82-84`
- **Evidence:** Each spawn does `new ParticleEffect(template)` and scales it. Jump smoke is tied to a common player action, so repeated jumping will steadily allocate emitter state instead of reusing it, despite the rest of the ECS using `PooledEngine`.
- **Fix:** Keep a `ParticleEffectPool` per template and store/free pooled effects when `ParticleSystem` sees completion.

## NIT — The test mutates global Gdx.input without restoring the previous value
- **Why it matters:** test isolation
- **Where:** `core/src/test/java/com/axehigh/platformer/ecs/systems/PlayerJumpSmokeTest.java:29-40`
- **Evidence:** `setUp()` overwrites `Gdx.input`, but `tearDown()` always sets it to `null` instead of restoring the original value. That is harmless in this class, but it can leak global state into other tests when the suite grows.
- **Fix:** Save the previous `Gdx.input` in `setUp()` and restore that exact instance in `tearDown()`.

## Checked and OK
- `core/src/main/java/com/axehigh/platformer/ecs/systems/ParticleSystem.java:54-56` — removing the entity from inside `IteratingSystem.processEntity()` is fine in Ashley; structural changes are deferred until the engine finishes updating.
- `core/src/main/java/com/axehigh/platformer/ecs/systems/ParticleSystem.java:27-30,50-52` — particle `update()` + `draw()` happens inside an active `SpriteBatch`, which matches libGDX's particle API contract.
- `core/src/main/java/com/axehigh/platformer/ecs/components/ParticleComponent.java:15-24` — not calling `dispose()` on cloned effects is okay here because the clone does not own textures; the `AssetManager` owns the underlying particle resources.
- `core/src/main/java/com/axehigh/platformer/ecs/systems/PlayerInputSystem.java:145-147,184-187` — the smoke spawn uses the collision box's feet center, which is consistent with this codebase's physics-space positioning. The `grounded` check is previous-frame state because input runs before movement, but that matches the existing jump semantics rather than introducing a new ECS safety issue.
