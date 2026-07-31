package com.axehigh.platformer.map;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * Shared, live room state for the current level: the list of {@link Room} rectangles parsed from the
 * map's "Rooms" object layer (see {@code MapLoader#getRooms()}; a map with no such layer yields a
 * single room covering the whole map), and the index of whichever one currently contains the
 * player, kept up to date every frame by {@code CameraSystem}. Held as a single shared instance
 * (same pattern as the shared {@code collisionRects} array) so {@code EnemySystem}/{@code
 * EnemyShootSystem} can cheaply tell whether their owning room is the active one, and {@code
 * LevelManager} can refill {@code rooms} in place on a level swap without re-wiring any system.
 */
public class RoomState {
    public final Array<Room> rooms = new Array<>();
    /** Index into {@code rooms} of whichever Room rectangle currently contains the player, or -1 if none does. */
    public int activeRoomIndex = -1;

    /** Returns the index of the first Room rectangle containing world point (x, y), or -1 if none does. */
    public int findRoomIndexContaining(float x, float y) {
        for (int i = 0; i < rooms.size; i++) {
            if (rooms.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }
}
