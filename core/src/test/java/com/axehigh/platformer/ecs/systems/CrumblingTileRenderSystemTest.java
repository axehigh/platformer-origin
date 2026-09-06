package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.map.CrumblingTile;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

/**
 * Headless tests for {@code CrumblingTileRenderSystem}: it redraws every {@code SHAKING} crumbling
 * tile from its captured original cell at a random integer-pixel jitter, and skips the batch
 * entirely when no tile is shaking. The {@code SpriteBatch} is Mockito-mocked (no GL headless), the
 * camera is a real {@code OrthographicCamera}, and the tiles are real {@code CrumblingTile}s over a
 * 128px {@code TiledMapTileLayer}.
 */
public class CrumblingTileRenderSystemTest extends SystemTestBase {

    private static final int TILE = 128;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Array<CrumblingTile> tiles;
    private CrumblingTileRenderSystem system;

    @Before
    public void setUp() {
        batch = mock(SpriteBatch.class);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 480, 272);
        camera.update();
        tiles = new Array<>();
        system = new CrumblingTileRenderSystem(batch, camera, tiles, 0);
    }

    /** A crumbling tile at the given cell with the given state, sharing a new {@code TiledMapTileLayer}. */
    private CrumblingTile tileAt(int cellX, int cellY, CrumblingTile.State state) {
        TiledMapTileLayer layer = new TiledMapTileLayer(4, 4, TILE, TILE);
        TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
        cell.setTile(new StaticTiledMapTile(new TextureRegion()));
        layer.setCell(cellX, cellY, cell);
        CrumblingTile tile = new CrumblingTile(layer, cellX, cellY, cell,
            new Rectangle(cellX * TILE, cellY * TILE, TILE, TILE));
        tile.state = state;
        if (state == CrumblingTile.State.SHAKING) {
            tile.shakeTimer.start(GameConstants.CRUMBLE_SHAKE_DURATION);
        }
        return tile;
    }

    @Test
    public void shakingTileDrawnOnceAtJitteredPosition() {
        CrumblingTile intact = tileAt(0, 0, CrumblingTile.State.INTACT);
        CrumblingTile shaking = tileAt(1, 0, CrumblingTile.State.SHAKING);
        CrumblingTile collapsed = tileAt(2, 0, CrumblingTile.State.COLLAPSED);
        tiles.add(intact);
        tiles.add(shaking);
        tiles.add(collapsed);

        system.update(DT);

        ArgumentCaptor<TextureRegion> regionCap = ArgumentCaptor.forClass(TextureRegion.class);
        ArgumentCaptor<Float> floatCap = ArgumentCaptor.forClass(Float.class);

        verify(batch, times(1)).draw(regionCap.capture(), floatCap.capture(), floatCap.capture(),
            floatCap.capture(), floatCap.capture());

        // Only the SHAKING tile's original region is drawn once.
        assertTrue(shaking.originalCell.getTile().getTextureRegion().equals(regionCap.getValue()));

        // Captured args are (x, y, w, h).
        float x = floatCap.getAllValues().get(0);
        float y = floatCap.getAllValues().get(1);
        float w = floatCap.getAllValues().get(2);
        float h = floatCap.getAllValues().get(3);

        assertEquals(TILE, w, EPSILON);
        assertEquals(TILE, h, EPSILON);

        // Jittered position stays within [-MAX_JITTER, +MAX_JITTER] of the tile rect on both axes.
        assertTrue(x >= shaking.rect.x - GameConstants.CRUMBLE_SHAKE_MAX_JITTER
            && x <= shaking.rect.x + GameConstants.CRUMBLE_SHAKE_MAX_JITTER);
        assertTrue(y >= shaking.rect.y - GameConstants.CRUMBLE_SHAKE_MAX_JITTER
            && y <= shaking.rect.y + GameConstants.CRUMBLE_SHAKE_MAX_JITTER);

        verify(batch).setProjectionMatrix(camera.combined);
        verify(batch, times(1)).begin();
        verify(batch, times(1)).end();
    }

    @Test
    public void noTilesShakingSkipsBatchEntirely() {
        tiles.add(tileAt(0, 0, CrumblingTile.State.INTACT));
        tiles.add(tileAt(1, 0, CrumblingTile.State.COLLAPSED));

        system.update(DT);

        verify(batch, never()).begin();
        verify(batch, never()).end();
        verify(batch, never()).setProjectionMatrix(camera.combined);
        verify(batch, never()).draw(any(TextureRegion.class), anyFloat(), anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    public void jitterDecaysToMinimum() {
        CrumblingTile shaking = tileAt(1, 0, CrumblingTile.State.SHAKING);
        // Near-zero remaining shake time: the jitter magnitude floors at 1px.
        shaking.shakeTimer.start(0.0001f);
        tiles.add(shaking);
        tiles.add(tileAt(0, 0, CrumblingTile.State.INTACT));
        tiles.add(tileAt(2, 0, CrumblingTile.State.COLLAPSED));

        system.update(DT);

        ArgumentCaptor<Float> floatCap = ArgumentCaptor.forClass(Float.class);
        verify(batch, times(1)).draw(any(TextureRegion.class),
            floatCap.capture(), floatCap.capture(), floatCap.capture(), floatCap.capture());

        float x = floatCap.getAllValues().get(0);
        float y = floatCap.getAllValues().get(1);

        assertTrue(x >= shaking.rect.x - GameConstants.CRUMBLE_SHAKE_MAX_JITTER
            && x <= shaking.rect.x + GameConstants.CRUMBLE_SHAKE_MAX_JITTER);
        assertTrue(y >= shaking.rect.y - GameConstants.CRUMBLE_SHAKE_MAX_JITTER
            && y <= shaking.rect.y + GameConstants.CRUMBLE_SHAKE_MAX_JITTER);
    }
}
