package com.axehigh.platformer.map;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@link MapLoader#scanEffectLayers()} verifying that both tile layers and
 * object layers carrying an {@code effect} tile property produce {@link MapLoader.EffectSpawn}
 * entries at the correct world positions.
 */
public class MapLoaderEffectScanTest {

    private static final int TILE = 32;

    private TiledMap map;
    private StaticTiledMapTile effectTile;

    @Before
    public void setUp() {
        map = new TiledMap();
        map.getProperties().put("width", 10);
        map.getProperties().put("height", 10);
        map.getProperties().put("tilewidth", TILE);
        map.getProperties().put("tileheight", TILE);

        effectTile = new StaticTiledMapTile(new TextureRegion());
        effectTile.setId(1);
        effectTile.getProperties().put("effect", "light");
    }

    private MapLoader loader() {
        return new MapLoader(map, "test.tmx");
    }

    /** Creates a mocked TiledMapTileMapObject (no GL context needed). */
    private TiledMapTileMapObject mockTileObject(StaticTiledMapTile tile, float x, float y) {
        TiledMapTileMapObject obj = Mockito.mock(TiledMapTileMapObject.class);
        when(obj.getTile()).thenReturn(tile);
        when(obj.getX()).thenReturn(x);
        when(obj.getY()).thenReturn(y);
        return obj;
    }

    @Test
    public void objectLayer_tileObjectWithEffect_producesEffectSpawn() {
        TiledMapTileLayer collisionLayer = new TiledMapTileLayer(10, 10, TILE, TILE);
        collisionLayer.setName("collision");
        map.getLayers().add(collisionLayer);

        TiledMapTileMapObject tileObj = mockTileObject(effectTile, 256f, 128f);
        MapLayer objectLayer = new MapLayer();
        objectLayer.getObjects().add(tileObj);
        map.getLayers().add(objectLayer);

        Array<MapLoader.EffectSpawn> spawns = loader().getEffectSpawns();

        assertEquals("should produce exactly one EffectSpawn", 1, spawns.size);
        MapLoader.EffectSpawn spawn = spawns.first();
        assertEquals("light", spawn.effectType);
        assertEquals(256f, spawn.x, 0f);
        assertEquals(128f, spawn.y, 0f);
        assertNotNull(spawn.tile);
    }

    @Test
    public void tileLayer_effectTile_producesEffectSpawn() {
        TiledMapTileLayer collisionLayer = new TiledMapTileLayer(10, 10, TILE, TILE);
        collisionLayer.setName("collision");
        map.getLayers().add(collisionLayer);

        TiledMapTileLayer effectLayer = new TiledMapTileLayer(10, 10, TILE, TILE);
        effectLayer.setName("decoration");
        TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
        cell.setTile(effectTile);
        effectLayer.setCell(3, 5, cell);
        map.getLayers().add(effectLayer);

        Array<MapLoader.EffectSpawn> spawns = loader().getEffectSpawns();

        assertEquals(1, spawns.size);
        MapLoader.EffectSpawn spawn = spawns.first();
        assertEquals("light", spawn.effectType);
        assertEquals(3 * TILE, spawn.x, 0f);
        assertEquals(5 * TILE, spawn.y, 0f);
        assertNotNull(spawn.tile);
    }

    @Test
    public void mixedLayers_bothTileAndObjectEffects_collected() {
        TiledMapTileLayer collisionLayer = new TiledMapTileLayer(10, 10, TILE, TILE);
        collisionLayer.setName("collision");
        map.getLayers().add(collisionLayer);

        // Tile-layer effect at (1, 2)
        TiledMapTileLayer effectLayer = new TiledMapTileLayer(10, 10, TILE, TILE);
        effectLayer.setName("decoration");
        TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
        cell.setTile(effectTile);
        effectLayer.setCell(1, 2, cell);
        map.getLayers().add(effectLayer);

        // Object-layer effect at (500, 300)
        TiledMapTileMapObject tileObj = mockTileObject(effectTile, 500f, 300f);
        MapLayer objectLayer = new MapLayer();
        objectLayer.getObjects().add(tileObj);
        map.getLayers().add(objectLayer);

        Array<MapLoader.EffectSpawn> spawns = loader().getEffectSpawns();

        assertEquals("should collect from both layers", 2, spawns.size);
    }

    @Test
    public void objectLayer_tileWithoutEffectProperty_noSpawn() {
        TiledMapTileLayer collisionLayer = new TiledMapTileLayer(10, 10, TILE, TILE);
        collisionLayer.setName("collision");
        map.getLayers().add(collisionLayer);

        // Tile without effect property
        StaticTiledMapTile plainTile = new StaticTiledMapTile(new TextureRegion());
        plainTile.setId(99);

        TiledMapTileMapObject tileObj = mockTileObject(plainTile, 100f, 100f);
        MapLayer objectLayer = new MapLayer();
        objectLayer.getObjects().add(tileObj);
        map.getLayers().add(objectLayer);

        Array<MapLoader.EffectSpawn> spawns = loader().getEffectSpawns();

        assertEquals("non-effect tile should be skipped", 0, spawns.size);
    }
}
