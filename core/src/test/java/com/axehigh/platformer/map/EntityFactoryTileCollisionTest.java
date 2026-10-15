package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies that a tile-object marker whose tile defines a collision shape in Tiled carries that
 * shape into the spawned entity's {@link CollisionComponent} — the generic mechanism in
 * {@link EntityFactory} that replaced the old "always the full tile-sized box" behavior for
 * chests (see {@code items.tsx} chest tiles: box {@code (7, 40, 102, 81)}). No GL required.
 */
public class EntityFactoryTileCollisionTest {

    /** Tile-local, bottom-left-origin rect as libGDX exposes a chest tile's collision shape. */
    private static final Rectangle CHEST_SHAPE = new Rectangle(7f, 7f, 102f, 81f);

    private static TiledMapTile tileWithShape(RectangleMapObject... rects) {
        TiledMapTile tile = new StaticTiledMapTile(new TextureRegion());
        for (RectangleMapObject rect : rects) {
            tile.getObjects().add(rect);
        }
        return tile;
    }

    @Test
    public void collisionBoundsFromTileReturnsShapeForSingleRect() {
        TiledMapTile tile = tileWithShape(new RectangleMapObject(7f, 7f, 102f, 81f));

        Rectangle bounds = com.axehigh.platformer.map.EntityFactory.collisionBoundsFromTile(tile);

        assertNotNull(bounds);
        assertEquals(CHEST_SHAPE, bounds);
    }

    @Test
    public void collisionBoundsFromTileUnionsMultipleRects() {
        TiledMapTile tile = tileWithShape(
                new RectangleMapObject(0f, 0f, 10f, 10f),
                new RectangleMapObject(20f, 5f, 10f, 20f));

        Rectangle bounds = com.axehigh.platformer.map.EntityFactory.collisionBoundsFromTile(tile);

        assertNotNull(bounds);
        assertEquals(new Rectangle(0f, 0f, 30f, 25f), bounds);
    }

    @Test
    public void collisionBoundsFromTileIgnoresPointOnlyShapes() {
        TiledMapTile tile = new StaticTiledMapTile(new TextureRegion());
        tile.getObjects().add(new PointMapObject(4f, 4f));

        assertNull("a point-only tile (light/torch marker) has no collision box",
                com.axehigh.platformer.map.EntityFactory.collisionBoundsFromTile(tile));
    }

    @Test
    public void collisionBoundsFromTileIsNullWithoutShapes() {
        assertNull(com.axehigh.platformer.map.EntityFactory.collisionBoundsFromTile(null));
        assertNull(com.axehigh.platformer.map.EntityFactory.collisionBoundsFromTile(new StaticTiledMapTile(new TextureRegion())));
    }

    @Test
    public void applyTileCollisionOverrideReplacesDefaultBoxAndOffsets() {
        Entity entity = new Entity();
        CollisionComponent collision = new CollisionComponent();
        collision.bounds.setSize(128f, 128f); // the old full-tile default
        entity.add(collision);

        com.axehigh.platformer.map.EntityFactory.applyTileCollisionOverride(entity, CHEST_SHAPE);

        assertEquals(CHEST_SHAPE.width, collision.bounds.width, 0f);
        assertEquals(CHEST_SHAPE.height, collision.bounds.height, 0f);
        assertEquals(CHEST_SHAPE.x, collision.bounds.x, 0f);
        assertEquals(CHEST_SHAPE.y, collision.bounds.y, 0f);
        // MovementSystem drives bounds.x/y from these each frame; they must carry the shape origin.
        assertEquals(CHEST_SHAPE.x, collision.baseOffsetX, 0f);
        assertEquals(CHEST_SHAPE.y, collision.baseOffsetY, 0f);
        assertEquals(0f, collision.currentOffsetX, 0f);
        assertEquals(0f, collision.currentOffsetY, 0f);
    }

    @Test
    public void applyTileCollisionOverrideIsNoOpWithoutCollisionOrShape() {
        Entity noCollision = new Entity();
        com.axehigh.platformer.map.EntityFactory.applyTileCollisionOverride(noCollision, CHEST_SHAPE); // must not throw

        Entity withCollision = new Entity();
        CollisionComponent collision = new CollisionComponent();
        collision.bounds.setSize(128f, 128f);
        withCollision.add(collision);
        com.axehigh.platformer.map.EntityFactory.applyTileCollisionOverride(withCollision, null);

        assertEquals(128f, collision.bounds.width, 0f);
        assertEquals(128f, collision.bounds.height, 0f);
    }
}
