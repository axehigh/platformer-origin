package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.Room;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.OrthographicCamera;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;

/**
 * Headless unit tests for {@code CameraSystem}'s hybrid flip-screen / dead-zone scroll framing,
 * using the same world-scaled viewport the game uses (3840x2176). Covers: flip rooms locked to
 * their center, dead-zone hold/chase/clamp in scroll rooms, the instant room-change snap, the
 * {@code SCROLL}-on-small-room centering fallback, the no-room fallback, and the static
 * {@code snapToRoom} start framing.
 */
public class CameraSystemTest extends SystemTestBase {

    private static final float VIEW_W = 3840f;
    private static final float VIEW_H = 2176f;

    private final RoomState roomState = new RoomState();
    private Engine engine;
    private OrthographicCamera camera;
    private CameraSystem system;

    @Before
    public void setUp() {
        camera = new OrthographicCamera(VIEW_W, VIEW_H);
        system = new CameraSystem(camera, roomState);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        Entity entity = entity(transform(x, y), player());
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void flipRoomCentersCamera() {
        roomState.rooms.add(new Room(0f, 0f, 100f, 100f, Room.Mode.AUTO));
        player(10f, 10f);

        engine.update(DT);

        assertEquals(50f, camera.position.x, EPSILON);
        assertEquals(50f, camera.position.y, EPSILON);
        assertEquals(0, roomState.activeRoomIndex);
    }

    @Test
    public void forcedScrollOnSmallRoomStillCenters() {
        roomState.rooms.add(new Room(0f, 0f, 100f, 100f, Room.Mode.SCROLL));
        player(10f, 10f);

        engine.update(DT);

        assertEquals(50f, camera.position.x, EPSILON);
        assertEquals(50f, camera.position.y, EPSILON);
    }

    @Test
    public void scrollRoomHoldsWhilePlayerStaysInDeadZone() {
        roomState.rooms.add(new Room(0f, 0f, 4096f, 2176f));
        Entity player = player(100f, 100f);
        TransformComponent transform = TRANSFORM.get(player);

        engine.update(DT);
        assertEquals(1920f, camera.position.x, EPSILON);
        assertEquals(1088f, camera.position.y, EPSILON);

        transform.position.x = 1000f;
        engine.update(DT);

        assertEquals(1920f, camera.position.x, EPSILON);
        assertEquals(1088f, camera.position.y, EPSILON);
    }

    @Test
    public void scrollRoomChasesPlayerPastMargin() {
        roomState.rooms.add(new Room(0f, 0f, 4096f, 2176f));
        Entity player = player(100f, 100f);
        TransformComponent transform = TRANSFORM.get(player);

        engine.update(DT);
        assertEquals(1920f, camera.position.x, EPSILON);

        transform.position.x = 3600f;
        engine.update(DT);

        assertEquals(2000f, camera.position.x, EPSILON);
    }

    @Test
    public void scrollRoomClampsToRoomEdge() {
        roomState.rooms.add(new Room(0f, 0f, 4096f, 2176f));
        Entity player = player(100f, 100f);
        TransformComponent transform = TRANSFORM.get(player);

        engine.update(DT);
        assertEquals(1920f, camera.position.x, EPSILON);

        transform.position.x = 4096f;
        engine.update(DT);

        assertEquals(2176f, camera.position.x, EPSILON);
    }

    @Test
    public void roomChangeSnapsInstantly() {
        roomState.rooms.add(new Room(0f, 0f, 2048f, 2176f, Room.Mode.AUTO));
        roomState.rooms.add(new Room(2048f, 0f, 2048f, 2176f, Room.Mode.AUTO));
        Entity player = player(100f, 100f);
        TransformComponent transform = TRANSFORM.get(player);

        engine.update(DT);
        assertEquals(1024f, camera.position.x, EPSILON);

        transform.position.x = 3000f;
        engine.update(DT);

        assertEquals(3072f, camera.position.x, EPSILON);
        assertEquals(1, roomState.activeRoomIndex);
    }

    @Test
    public void noRoomLeavesCameraAtPlayer() {
        player(100f, 50f);

        engine.update(DT);

        assertEquals(100f, camera.position.x, EPSILON);
        assertEquals(50f, camera.position.y, EPSILON);
    }

    @Test
    public void snapToRoomFramesStartingFlipRoom() {
        roomState.rooms.add(new Room(0f, 0f, 2048f, 2176f, Room.Mode.FLIP));

        CameraSystem.snapToRoom(camera, roomState, 100f, 100f);

        assertEquals(1024f, camera.position.x, EPSILON);
        assertEquals(1088f, camera.position.y, EPSILON);
        assertEquals(0, roomState.activeRoomIndex);
    }
}
