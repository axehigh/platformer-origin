package com.axehigh.platformer.map;

import com.axehigh.platformer.GameConstants;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.CircleMapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

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
    private static final String SECRET_HIDE_LAYER = "secret_hide";
    private static final String TYPE_PLAYER_START = "playerStart";
    /** Optional per-room property choosing the camera mode: "flip" or "scroll" (default: infer by size). */
    private static final String PROPERTY_CAMERA = "camera";
    /** Tile property distinguishing solid wall tiles from non-blocking ones (e.g. natural passageways). */
    private static final String PROPERTY_SOLID = "solid";
    /** Tile property marking a player-only drop-through platform (top-only solid; drop with down/S). */
    private static final String PROPERTY_ONE_WAY = "oneWay";
    /** Tile property marking a non-solid hazard (spikes/lava) that damages the player on touch. */
    private static final String PROPERTY_HAZARD = "hazard";
    /** Tile property marking a solid breakable wall tile that opens a secret room when melee-struck. */
    private static final String PROPERTY_SECRET = "secret";
    /** Tile/object property naming the {@code Rooms}-layer rectangle (and thus the secret room) a secret wall guards or an object is deferred for. */
    private static final String PROPERTY_SECRET_ROOM = "secretRoom";

    private final TiledMap map;
    private final Array<Rectangle> collisionRects = new Array<>();
    private final Array<Rectangle> oneWayRects = new Array<>();
    private final Array<Rectangle> hazardRects = new Array<>();
    private final Array<Rectangle> secretRects = new Array<>();
    private final String tmxPath;
    private final float tileWidth;
    private final float tileHeight;

    public MapLoader(String tmxPath) {
        this(new TmxMapLoader().load(tmxPath), tmxPath);
    }

    /** Package-private constructor with a pre-built map, so headless tests can exercise the parsing without a .tmx file. */
    MapLoader(TiledMap map, String tmxPath) {
        this.map = map;
        this.tmxPath = tmxPath;
        
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
                if (cell == null || cell.getTile() == null) {
                    continue;
                }
                Rectangle rect = new Rectangle(x * tileWidth, y * tileHeight, tileWidth, tileHeight);
                TiledMapTile tile = cell.getTile();
                if (tile.getProperties().get(PROPERTY_HAZARD, false, Boolean.class)) {
                    addHazardRects(cell, x, y, tileWidth, tileHeight, rect);
                } else if (!isSolid(cell)) {
                    // solid=false passage tile: no collision at all.
                    continue;
                } else if (tile.getProperties().get(PROPERTY_ONE_WAY, false, Boolean.class)) {
                    oneWayRects.add(rect);
                } else {
                    // A secret wall tile is fully solid until struck (unlike hazard, which is never
                    // solid), so it lands in the normal collision set too; breaking it later removes
                    // the rect from both arrays to open the doorway.
                    if (tile.getProperties().get(PROPERTY_SECRET, false, Boolean.class)) {
                        secretRects.add(rect);
                    }
                    collisionRects.add(rect);
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

    /**
     * Adds the hazard rect(s) for one hazard cell. When the tile carries collision-editor shapes
     * (drawn in Tiled on the tile, e.g. a small hitbox on the spike art) and the cell isn't
     * flipped, one world-space hazard rect is added per shape (rectangle/polygon/circle/ellipse
     * bounding box), so spikes can have a smaller damage zone than the full tile; otherwise the
     * full-tile rect is used. Shapes load in tile-local coordinates with a bottom-left Y-up origin,
     * matching this world's axes.
     */
    private void addHazardRects(TiledMapTileLayer.Cell cell, float cellX, float cellY, float tileWidth, float tileHeight, Rectangle fullTile) {
        if (cell.getFlipHorizontally() || cell.getFlipVertically() || cell.getRotation() != 0) {
            hazardRects.add(fullTile);
            return;
        }
        TiledMapTile tile = cell.getTile();
        MapObjects shapes = tile.getObjects();
        if (shapes.getCount() == 0) {
            hazardRects.add(fullTile);
            return;
        }
        boolean added = false;
        for (MapObject shape : shapes) {
            Rectangle local = shapeBounds(shape);
            if (local == null) {
                continue;
            }
            hazardRects.add(new Rectangle(cellX * tileWidth + local.x, cellY * tileHeight + local.y, local.width, local.height));
            added = true;
        }
        if (!added) {
            hazardRects.add(fullTile);
        }
    }

    /** World-local bounding box (bottom-left origin) of one tile collision-editor shape, or null for unsupported shapes. */
    private Rectangle shapeBounds(MapObject shape) {
        if (shape instanceof RectangleMapObject) {
            return new Rectangle(((RectangleMapObject) shape).getRectangle());
        }
        if (shape instanceof PolygonMapObject) {
            return ((PolygonMapObject) shape).getPolygon().getBoundingRectangle();
        }
        if (shape instanceof CircleMapObject) {
            Circle circle = ((CircleMapObject) shape).getCircle();
            return new Rectangle(circle.x - circle.radius, circle.y - circle.radius, circle.radius * 2f, circle.radius * 2f);
        }
        if (shape instanceof EllipseMapObject) {
            Ellipse ellipse = ((EllipseMapObject) shape).getEllipse();
            return new Rectangle(ellipse.x, ellipse.y, ellipse.width, ellipse.height);
        }
        return null;
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

    /** Player-only drop-through platforms (collision-layer tiles flagged {@code oneWay=true}). */
    public Array<Rectangle> getOneWayRects() {
        return oneWayRects;
    }

    /** Non-solid hazard tiles (collision-layer tiles flagged {@code hazard=true}). */
    public Array<Rectangle> getHazardRects() {
        return hazardRects;
    }

    /** Solid breakable secret-wall tiles (collision-layer tiles flagged {@code secret=true}), also present in {@link #getCollisionRects()} until struck. */
    public Array<Rectangle> getSecretRects() {
        return secretRects;
    }

    /** The {@code collision} tile layer itself (the visual wall layer) so secret-wall cells can be blanked when broken. */
    public TiledMapTileLayer getCollisionLayer() {
        MapLayer layer = map.getLayers().get(COLLISION_LAYER);
        return layer instanceof TiledMapTileLayer ? (TiledMapTileLayer) layer : null;
    }

    /**
     * The {@code secret_hide} tile layer (the visual veil painted over secret-room footprints so the
     * cavity reads as solid rock), or {@code null} when the map has none. Reveal blanks its cells.
     */
    public TiledMapTileLayer getSecretHideLayer() {
        MapLayer layer = map.getLayers().get(SECRET_HIDE_LAYER);
        return layer instanceof TiledMapTileLayer ? (TiledMapTileLayer) layer : null;
    }

    /**
     * Discovers every secret room of the map: the unique {@code secretRoom} values found on
     * secret-wall tiles and on deferred object/enemy markers. Each {@link SecretRoom} groups the
     * collision-layer wall rects guarding it, the {@code secret_hide} veil cells overlapping its
     * {@code Rooms}-layer rect, and the object/enemy markers that carry its name — which are
     * **partitioned out** of the main {@code objects}/{@code enemies} layers here, so
     * {@code EntityFactory.spawnObjects} only ever sees non-secret markers and the deferred ones
     * stay unspawned until {@code SecretRoomRevealer.reveal(...)} spawns them. Call before spawning
     * on a fresh {@code MapLoader} (both {@code GameScreen.show()} and {@code LevelManager} do).
     */
    public Array<SecretRoom> getSecretRooms() {
        Array<SecretRoom> rooms = new Array<>();
        TiledMapTileLayer hideLayer = getSecretHideLayer();
        ObjectSet<String> names = new ObjectSet<>();
        ObjectMap<Rectangle, String> wallRooms = new ObjectMap<>();

        TiledMapTileLayer collisionLayer = getCollisionLayer();
        if (collisionLayer != null) {
            for (int y = 0; y < collisionLayer.getHeight(); y++) {
                for (int x = 0; x < collisionLayer.getWidth(); x++) {
                    TiledMapTileLayer.Cell cell = collisionLayer.getCell(x, y);
                    if (cell == null || cell.getTile() == null) {
                        continue;
                    }
                    String roomName = cell.getTile().getProperties().get(PROPERTY_SECRET_ROOM, String.class);
                    if (roomName == null) {
                        continue;
                    }
                    names.add(roomName);
                    wallRooms.put(new Rectangle(x * tileWidth, y * tileHeight, tileWidth, tileHeight), roomName);
                }
            }
        }

        ObjectMap<String, MapObjects> deferredByRoom = new ObjectMap<>();
        partitionSecretObjects(map.getLayers().get(OBJECTS_LAYER), names, deferredByRoom);
        partitionSecretObjects(map.getLayers().get(ENEMIES_LAYER), names, deferredByRoom);

        for (String name : names) {
            SecretRoom room = new SecretRoom(name, findRoomRect(name));
            for (ObjectMap.Entry<Rectangle, String> entry : wallRooms) {
                if (name.equals(entry.value)) {
                    room.walls.add(entry.key);
                }
            }
            MapObjects deferred = deferredByRoom.get(name);
            if (deferred != null) {
                for (MapObject object : deferred) {
                    room.deferredObjects.add(object);
                }
            }
            if (hideLayer != null && room.room != null) {
                collectVeilCells(hideLayer, room.room, room.veilCells);
            }
            rooms.add(room);
        }
        return rooms;
    }

    /** Moves every marker carrying a {@code secretRoom} property out of the layer into a per-room bucket. */
    private void partitionSecretObjects(MapLayer layer, ObjectSet<String> names, ObjectMap<String, MapObjects> deferredByRoom) {
        if (layer == null) {
            return;
        }
        MapObjects objects = layer.getObjects();
        for (int i = objects.getCount() - 1; i >= 0; i--) {
            MapObject object = objects.get(i);
            String roomName = object.getProperties().get(PROPERTY_SECRET_ROOM, String.class);
            if (roomName == null) {
                continue;
            }
            names.add(roomName);
            MapObjects bucket = deferredByRoom.get(roomName);
            if (bucket == null) {
                bucket = new MapObjects();
                deferredByRoom.put(roomName, bucket);
            }
            bucket.add(object);
            objects.remove(i);
        }
    }

    /** The {@code Rooms}-layer rectangle whose object name matches, or null when absent. */
    private Room findRoomRect(String name) {
        MapLayer layer = map.getLayers().get(ROOMS_LAYER);
        if (layer == null) {
            return null;
        }
        for (MapObject object : layer.getObjects()) {
            if (name.equals(object.getName()) && object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                return new Room(rect.x, rect.y, rect.width, rect.height);
            }
        }
        return null;
    }

    /** Collects the world rects of every non-empty {@code secret_hide} cell overlapping the room rect. */
    private void collectVeilCells(TiledMapTileLayer layer, Rectangle roomRect, Array<Rectangle> out) {
        int minX = (int) (roomRect.x / layer.getTileWidth());
        int maxX = (int) ((roomRect.x + roomRect.width - 1) / layer.getTileWidth());
        int minY = (int) (roomRect.y / layer.getTileHeight());
        int maxY = (int) ((roomRect.y + roomRect.height - 1) / layer.getTileHeight());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                if (cell == null || cell.getTile() == null) {
                    continue;
                }
                out.add(new Rectangle(x * layer.getTileWidth(), y * layer.getTileHeight(), layer.getTileWidth(), layer.getTileHeight()));
            }
        }
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
        if (rooms.size == 0) {
            float mapWidth = map.getProperties().get("width", 0, Integer.class) * tileWidth;
            float mapHeight = map.getProperties().get("height", 0, Integer.class) * tileHeight;
            if (mapWidth > 0 && mapHeight > 0) {
                rooms.add(new Room(0f, 0f, mapWidth, mapHeight));
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
