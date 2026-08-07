package com.axehigh.platformer.map;

import com.axehigh.platformer.particles.ParticleHelper;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Reveals a secret room the moment one of its breakable walls is destroyed: blanks the room's
 * {@code secret_hide} veil cells (so the cavity's painted floor/decoration appear), spawns the
 * room's deferred object/enemy markers via {@code EntityFactory.spawnObjects} (so loot/enemies do
 * not exist — and cannot be seen, picked up, or fought — before the wall breaks), and pops a smoke
 * puff at each veil cell so the reveal reads as the wall crumbling. Reveal is per-room and
 * idempotent: {@link SecretRoom#revealed} gates it, so breaking a second wall of the same room just
 * breaks that wall. A plain class (not an Ashley system): reveal is a one-shot trigger, not a
 * per-frame behavior. Held by {@code MeleeAttackSystem} and refreshed per level by {@code
 * LevelManager} ({@link #setRooms(Array)} + {@link #setHideLayer(TiledMapTileLayer)}).
 */
public class SecretRoomRevealer {
    private final PooledEngine engine;
    private final EntityFactory entityFactory;
    private final RoomState roomState;
    private final ObjectMap<String, SecretRoom> roomsByName = new ObjectMap<>();

    private TiledMapTileLayer hideLayer;

    public SecretRoomRevealer(PooledEngine engine, EntityFactory entityFactory, RoomState roomState) {
        this.engine = engine;
        this.entityFactory = entityFactory;
        this.roomState = roomState;
    }

    /** Replaces the per-level secret-room set (from {@code MapLoader.getSecretRooms()}). */
    public void setRooms(Array<SecretRoom> rooms) {
        roomsByName.clear();
        for (SecretRoom room : rooms) {
            roomsByName.put(room.name, room);
        }
    }

    /** The current level's {@code secret_hide} tile layer (may be null on a map without secret rooms). */
    public void setHideLayer(TiledMapTileLayer hideLayer) {
        this.hideLayer = hideLayer;
    }

    /** Whether a room with this name exists and has been revealed already. */
    public boolean isRevealed(String roomName) {
        SecretRoom room = roomsByName.get(roomName);
        return room != null && room.revealed;
    }

    /**
     * Reveals the named room (no-op when unknown or already revealed): blanks every veil cell,
     * spawns the deferred markers, and pops a smoke puff at each blanked cell.
     */
    public void reveal(String roomName) {
        SecretRoom room = roomsByName.get(roomName);
        if (room == null || room.revealed) {
            return;
        }
        room.revealed = true;

        if (hideLayer != null) {
            for (Rectangle veilCell : room.veilCells) {
                blankVeilCell(veilCell);
            }
        }

        if (room.deferredObjects.getCount() > 0) {
            entityFactory.spawnObjects(engine, room.deferredObjects, roomState);
        }

        for (Rectangle veilCell : room.veilCells) {
            ParticleHelper.spawnSmallSmoke(engine,
                veilCell.x + veilCell.width / 2f, veilCell.y + veilCell.height / 2f);
        }
    }

    private void blankVeilCell(Rectangle rect) {
        int tileX = (int) (rect.x / hideLayer.getTileWidth());
        int tileY = (int) (rect.y / hideLayer.getTileHeight());
        TiledMapTileLayer.Cell cell = hideLayer.getCell(tileX, tileY);
        if (cell != null) {
            cell.setTile(null);
        }
    }
}
