package com.axehigh.platformer.map;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.XmlReader;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that every {@code .tmx} map under {@code assets/maps} only ever places {@code gid=}
 * tile-object markers (object-layer objects, e.g. coins/enemies/signs) that resolve to a real
 * tile — reusing libGDX's own {@link com.badlogic.gdx.maps.tiled.TiledMapTileSets#getTile(int)}
 * resolution path (no GL required: tiles get a no-arg {@link TextureRegion}).
 * <p>
 * A map/tileset authoring mistake (a tileset edited so a {@code <tile id>} no longer exists, an
 * object gid left stale after tiles were renumbered, etc.) makes {@code getTile(gid)} return
 * {@code null}. For an object-layer marker that null tile crashes {@code TmxMapLoader} with
 * {@code NullPointerException: Cannot invoke "TiledMapTile.getTextureRegion()" because "tile" is
 * null} the moment the real game tries to load the level — {@code BaseTmxMapLoader#loadObject}
 * calls {@code tile.getTextureRegion()} unconditionally. This test catches that at build time, for
 * every shipped map, instead of at first launch of a specific level.
 * <p>
 * Plain tile-<em>layer</em> cells (background/collision/decoration CSV data) are intentionally
 * <strong>not</strong> checked here: {@code BaseTmxMapLoader#loadTileLayer} guards with
 * {@code if (tile != null)}, so a stale/missing tile-layer gid only renders as a silent gap — a
 * cosmetic authoring issue, not the crash this test guards against.
 */
public class MapGidIntegrityTest {

    /** Masks off Tiled's horizontal/vertical/diagonal flip flags stored in the top bits of a gid. */
    private static final int GID_FLAG_MASK = 0x1FFFFFFF;

    /** Walks up from the working dir to find the repo root holding assets/maps. */
    private static final File MAPS_ROOT = findMapsRoot();

    private static File findMapsRoot() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 8 && dir != null; i++) {
            File candidate = new File(dir, "assets/maps");
            if (candidate.isDirectory()) return candidate;
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("assets/maps not found from " + System.getProperty("user.dir"));
    }

    private static XmlReader.Element readXml(File file) {
        return new XmlReader().parse(new FileHandle(file.getAbsolutePath()));
    }

    private static List<File> findTmxFiles(File dir) {
        List<File> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return result;
        for (File child : children) {
            if (child.isDirectory()) {
                result.addAll(findTmxFiles(child));
            } else if (child.getName().endsWith(".tmx")) {
                result.add(child);
            }
        }
        return result;
    }

    @Test
    public void everyMapObjectGidResolvesToARealTile() throws Exception {
        List<String> failures = new ArrayList<>();
        List<File> tmxFiles = findTmxFiles(MAPS_ROOT);
        assertTrue("expected to find at least one .tmx map under " + MAPS_ROOT, !tmxFiles.isEmpty());

        for (File tmxFile : tmxFiles) {
            checkMap(tmxFile, failures);
        }

        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(failures.size()).append(" unresolved object-layer gid(s) found (these crash TmxMapLoader at level load):\n");
            for (String f : failures) {
                sb.append("  - ").append(f).append('\n');
            }
            fail(sb.toString());
        }
    }

    private void checkMap(File tmxFile, List<String> failures) throws Exception {
        XmlReader.Element tmx = readXml(tmxFile);
        String mapName = tmxFile.getName();

        TiledMap map = new TiledMap();
        for (XmlReader.Element ts : tmx.getChildrenByName("tileset")) {
            int firstGid = ts.getIntAttribute("firstgid", 1);
            TiledMapTileSet set = new TiledMapTileSet();
            if (ts.getAttribute("source", null) != null) {
                File tsxFile = new File(tmxFile.getParentFile(), ts.getAttribute("source")).getCanonicalFile();
                XmlReader.Element tsx = readXml(tsxFile);
                set.setName(tsx.getAttribute("name", tsxFile.getName()));
                putAllTiles(set, firstGid, tsx);
            } else {
                set.setName(ts.getAttribute("name", "inline"));
                putAllTiles(set, firstGid, ts);
            }
            map.getTileSets().addTileSet(set);
        }

        // Object layers: every object with a gid= attribute must resolve (this is the exact
        // TiledMapTileMapObject path that crashes with a null tile at runtime).
        for (XmlReader.Element og : tmx.getChildrenByName("objectgroup")) {
            String layerName = og.getAttribute("name", "?");
            for (XmlReader.Element obj : og.getChildrenByName("object")) {
                String gidAttr = obj.getAttribute("gid", null);
                if (gidAttr == null) continue;
                long gid = Long.parseLong(gidAttr) & GID_FLAG_MASK;
                TiledMapTile tile = map.getTileSets().getTile((int) gid);
                if (tile == null) {
                    failures.add(mapName + " objectgroup '" + layerName + "' object id=" + obj.getAttribute("id", "?") + " gid=" + gid);
                }
            }
        }
    }

    /**
     * Populates every valid local tile id for one {@code <tileset>}. Two authoring styles exist:
     * <ul>
     *   <li>Image-collection (a {@code <tile>}-level {@code <image>} per tile, regardless of the
     *       {@code columns} attribute): only ids with an explicit {@code <tile>} element are real
     *       tiles — local ids are not necessarily contiguous (e.g. {@code hazards.tsx}).</li>
     *   <li>Atlas/sprite-sheet (a single {@code <image>} directly under {@code <tileset>}): every
     *       id from {@code 0} to {@code tilecount - 1} is a real tile, whether or not Tiled wrote
     *       an explicit {@code <tile>} element for it (elements only appear for ids carrying
     *       custom properties/collision shapes).</li>
     * </ul>
     */
    private static void putAllTiles(TiledMapTileSet set, int firstGid, XmlReader.Element tilesetEl) {
        if (tilesetEl.getChildByName("image") != null) {
            int tilecount = tilesetEl.getIntAttribute("tilecount", 0);
            for (int localId = 0; localId < tilecount; localId++) {
                set.putTile(firstGid + localId, new StaticTiledMapTile(new TextureRegion()));
            }
        } else {
            for (XmlReader.Element tileEl : tilesetEl.getChildrenByName("tile")) {
                int localId = tileEl.getIntAttribute("id");
                set.putTile(firstGid + localId, new StaticTiledMapTile(new TextureRegion()));
            }
        }
    }
}
