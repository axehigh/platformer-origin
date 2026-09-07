package com.axehigh.platformer.map;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Parses every real {@code assets/maps/world3/*.tmx} (level_01..level_10_final) with a
 * texture-light parse, mirroring {@link CrumblingTileRealMapTest}: external .tsx tilesets are
 * resolved relative to the map dir and rebuilt as libGDX {@link TiledMapTileSet}s keyed by
 * firstgid, tile layers get their real CSV cells (no-arg {@link TextureRegion} tiles), and object
 * groups become named {@link MapLayer}s of {@link RectangleMapObject}s with flipped Y (as
 * TmxMapLoader does). The real {@link MapLoader} then runs over each map, and the world-3 level
 * contract is asserted: 60x10 arena, single whole-map "room0", one playerStart, one exitGate
 * chaining to the next level (or final), no spawn markers, and a fully solid collision border with
 * an empty interior. No GL / Gdx.app required.
 */
public class World3MapsLoadTest {

    private static final int EXPECTED_WIDTH = 60;
    private static final int EXPECTED_HEIGHT = 10;
    private static final File BASE = findBase();

    private static File findBase() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 8 && dir != null; i++) {
            File candidate = new File(dir, "assets/maps/world3");
            if (candidate.isDirectory()) return candidate;
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("assets/maps/world3 not found from " + System.getProperty("user.dir"));
    }

    private static XmlReader.Element readXml(File file) {
        return new XmlReader().parse(new FileHandle(file.getAbsolutePath()));
    }

    @Test
    public void allTenWorld3MapsLoadThroughRealMapLoader() throws Exception {
        String[] names = BASE.list();
        assertNotNull("world3 dir must list", names);
        assertEquals("exactly 10 world-3 maps", 10, names.length);

        File[] files = BASE.listFiles((dir, name) -> name.endsWith(".tmx"));
        Arrays.sort(files);
        assertEquals("no non-tmx strays expected", 10, files.length);
        for (File file : files) {
            verifyMap(file);
        }
    }

    private void verifyMap(File file) throws Exception {
        String name = file.getName();
        String msg = name + ": ";
        XmlReader.Element tmx = readXml(file);
        int mapWidth = tmx.getIntAttribute("width");
        int mapHeight = tmx.getIntAttribute("height");
        int tileWidth = tmx.getIntAttribute("tilewidth");
        int tileHeight = tmx.getIntAttribute("tileheight");
        float mapHeightWorld = mapHeight * tileHeight;

        TiledMap map = new TiledMap();
        map.getProperties().put("width", mapWidth);
        map.getProperties().put("height", mapHeight);
        map.getProperties().put("tilewidth", tileWidth);
        map.getProperties().put("tileheight", tileHeight);

        // --- Tilesets: external .tsx files, keyed by (firstgid + localId) as libGDX expects.
        for (XmlReader.Element ts : tmx.getChildrenByName("tileset")) {
            int firstGid = ts.getIntAttribute("firstgid", 1);
            TiledMapTileSet set = new TiledMapTileSet();
            if (ts.getAttribute("source", null) != null) {
                File tsxFile = new File(BASE, ts.getAttribute("source")).getCanonicalFile();
                String baseName = new File(ts.getAttribute("source")).getName();
                XmlReader.Element tsx = readXml(tsxFile);
                set.setName(tsx.getAttribute("name", baseName));
                for (XmlReader.Element tileEl : tsx.getChildrenByName("tile")) {
                    putTileWithProps(set, firstGid, tileEl);
                }
            } else {
                set.setName(ts.getAttribute("name", "inline"));
                for (XmlReader.Element tileEl : ts.getChildrenByName("tile")) {
                    putTileWithProps(set, firstGid, tileEl);
                }
            }
            map.getTileSets().addTileSet(set);
        }

        // --- Tile layers: real CSV cells in every layer, gids resolved through libGDX.
        for (XmlReader.Element layerEl : tmx.getChildrenByName("layer")) {
            String layerName = layerEl.getAttribute("name");
            TiledMapTileLayer layer = new TiledMapTileLayer(mapWidth, mapHeight, tileWidth, tileHeight);
            layer.setName(layerName);
            XmlReader.Element data = layerEl.getChildByName("data");
            String[] rows = data.getText().trim().split("\n");
            assertEquals(msg + layerName + " row count", mapHeight, rows.length);
            for (int y = 0; y < mapHeight; y++) {
                String[] cols = rows[y].trim().split(",");
                assertEquals(msg + layerName + " col count", mapWidth, cols.length);
                for (int x = 0; x < mapWidth; x++) {
                    int gid = Integer.parseInt(cols[x].trim());
                    if (gid == 0) continue;
                    TiledMapTile tile = map.getTileSets().getTile(gid);
                    assertNotNull(msg + layerName + "(" + x + "," + y + ") gid " + gid + " must resolve", tile);
                    TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                    cell.setTile(tile);
                    layer.setCell(x, y, cell);
                }
            }
            map.getLayers().add(layer);
        }

        // --- Object groups: named MapLayers of RectangleMapObjects with Y flipped to world space.
        for (XmlReader.Element groupEl : tmx.getChildrenByName("objectgroup")) {
            MapLayer group = new MapLayer();
            group.setName(groupEl.getAttribute("name"));
            for (XmlReader.Element objEl : groupEl.getChildrenByName("object")) {
                float x = objEl.getFloatAttribute("x", 0f);
                float y = objEl.getFloatAttribute("y", 0f);
                float w = objEl.getFloatAttribute("width", 0f);
                float h = objEl.getFloatAttribute("height", 0f);
                RectangleMapObject obj = new RectangleMapObject(x, mapHeightWorld - y - h, w, h);
                String oName = objEl.getAttribute("name", null);
                if (oName != null) obj.setName(oName);
                String type = objEl.getAttribute("type", null);
                if (type != null) obj.getProperties().put("type", type);
                XmlReader.Element props = objEl.getChildByName("properties");
                if (props != null) {
                    for (XmlReader.Element prop : props.getChildrenByName("property")) {
                        obj.getProperties().put(prop.getAttribute("name"), prop.getAttribute("value", ""));
                    }
                }
                group.getObjects().add(obj);
            }
            map.getLayers().add(group);
        }

        MapLoader loader = new MapLoader(map, "maps/world3/" + name);

        // 1. Map width/height (tile layers).
        assertEquals(msg + "map width", EXPECTED_WIDTH, mapWidth);
        assertEquals(msg + "map height", EXPECTED_HEIGHT, mapHeight);
        assertEquals(msg + "tilewidth", tileWidth, loader.getTileWidth(), 0f);
        assertEquals(msg + "tileheight", tileHeight, loader.getTileHeight(), 0f);

        // 2. Exactly one room, named "room0" in the Rooms layer, covering the whole map.
        MapLayer roomsLayer = map.getLayers().get("Rooms");
        assertNotNull(msg + "Rooms layer", roomsLayer);
        assertEquals(msg + "one room marker", 1, roomsLayer.getObjects().getCount());
        assertEquals(msg + "room name", "room0", roomsLayer.getObjects().get(0).getName());
        Array<Room> rooms = loader.getRooms();
        assertEquals(msg + "one parsed room", 1, rooms.size);
        Room room = rooms.first();
        assertEquals(msg + "room.x", 0f, room.x, 0f);
        assertEquals(msg + "room.y", 0f, room.y, 0f);
        assertEquals(msg + "room.width", mapWidth * tileWidth, room.width, 0f);
        assertEquals(msg + "room.height", mapHeight * tileHeight, room.height, 0f);

        // 3. Exactly one playerStart marker, found by MapLoader's real accessor.
        MapObjects objects = loader.getObjectLayer();
        MapObject playerStart = null;
        for (MapObject object : objects) {
            if ("playerStart".equals(object.getProperties().get("type", String.class))) {
                assertNull(msg + "multiple playerStarts", playerStart);
                playerStart = object;
            }
        }
        assertNotNull(msg + "playerStart marker", playerStart);
        Rectangle startRect = ((RectangleMapObject) playerStart).getRectangle();
        Vector2 found = loader.findPlayerStart();
        assertEquals(msg + "findPlayerStart x", startRect.x, found.x, 0f);
        assertEquals(msg + "findPlayerStart y", startRect.y, found.y, 0f);

        // 4. Exactly one exitGate marker with the correct level-chaining property.
        MapObject exitGate = null;
        for (MapObject object : objects) {
            if ("exitGate".equals(object.getProperties().get("type", String.class))) {
                assertNull(msg + "multiple exitGates", exitGate);
                exitGate = object;
            }
        }
        assertNotNull(msg + "exitGate marker", exitGate);
        if (name.equals("level_10_final.tmx")) {
            assertEquals(msg + "isFinal", "true", exitGate.getProperties().get("isFinal", String.class));
            assertNull(msg + "final gate must have no nextLevel", exitGate.getProperties().get("nextLevel", String.class));
        } else {
            assertNull(msg + "non-final gate must have no isFinal", exitGate.getProperties().get("isFinal", String.class));
            String expectedNext = nextLevelFor(name);
            assertEquals(msg + "nextLevel", expectedNext, exitGate.getProperties().get("nextLevel", String.class));
        }

        // 5. No enemy / coin / chest markers in the spawn layers.
        assertEquals(msg + "enemies layer empty", 0, loader.getEnemiesLayer().getCount());
        assertEquals(msg + "objects layer only playerStart+exitGate", 2, objects.getCount());

        // 6. Collision border fully solid, interior fully empty.
        TiledMapTileLayer collision = (TiledMapTileLayer) map.getLayers().get("collision");
        assertNotNull(msg + "collision layer", collision);
        assertEquals(msg + "collision width", EXPECTED_WIDTH, collision.getWidth());
        assertEquals(msg + "collision height", EXPECTED_HEIGHT, collision.getHeight());
        int solidCells = 0;
        for (int y = 0; y < collision.getHeight(); y++) {
            for (int x = 0; x < collision.getWidth(); x++) {
                boolean onFrame = x == 0 || y == 0 || x == collision.getWidth() - 1 || y == collision.getHeight() - 1;
                boolean filled = collision.getCell(x, y) != null;
                assertEquals(msg + "border cell(" + x + "," + y + ") must be solid", onFrame, filled);
                if (filled) solidCells++;
            }
        }
        assertEquals(msg + "border cell count", 2 * EXPECTED_WIDTH + 2 * (EXPECTED_HEIGHT - 2), solidCells);
        assertEquals(msg + "MapLoader collision rects = border", solidCells, loader.getCollisionRects().size);
    }

    /** The next level's asset path, keyed by this map's file name (level_01..level_09). */
    private static String nextLevelFor(String fileName) {
        int n = Integer.parseInt(fileName.substring("level_".length(), fileName.indexOf('.')));
        return n == 9 ? "maps/world3/level_10_final.tmx" : String.format("maps/world3/level_%02d.tmx", n + 1);
    }

    private static void putTileWithProps(TiledMapTileSet set, int firstGid, XmlReader.Element tileEl) {
        int localId = tileEl.getIntAttribute("id");
        TiledMapTile tile = new StaticTiledMapTile(new TextureRegion()); // no GL needed
        XmlReader.Element props = tileEl.getChildByName("properties");
        if (props != null) {
            for (XmlReader.Element prop : props.getChildrenByName("property")) {
                String name = prop.getAttribute("name");
                String type = prop.getAttribute("type", "string");
                String value = prop.getAttribute("value", "");
                switch (type) {
                    case "bool":
                        tile.getProperties().put(name, value.trim().equalsIgnoreCase("true"));
                        break;
                    case "int":
                        tile.getProperties().put(name, Integer.parseInt(value.trim()));
                        break;
                    case "float":
                        tile.getProperties().put(name, Float.parseFloat(value.trim()));
                        break;
                    default:
                        tile.getProperties().put(name, value);
                }
            }
        }
        set.putTile(firstGid + localId, tile);
    }
}
