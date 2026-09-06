package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.CrumblingTile;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test reproducing the REAL standing-on-a-crumbling-tile state as a frame loop through
 * the actual gameplay systems, instead of hand-placing a grounded player like the original
 * {@code CrumblingTileSystemTest} did.
 * <p>
 * Wiring mirrors {@code GameSystems}: {@code CrumblingTileSystem} (tier-5, inserted first) and
 * {@code MovementSystem} (tier-5) share the SAME {@code oneWayRects} Array instance, and
 * {@code CollisionBoundsSystem} (tier-6) re-derives {@code worldBounds} after movement — exactly
 * the live ordering. The player starts above the tile and lands on it through the real gravity +
 * one-way snap; no component field is pre-set to fake a landed posture.
 * <p>
 * {@code MovementSystem} applies gravity itself, so the loop only calls {@code engine.update(DT)} —
 * identical to a live frame. {@code Gdx.app} is mocked (with preference stubs returning the
 * documented defaults) because the crumble system's arm/collapse debug lines log through it, and
 * {@code FeatureFlags} would otherwise seed from a null-returning preferences mock.
 */
public class CrumblingTileStandingFrameLoopTest extends SystemTestBase {

    private static final float TILE_X = 100f;
    private static final float TILE_Y = 0f;
    private static final float TILE_W = 128f;
    private static final float TILE_H = 128f;

    /** Collision box offsets relative to the transform position; feet = pos.y + BOX_OFFSET_Y. */
    private static final float BOX_OFFSET_X = -20f;
    private static final float BOX_OFFSET_Y = -50f;
    private static final float BOX_W = 40f;
    private static final float BOX_H = 80f;

    /** Player start: feet (= 200 - 50) at 150, i.e. 22px above the tile top at 128, falling onto it. */
    private static final float START_X = TILE_X + TILE_W / 2f; // AABB centered over the tile
    private static final float START_Y = 200f;
    /** Arming must happen well within this window (0.1s grace once fresh contact + grounded). */
    private static final int ARM_WINDOW_FRAMES = (int) (3f * 60f); // 180 frames = 3 s
    private static final int LAND_DEADLINE_FRAMES = (int) (1f * 60f); // sanity: must land within 1 s

    private Engine engine;
    private Array<Rectangle> oneWayRects;
    private TiledMapTileLayer layer;
    private Rectangle tileRect;
    private CrumblingTile tile;
    private TransformComponent transform;
    private CollisionComponent collision;
    private MovementComponent movement;
    private PlayerComponent playerComp;

    @Before
    public void setUp() {
        // Pin the flags before a Gdx.app exists so no preferences round-trip happens (headless).
        FeatureFlags.setWallClimbingEnabled(true);
        FeatureFlags.setSquashEnabled(false);
        Gdx.app = mock(Application.class);
        Preferences preferences = mock(Preferences.class);
        when(Gdx.app.getPreferences(anyString())).thenReturn(preferences);
        // Every GamePreferences read falls back to its documented default (e.g. wall-climb on).
        when(preferences.getBoolean(anyString(), anyBoolean()))
            .thenAnswer(invocation -> (Boolean) invocation.getArgument(1));

        oneWayRects = new Array<>();
        Array<Rectangle> collisionRects = new Array<>();
        Array<CrumblingTile> crumblingTiles = new Array<>();

        layer = new TiledMapTileLayer(16, 16, (int) TILE_W, (int) TILE_H);
        TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
        cell.setTile(new StaticTiledMapTile(new TextureRegion()));
        layer.setCell(0, 0, cell);
        tileRect = new Rectangle(TILE_X, TILE_Y, TILE_W, TILE_H);
        oneWayRects.add(tileRect);
        tile = new CrumblingTile(layer, 0, 0, cell, tileRect);
        crumblingTiles.add(tile);

        // Runtime wiring (GameSystems): same priority tier, crumble inserted BEFORE movement so a
        // collapse removes the rect before the player's move resolves; same oneWayRects instance.
        engine = newEngine();
        engine.addSystem(new CrumblingTileSystem(crumblingTiles, oneWayRects, 5));
        MovementSystem movementSystem = new MovementSystem(collisionRects, oneWayRects, 5);
        movementSystem.setUnitScale(1f);
        engine.addSystem(movementSystem);
        engine.addSystem(new CollisionBoundsSystem(6));

        transform = transform(START_X, START_Y);
        transform.scale.x = 0f; // stops the runtime x-offset lerp (MovementSystemTest convention)
        collision = collision(BOX_OFFSET_X, BOX_OFFSET_Y, BOX_W, BOX_H);
        place(transform, collision, START_X, START_Y);
        movement = movement();
        playerComp = player();
        engine.addEntity(entity(transform, playerComp, movement, collision));
    }

    @After
    public void tearDown() {
        Gdx.app = null;
    }

    @Test
    public void frameLoop_standingOnCrumbleTile_armsShaking() {
        StringBuilder gateDump = new StringBuilder();
        // Per-condition blocking counters, tracked only after the player has genuinely landed.
        int landedFrame = -1;
        int armedFrame = -1;
        int standingFrames = 0;
        int overlapsFalse = 0;
        int settleBlocked = 0;
        int contactNotDone = 0;
        int feetGateFalse = 0;
        int groundedFalse = 0;
        int dropBlocked = 0;

        for (int i = 0; i < ARM_WINDOW_FRAMES; i++) {
            engine.update(DT);

            if (movement.grounded && landedFrame < 0) {
                landedFrame = i;
            }
            gateDump.append(gateLine(i)).append('\n');

            if (tile.state == CrumblingTile.State.SHAKING) {
                armedFrame = i;
                break;
            }
            if (landedFrame >= 0) {
                standingFrames++;
                if (!collision.worldBounds.overlaps(tileRect)) overlapsFalse++;
                if (tile.settleTimer.isActive()) settleBlocked++;
                if (!tile.contactTimer.isDone()) contactNotDone++;
                if (!(transform.position.y + collision.bounds.y >= tileRect.y + tileRect.height - 1.0f)) feetGateFalse++;
                if (!movement.grounded) groundedFalse++;
                if (playerComp.dropWindow.isActive()) dropBlocked++;
            }
        }

        String dump = gateDump.toString();
        String summary = "landedFrame=" + landedFrame
            + " armedFrame=" + armedFrame
            + " standingFrames=" + standingFrames
            + " | standing-frame gate blockers: overlapsFalse=" + overlapsFalse + "/" + standingFrames
            + " settleBlocked=" + settleBlocked + "/" + standingFrames
            + " contactNotDone=" + contactNotDone + "/" + standingFrames
            + " feetGateFalse=" + feetGateFalse + "/" + standingFrames
            + " groundedFalse=" + groundedFalse + "/" + standingFrames
            + " dropBlocked=" + dropBlocked + "/" + standingFrames + "\n";

        // Diagnosis first: the REAL landing posture must be grounded with feet exactly on the top.
        assertTrue("player never became grounded on the tile (must land within "
            + LAND_DEADLINE_FRAMES + " frames); landedFrame=" + landedFrame + "\n" + dump,
            landedFrame >= 0 && landedFrame < LAND_DEADLINE_FRAMES);
        assertTrue("player must be grounded on the tile, not floating", movement.grounded);
        assertEquals("player feet must sit exactly on the tile top (rect y+h)",
            tileRect.y + tileRect.height, transform.position.y + collision.bounds.y, EPSILON);

        // THE point of this test: standing on a crumbling tile through the real movement physics
        // must arm SHAKING within the landing grace + a margin.
        assertTrue("standing on the tile for " + standingFrames + " frames never armed SHAKING; "
            + summary + dump, armedFrame != -1);

        // Only reached if arming worked: the shake (0.5s) ends in a collapse that removes the rect.
        for (int i = 0; i < (int) (1f * 60f); i++) {
            engine.update(DT);
        }
        assertEquals("tile must COLLAPSE after the shake", CrumblingTile.State.COLLAPSED, tile.state);
        assertFalse("collapsing removes the tile rect from the shared oneWayRects",
            oneWayRects.contains(tileRect, true));
        assertTrue("collapsing blanks the layer cell", layer.getCell(0, 0) == null);
    }

    /**
     * One frame of the arm-gate state, labeled to match the system's INTACT arming conditions:
     * overlaps + !settleActive + contactDone + feetOnTopGate + grounded + !dropActive.
     */
    private String gateLine(int frame) {
        float feetY = transform.position.y + collision.bounds.y;
        boolean overlaps = collision.worldBounds.overlaps(tileRect);
        boolean feetGate = feetY >= tileRect.y + tileRect.height - 1.0f;
        return "f" + frame
            + " posY=" + fmt(transform.position.y)
            + " vY=" + fmt(movement.velocity.y)
            + " state=" + tile.state
            + " |gate| overlaps=" + overlaps
            + " !settleActive=" + !tile.settleTimer.isActive()
            + " contactDone=" + tile.contactTimer.isDone()
            + "(rem=" + fmt(tile.contactTimer.getRemaining()) + ")"
            + " feetOnTopGate=" + feetGate
            + "(feetY=" + fmt(feetY) + " top=" + fmt(tileRect.y + tileRect.height) + ")"
            + " grounded=" + movement.grounded
            + " !dropActive=" + !playerComp.dropWindow.isActive();
    }

    private static String fmt(float value) {
        return String.format("%.3f", value);
    }
}
