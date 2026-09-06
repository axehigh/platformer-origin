package com.axehigh.platformer.map;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Loads the real {@code assets/maps/world_demo/platforming_clamp.tmx} (plus its external .tsx
 * tilesets) with a texture-light parse — reusing libGDX's own {@link TiledMapTileSets#getTile(int)}
 * gid resolution — then runs the real {@link MapLoader} parsing on it and asserts the crumble
 * cells resolve. Guards against map/tileset authoring regressions (wrong property, misplaced
 * tile, out-of-range gid, etc). No GL required: every tile gets a no-arg {@link TextureRegion}.
 */
public class CrumblingTileRealMapTest {

    /** Walks up from the working dir to find the repo root holding assets/maps/world_demo. */
    private static final File BASE = findBase();

    private static File findBase() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 8 && dir != null; i++) {
            File candidate = new File(dir, "assets/maps/world_demo");
            if (candidate.isDirectory()) return candidate;
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("assets/maps/world_demo not found from " + System.getProperty("user.dir"));
    }

    private static XmlReader.Element readXml(File file) {
        return new XmlReader().parse(new FileHandle(file.getAbsolutePath()));
    }

    @Test
    public void realMapCrumbleCellsParseIntoCrumblingTiles() throws Exception {
        XmlReader.Element tmx = readXml(new File(BASE, "platforming_clamp.tmx"));
        int mapWidth = tmx.getIntAttribute("width");
        int mapHeight = tmx.getIntAttribute("height");
        int tileWidth = tmx.getIntAttribute("tilewidth");
        int tileHeight = tmx.getIntAttribute("tileheight");

        TiledMap map = new TiledMap();
        map.getProperties().put("tilewidth", tileWidth);
        map.getProperties().put("tileheight", tileHeight);

        // --- Tilesets: external .tsx plus the inline secret_room_wall, keyed by (firstgid*localId)
        // exactly as TiledMapTileSets expects (libGDX's own getTile path, no tilecount bounding).
        for (XmlReader.Element ts : tmx.getChildrenByName("tileset")) {
            int firstGid = ts.getIntAttribute("firstgid", 1);
            TiledMapTileSet set = new TiledMapTileSet();
            String baseName;
            if (ts.getAttribute("source", null) != null) {
                File tsxFile = new File(BASE, ts.getAttribute("source")).getCanonicalFile();
                baseName = new File(ts.getAttribute("source")).getName();
                XmlReader.Element tsx = readXml(tsxFile);
                set.setName(tsx.getAttribute("name", baseName));
                for (XmlReader.Element tileEl : tsx.getChildrenByName("tile")) {
                    putTileWithProps(set, firstGid, tileEl);
                }
            } else {
                baseName = ts.getAttribute("name", "inline");
                set.setName(baseName);
                for (XmlReader.Element tileEl : ts.getChildrenByName("tile")) {
                    putTileWithProps(set, firstGid, tileEl);
                }
            }
            map.getTileSets().addTileSet(set);
        }

        // --- Layers: everything empty except the collision layer, which gets its real CSV cells.
        for (XmlReader.Element layerEl : tmx.getChildrenByName("layer")) {
            String name = layerEl.getAttribute("name");
            TiledMapTileLayer layer = new TiledMapTileLayer(mapWidth, mapHeight, tileWidth, tileHeight);
            layer.setName(name);
            if (name.equals("collision")) {
                XmlReader.Element data = layerEl.getChildByName("data");
                String[] rows = data.getText().trim().split("\n");
                assertEquals("row count", mapHeight, rows.length);
                for (int y = 0; y < mapHeight; y++) {
                    String[] cols = rows[y].trim().split(",");
                    for (int x = 0; x < mapWidth; x++) {
                        int gid = Integer.parseInt(cols[x].trim());
                        TiledMapTile tile = null;
                        if (gid != 0) {
                            tile = map.getTileSets().getTile(gid); // real libGDX resolution path
                            assertNotNull("gid " + gid + " must resolve to a tile", tile);
                            TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                            cell.setTile(tile);
                            layer.setCell(x, y, cell);
                        }
                    }
                }
            }
            map.getLayers().add(layer);
        }

        // Fill in the named object groups so MapLoader's other scans find them (blanks).
        for (String groupName : new String[]{"objects", "enemies", "Rooms"}) {
            MapLayer group = new MapLayer();
            group.setName(groupName);
            map.getLayers().add(group);
        }

        MapLoader loader = new MapLoader(map, "platforming_clamp.tmx"); // package-private ctor

        Array<CrumblingTile> crumblingTiles = loader.getCrumblingTiles();
        Array<Rectangle> oneWayRects = loader.getOneWayRects();

        // Adaptively derive the expected cells: every collision-layer cell whose tile carries the
        // crumble=true property must have been turned into a CrumblingTile at that exact cell.
        TiledMapTileLayer collision = (TiledMapTileLayer) map.getLayers().get("collision");
        int expectedCount = 0;
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                TiledMapTileLayer.Cell cell = collision.getCell(x, y);
                if (cell == null) continue;
                Boolean crumble = cell.getTile().getProperties().get("crumble", Boolean.class);
                if (crumble != null && crumble) {
                    expectedCount++;
                    CrumblingTile match = null;
                    for (CrumblingTile tile : crumblingTiles) {
                        if (tile.cellX == x && tile.cellY == y) { match = tile; break; }
                    }
                    assertNotNull("crumble cell (" + x + "," + y + ") must produce a CrumblingTile", match);
                    assertEquals(new Rectangle(x * tileWidth, y * tileHeight, tileWidth, tileHeight), match.rect);
                    assertTrue("crumble rect must be THE SAME instance shared with oneWayRects",
                        oneWayRects.contains(match.rect, true));
                }
            }
        }
        assertEquals("every crumble cell must be parsed", expectedCount, crumblingTiles.size);
        assertTrue("at least one crumble cell expected in this demo map", expectedCount >= 1);
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
