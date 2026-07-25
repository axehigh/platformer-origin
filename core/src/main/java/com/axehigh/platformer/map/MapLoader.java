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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Loads a .tmx map and extracts the static collision boundary set (from the "collision" tile
 * layer) plus the player-start position (from the "objects" layer), used at level-start.
 */
public class MapLoader implements Disposable {
    private static final String COLLISION_LAYER = "collision";
    private static final String OBJECTS_LAYER = "objects";
    private static final String ROOMS_LAYER = "Rooms";
    private static final String TYPE_PLAYER_START = "playerStart";
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

    /**
     * Extracts every rectangle from the "Rooms" object layer, each defining a distinct room zone
     * used by {@code CameraSystem} to clamp the camera and by {@code EnemySystem}/{@code
     * EnemyShootSystem} to tell whether an enemy's owning room is the currently active one.
     * Returns an empty array if the map has no such layer.
     */
    public Array<Rectangle> getRooms() {
        Array<Rectangle> rooms = new Array<>();
        MapLayer layer = map.getLayers().get(ROOMS_LAYER);
        if (layer == null) {
            return rooms;
        }
        for (MapObject object : layer.getObjects()) {
            if (object instanceof RectangleMapObject) {
                rooms.add(((RectangleMapObject) object).getRectangle());
            }
        }
        return rooms;
    }

    /** Returns the center of the "playerStart" object, or the middle of the virtual screen if missing. */
    public Vector2 findPlayerStart() {
        for (MapObject object : getObjectLayer()) {
            if (!(object instanceof RectangleMapObject)) {
                continue;
            }
            String type = object.getProperties().get("type", String.class);
            if (TYPE_PLAYER_START.equals(type)) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                return new Vector2(rect.x + rect.width / 2f, rect.y + rect.height / 2f);
            }
        }
        return new Vector2(GameConstants.VIRTUAL_WIDTH / 2f, GameConstants.VIRTUAL_HEIGHT / 2f);
    }

    @Override
    public void dispose() {
        map.dispose();
    }
}
