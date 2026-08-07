package com.axehigh.platformer.map;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the {@code MapLoader} secret-room parsing: walls carrying a
 * {@code secretRoom} tile property are grouped with the matching {@code Rooms}-layer rect and the
 * {@code secret_hide} veil cells overlapping it, while object/enemy markers carrying the
 * {@code secretRoom} object property are partitioned OUT of the normal spawn layers into the room's
 * deferred set. Maps are built from plain libGDX objects, so no .tmx file is needed.
 */
public class MapLoaderTest {

    private static final int TILE = 128;

    private TiledMap map;
    private TiledMapTileLayer collisionLayer;
    private TiledMapTileLayer hideLayer;
    private MapLayer objectsLayer;
    private MapLayer enemiesLayer;
    private MapLayer roomsLayer;

    @Before
    public void setUp() {
        map = new TiledMap();
        map.getProperties().put("width", 20);
        map.getProperties().put("height", 10);
        map.getProperties().put("tilewidth", TILE);
        map.getProperties().put("tileheight", TILE);

        collisionLayer = new TiledMapTileLayer(20, 10, TILE, TILE);
        collisionLayer.setName("collision");
        placeTile(collisionLayer, 0, 0, solidTile());
        map.getLayers().add(collisionLayer);

        hideLayer = new TiledMapTileLayer(20, 10, TILE, TILE);
        hideLayer.setName("secret_hide");
        for (int x = 10; x <= 14; x++) {
            for (int y = 0; y < 10; y++) {
                placeTile(hideLayer, x, y, solidTile());
            }
        }
        map.getLayers().add(hideLayer);

        objectsLayer = new MapLayer();
        objectsLayer.setName("objects");
        MapObject normalCoin = rectObject("coin_0", 0f, 0f, TILE, TILE);
        objectsLayer.getObjects().add(normalCoin);
        MapObject deferredCoin = rectObject("coin_secretRoom1", 1400f, 64f, TILE, TILE);
        deferredCoin.getProperties().put("secretRoom", "secretRoom1");
        objectsLayer.getObjects().add(deferredCoin);
        map.getLayers().add(objectsLayer);

        enemiesLayer = new MapLayer();
        enemiesLayer.setName("enemies");
        MapObject deferredEnemy = rectObject("enemy_secretRoom1", 1536f, 64f, TILE, TILE);
        deferredEnemy.getProperties().put("secretRoom", "secretRoom1");
        enemiesLayer.getObjects().add(deferredEnemy);
        map.getLayers().add(enemiesLayer);

        roomsLayer = new MapLayer();
        roomsLayer.setName("Rooms");
        roomsLayer.getObjects().add(rectObject("room0", 0f, 0f, 10 * TILE, 10 * TILE));
        roomsLayer.getObjects().add(rectObject("secretRoom1", 10 * TILE, 0f, 5 * TILE, 10 * TILE));
        map.getLayers().add(roomsLayer);
    }

    private static StaticTiledMapTile solidTile() {
        return new StaticTiledMapTile(new TextureRegion());
    }

    private static void placeTile(TiledMapTileLayer layer, int x, int y, StaticTiledMapTile tile) {
        TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
        cell.setTile(tile);
        layer.setCell(x, y, cell);
    }

    private static RectangleMapObject rectObject(String name, float x, float y, float width, float height) {
        RectangleMapObject object = new RectangleMapObject(x, y, width, height);
        object.setName(name);
        return object;
    }

    private void addSecretWall(int x, int y, String roomName) {
        StaticTiledMapTile tile = solidTile();
        tile.getProperties().put("secret", true);
        tile.getProperties().put("secretRoom", roomName);
        placeTile(collisionLayer, x, y, tile);
    }

    private static MapLoader loader(TiledMap tiledMap) {
        return new MapLoader(tiledMap, "test.tmx");
    }

    @Test
    public void discoversSecretRoomWithWallsVeilAndDeferredObjects() {
        addSecretWall(10, 1, "secretRoom1");

        MapLoader loader = loader(map);
        Array<SecretRoom> rooms = loader.getSecretRooms();

        assertEquals(1, rooms.size);
        SecretRoom room = rooms.first();
        assertEquals("secretRoom1", room.name);
        assertNotNull(room.room);
        assertEquals(10 * TILE, room.room.x, 0f);
        assertEquals(5 * TILE, room.room.width, 0f);
        assertEquals(1, room.walls.size);
        Rectangle wall = room.walls.first();
        assertEquals(10 * TILE, wall.x, 0f);
        assertEquals(1 * TILE, wall.y, 0f);
        assertEquals(50, room.veilCells.size);
        assertEquals(2, room.deferredObjects.getCount());
        assertEquals(1, loader.getSecretRects().size);
        assertEquals(hideLayer, loader.getSecretHideLayer());
    }

    @Test
    public void partitionsDeferredMarkersOutOfSpawnLayers() {
        addSecretWall(10, 1, "secretRoom1");

        MapLoader loader = loader(map);
        SecretRoom room = loader.getSecretRooms().first();

        assertEquals(1, objectsLayer.getObjects().getCount());
        assertEquals("coin_0", objectsLayer.getObjects().get(0).getName());
        assertEquals(0, enemiesLayer.getObjects().getCount());
        assertEquals(2, room.deferredObjects.getCount());
    }

    @Test
    public void secretRoomWithoutRoomsRectStillKeepsWallsAndDeferred() {
        while (roomsLayer.getObjects().getCount() > 0) {
            roomsLayer.getObjects().remove(0);
        }
        roomsLayer.getObjects().add(rectObject("room0", 0f, 0f, 10 * TILE, 10 * TILE));
        addSecretWall(10, 1, "secretRoom1");

        SecretRoom room = loader(map).getSecretRooms().first();

        assertNull(room.room);
        assertEquals(1, room.walls.size);
        assertEquals(0, room.veilCells.size);
        assertEquals(2, room.deferredObjects.getCount());
    }

    @Test
    public void mapWithoutSecretHideLayerStillFindsRoomsAndDefers() {
        map.getLayers().remove(hideLayer);
        addSecretWall(10, 1, "secretRoom1");

        MapLoader loader = loader(map);
        SecretRoom room = loader.getSecretRooms().first();

        assertNull(loader.getSecretHideLayer());
        assertEquals(1, room.walls.size);
        assertEquals(0, room.veilCells.size);
        assertEquals(2, room.deferredObjects.getCount());
    }

    @Test
    public void secretTileWithoutSecretRoomPropertyIsNotGrouped() {
        addSecretWall(10, 1, "secretRoom1");
        StaticTiledMapTile loneSecret = solidTile();
        loneSecret.getProperties().put("secret", true);
        placeTile(collisionLayer, 2, 2, loneSecret);

        MapLoader loader = loader(map);

        assertEquals(2, loader.getSecretRects().size);
        assertEquals(1, loader.getSecretRooms().size);
        assertEquals(1, loader.getSecretRooms().first().walls.size);
    }

    @Test
    public void veilCellsOnlyCoverSecretRoomFootprint() {
        addSecretWall(10, 1, "secretRoom1");

        SecretRoom room = loader(map).getSecretRooms().first();

        assertTrue(room.veilCells.size > 0);
        for (Rectangle veilCell : room.veilCells) {
            assertTrue(veilCell.x >= 10 * TILE);
            assertTrue(veilCell.x + veilCell.width <= 15 * TILE);
        }
    }
}
