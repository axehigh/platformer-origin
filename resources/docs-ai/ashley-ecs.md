# Ashley ECS Reference

This document is the **single source of truth for the ECS layer** (`com.axehigh.platformer.ecs`): every `Component` and every `System` currently implemented, what data/behavior it owns, and how systems are wired together at runtime. It is written to be consumed by an AI agent before making any ECS change, so keep it terse, structured, and exhaustive rather than narrative.

> **Maintenance rule:** Any time a `Component` or `System` is added, removed, renamed, or has its fields/behavior/family/priority changed, the relevant sub-file(s) in this set MUST be updated in the same change. This covers the whole ECS doc set and is the ECS counterpart to the `gameplay.md` sync rule in `AGENTS.md`.

---

## 1. File map

This file is the **entry point** of the Ashley ECS documentation set. The ECS content itself lives in the three sub-files below; the `> **Maintenance rule:**` block above covers the whole set, so any ECS change must be reflected in every affected sub-file, not just the index:

- `ashley-ecs-components.md` — every ECS `Component` (fields, pooling, purpose) and the `Mappers` holder.
- `ashley-ecs-systems.md` — every ECS `System` (base class, `Family`, responsibility) and the System wiring & priority order (`ecs.GameSystems`).
- `ashley-ecs-utilities.md` — the non-`Component`/`System` classes the ECS relies on: `RoomState`, `Room`, `SecretRoom`, `SecretRoomRevealer`, `CrumblingTile`, `Timer`, `FeatureFlags`, `EnemyDamageResolver`, `PlayerDamageResolver`, `LevelManager`.

## 2. Adding/removing/changing an ECS element â€” checklist
When you add, remove, or edit a `Component` or `System`:
1. Update the relevant table row(s) in `ashley-ecs-components.md`/`ashley-ecs-systems.md` (fields, family, priority, responsibility).
2. If the change affects gameplay-visible mechanics (movement, combat, traversal, enemy behavior, pickups, etc.), also update `resources/docs-ai/gameplay.md` per the `AGENTS.md` "Gameplay Documentation Sync" rule â€” the two docs are complementary: this file describes the ECS *shape*, `gameplay.md` describes the *mechanics/logic* built on top of it.
3. If a new component needs cross-system lookups, add its `ComponentMapper` to `Mappers`.
4. If a new system is registered, add it to the `GameSystems` installer with an explicit priority constant and update the System wiring & priority order section in `ashley-ecs-systems.md`.
