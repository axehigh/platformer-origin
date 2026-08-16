package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.Room;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Hybrid flip-screen / dead-zone scroll camera driven by the map's "Rooms" rectangles (see {@code
 * RoomState}/{@code MapLoader#getRooms()}). Each room's camera mode is resolved per axis from its
 * {@link Room.Mode} (defaulting to inference by size): a flip axis (room no bigger than the
 * viewport on that axis) keeps the camera locked to the room's center — static, with the viewport
 * allowed to overshoot the room's edges — and snaps instantly to that center when the player
 *  enters the room; a scroll axis (room bigger than the viewport) holds the camera still while the
 *  player roams anywhere inside a dead zone (each screen edge inset by a margin — the fixed {@code
 *  GameConstants.CAMERA_SCROLL_MARGIN} at zoom 1, or a fraction of the effective view when zoomed,
 *  see {@code scrollMargin}) and only scrolls once the player crosses that margin,
 * clamped so the viewport never shows anything past the room's own edges. A map with no "Rooms"
 * layer is treated as a single room covering the whole map (see {@code MapLoader#getRooms()}), so
 * it scrolls exactly like a normal room. Replaces the old fixed VIRTUAL_WIDTH/HEIGHT flip-screen
 * grid entirely. The camera compares rooms against the *effective* (zoomed) view size
 * ({@code camera.viewportWidth/Height * camera.zoom}), so the shipped {@code BAND_ZOOM} layout
 * (default on every platform) makes screen-sized rooms scroll like any bigger-than-frame room — see
 * {@code com.axehigh.platformer.ui.LayoutMode}.
 */
public class CameraSystem extends EntitySystem {
    private final OrthographicCamera camera;
    private final RoomState roomState;
    private ImmutableArray<Entity> players;
    private int lastActiveRoomIndex = -1;

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
        updateCamera(camera, roomState, transform.position.x, transform.position.y);
    }

    private void updateCamera(OrthographicCamera camera, RoomState roomState, float x, float y) {
        int index = roomState.findRoomIndexContaining(x, y);
        if (index >= 0) {
            roomState.activeRoomIndex = index;
        }

        Room room = (roomState.activeRoomIndex >= 0 && roomState.activeRoomIndex < roomState.rooms.size)
            ? roomState.rooms.get(roomState.activeRoomIndex)
            : null;

        if (room == null) {
            camera.position.set(x, y, 0f);
            camera.update();
            return;
        }

        boolean roomChanged = roomState.activeRoomIndex != lastActiveRoomIndex;
        lastActiveRoomIndex = roomState.activeRoomIndex;

        float viewW = camera.viewportWidth * camera.zoom;
        float viewH = camera.viewportHeight * camera.zoom;
        float marginX = scrollMargin(viewW, camera.zoom);
        float marginY = scrollMargin(viewH, camera.zoom);

        float camX = roomChanged
            ? frameAxis(x, room.x, room.width, viewW, room)
            : deadZoneAxis(camera.position.x, x, room.x, room.width, viewW, room, marginX);
        float camY = roomChanged
            ? frameAxis(y, room.y, room.height, viewH, room)
            : deadZoneAxis(camera.position.y, y, room.y, room.height, viewH, room, marginY);

        camera.position.set(camX, camY, 0f);
        camera.update();
    }

    /**
     * Frames the camera for a just-entered room (instant snap / level start) from the player's
     * position: scroll axes clamp so the player stays in view, flip axes center on the room.
     */
    private static float frameAxis(float playerPos, float roomMin, float roomSize, float viewportSize, Room room) {
        if (isScrollAxis(room, roomSize, viewportSize)) {
            return clampAxis(playerPos, roomMin, roomSize, viewportSize);
        }
        return roomMin + roomSize / 2f;
    }

    /**
     * Dead-zone camera axis: the camera keeps its current position while the player stays within a
     * screen-edge margin, chases only when the player crosses that margin (keeping the player at
     * the margin line), and is clamped so the viewport never crosses the room's edges. Flip axes
     * simply stay centered on the room.
     */
    private static float deadZoneAxis(float currentCam, float playerPos, float roomMin, float roomSize,
                                      float viewportSize, Room room, float margin) {
        if (!isScrollAxis(room, roomSize, viewportSize)) {
            return roomMin + roomSize / 2f;
        }
        float half = viewportSize / 2f;
        float cam = currentCam;
        float leftLine = cam - half + margin;
        float rightLine = cam + half - margin;
        if (playerPos < leftLine) {
            cam = playerPos + half - margin;
        } else if (playerPos > rightLine) {
            cam = playerPos - half + margin;
        }
        return clampAxis(cam, roomMin, roomSize, viewportSize);
    }

    /**
     * The dead-zone margin for an axis: at zoom 1 it is the fixed {@code CAMERA_SCROLL_MARGIN}
     * (world units); once the camera zooms in (mobile {@code BAND_ZOOM}) it becomes a fraction of
     * the effective (zoomed) view size so the player keeps tracking well clear of the edges.
     */
    private static float scrollMargin(float viewportSize, float zoom) {
        return zoom < 1f
            ? viewportSize * GameConstants.MOBILE_SCROLL_MARGIN_FRACTION
            : GameConstants.CAMERA_SCROLL_MARGIN;
    }

    /**
     * Whether the camera scrolls (dead-zone) on an axis for the given room, honoring {@link
     * Room.Mode}. A room can only ever scroll on an axis it is actually bigger than the viewport
     * on, so a forced {@code SCROLL} room smaller than the screen still falls back to centering.
     */
    private static boolean isScrollAxis(Room room, float roomSize, float viewportSize) {
        if (roomSize <= viewportSize || room.mode == Room.Mode.FLIP) {
            return false;
        }
        return room.mode == Room.Mode.SCROLL || room.mode == Room.Mode.AUTO;
    }

    /** Clamps the camera's center along one axis so the viewport never leaves the room's bounds. */
    private static float clampAxis(float value, float roomMin, float roomSize, float viewportSize) {
        float half = viewportSize / 2f;
        return MathUtils.clamp(value, roomMin + half, roomMin + roomSize - half);
    }

    /**
     * Updates {@code roomState.activeRoomIndex} (if the given point lies within a known room) and
     * frames {@code camera} for that room: flip axes center on the room, scroll axes clamp the
     * player position within the room's bounds. Static so {@code GameScreen}/{@code LevelManager}
     * can reuse the exact same placement logic right after a level swap/start.
     */
    public static void snapToRoom(OrthographicCamera camera, RoomState roomState, float x, float y) {
        int index = roomState.findRoomIndexContaining(x, y);
        if (index >= 0) {
            roomState.activeRoomIndex = index;
        }

        Room room = (roomState.activeRoomIndex >= 0 && roomState.activeRoomIndex < roomState.rooms.size)
            ? roomState.rooms.get(roomState.activeRoomIndex)
            : null;

        float viewW = camera.viewportWidth * camera.zoom;
        float viewH = camera.viewportHeight * camera.zoom;

        float camX = room != null ? frameAxis(x, room.x, room.width, viewW, room) : x;
        float camY = room != null ? frameAxis(y, room.y, room.height, viewH, room) : y;
        camera.position.set(camX, camY, 0f);
        camera.update();
    }
}
