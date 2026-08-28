package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.Room;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Headless unit tests for {@code EnemyAttackSystem}: live centered-box player detection
 * ({@code attackRange*3} per side, {@code detectionHeight} tall), a front commit rectangle that
 * telegraphs a wind-up, then a short live strike window during which an enemy-sized hitbox in
 * front of the enemy can deal damage. Enemies only ever swing at a player they are CURRENTLY
 * detected (no awareness latch): a detected-but-out-of-reach player (beyond the front commit
 * rect) never triggers a swing, and a player elevated above the detection box is ignored even
 * though the range rect would overlap. Also covers hitbox direction vs. facing, the
 * {@code strike} window's liveness via {@code getActiveStrikeBounds()}, facing-required strikes,
 * cooldown/recovery gating, dead/stunned enemy skips, the room gate, and — via a production-mirror
 * engine — the post-strike recovery stand that must not misread its zeroed velocity as a wall block.
 *
 * <p>Timing note: the wind-up and strike timers are ticked with the SAME {@code processEntity}
 * delta, so a single {@code engine.update(windUpDuration + DT)} would tick the strike past its
 * whole window in one frame and never check damage. Tests that must observe the live window use a
 * three-step cadence: wind-up still ticking, then a short frame that makes the strike go live
 * (damage applies, {@code getActiveStrikeBounds()} non-null), then a final frame that completes it.
 */
public class EnemyAttackSystemTest extends SystemTestBase {

    /** Thin floor just below foot level satisfying the ledge probe; never overlaps the enemy box. */
    private static final Rectangle FLOOR = new Rectangle(-100f, -24f, 300f, 2f);

    private Engine engine;
    private EnemyAttackSystem system;

    @Before
    public void setUp() {
        system = new EnemyAttackSystem();
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        EnemyComponent enemyComponent = new EnemyComponent();
        EnemyAttackComponent attackComponent = new EnemyAttackComponent();
        MovementComponent movement = movement();
        Entity entity = entity(transform, movement, enemyComponent, attackComponent, collision);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void enemyFacesPlayerAndStartsWindUpWhenInRange() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);

        engine.update(DT);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        EnemyComponent enemyComp = ENEMY.get(enemyEntity);

        assertTrue("attack should be active", attack.isAttacking);
        assertTrue("wind-up should be active", attack.windUp.isActive());
        assertEquals("enemy should face right toward player", 1, enemyComp.direction);
        assertEquals("enemy velocity.x should be zeroed", 0f, MOVEMENT.get(enemyEntity).velocity.x, EPSILON);
    }

    @Test
    public void windUpCompletesAndDealsDamage() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        // Manually start the attack to skip the aggro-range check
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        // Step 1: wind-up still ticking, strike not yet live.
        engine.update(attack.windUpDuration - 0.05f);
        assertTrue("still in wind-up", attack.windUp.isActive());
        assertTrue("still attacking", attack.isAttacking);

        // Step 2: wind-up done on this frame -> strike goes live -> damage applies.
        engine.update(0.1f);
        assertEquals("player should take damage", 2, playerComp.health);

        // Step 3: strike window elapses -> attack completes.
        engine.update(1.0f);
        assertFalse("attack should be finished", attack.isAttacking);
    }

    @Test
    public void attackSkippedWhenPlayerOutOfRange() {
        // Player far beyond the detection box (attackRange*3 = 72 at unitScale=1)
        Entity playerEntity = player(200f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        engine.update(DT);

        assertFalse("attack should not start", ENEMY_ATTACK.get(enemyEntity).isAttacking);
        assertEquals("health unchanged", 3, playerComp.health);
    }

    @Test
    public void cooldownPreventsRapidReattack() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        // First attack completes via the cadence (strike live on step 2, done on step 3).
        engine.update(attack.windUpDuration - 0.05f);
        engine.update(0.1f);
        engine.update(1.0f);
        assertEquals("first hit lands", 2, playerComp.health);
        assertTrue("cooldown should be active", attack.attackCooldown.isActive());

        // Immediately update again — cooldown should block a second attack.
        engine.update(DT);
        assertFalse("second attack should not start", attack.isAttacking);
        assertEquals("health unchanged", 2, playerComp.health);
    }

    @Test
    public void deadEnemyDoesNotAttack() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        ENEMY.get(enemyEntity).isDead = true;

        engine.update(DT);

        assertFalse("dead enemy should not attack", ENEMY_ATTACK.get(enemyEntity).isAttacking);
    }

    @Test
    public void stunnedEnemyDoesNotAttack() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        ENEMY.get(enemyEntity).hitStun.start(1.0f);

        engine.update(DT);

        assertFalse("stunned enemy should not attack", ENEMY_ATTACK.get(enemyEntity).isAttacking);
    }

    @Test
    public void meleeHitboxDirectionMatchesEnemyFacing() {
        // --- Case 1: enemy to the LEFT of player → should face right (direction=1) ---
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(-2f, 0f);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        // Let the system naturally detect aggro and set direction
        engine.update(DT);
        assertTrue("attack should have started", attack.isAttacking);
        assertEquals("enemy should face right", 1, ENEMY.get(enemyEntity).direction);

        // Advance via the cadence so the strike window is live when the hit lands.
        engine.update(attack.windUpDuration - 0.05f);
        engine.update(0.1f);
        assertEquals("damage from the right side", 2, PLAYER.get(playerEntity).health);
        engine.update(1.0f);

        // --- Case 2: enemy to the RIGHT of player → should face left (direction=-1) ---
        PlayerComponent playerComp = PLAYER.get(playerEntity);
        // Clear invulnerability so the next hit can land
        playerComp.hitInvulnerability.reset();
        playerComp.hurtTimer.reset();

        // Move enemy to the right of the player
        TransformComponent eTransform = TRANSFORM.get(enemyEntity);
        CollisionComponent eCollision = eCollision(enemyEntity);
        place(eTransform, eCollision, 20f, 0f);
        ENEMY.get(enemyEntity).direction = -1; // re-faced left so the left commit rect overlaps

        // Reset attack state so the system can naturally start a new attack. The recovery stand
        // from the case-1 strike (0.5s) is still active and would keep blocking the new trigger,
        // so clear it too.
        attack.isAttacking = false;
        attack.attackCooldown.reset();
        attack.recovery.reset();
        attack.strike.reset();

        engine.update(DT);
        assertTrue("attack should have started", attack.isAttacking);
        assertEquals("enemy should face left", -1, ENEMY.get(enemyEntity).direction);

        // Advance via the cadence to land the hit from the left.
        engine.update(attack.windUpDuration - 0.05f);
        engine.update(0.1f);
        assertEquals("damage from the left side", 1, PLAYER.get(playerEntity).health);
    }

    @Test
    public void noDamageWhenMeleeMisses() {
        // Enemy at x=0, player at x=60: centers dx=60 <= 72 so the player IS detected, but the
        // enemy's front commit rect [10,34] doesn't reach the player's worldBounds [45,75] — so
        // the enemy never even commits (a detected-but-out-of-reach player is not swung at).
        Entity playerEntity = player(60f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        engine.update(DT);

        assertFalse("out-of-reach player should not trigger a swing", ENEMY_ATTACK.get(enemyEntity).isAttacking);
        assertEquals("no damage on miss", 3, playerComp.health);
    }

    @Test
    public void noDamageWhenPlayerLeavesStrikeDuringWindup() {
        // Player is initially in reach so the attack can be started, then steps out of the strike
        // hitbox before the blade goes live — a true in-swing whiff: the attack still completes
        // but no damage lands.
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        PlayerComponent playerComp = PLAYER.get(playerEntity);

        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        engine.update(attack.windUpDuration - 0.05f); // wind-up still ticking
        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 200f, 0f);
        engine.update(0.1f); // strike live, but player is out of the hitbox
        engine.update(1.0f);

        assertFalse("attack should be finished", attack.isAttacking);
        assertEquals("no damage on whiff", 3, playerComp.health);
    }

    @Test
    public void strikeHitboxOnlyLiveDuringStrikeWindow() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        // Step 1: wind-up — strike bounds not live.
        engine.update(attack.windUpDuration - 0.05f);
        assertNull("strike not live during wind-up", system.getActiveStrikeBounds());

        // Step 2: strike live — bounds = enemy collision width × height, adjacent to the facing
        // (right) edge. Enemy worldBounds at (0,0) = (-10,-20,20,40): right edge at x=10.
        engine.update(0.1f);
        Rectangle live = system.getActiveStrikeBounds();
        assertNotNull("strike should be live during the strike window", live);
        assertEquals(10f, live.x, EPSILON);
        assertEquals(-20f, live.y, EPSILON);
        assertEquals(20f, live.width, EPSILON);
        assertEquals(40f, live.height, EPSILON);

        // Step 3: strike done — bounds cleared.
        engine.update(1.0f);
        assertNull("strike bounds cleared after completion", system.getActiveStrikeBounds());
        assertFalse("attack completed", attack.isAttacking);
    }

    @Test
    public void strikeRequiresFacingThePlayer() {
        // Player BEHIND the enemy (enemy center 0, player center -20, enemy faces right): detected
        // (dx=20 <= 72) but the front commit rect faces away, so no swing.
        Entity playerEntity = player(-20f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        ENEMY.get(enemyEntity).direction = 1; // facing right, away from the player

        engine.update(DT);
        assertFalse("must not strike a player behind it", attack.isAttacking);

        // Re-face the enemy toward the player (left); the front commit rect now overlaps -> commits.
        ENEMY.get(enemyEntity).direction = -1;
        engine.update(DT);
        assertTrue("should strike once facing the player", attack.isAttacking);
        assertEquals("should face the player (left)", -1, ENEMY.get(enemyEntity).direction);
    }

    @Test
    public void noStrikeWhenPlayerElevated() {
        // Player far above the detection box: enemy center (0,0), player center (10,40), dy=40 >
        // detectionHeight/2 (=10) — never detected, even though the range rect overlaps vertically.
        Entity playerEntity = player(10f, 40f);
        Entity enemyEntity = enemy(0f, 0f);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);

        engine.update(DT);

        assertFalse("elevated player must not trigger a swing", attack.isAttacking);
        assertEquals("health unchanged", 3, PLAYER.get(playerEntity).health);
    }

    @Test
    public void roomInactiveEnemyDoesNotAttack() {
        RoomState roomState = new RoomState();
        roomState.rooms.add(new Room(0f, 0f, 480f, 272f));
        roomState.activeRoomIndex = 0;

        engine = newEngine();
        system = new EnemyAttackSystem(roomState);
        engine.addSystem(system);

        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        ENEMY.get(enemyEntity).roomIndex = 1;

        engine.update(DT);
        assertFalse("inactive-room enemy should not attack", ENEMY_ATTACK.get(enemyEntity).isAttacking);

        roomState.activeRoomIndex = 1;
        engine.update(DT);
        assertTrue("active-room enemy should attack", ENEMY_ATTACK.get(enemyEntity).isAttacking);
    }

    @Test
    public void strikeStartsRecoveryStand_thenResumesPatrolWithoutDirectionFlip() {
        // Assemble the production-mirror engine: EnemySystem (priority 4) runs before
        // EnemyAttackSystem (priority 8), so the strike's recovery stand is observed exactly as
        // in production. A floor rect keeps the ground probe satisfied so the ONLY possible turn
        // source on the recovery-end frame is the wall-block misread of the stand's zeroed
        // velocity — which the resumedFromAttack guard must suppress.
        Array<Rectangle> collisionRects = new Array<>();
        collisionRects.add(FLOOR);
        engine = newEngine();
        engine.addSystem(new EnemySystem(mock(EntityFactory.class), collisionRects, new Array<>(), new Array<>(), null, 4));
        engine.addSystem(new EnemyAttackSystem(null, 8));

        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        EnemyComponent enemyComp = ENEMY.get(enemyEntity);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);
        enemyComp.roomIndex = -1; // always room-active so the room gate doesn't interfere
        MOVEMENT.get(enemyEntity).grounded = true;

        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);

        // Cadence through the attack: strike live on step 2, strike done on step 3 -> recovery stand.
        engine.update(attack.windUpDuration - 0.05f);
        engine.update(0.1f);
        engine.update(1.0f);
        assertFalse("attack should be finished", attack.isAttacking);
        assertTrue("recovery stand should start after the strike", attack.recovery.isActive());

        // Move the player out of range so nothing can re-trigger an attack during the stand.
        TransformComponent playerTransform = TRANSFORM.get(playerEntity);
        CollisionComponent playerCollision = COLLISION.get(playerEntity);
        place(playerTransform, playerCollision, 300f, 0f);
        int prevDirection = enemyComp.direction;

        // During the recovery window the enemy stands still: patrol paused (velocity zeroed).
        MovementComponent movement = MOVEMENT.get(enemyEntity);
        for (int i = 0; i < 10; i++) {
            engine.update(DT);
            assertEquals("recovery stand should pause patrol (frame " + i + ")",
                0f, movement.velocity.x, EPSILON);
        }
        assertTrue("still recovering through the paused frames", attack.recovery.isActive());

        // Advance past the full recovery duration (recovery is ticked by EnemySystem, 0.5s total).
        int guard = 0;
        while (attack.recovery.isActive() && guard < 100) {
            engine.update(DT);
            guard++;
        }

        assertFalse("recovery should have elapsed", attack.recovery.isActive());
        assertEquals("no spurious direction flip from the recovery-end wall-block misread",
            prevDirection, enemyComp.direction);
        assertEquals("enemy should resume patrolling in the attack direction",
            enemyComp.speed * enemyComp.direction, movement.velocity.x, EPSILON);
    }

    @Test
    public void triggerBlockedDuringRecovery() {
        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);

        // Start an attack and let the strike resolve via the cadence, which begins recovery.
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);
        engine.update(attack.windUpDuration - 0.05f);
        engine.update(0.1f);
        engine.update(1.0f);
        assertTrue("recovery stand should be active after the strike", attack.recovery.isActive());

        // Bust the cooldown so the recovery gate is the ONLY thing blocking a new attack.
        attack.attackCooldown.reset();
        assertFalse("cooldown should no longer block", attack.attackCooldown.isActive());

        // Player still in range, cooldown ready — yet the recovery gate (and the
        // defense-in-depth gate) must prevent a new attack from triggering.
        engine.update(DT);
        assertFalse("no new attack while recovering", attack.isAttacking);
        assertTrue("still recovering", attack.recovery.isActive());
    }

    @Test
    public void reattacksAfterRecoveryWhenCooldownReady() {
        // Recovery is ticked by EnemySystem, so run the production-mirror engine here too.
        Array<Rectangle> collisionRects = new Array<>();
        collisionRects.add(FLOOR);
        engine = newEngine();
        engine.addSystem(new EnemySystem(mock(EntityFactory.class), collisionRects, new Array<>(), new Array<>(), null, 4));
        engine.addSystem(new EnemyAttackSystem(null, 8));

        Entity playerEntity = player(10f, 0f);
        Entity enemyEntity = enemy(0f, 0f);
        EnemyComponent enemyComp = ENEMY.get(enemyEntity);
        EnemyAttackComponent attack = ENEMY_ATTACK.get(enemyEntity);

        // First attack: strike resolves via the cadence, then recovery stand + cooldown begin.
        attack.isAttacking = true;
        attack.windUp.start(attack.windUpDuration);
        engine.update(attack.windUpDuration - 0.05f);
        engine.update(0.1f);
        engine.update(1.0f);
        assertTrue("recovery stand active after first strike", attack.recovery.isActive());

        // Advance past the full recovery duration, then arm the cooldown.
        engine.update(attack.recoveryDuration + DT);
        assertFalse("recovery should have elapsed", attack.recovery.isActive());
        attack.attackCooldown.reset();

        // Player still in range and the enemy still faces them → a new attack triggers.
        engine.update(DT);
        assertTrue("enemy should start a new attack after recovery", attack.isAttacking);
        assertEquals("enemy still faces the player", 1, enemyComp.direction);
    }

    // --- helpers ---

    /** Returns the CollisionComponent for the given entity. */
    private CollisionComponent eCollision(Entity entity) {
        return com.axehigh.platformer.ecs.components.Mappers.COLLISION.get(entity);
    }
}
