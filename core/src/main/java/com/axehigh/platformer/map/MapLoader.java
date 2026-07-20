package com.axehigh.platformer.map;

import com.axehigh.platformer.GameConstants;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
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
    private static final String TYPE_PLAYER_START = "playerStart";

    private final TiledMap map;
    private final Array<Rectangle> collisionRects = new Array<>();

    public MapLoader(String tmxPath) {
        map = new TmxMapLoader().load(tmxPath);
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
                if (cell != null) {
                    collisionRects.add(new Rectangle(x * tileWidth, y * tileHeight, tileWidth, tileHeight));
                }
            }
        }
    }

    public TiledMap getMap() {
        return map;
    }

    public Array<Rectangle> getCollisionRects() {
        return collisionRects;
    }

    public MapObjects getObjectLayer() {
        MapLayer layer = map.getLayers().get(OBJECTS_LAYER);
        return layer != null ? layer.getObjects() : new MapObjects();
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
