package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless unit tests for {@code EnemyShootSystem}, centered on the two-phase shooter telegraph:
 * a commit either enters a facing-change **turn-idle** ({@code EnemySystem.TURN_PAUSE_DURATION ×
 * unitScale} stand-still, velocity zeroed every frame) or starts the **wind-up** immediately; the
 * wind-up runs for the **effective duration** {@code max(windUpSeconds, ATTACKING clip duration)}
 * ({@link #DEFAULT_WINDUP_SECONDS} = 0.5s returns {@link #WINDUP_FRAMES} frames at {@link #DT})
 * during which the shooter plants in place (velocity zeroed every frame) and the bullet spawns the
 * frame the charge completes. Also covered: the cooldown-triggered cadence, the room-activity gate,
 * the hit-stun suppression (the post-hit recovery idle does NOT gate firing), cancellation on
 * death/hit-stun/room-inactive/player-out-of-detection (never restarting the cooldown), and the
 * bullet's spawn position at the collision box's vertical center on the leading edge. The
 * {@code AssetManager} is mocked so the bullet's texture resolves headless.
 */
public class EnemyShootSystemTest extends SystemTestBase {

    private static final String BULLET_TEXTURE_PATH = "gfx/old/bullet.png";

    /** Mirrors {@code EnemyShootSystem.BULLET_SIZE}; the test system's unitScale stays at the default 1. */
    private static final float BULLET_SIZE = 4f;

    /** Default {@code EnemyShooterComponent.windUpSeconds} (Tiled `windUp`, real seconds). */
    private static final float DEFAULT_WINDUP_SECONDS = 0.5f;

    /** Updates for the effective 0.5s wind-up at {@link #DT}: {@code Math.max(0.5, DT)} → 30 frames. */
    private static final int WINDUP_FRAMES = (int) Math.ceil(DEFAULT_WINDUP_SECONDS / DT);

    /** A realistic shooter collision box: 128px sprite scaled to the 16x16 spider at 8x unit scale,
     *  with the box's local Y offset sitting ~10.28 world units above the sprite origin. */
    private static final float COLLISION_OFFSET_X = 9.48f;
    private static final float COLLISION_OFFSET_Y = 10.28f;
    private static final float COLLISION_WIDTH = 6.64f;
    private static final float COLLISION_HEIGHT = 6.64f;

    /**
     * World-unit shoot/detection ranges used by the fixtures. The factory converts the tile-count
     * defaults (8) into world units via {@code × tileWidth = 16}, so these fixtures set the runtime
     * values directly: 8 × 16 = 128 world units.
     */
    private static final float DETECTION_RANGE = 128f;
    private static final float SHOOT_RANGE = 128f;

    private PooledEngine engine;
    private EnemyShootSystem system;
    private RoomState roomState;

    @Before
    public void setUp() {
        roomState = new RoomState();
        system = new EnemyShootSystem(mockAssets(), roomState);
        engine = new PooledEngine();
        engine.addSystem(system);
    }

    private static AssetManager mockAssets() {
        AssetManager assets = mock(AssetManager.class);
        when(assets.get(BULLET_TEXTURE_PATH, Texture.class)).thenReturn(mock(Texture.class));
        return assets;
    }

    /** {@code ATTACKING} clip of {@code duration} seconds (default one 60fps frame = DT). */
    private static AnimationComponent shooterAnimation(float duration) {
        AnimationComponent animation = new AnimationComponent();
        Array<TextureRegion> frames = new Array<>();
        frames.add(new TextureRegion(mock(Texture.class)));
        animation.animations.put(AnimationComponent.State.ATTACKING,
            new Animation<>(duration, frames, Animation.PlayMode.NORMAL));
        return animation;
    }

    private Entity shooter(float x, float y) {
        return shooter(x, y, DEFAULT_WINDUP_SECONDS, DT);
    }

    private Entity shooter(float x, float y, float windUpSeconds, float clipDuration) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(COLLISION_OFFSET_X, COLLISION_OFFSET_Y, COLLISION_WIDTH, COLLISION_HEIGHT);
        place(transform, collision, x, y);
        EnemyComponent enemy = new EnemyComponent();
        enemy.direction = 1;
        EnemyShooterComponent shooter = new EnemyShooterComponent();
        shooter.shootCooldown.reset();
        shooter.detectionRange = DETECTION_RANGE;
        shooter.shootRange = SHOOT_RANGE;
        shooter.windUpSeconds = windUpSeconds;
        Entity entity = entity(transform, collision, enemy, shooter, movement(), shooterAnimation(clipDuration));
        engine.addEntity(entity);
        return entity;
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 16f, 16f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, collision, new PlayerComponent());
        engine.addEntity(entity);
        return entity;
    }

    private Entity singleBullet() {
        for (Entity e : engine.getEntities()) {
            if (BULLET.get(e) != null) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void shooterFiresOnlyAfterWindUpElapses() {
        shooter(0f, 0f); // right-facing, ahead commit
        player(100f, 0f);

        engine.update(DT);

        assertNull("no bullet on the commit frame — only the wind-up starts", singleBullet());

        for (int i = 0; i < WINDUP_FRAMES - 1; i++) {
            engine.update(DT);
        }

        assertNull("still charging before the 0.5s effective wind-up elapses (the DT clip does not govern)",
                singleBullet());

        engine.update(DT);

        Entity bullet = singleBullet();
        assertNotNull("bullet spawns the frame the effective wind-up duration elapses", bullet);
        assertEquals(150f, MOVEMENT.get(bullet).velocity.x, 0.001f);
        assertEquals(0f, MOVEMENT.get(bullet).velocity.y, 0.001f);
        assertNotNull("bullet should be tagged as an enemy bullet", ENEMY_BULLET.get(bullet));
        assertNotNull("bullet should be tagged as a bullet", BULLET.get(bullet));
        assertEquals("enemy bullet carries the shooter's shootRange as its max travel distance",
                128f, BULLET.get(bullet).maxTravelDistance, 0.001f);
    }

    @Test
    public void readyShooterFiresOnceCooldownDone() {
        shooter(0f, 0f);
        player(100f, 0f);

        engine.update(DT); // commit, wind-up starts
        for (int i = 0; i < WINDUP_FRAMES; i++) {
            engine.update(DT);
        }

        assertNotNull("cooldown-done shooter with a detected player should fire", singleBullet());
    }

    @Test
    public void postHitIdleEnemyFiresAgain() {
        Entity shooter = shooter(0f, 0f);
        player(100f, 0f);
        ENEMY.get(shooter).postHitIdle.start(0.5f);

        engine.update(DT); // commit
        for (int i = 0; i < WINDUP_FRAMES; i++) {
            engine.update(DT);
        }

        assertEquals(3, engine.getEntities().size());
        assertNotNull("post-hit-idle shooter should fire", singleBullet());
    }

    @Test
    public void postHitIdleShooterFiresOnceCooldownDone() {
        Entity shooter = shooter(0f, 0f);
        player(100f, 0f);
        ENEMY.get(shooter).postHitIdle.start(0.5f);
        ENEMY_SHOOTER.get(shooter).shootCooldown.start(0.3f);

        engine.update(DT);
        assertEquals(2, engine.getEntities().size());

        // The cooldown finishes while the post-hit idle is still running; firing must resume
        // regardless of the recovery window.
        assertNull("cooldown still ticking — no commit yet", singleBullet());
        ENEMY_SHOOTER.get(shooter).shootCooldown.update(1f);
        assertTrue(ENEMY.get(shooter).postHitIdle.isActive());

        engine.update(DT); // commit frame
        for (int i = 0; i < WINDUP_FRAMES; i++) {
            engine.update(DT);
        }

        assertEquals(3, engine.getEntities().size());
        assertNotNull("active post-hit idle must not gate firing once the cooldown is done", singleBullet());
    }

    @Test
    public void bulletSpawnsAtCollisionBoxVerticalCenter() {
        float x = 40f;
        float y = 120f;
        shooter(x, y);
        player(x + 60f, y);

        engine.update(DT); // commit
        for (int i = 0; i < WINDUP_FRAMES; i++) {
            engine.update(DT);
        }

        Entity bullet = singleBullet();
        assertNotNull("ready shooter should spawn a bullet", bullet);
        float expectedCenterY = y + COLLISION_OFFSET_Y + (COLLISION_HEIGHT - BULLET_SIZE) / 2f;
        assertEquals("bullet must spawn at the collision box's vertical center, not its bottom",
                expectedCenterY, TRANSFORM.get(bullet).position.y, 0.001f);
    }

    @Test
    public void hitStunnedEnemyDoesNotFire() {
        Entity shooter = shooter(0f, 0f);
        player(100f, 0f);
        ENEMY.get(shooter).hitStun.start(0.3f);

        engine.update(DT);

        assertEquals(2, engine.getEntities().size());
        assertNull(singleBullet());
    }

    @Test
    public void shooterInInactiveRoomDoesNotFire() {
        Entity shooter = shooter(0f, 0f);
        player(100f, 0f);
        ENEMY.get(shooter).roomIndex = 0;
        roomState.activeRoomIndex = 1;

        engine.update(DT);

        assertEquals(2, engine.getEntities().size());
        assertNull(singleBullet());
    }

    @Test
    public void shooterDoesNotFireWhenPlayerOutOfHorizontalRange() {
        shooter(0f, 0f);
        player(140f, 0f); // player center 148, 135.2u ahead — beyond the 128u reach

        engine.update(DT);
        engine.update(DT);

        assertEquals("no commit, no bullet", 2, engine.getEntities().size());
        assertNull(singleBullet());
    }

    @Test
    public void shooterDoesNotFireWhenPlayerOnDifferentVerticalLine() {
        shooter(0f, 0f);
        player(100f, 60f); // 54.4u above the shooter center — beyond the ±1-tile band

        engine.update(DT);
        engine.update(DT);

        assertEquals(2, engine.getEntities().size());
        assertNull(singleBullet());
    }

    @Test
    public void shooterCommitsTurnIdleWhenPlayerBehindWithinHalfDetectionRange() {
        Entity shooterEntity = shooter(0f, 0f); // faces right (direction = 1)
        // Player center X = -47 vs shooter's 12.8 -> 59.8 behind, within the 64u half-range.
        // Behind the facing commits too — but the snap-face turn IS the facing change, so the
        // shot holds a turn-idle stand-still before the wind-up.
        player(-55f, 0f);

        engine.update(DT);

        EnemyComponent enemy = ENEMY.get(shooterEntity);
        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(shooterEntity);
        assertEquals("commit snap-faces the player behind it", -1, enemy.direction);
        assertTrue("a behind-commit needs the facing change, so the turn-idle starts", shooter.turnIdle.isActive());
        assertFalse("no wind-up during the turn-idle", shooter.windUp.isActive());
        assertNull("no bullet on the commit frame", singleBullet());
        assertTrue("commit alone must not restart the cooldown", shooter.shootCooldown.isDone());
    }

    @Test
    public void shooterDoesNotCommitWhenPlayerBehindBeyondHalfButWithinFullDetectionRange() {
        Entity shooterEntity = shooter(0f, 0f); // faces right (direction = 1)
        // Player center X = -83 vs shooter's 12.8 -> 95.8 behind: beyond the 64u half-range but
        // inside the 128u full range. Behind the facing gates on the HALF reach, so no commit.
        player(-91f, 0f);

        engine.update(DT);
        engine.update(DT);

        EnemyComponent enemy = ENEMY.get(shooterEntity);
        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(shooterEntity);
        assertEquals("no commit — the facing is untouched", 1, enemy.direction);
        assertFalse("no turn-idle", shooter.turnIdle.isActive());
        assertFalse("no wind-up", shooter.windUp.isActive());
        assertNull("no bullet", singleBullet());
        assertTrue("cooldown stays done/ready (no commit, no restart)", shooter.shootCooldown.isDone());
    }

    @Test
    public void shooterCommitsImmediatelyWhenPlayerAheadAtFullDetectionRange() {
        Entity shooterEntity = shooter(0f, 0f); // faces right (direction = 1)
        // Player center X = 138 vs shooter's 12.8 -> 125.2 ahead, at the full 128u reach.
        // Ahead of the facing keeps the full range, and no facing change is needed, so the
        // wind-up starts on the commit frame with no turn-idle.
        player(130f, 0f);

        engine.update(DT); // commit frame

        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(shooterEntity);
        assertEquals("no facing change for an ahead commit", 1, ENEMY.get(shooterEntity).direction);
        assertFalse("ahead commit skips the turn-idle", shooter.turnIdle.isActive());
        assertTrue("wind-up starts on the same frame as the commit", shooter.windUp.isActive());

        for (int i = 0; i < WINDUP_FRAMES; i++) {
            engine.update(DT); // wind-up elapses -> fire
        }

        assertNotNull("full-range ahead shot fires after the effective wind-up", singleBullet());
    }

    @Test
    public void bulletFiresTowardPlayerDirection() {
        Entity shooterEntity = shooter(0f, 0f);
        // Shooter center X = 12.8, player center X = -47 -> player to the left, within the
        // half-range (64u) behind the right-facing shooter, so the commit needs a facing change
        // and holds a turn-idle stand-still before the wind-up.
        player(-55f, 0f);

        engine.update(DT); // commit: snap-face the player, turn-idle starts

        assertEquals("commit snap-faces the player", -1, ENEMY.get(shooterEntity).direction);
        assertTrue("a needed facing change starts the turn-idle first", ENEMY_SHOOTER.get(shooterEntity).turnIdle.isActive());
        assertNull(singleBullet());

        // Turn-idle + the 30-frame (0.5s) wind-up, +1 for the float residue that leaves the 6-frame
        // turn-idle (0.1s at DT) still active after its computed ceil() tick count.
        int idleFrames = (int) Math.ceil(EnemySystem.TURN_PAUSE_DURATION / DT);
        for (int i = 0; i < idleFrames + WINDUP_FRAMES + 1; i++) {
            engine.update(DT);
        }

        Entity bullet = singleBullet();
        assertNotNull("committed shot must fire once the turn-idle and wind-up elapse", bullet);
        assertTrue("bullet flies at the player (left, negative velocity.x)", MOVEMENT.get(bullet).velocity.x < 0f);
        assertEquals("bullet spawns at the collision box's left edge (bounds.x honored)",
                COLLISION.get(shooterEntity).bounds.x - BULLET_SIZE, TRANSFORM.get(bullet).position.x, 0.001f);
    }

    @Test
    public void noFacingChangeSkipsTurnIdleAndStartsWindUpOnCommitFrame() {
        Entity shooterEntity = shooter(0f, 0f);
        // Player to the right; the shooter already faces right (direction = 1).
        player(100f, 0f);

        engine.update(DT); // commit

        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(shooterEntity);
        assertFalse("already facing the player — no turn-idle", shooter.turnIdle.isActive());
        assertTrue("wind-up starts on the same frame as the commit", shooter.windUp.isActive());

        for (int i = 0; i < WINDUP_FRAMES; i++) {
            engine.update(DT); // wind-up elapses -> fire
        }

        assertNotNull("shot without a facing change fires after the effective wind-up", singleBullet());
    }

    @Test
    public void turnIdleStandsStillAndDelaysWindUpWhenFacingChangeNeeded() {
        Entity shooterEntity = shooter(0f, 0f);
        player(-55f, 0f); // player to the LEFT of a right-facing shooter (within half-range behind)

        engine.update(DT); // commit: snap-turn + turn-idle

        EnemyComponent enemy = ENEMY.get(shooterEntity);
        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(shooterEntity);
        assertEquals("commit snap-faces the player", -1, enemy.direction);
        assertTrue("facing change starts the turn-idle", shooter.turnIdle.isActive());
        assertFalse("no wind-up during the idle", shooter.windUp.isActive());
        assertNull("no bullet during the idle", singleBullet());
        assertEquals("the shooter stands still on the commit frame", 0f, MOVEMENT.get(shooterEntity).velocity.x, 0.001f);

        engine.update(DT);

        assertTrue("turn-idle still active after one frame", shooter.turnIdle.isActive());
        assertFalse("wind-up must wait for the idle", shooter.windUp.isActive());
        assertEquals("the shooter stays still while idling", 0f, MOVEMENT.get(shooterEntity).velocity.x, 0.001f);

        // Idle duration comes from the TURN_PAUSE_DURATION knob; step past the idle (which needs
        // one more frame than ceil() predicts due to float residue), the whole 0.5s wind-up, and
        // the spawn frame with margin.
        int idleFrames = (int) Math.ceil(EnemySystem.TURN_PAUSE_DURATION / DT);
        for (int i = 0; i < idleFrames + WINDUP_FRAMES + 1; i++) {
            engine.update(DT);
        }

        assertFalse("turn-idle done", shooter.turnIdle.isActive());
        Entity bullet = singleBullet();
        assertNotNull("shot fires after the turn-idle and the effective wind-up both elapse", bullet);
        assertEquals("fireDirection locked to the re-tracked facing at wind-up start, not commit", -1, shooter.fireDirection);
        assertTrue("bullet flies toward the player (left, negative velocity.x)", MOVEMENT.get(bullet).velocity.x < 0f);
    }

    @Test
    public void turnIdleReTracksFacingAndReAimsWhenPlayerCrossesSides() {
        Entity shooterEntity = shooter(0f, 0f);
        Entity playerEntity = player(-55f, 0f); // player LEFT of a right-facing shooter (half-range behind)

        engine.update(DT); // commit: snap-turn to -1, turn-idle

        assertEquals("commit snap-faces the player", -1, ENEMY.get(shooterEntity).direction);
        assertTrue(ENEMY_SHOOTER.get(shooterEntity).turnIdle.isActive());

        // The player crosses to the RIGHT side (still within half-range behind the now-left-facing
        // shooter) mid-idle.
        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 60f, 0f);

        engine.update(DT);

        assertEquals("re-track flips facing back to the player's new side", 1, ENEMY.get(shooterEntity).direction);
        assertTrue("turn-idle continues through the re-track", ENEMY_SHOOTER.get(shooterEntity).turnIdle.isActive());

        // Complete the idle (plus the extra frame the cross-side replay already consumed is netted
        // by the re-track update) and the 0.5s wind-up; by the end of the loop the shot is already
        // out, aimed with the re-tracked (right) direction.
        int idleFrames = (int) Math.ceil(EnemySystem.TURN_PAUSE_DURATION / DT);
        for (int i = 0; i < idleFrames + WINDUP_FRAMES; i++) {
            engine.update(DT);
        }

        assertFalse("turn-idle done", ENEMY_SHOOTER.get(shooterEntity).turnIdle.isActive());
        assertEquals("fireDirection locked to the re-tracked facing", 1, ENEMY_SHOOTER.get(shooterEntity).fireDirection);

        Entity bullet = singleBullet();
        assertNotNull("cross-side re-aimed shot still fires", bullet);
        assertTrue("bullet flies toward the player's side at idle completion (right, positive velocity.x)",
                MOVEMENT.get(bullet).velocity.x > 0f);
    }

    @Test
    public void turnIdleCancelsWhenPlayerLeavesRange() {
        Entity shooterEntity = shooter(0f, 0f);
        Entity playerEntity = player(-55f, 0f); // player LEFT of a right-facing shooter (half-range behind)

        engine.update(DT); // commit: snap-turn + turn-idle

        assertTrue("commit with a facing change enters the turn-idle", ENEMY_SHOOTER.get(shooterEntity).turnIdle.isActive());

        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 400f, 0f);

        engine.update(DT); // cancel frame

        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(shooterEntity);
        assertFalse("a cancel frame resets the turn-idle", shooter.turnIdle.isActive());
        assertFalse("a cancelled idle must not start the wind-up", shooter.windUp.isActive());
        assertEquals("the snap-turn stays even though the shot was abandoned", -1, ENEMY.get(shooterEntity).direction);
        assertNull("a cancelled idle must not spawn a bullet", singleBullet());
        assertTrue("cancelling must NOT restart the shoot cooldown (re-detect allows re-commit)", shooter.shootCooldown.isDone());

        // Player comes back within range on the LEFT side: the facing still differs, so the
        // shooter re-commits and re-enters the turn-idle.
        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 60f, 0f);

        engine.update(DT);

        assertTrue("re-commit with the facing still differing starts the turn-idle again", shooter.turnIdle.isActive());
    }

    @Test
    public void windUpCancelsWhenPlayerLeavesRange() {
        Entity shooterEntity = shooter(0f, 0f);
        Entity playerEntity = player(100f, 0f); // player AHEAD (right side), no facing change

        engine.update(DT); // commit: wind-up starts immediately

        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 400f, 0f);

        engine.update(DT); // cancel frame

        assertEquals(2, engine.getEntities().size());
        assertNull("a cancelled wind-up must not spawn a bullet", singleBullet());
        assertTrue("cancelling must NOT restart the shoot cooldown (re-detect allows re-commit)",
                ENEMY_SHOOTER.get(shooterEntity).shootCooldown.isDone());
    }

    @Test
    public void windUpCancelsOnHitStun() {
        Entity shooterEntity = shooter(0f, 0f);
        player(100f, 0f);

        engine.update(DT); // commit: wind-up starts

        ENEMY.get(shooterEntity).hitStun.start(0.3f);

        engine.update(DT); // cancel frame

        assertEquals(2, engine.getEntities().size());
        assertNull("hit-stun during the wind-up must cancel the shot", singleBullet());
    }

    @Test
    public void cooldownRestartsOnlyWhenShotFires() {
        Entity shooterEntity = shooter(0f, 0f);
        player(100f, 0f);

        engine.update(DT); // commit frame, wind-up starts

        assertTrue("commit alone must not restart the cooldown", ENEMY_SHOOTER.get(shooterEntity).shootCooldown.isDone());

        for (int i = 0; i < WINDUP_FRAMES; i++) {
            engine.update(DT); // wind-up elapses -> fire
        }

        assertFalse("the cooldown restarts the frame the shot actually fires", ENEMY_SHOOTER.get(shooterEntity).shootCooldown.isDone());
        assertNotNull(singleBullet());
    }

    @Test
    public void windUpLastsConfiguredSecondsOverShorterClip() {
        // Default fixture: windUpSeconds = 0.5s, ATTACKING clip = one 60fps frame (DT), so the
        // effective charge is the configured 0.5s, NOT the trivial one-frame clip.
        shooter(0f, 0f);
        player(100f, 0f);

        engine.update(DT); // commit, wind-up starts

        for (int i = 0; i < WINDUP_FRAMES - 1; i++) {
            engine.update(DT);
        }

        assertNull("the configured 0.5s (not the one-frame clip) governs the charge", singleBullet());

        engine.update(DT);

        assertNotNull("bullet spawns only once the 0.5s effective wind-up elapses", singleBullet());
    }

    @Test
    public void windUpTakesLongerClipWhenClipExceedsConfiguredSeconds() {
        int clipFrames = (int) Math.ceil(0.3f / DT);
        assertTrue(clipFrames > 1);

        // Configured 0.1s loses to the 0.3s ATTACKING clip: the charge must last for the clip.
        shooter(0f, 0f, 0.1f, 0.3f);
        player(100f, 0f);

        engine.update(DT); // commit, wind-up starts at the clip-driven 0.3s

        for (int i = 0; i < clipFrames - 1; i++) {
            engine.update(DT);
        }

        assertNull("0.1s configured must not fire early — the longer 0.3s clip governs", singleBullet());

        engine.update(DT);

        assertNotNull("bullet spawns at the clip-driven effective wind-up instead", singleBullet());
    }

    @Test
    public void windUpPlantsShooterInPlaceZeroingVelocityEachFrame() {
        Entity shooterEntity = shooter(0f, 0f);
        player(100f, 0f);

        engine.update(DT); // commit, wind-up starts

        EnemyShooterComponent shooter = ENEMY_SHOOTER.get(shooterEntity);
        assertTrue("ahead commit starts the wind-up", shooter.windUp.isActive());

        MOVEMENT.get(shooterEntity).velocity.x = 50f;
        engine.update(DT);

        assertEquals("velocity re-zeroed on the first wind-up frame", 0f, MOVEMENT.get(shooterEntity).velocity.x, 0.001f);

        for (int i = 0; i < WINDUP_FRAMES - 1; i++) {
            MOVEMENT.get(shooterEntity).velocity.x = 50f;
            engine.update(DT);
            assertEquals("velocity stays zeroed while the wind-up is active", 0f, MOVEMENT.get(shooterEntity).velocity.x, 0.001f);
        }

        assertTrue("shooter stayed planted for the whole charge, which just completed", shooter.windUp.isDone());
    }
}
