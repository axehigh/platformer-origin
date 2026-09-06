package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.CrumblingTile;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;

/**
 * Headless tests for {@code CrumblingTileSystem}: the shake → collapse → respawn state machine for
 * one-way crumbling platform tiles. The tile lives at cell (0,0) of a real 16px {@code
 * TiledMapTileLayer}, sharing its world rect (0,0,16,16) with {@code oneWayRects} by identity. The
 * player fixture uses the SystemTestBase collision box (offset (-8,-30), 16x60) placed so the feet
 * (transform.y + bounds.y = 16) sit exactly on the tile top. Since MovementSystem is out of scope,
 * {@code movement.grounded} is set explicitly whenever the player is meant to be landed.
 */
public class CrumblingTileSystemTest extends SystemTestBase {

    private static final int TILE = 16;

    private static final float PLAYER_OFFSET_X = -8f;
    private static final float PLAYER_OFFSET_Y = -30f;
    private static final float PLAYER_W = 16f;
    private static final float PLAYER_H = 60f;
    /** Transform Y that puts the 60px-tall player's feet (transform.y + bounds.y) a hair below the
     *  16px tile top (15.5): a true AABB overlap while still satisfying the "feet on top" gate
     *  ({@code >= rect.y + rect.height - 1f}, the standing position of a landed-on one-way platform). */
    private static final float STAND_Y = 45.5f;
    /** Transform X that centers the 16px-wide player AABB over the tile at cell x=0. */
    private static final float STAND_X = 8f;

    /** All the pieces of one fixture, so a test drives the tile through its whole lifecycle. */
    private static class Fixture {
        final Engine engine;
        final CrumblingTileSystem system;
        final Array<Rectangle> oneWayRects;
        final Array<CrumblingTile> crumblingTiles;
        final TiledMapTileLayer layer;
        final TiledMapTileLayer.Cell cell;
        final Rectangle rect;
        final CrumblingTile tile;

        Fixture(Engine engine, CrumblingTileSystem system, Array<Rectangle> oneWayRects,
                Array<CrumblingTile> crumblingTiles, TiledMapTileLayer layer,
                TiledMapTileLayer.Cell cell, Rectangle rect, CrumblingTile tile) {
            this.engine = engine;
            this.system = system;
            this.oneWayRects = oneWayRects;
            this.crumblingTiles = crumblingTiles;
            this.layer = layer;
            this.cell = cell;
            this.rect = rect;
            this.tile = tile;
        }
    }

    /** Builds a wired engine + system with one crumble tile at cell (0,0) → world rect (0,0,16,16). */
    private Fixture newFixture(boolean pooledEngine) {
        Engine engine = pooledEngine ? new PooledEngine() : newEngine();
        Array<Rectangle> oneWayRects = new Array<>();
        Array<CrumblingTile> crumblingTiles = new Array<>();
        TiledMapTileLayer layer = new TiledMapTileLayer(16, 16, TILE, TILE);
        TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
        cell.setTile(new StaticTiledMapTile(new TextureRegion()));
        layer.setCell(0, 0, cell);
        Rectangle rect = new Rectangle(0f, 0f, TILE, TILE);
        oneWayRects.add(rect);
        CrumblingTile tile = new CrumblingTile(layer, 0, 0, cell, rect);
        crumblingTiles.add(tile);
        CrumblingTileSystem system = new CrumblingTileSystem(crumblingTiles, oneWayRects);
        engine.addSystem(system);
        return new Fixture(engine, system, oneWayRects, crumblingTiles, layer, cell, rect, tile);
    }

    /** Player standing on the tile top (feet at 16, AABB overlapping the tile, grounded). */
    private Entity playerStandingOnTile(Fixture fx) {
        TransformComponent transform = transform(STAND_X, STAND_Y);
        CollisionComponent collision = collision(PLAYER_OFFSET_X, PLAYER_OFFSET_Y, PLAYER_W, PLAYER_H);
        place(transform, collision, STAND_X, STAND_Y);
        MovementComponent movement = movement();
        movement.grounded = true;
        Entity entity = entity(transform, player(), movement, collision);
        fx.engine.addEntity(entity);
        return entity;
    }

    /** Moves the player far clear of the tile (no overlap), keeping the same height. */
    private void moveOffTile(Fixture fx, Entity player) {
        place(TRANSFORM.get(player), COLLISION.get(player), 200f, STAND_Y);
    }

    /** Moves the player back onto the tile top (feet on top, grounded). */
    private void moveOntoTile(Fixture fx, Entity player) {
        place(TRANSFORM.get(player), COLLISION.get(player), STAND_X, STAND_Y);
        MOVEMENT.get(player).grounded = true;
    }

    /** Enemy overlapping the tile rect (its AABB (2,2,12,12) sits inside (0,0,16,16)). */
    private Entity enemyOnTile(Fixture fx) {
        TransformComponent transform = transform(8f, 8f);
        CollisionComponent collision = collision(-6f, -6f, 12f, 12f);
        place(transform, collision, 8f, 8f);
        Entity entity = entity(transform, new EnemyComponent(), collision);
        fx.engine.addEntity(entity);
        return entity;
    }

    private int particleEntityCount(Engine engine) {
        int count = 0;
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(ParticleComponent.class) != null) {
                count++;
            }
        }
        return count;
    }

    /** Steps the engine for the given duration in whole 60fps frames. */
    private static void step(Engine engine, float seconds) {
        int frames = (int) Math.ceil(seconds / DT);
        for (int i = 0; i < frames; i++) {
            engine.update(DT);
        }
    }

    /** Steps until either the state matches or the time budget is exhausted. */
    private static void stepUntilState(Fixture fx, CrumblingTile.State state, float maxSeconds) {
        int frames = (int) Math.ceil(maxSeconds / DT);
        for (int i = 0; i < frames && fx.tile.state != state; i++) {
            fx.engine.update(DT);
        }
    }

    // --- INTACT → SHAKING -------------------------------------------------------------

    @Test
    public void landingArmsShakingAfterGrace() {
        Fixture fx = newFixture(false);
        playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + 0.05f);

        assertSame(CrumblingTile.State.SHAKING, fx.tile.state);
        // Shaking is still solid (the rect stands in oneWayRects) until the shake completes, but the
        // cell was blanked at arm — the static tile is redrawn jittered from originalCell instead.
        assertTrue(fx.oneWayRects.contains(fx.tile.rect, true));
        assertNull(fx.layer.getCell(0, 0));
    }

    @Test
    public void grazeDoesNotArm() {
        Fixture fx = newFixture(false);
        Entity player = playerStandingOnTile(fx);

        step(fx.engine, 0.05f); // 3 frames, well under the 0.1s landing grace
        assertSame(CrumblingTile.State.INTACT, fx.tile.state);

        moveOffTile(fx, player);
        step(fx.engine, 1f);

        assertSame(CrumblingTile.State.INTACT, fx.tile.state);
        assertTrue(fx.oneWayRects.contains(fx.tile.rect, true));
        assertNotNull(fx.layer.getCell(0, 0).getTile());
    }

    @Test
    public void dropWindowDoesNotArm() {
        Fixture fx = newFixture(false);
        Entity player = playerStandingOnTile(fx);
        PLAYER.get(player).dropWindow.start(0.25f);

        step(fx.engine, 0.2f); // past the landing grace, but the drop window blocks arming

        assertSame(CrumblingTile.State.INTACT, fx.tile.state);
        assertNotNull(fx.layer.getCell(0, 0).getTile());
    }

    // --- SHAKING → COLLAPSED ----------------------------------------------------------

    @Test
    public void collapseRemovesRectAndBlanksCell() {
        Fixture fx = newFixture(false);
        playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + GameConstants.CRUMBLE_SHAKE_DURATION + 0.05f);

        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);
        assertFalse(fx.oneWayRects.contains(fx.tile.rect, true));
        assertNull(fx.layer.getCell(0, 0));
    }

    @Test
    public void collapseSpawnsSmoke() {
        Fixture fx = newFixture(true);
        playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + GameConstants.CRUMBLE_SHAKE_DURATION + 0.05f);

        // At the collapse moment the smoke puff and the stone-chip burst both spawn (2 ParticleComponent
        // entities) under the PooledEngine. The respawn duration is far longer than this step window, so
        // no third particle can appear here.
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);
        assertEquals(2, particleEntityCount(fx.engine));
    }

    // --- COLLAPSED → respawn ----------------------------------------------------------

    @Test
    public void respawnRestoresRectAndCell() {
        Fixture fx = newFixture(false);
        Entity player = playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + GameConstants.CRUMBLE_SHAKE_DURATION + 0.05f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);

        moveOffTile(fx, player);
        step(fx.engine, GameConstants.CRUMBLE_RESPAWN_DURATION + 0.2f);

        assertSame(CrumblingTile.State.INTACT, fx.tile.state);
        assertTrue(fx.oneWayRects.contains(fx.tile.rect, true));
        assertNotNull(fx.layer.getCell(0, 0));
        assertNotNull(fx.layer.getCell(0, 0).getTile());
    }

    @Test
    public void respawnDeferredWhilePlayerOverlaps() {
        Fixture fx = newFixture(false);
        Entity player = playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + GameConstants.CRUMBLE_SHAKE_DURATION + 0.05f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);

        // Player stays standing over the hole past the full respawn delay.
        step(fx.engine, GameConstants.CRUMBLE_RESPAWN_DURATION + 0.5f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);
        assertNull(fx.layer.getCell(0, 0));

        // Only once the player leaves does the tile come back.
        moveOffTile(fx, player);
        step(fx.engine, GameConstants.CRUMBLE_RESPAWN_DURATION + 0.2f);
        assertSame(CrumblingTile.State.INTACT, fx.tile.state);
        assertTrue(fx.oneWayRects.contains(fx.tile.rect, true));
        assertNotNull(fx.layer.getCell(0, 0).getTile());
    }

    @Test
    public void enemyOccupationDefersRespawn() {
        Fixture fx = newFixture(false);
        Entity player = playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + GameConstants.CRUMBLE_SHAKE_DURATION + 0.05f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);

        // Player clears the hole; the enemy stays behind and alone blocks the respawn.
        moveOffTile(fx, player);
        Entity enemy = enemyOnTile(fx);
        step(fx.engine, GameConstants.CRUMBLE_RESPAWN_DURATION + 0.5f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);
        assertNull(fx.layer.getCell(0, 0));

        fx.engine.removeEntity(enemy);
        step(fx.engine, GameConstants.CRUMBLE_RESPAWN_DURATION + 0.2f);
        assertSame(CrumblingTile.State.INTACT, fx.tile.state);
        assertTrue(fx.oneWayRects.contains(fx.tile.rect, true));
        assertNotNull(fx.layer.getCell(0, 0).getTile());
    }

    // --- settle grace / edge trigger ----------------------------------------------------

    @Test
    public void settleGracePreventsImmediateReArm() {
        Fixture fx = newFixture(false);
        Entity player = playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + GameConstants.CRUMBLE_SHAKE_DURATION + 0.05f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);

        moveOffTile(fx, player);
        stepUntilState(fx, CrumblingTile.State.INTACT, GameConstants.CRUMBLE_RESPAWN_DURATION);
        assertTrue(fx.tile.settleTimer.isActive());
        assertEquals(0f, fx.tile.contactTimer.getRemaining(), EPSILON);

        // Re-land while the settle grace is still running: no immediate re-crumble.
        moveOntoTile(fx, player);
        step(fx.engine, GameConstants.CRUMBLE_SETTLE_GRACE - 0.2f);
        assertSame(CrumblingTile.State.INTACT, fx.tile.state);

        // Leave, wait out the settle grace, re-land → a fresh arming edge works.
        moveOffTile(fx, player);
        step(fx.engine, GameConstants.CRUMBLE_SETTLE_GRACE + 0.1f);
        moveOntoTile(fx, player);
        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + 0.05f);
        assertSame(CrumblingTile.State.SHAKING, fx.tile.state);
    }

    @Test
    public void standingThroughRespawnDoesNotReArm() {
        Fixture fx = newFixture(false);
        Entity player = playerStandingOnTile(fx);

        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + GameConstants.CRUMBLE_SHAKE_DURATION + 0.05f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);

        // Stays overlapping the hole for the whole respawn duration — respawn is deferred.
        step(fx.engine, GameConstants.CRUMBLE_RESPAWN_DURATION + 0.5f);
        assertSame(CrumblingTile.State.COLLAPSED, fx.tile.state);

        // Once the player leaves (falls through and moves away), respawn restores the tile.
        moveOffTile(fx, player);
        step(fx.engine, GameConstants.CRUMBLE_RESPAWN_DURATION + 0.2f);
        assertSame(CrumblingTile.State.INTACT, fx.tile.state);
        assertFalse(fx.tile.wasOverlapped);
        assertEquals(0f, fx.tile.contactTimer.getRemaining(), EPSILON);

        // The standing-through did not arm the fresh tile; a leave+re-land edge re-arms it.
        moveOntoTile(fx, player);
        step(fx.engine, GameConstants.CRUMBLE_LANDING_GRACE + 0.05f);
        assertSame(CrumblingTile.State.SHAKING, fx.tile.state);
        // Still solid while shaking — the rect is only removed when the shake completes.
        assertTrue(fx.oneWayRects.contains(fx.tile.rect, true));
    }
}
