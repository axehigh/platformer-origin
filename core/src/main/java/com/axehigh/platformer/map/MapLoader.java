package com.axehigh.platformer.map;

import com.axehigh.platformer.GameConstants;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Loads a .tmx map and extracts the static collision boundary set (from the "collision" tile
 * layer), the player-start position (from the "objects" layer), and other objects (from the
 * "objects" and "enemies" layers), used at level-start.
 */
public class MapLoader implements Disposable {
    private static final String COLLISION_LAYER = "collision";
    private static final String OBJECTS_LAYER = "objects";
    private static final String ENEMIES_LAYER = "enemies";
    private static final String ROOMS_LAYER = "Rooms";
    private static final String TYPE_PLAYER_START = "playerStart";
    /** Optional per-room property choosing the camera mode: "flip" or "scroll" (default: infer by size). */
    private static final String PROPERTY_CAMERA = "camera";
    /** Tile property distinguishing solid wall tiles from non-blocking ones (e.g. natural passageways). */
    private static final String PROPERTY_SOLID = "solid";

    private final TiledMap map;
    private final Array<Rectangle> collisionRects = new Array<>();
    private final String tmxPath;
    private final float tileWidth;
    private final float tileHeight;

    public MapLoader(String tmxPath) {
        this.tmxPath = tmxPath;
        map = new TmxMapLoader().load(tmxPath);
        
        // Get tile size from properties or from first tile layer
        tileWidth = map.getProperties().get("tilewidth", 16, Integer.class);
        tileHeight = map.getProperties().get("tileheight", 16, Integer.class);

        buildCollisionRects();
    }

    private void buildCollisionRects() {
        MapLayer rawLayer = map.getLayers().get(COLLISION_LAYER);
        if (!(rawLayer instanceof TiledMapTileLayer)) {
            return;
        }
        TiledMapTileLayer layer = (TiledMapTileLayer) rawLayer;
        float tileWidth = layer.getTileWidth();
        float tileHeight = layer.getTileHeight();

        for (int y = 0; y < layer.getHeight(); y++) {
            for (int x = 0; x < layer.getWidth(); x++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                if (cell != null && isSolid(cell)) {
                    collisionRects.add(new Rectangle(x * tileWidth, y * tileHeight, tileWidth, tileHeight));
                }
            }
        }
    }

    /**
     * Whether a collision-layer cell actually blocks movement. Tiles are solid by default; a tile
     * can opt out via a {@code solid=false} property (e.g. the "passage_tile" tileset), which marks
     * a natural passageway between rooms without requiring a door/gap that's invisible on the map.
     */
    private boolean isSolid(TiledMapTileLayer.Cell cell) {
        TiledMapTile tile = cell.getTile();
        if (tile == null) {
            return true;
        }
        return tile.getProperties().get(PROPERTY_SOLID, true, Boolean.class);
    }

    public TiledMap getMap() {
        return map;
    }

    /** Returns the .tmx path this MapLoader was constructed with. */
    public String getTmxPath() {
        return tmxPath;
    }

    public Array<Rectangle> getCollisionRects() {
        return collisionRects;
    }

    public float getTileWidth() {
        return tileWidth;
    }

    public float getTileHeight() {
        return tileHeight;
    }

    public MapObjects getObjectLayer() {
        MapLayer layer = map.getLayers().get(OBJECTS_LAYER);
        return layer != null ? layer.getObjects() : new MapObjects();
    }

    public MapObjects getEnemiesLayer() {
        MapLayer layer = map.getLayers().get(ENEMIES_LAYER);
        return layer != null ? layer.getObjects() : new MapObjects();
    }

    /**
     * Extracts every rectangle from the "Rooms" object layer as a {@link Room}, each defining a
     * distinct room zone used by {@code CameraSystem} to frame/scroll the camera and by {@code
     * EnemySystem}/{@code EnemyShootSystem} to tell whether an enemy's owning room is the currently
     * active one. Each room's {@link Room.Mode} is read from an optional {@code camera} custom
     * property ("flip" or "scroll"); absent, the mode is inferred from the room's size. A map with
     * **no** "Rooms" layer at all falls back to a single room covering the whole map, so a map
     * without rooms still gets camera framing and enemy activation for its entire area. Returns an
     * empty array only for a "Rooms" layer that contains no rectangles.
     */
    public Array<Room> getRooms() {
        Array<Room> rooms = new Array<>();
        MapLayer layer = map.getLayers().get(ROOMS_LAYER);
        if (layer == null) {
            float mapWidth = map.getProperties().get("width", 0, Integer.class) * tileWidth;
            float mapHeight = map.getProperties().get("height", 0, Integer.class) * tileHeight;
            if (mapWidth > 0 && mapHeight > 0) {
                rooms.add(new Room(0f, 0f, mapWidth, mapHeight));
            }
            return rooms;
        }
        for (MapObject object : layer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                RectangleMapObject rectObject = (RectangleMapObject) object;
                Rectangle rect = rectObject.getRectangle();
                String camera = rectObject.getProperties().get(PROPERTY_CAMERA, String.class);
                rooms.add(new Room(rect.x, rect.y, rect.width, rect.height, parseCameraMode(camera)));
            }
        }
        return rooms;
    }

    /** Maps the {@code camera} property string onto a {@link Room.Mode}; anything unrecognized = {@code AUTO}. */
    private Room.Mode parseCameraMode(String camera) {
        if (camera != null) {
            if (camera.equalsIgnoreCase("flip")) {
                return Room.Mode.FLIP;
            }
            if (camera.equalsIgnoreCase("scroll")) {
                return Room.Mode.SCROLL;
            }
        }
        return Room.Mode.AUTO;
    }

    public Vector2 findPlayerStart() {
        for (MapObject object : getObjectLayer()) {
            float x, y, width, height;
            TiledMapTile tile = null;
            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                x = rect.x;
                y = rect.y;
                width = rect.width;
                height = rect.height;
            } else if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObj = (TiledMapTileMapObject) object;
                tile = tileObj.getTile();
                width = object.getProperties().get("width", 0f, Float.class);
                height = object.getProperties().get("height", 0f, Float.class);
                if ((width == 0f || height == 0f) && tile != null) {
                    width = tile.getTextureRegion().getRegionWidth();
                    height = tile.getTextureRegion().getRegionHeight();
                }
                x = tileObj.getX();
                y = tileObj.getY();
            } else {
                continue;
            }

            String type = object.getProperties().get("type", String.class);
            if (type == null && tile != null) {
                type = tile.getProperties().get("type", String.class);
            }

            if (TYPE_PLAYER_START.equals(type)) {
                return new Vector2(x, y);
            }
        }
        float scale = tileWidth / 16f;
        return new Vector2(GameConstants.VIRTUAL_WIDTH * scale / 2f, GameConstants.VIRTUAL_HEIGHT * scale / 2f);
    }

    @Override
    public void dispose() {
        map.dispose();
    }
}
