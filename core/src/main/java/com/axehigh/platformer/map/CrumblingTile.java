package com.axehigh.platformer.map;

import com.axehigh.platformer.util.Timer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;

/**
 * Runtime state holder for one crumbling platform tile, parsed by {@code MapLoader} from a
 * collision-layer tile flagged {@code crumble=true}. The tile is landable like a one-way platform
 * (its world rect lives in the shared {@code oneWayRects} array) until the player stands on it long
 * enough: the static cell is blanked at the moment it starts shaking (redrawn jittered from this
 * captured {@code originalCell} by {@code CrumblingTileRenderSystem}), then it collapses (the rect
 * is removed from {@code oneWayRects}) and respawns after a delay. {@code CrumblingTileSystem}
 * drives the state machine; each {@code Timer} field tracks one phase of the shake→collapse→respawn
 * cycle.
 */
public class CrumblingTile {
    /** The playback state of the shake → collapse → respawn cycle. */
    public enum State {
        /** Standing and full; arms the shake when the player satisfies the contact/grounding conditions. */
        INTACT,
        /** Shaking; still solid (rect stands in {@code oneWayRects}), but the cell is blanked and the tile redrawn jittered. */
        SHAKING,
        /** Gone (rect removed from {@code oneWayRects}, cell already blanked since arm); respawns once {@link #respawnTimer} elapses and the cell is clear. */
        COLLAPSED
    }

    /** The collision layer this tile lives on; its cell is blanked on collapse and restored on respawn. */
    public final TiledMapTileLayer layer;
    /** Grid column of the tile on {@link #layer}. */
    public final int cellX;
    /** Grid row of the tile on {@link #layer}. */
    public final int cellY;
    /** The cell captured at map build time; restored on respawn. */
    public final TiledMapTileLayer.Cell originalCell;
    /** World-space full-tile rect; THE SAME instance also present in {@code oneWayRects}, so identity add/remove works. */
    public final Rectangle rect;

    /** Current phase of the crumble cycle; {@link State#INTACT} until the player arms it. */
    public State state = State.INTACT;
    /** Counts down the visible shake before collapse. */
    public final Timer shakeTimer = new Timer();
    /** Counts down before the tile respawns after collapsing. */
    public final Timer respawnTimer = new Timer();
    /** Arming grace (graze guard): measures continuous contact; reset whenever contact breaks. */
    public final Timer contactTimer = new Timer();
    /** Post-respawn settle grace: blocks re-arming while the player is still close after respawn. */
    public final Timer settleTimer = new Timer();
    /** Edge-trigger guard: last frame's overlap state, so arming needs a fresh contact entry. */
    public boolean wasOverlapped = false;

    public CrumblingTile(TiledMapTileLayer layer, int cellX, int cellY, TiledMapTileLayer.Cell originalCell, Rectangle rect) {
        this.layer = layer;
        this.cellX = cellX;
        this.cellY = cellY;
        this.originalCell = originalCell;
        this.rect = rect;
    }
}