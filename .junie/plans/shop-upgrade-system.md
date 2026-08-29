---
sessionId: session-260722-143506-1ha9
---

# Requirements

### Overview & Goals
Introduce a stat-threshold combat balance model (Grunt/Elite/Knight enemy tiers) and a Shop/Upgrade backend that lets the player spend gold (the existing `PlayerComponent.coins` field) on three upgrades that permanently change combat stats for the rest of the play session. The actual shop map/UI/vendor NPC is out of scope for this change — only the data model and transaction logic are built now, ready for a future vendor entity to call into.

### Scope
**In scope:**
- Make sword damage a per-player dynamic stat (`PlayerComponent.swordDamage`, default `5`) instead of `MeleeAttackSystem`'s hardcoded constant.
- Add upgrade-tracking fields to `PlayerComponent`: `swordDamage`, `sharpEdgePurchased`, `daggerBandolierPurchased`, `ironHeartCount`.
- Add a new **Knight** enemy tier (`enemyType="knight"`, 15 HP) alongside the existing Grunt (Flyer, 5 HP) and Elite (Walker/Shooter, 10 HP) tiers — existing HP values already match the Grunt/Elite tiers, so only Knight is newly added.
- Set Sharp Edge's damage value to **8** (not the literally-specified 7) so the hit-count math holds exactly: Grunt 1 hit, Elite 2 hits, Knight 3→2 hits after upgrade — confirmed with the user as a deliberate correction.
- Build a plain (non-ECS) `ShopManager`/`ShopItem` catalog + `purchase(...)` transaction method covering all 3 items, with gold validation.
- Update `resources/docs-ai/enemies.md` and `resources/docs-ai/gameplay.md` per the project's documentation-sync rules.

**Out of scope:**
- Shop map, vendor NPC entity, interaction UI/prompt, and any Tiled object-layer wiring — postponed to a future change.
- Save/persistence across app restarts — upgrades live only in `PlayerComponent` for the current session, matching how coins/health/items already work.
- Renaming `coins` to `gold` — the existing field already serves as the currency; no rename needed.

### Confirmed Design Decisions (from clarification)
- Grunt = Flyer (5 HP), Elite = Walker + Shooter (10 HP, unchanged), Knight = new type (15 HP).
- `maxAmmo` base stays at `30` (not the spec's literal `5`); Dagger Bandolier doubles it to `60`.
- Sharp Edge's actual damage bonus is **8**, not 7, chosen so hit-count claims ("3 hits → 2 hits" for Knight) are mathematically exact.
- Shop logic is a plain Java manager class, not an Ashley `Component`/`System` — no map/entity integration yet.
- Upgrades are in-memory only, no persistence layer added.

### Functional Requirements
1. Player's melee damage is read from `PlayerComponent.swordDamage` everywhere combat damage is applied (currently only `MeleeAttackSystem`).
2. Buying "Sharp Edge" sets `player.swordDamage = 8` and `player.sharpEdgePurchased = true`; buying it twice is a no-op (already-purchased items aren't sellable again).
3. Buying "Dagger Bandolier" sets `player.maxItems = 60` and `player.daggerBandolierPurchased = true` (one-time).
4. Buying "Iron Heart" increments both `player.maxHealth` and `player.health` by `1` and increments `player.ironHeartCount` (repeatable — no cap specified, so it can be bought multiple times).
5. Every purchase first checks `player.coins >= item.cost`; if insufficient, the transaction is rejected with no state change; otherwise `player.coins -= item.cost` then the stat effect is applied immediately, atomically.
6. Grunt enemies (5 HP) die in 1 hit from both base (5) and upgraded (8) sword damage.
7. Elite enemies (10 HP) die in 2 hits from both base and upgraded sword damage.
8. Knight enemies (15 HP) die in 3 hits with base sword (5×3=15), 2 hits with Sharp Edge (8×2=16≥15).

# Technical Design

### Current Implementation
- `PlayerComponent` (`core/.../ecs/components/PlayerComponent.java`): already has `coins` (currency), `health`/`maxHealth`, `items`/`maxAmmo` (dagger ammo, default `30`). No sword-damage or upgrade-flag fields yet.
- `MeleeAttackSystem` (`core/.../ecs/systems/MeleeAttackSystem.java`): hardcodes `private static final float MELEE_DAMAGE = 5f;` at line 37, passed straight into `EnemyDamageResolver.applyHit(enemy, enemyMovement, MELEE_DAMAGE, ...)` at line 85.
- `EnemyComponent`/`EntityFactory.createEnemy` (`core/.../map/EntityFactory.java` lines 227-272): `enemyType` switch (`"flyer"`, `"shooter"`, default `"walker"`) sets sprite + only overrides `health = 5f` for `"flyer"`; walker/shooter default to `EnemyComponent.health = 10f`.
- `resources/docs-ai/enemies.md`: single source of truth for the enemy catalog table (§2) — currently lists Patrol/Flying/Shooting types only.
- No shop/vendor code exists anywhere in the project; no save/persistence layer exists (`LevelManager` keeps `PlayerComponent` alive in-memory across map swaps only).

### Key Decisions
1. **Dynamic damage source:** `swordDamage` lives on `PlayerComponent` (not a new component) since it's a player-wide stat, mirroring how `maxAmmo`/`maxHealth` already live there. `MeleeAttackSystem` reads `player.swordDamage` instead of its private constant.
2. **Enemy tiers:** No renaming of Java enum/type strings needed — Grunt/Elite/Knight are documentation-level tier names mapped onto the existing `enemyType` values (`flyer`→Grunt, `walker`/`shooter`→Elite) plus one new `enemyType="knight"` value (15 HP, reuses the walker sprite/behavior, no new component).
3. **Sharp Edge damage value:** set to `8` (confirmed override) rather than the issue's literal `7`, so Knight's "3 hits → 2 hits" claim is exactly true (`8*2=16 ≥ 15` but `8*1=8 < 15`).
4. **Shop as plain class:** `ShopManager` is a standalone Java class (not `Component`/`System`) holding a fixed `List<ShopItem>` catalog and a `purchase` method that mutates `PlayerComponent` directly — no Ashley wiring needed since there's no vendor entity yet. This keeps the change additive and easy to wire into a future `ShopSystem`/UI later.
5. **Repeatable vs one-time items:** Sharp Edge and Dagger Bandolier are one-time (idempotent — buying twice does nothing further); Iron Heart is repeatable per its "+1 capacity" phrasing.

### Proposed Changes
- **`PlayerComponent.java`**: add `public int swordDamage = 5;`, `public boolean sharpEdgePurchased = false;`, `public boolean daggerBandolierPurchased = false;`, `public int ironHeartCount = 0;`. Keep `maxAmmo` default at `30` (unchanged).
- **`MeleeAttackSystem.java`**: remove `MELEE_DAMAGE` constant; replace its one usage with `player.swordDamage` read from the already-fetched `PlayerComponent player` in `processEntity`.
- **`EntityFactory.java`**: add a `"knight"` branch to the `enemyType` switch in `createEnemy` — same sprite/texture path as the default walker (`gfx/enemy.png`) unless a dedicated sprite is desired later, sets `enemyComponent.health = 15f`.
- **New `shop/ShopItem.java`**: a small immutable data holder — `String name`, `int cost`, `Consumer<PlayerComponent> effect` (or an equivalent apply method) — describing one purchasable upgrade.
- **New `shop/ShopManager.java`**: holds the fixed 3-item catalog (Sharp Edge/100, Dagger Bandolier/75, Iron Heart/150) and exposes `boolean purchase(PlayerComponent player, ShopItem item)`: returns `false` (no state change) if `player.coins < item.cost` or the item is already purchased (for one-time items); otherwise deducts `item.cost` from `player.coins`, applies `item.effect`, marks the purchased flag, and returns `true`.
- **Docs:** update `resources/docs-ai/enemies.md` §2 catalog table to add the Knight row and annotate Grunt/Elite tier names next to Flyer/Walker+Shooter; update `resources/docs-ai/gameplay.md` to document the new dynamic sword-damage stat and the shop/upgrade mechanic under a new subsection.

### Data Models / Contracts
```java
// shop/ShopItem.java
public class ShopItem {
    public final String name;
    public final int cost;
    public final Consumer<PlayerComponent> effect; // mutates player stats
    public final boolean repeatable; // false = one-time purchase
}

// shop/ShopManager.java
public class ShopManager {
    private final List<ShopItem> catalog; // Sharp Edge, Dagger Bandolier, Iron Heart
    public List<ShopItem> getCatalog();
    public boolean purchase(PlayerComponent player, ShopItem item);
}
```

### File Structure
- `core/src/main/java/com/axehigh/platformer/ecs/components/PlayerComponent.java` — modified (new fields).
- `core/src/main/java/com/axehigh/platformer/ecs/systems/MeleeAttackSystem.java` — modified (dynamic damage).
- `core/src/main/java/com/axehigh/platformer/map/EntityFactory.java` — modified (Knight enemy branch).
- `core/src/main/java/com/axehigh/platformer/shop/ShopItem.java` — new.
- `core/src/main/java/com/axehigh/platformer/shop/ShopManager.java` — new.
- `resources/docs-ai/enemies.md` — modified (catalog table, tier names).
- `resources/docs-ai/gameplay.md` — modified (dynamic damage + shop mechanic section).

### Risks
- Sharp Edge's damage value (`8`) deliberately diverges from the issue's literal text ("5 to 7") — documented clearly in code comments and docs so it isn't mistaken for a bug later.
- Since there's no vendor entity yet, `ShopManager.purchase` cannot be exercised end-to-end in-game; validation relies on unit-style checks called directly against a `PlayerComponent` instance.

# Testing

### Validation Approach
Since there's no shop UI/vendor entity yet, validation is done by directly exercising `ShopManager`/`MeleeAttackSystem` logic against constructed `PlayerComponent`/`EnemyComponent` instances (unit-level checks), plus a manual project build to confirm compilation.

### Key Scenarios
- Purchasing each of the 3 items with sufficient gold deducts the correct cost and applies the correct stat change (`swordDamage`→8, `maxAmmo`→60, `maxHealth`+1).
- Purchasing with insufficient gold leaves `coins` and all stats unchanged, returns `false`.
- Re-purchasing a one-time item (Sharp Edge, Dagger Bandolier) is a no-op the second time; re-purchasing Iron Heart stacks correctly.
- `MeleeAttackSystem` deals `player.swordDamage` (not a fixed `5`) to a hit enemy — verified by changing `swordDamage` and confirming a Grunt (5 HP)/Elite (10 HP)/Knight (15 HP) enemy dies in the expected number of hits before/after Sharp Edge.

### Edge Cases
- Buying with `coins` exactly equal to `cost` succeeds (boundary check, not just `>`).
- Iron Heart purchase when `health == maxHealth` (full health) still raises the cap correctly.
- Knight enemy at exactly `15` HP with `8` damage: first hit leaves `7` HP (alive), second hit kills it — confirms the "2 hits" claim precisely.

# Delivery Steps

### ✓ Step 1: Add player upgrade stats and dynamic sword damage
PlayerComponent tracks upgrade state and melee damage is read dynamically instead of hardcoded.

- Add `swordDamage` (default 5), `sharpEdgePurchased`, `daggerBandolierPurchased`, `ironHeartCount` fields to `PlayerComponent`.
- Remove the `MELEE_DAMAGE` constant from `MeleeAttackSystem` and replace its usage with `player.swordDamage`.
- Verify `EnemyDamageResolver.applyHit` still receives a correctly-typed damage value from the new dynamic source.

### ✓ Step 2: Add Knight enemy tier and rebalance documentation
A new Knight enemy type (15 HP) exists alongside the existing Grunt/Elite-tier enemies, with correct hit-count math for both base and upgraded sword damage.

- Add an `enemyType="knight"` branch to `EntityFactory.createEnemy`, setting `EnemyComponent.health = 15f` and reusing the default walker sprite/behavior.
- Confirm existing Flyer (5 HP) and Walker/Shooter (10 HP) values already satisfy the Grunt/Elite tiers, requiring no numeric change there.
- Update `resources/docs-ai/enemies.md` §2 catalog table with the new Knight row and Grunt/Elite/Knight tier annotations next to the existing type names.

### ✓ Step 3: Implement ShopItem/ShopManager transaction logic
A standalone shop backend can validate and apply all three upgrade purchases against a PlayerComponent.

- Create `shop/ShopItem.java` holding `name`, `cost`, an effect callback, and a `repeatable` flag.
- Create `shop/ShopManager.java` with the fixed 3-item catalog (Sharp Edge/100 -> swordDamage=8, Dagger Bandolier/75 -> maxItems=60, Iron Heart/150 -> maxHealth+1/health+1, repeatable).
- Implement `purchase(PlayerComponent, ShopItem)`: validates gold, rejects already-purchased one-time items, deducts cost, applies effect, updates purchased flags, returns success/failure.

### ✓ Step 4: Update gameplay documentation for the new mechanics
Gameplay documentation reflects the new dynamic damage stat and shop/upgrade system as the single source of truth.

- Add a subsection to `resources/docs-ai/gameplay.md` describing `PlayerComponent.swordDamage` and how `MeleeAttackSystem` now reads it dynamically.
- Document the Shop/Upgrade mechanic: the three items, their costs, effects, and the one-time-vs-repeatable purchase rule.
- Cross-reference `resources/docs-ai/enemies.md`'s Grunt/Elite/Knight tiers so damage-balancing intent stays discoverable from either doc.
