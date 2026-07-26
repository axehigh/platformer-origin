package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Room-based camera: tracks which Tiled "Rooms" rectangle (see {@code RoomState}/{@code
 * MapLoader#getRooms()}) currently contains the player, then clamps the camera's fixed
 * VIRTUAL_WIDTH/HEIGHT viewport within that room's edges every frame. A room no bigger than the
 * viewport on an axis keeps the camera centered on the room on that axis (static, no scrolling,
 * with the viewport allowed to overshoot the room's edges); a bigger room lets the camera
 * continuously follow the player while never showing anything past the room's own edges. Replaces
 * the previous fixed VIRTUAL_WIDTH/HEIGHT flip-screen grid entirely.
 */
public class CameraSystem extends EntitySystem {
    private final OrthographicCamera camera;
    private final RoomState roomState;
    private ImmutableArray<Entity> players;

    public CameraSystem(OrthographicCamera camera, RoomState roomState) {
        this(camera, roomState, 0);
    }

    public CameraSystem(OrthographicCamera camera, RoomState roomState, int priority) {
        super(priority);
        this.camera = camera;
        this.roomState = roomState;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() == 0) {
            return;
        }

        TransformComponent transform = TRANSFORM.get(players.first());
        snapToRoom(camera, roomState, transform.position.x, transform.position.y);
    }

    /**
     * Updates {@code roomState.activeRoomIndex} (if the given point lies within a known room) and
     * repositions/clamps {@code camera} to that room's bounds. Static so {@code LevelManager} can
     * reuse the exact same placement logic right after a level swap.
     */
    public static void snapToRoom(OrthographicCamera camera, RoomState roomState, float x, float y) {
        int index = roomState.findRoomIndexContaining(x, y);
        if (index >= 0) {
            roomState.activeRoomIndex = index;
        }

        Rectangle room = (roomState.activeRoomIndex >= 0 && roomState.activeRoomIndex < roomState.rooms.size)
            ? roomState.rooms.get(roomState.activeRoomIndex)
            : null;

        float camX = room != null ? clampAxis(x, room.x, room.width, camera.viewportWidth) : x;
        float camY = room != null ? clampAxis(y, room.y, room.height, camera.viewportHeight) : y;
        camera.position.set(camX, camY, 0f);
        camera.update();
    }

    /**
     * Clamps the camera's center along one axis to the room's edges; if the room is no bigger
     * than the viewport on this axis, centers on the room instead (letting the viewport overshoot
     * the room's edges rather than clamping into an impossible, too-small range).
     */
    private static float clampAxis(float playerPos, float roomMin, float roomSize, float viewportSize) {
        if (roomSize <= viewportSize) {
            return roomMin + roomSize / 2f;
        }
        float half = viewportSize / 2f;
        return MathUtils.clamp(playerPos, roomMin + half, roomMin + roomSize - half);
    }
}
