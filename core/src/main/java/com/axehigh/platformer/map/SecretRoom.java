package com.axehigh.platformer.map;

import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * A secret room discovered from the map's secret-wall and object markers: the {@code Rooms}-layer
 * rectangle whose name matches the wall tile's {@code secretRoom} property (the room's footprint,
 * {@code null} when the author never dropped a matching room rect), the breakable secret-wall rects
 * that guard it, the veil cells of the {@code secret_hide} tile layer covering the footprint, and
 * the object-layer markers whose {@code secretRoom} property names this room (partitioned out of the
 * main spawn layers at load so they only exist once the room is revealed). {@link
 * SecretRoomRevealer} flips {@link #revealed} once any of {@link #walls} is broken, blanks {@link
 * #veilCells}, and spawns {@link #deferredObjects}.
 */
public class SecretRoom {
    /** Name shared by the room's secret-wall tiles, deferred markers, and {@code Rooms}-layer rect. */
    public final String name;
    /** The {@code Rooms}-layer footprint (may be null if the author never placed a matching rect). */
    public final Room room;
    /** Breakable secret-wall world rects guarding this room (collision-layer cells whose tile is {@code secretRoom}=this.name). */
    public final Array<Rectangle> walls = new Array<>();
    /** World rects of the {@code secret_hide} veil cells covering {@link #room}. */
    public final Array<Rectangle> veilCells = new Array<>();
    /** Object/enemy markers to spawn the first time the room is revealed. */
    public final MapObjects deferredObjects = new MapObjects();
    /** True once the room has been revealed (idempotent: veil blanked + objects spawned once). */
    public boolean revealed = false;

    public SecretRoom(String name, Room room) {
        this.name = name;
        this.room = room;
    }
}
