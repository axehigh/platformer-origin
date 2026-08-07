package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.EntityFactory;
import com.axehigh.platformer.map.Room;
import com.axehigh.platformer.map.RoomState;
import com.axehigh.platformer.map.SecretRoom;
import com.axehigh.platformer.map.SecretRoomRevealer;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Headless tests for the {@code MeleeAttackSystem} secret-wall break: a live strike overlapping a
 * secret wall removes its rect from both the shared {@code secretRects} and {@code collisionRects}
 * arrays, blanks the tile in the collision layer, spawns the smoke puff, and marks the swing as hit
 * (one wall per swing). A swing missing every secret wall breaks nothing, and an enemy hit still
 * takes priority over a wall hit. The collision layer and tiles are plain libGDX objects, so the
 * test runs headless.
 */
public class MeleeAttackSystemSecretWallTest extends SystemTestBase {

    private static final int TILE = 128;

    private PooledEngine engine;
    private MeleeAttackSystem system;
    private Array<Rectangle> secretRects;
    private Array<Rectangle> collisionRects;
    private TiledMapTileLayer collisionLayer;

    @Before
    public void setUp() {
        engine = new PooledEngine();
        secretRects = new Array<>();
        collisionRects = new Array<>();
        collisionLayer = new TiledMapTileLayer(16, 16, TILE, TILE);
        system = new MeleeAttackSystem(mockAssets(), secretRects, collisionRects, collisionLayer, null, 0);
        system.setUnitScale(1f);
        engine.addSystem(system);
    }

    private static AssetManager mockAssets() {
        return mock(AssetManager.class);
    }

    private static Animation<TextureRegion> attackAnimation() {
        TextureRegion[] frames = new TextureRegion[] {
            new TextureRegion(), new TextureRegion(), new TextureRegion(), new TextureRegion(), new TextureRegion()
        };
        return new Animation<TextureRegion>(0.1f, frames);
    }

    /** Adds one secret wall at the given world rect, registering it in both shared arrays and its cell. */
    private void addSecretWall(float x, float y) {
        Rectangle rect = new Rectangle(x, y, TILE, TILE);
        secretRects.add(rect);
        collisionRects.add(rect);
        TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
        cell.setTile(new StaticTiledMapTile(new TextureRegion()));
        collisionLayer.setCell((int) (x / TILE), (int) (y / TILE), cell);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        PlayerComponent playerComponent = player();
        playerComponent.facingDirection = 1;
        AnimationComponent animation = new AnimationComponent();
        animation.animations.put(AnimationComponent.State.ATTACKING, attackAnimation());
        Entity entity = entity(transform, playerComponent, collision, animation);
        engine.addEntity(entity);
        return entity;
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), collision, new EnemyComponent());
        engine.addEntity(entity);
        return entity;
    }

    private int particleEntityCount() {
        int count = 0;
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(ParticleComponent.class) != null) {
                count++;
            }
        }
        return count;
    }

    private ParticleComponent firstParticle() {
        for (Entity entity : engine.getEntities()) {
            ParticleComponent particle = entity.getComponent(ParticleComponent.class);
            if (particle != null) {
                return particle;
            }
        }
        return null;
    }

    @Test
    public void strikingWallRemovesRectsAndBlanksCell() {
        addSecretWall(16f, 105f);
        Entity playerEntity = player(0f, 130f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertEquals(0, secretRects.size);
        assertEquals(0, collisionRects.size);
        assertNull(collisionLayer.getCell(0, 0).getTile());
        assertTrue(PLAYER.get(playerEntity).meleeHasHit);
    }

    @Test
    public void strikingWallSpawnsSmokePuff() {
        addSecretWall(16f, 105f);
        Entity playerEntity = player(0f, 130f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertEquals(1, particleEntityCount());
    }

    @Test
    public void swingMissingEveryWallBreaksNothing() {
        addSecretWall(2000f, 105f);
        Entity playerEntity = player(0f, 130f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertEquals(1, secretRects.size);
        assertEquals(1, collisionRects.size);
        assertNotNull(collisionLayer.getCell((int) (2000f / TILE), 0).getTile());
        assertFalse(PLAYER.get(playerEntity).meleeHasHit);
        assertEquals(0, particleEntityCount());
    }

    @Test
    public void strikingRegularWallSpawnsSpark() {
        collisionRects.add(new Rectangle(16f, 105f, 128f, 128f));
        Entity playerEntity = player(0f, 130f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertTrue(PLAYER.get(playerEntity).meleeHasHit);
        assertEquals(1, particleEntityCount());
        ParticleComponent spark = firstParticle();
        assertNotNull(spark);
        assertEquals(EnemyDamageResolver.HIT_SPARK_MAX_LIFETIME, spark.maxLifetime, EPSILON);
        assertEquals(0, secretRects.size);
    }

    @Test
    public void enemyHitTakesPriorityOverWallInSameSwing() {
        addSecretWall(16f, 105f);
        Entity playerEntity = player(0f, 130f);
        Entity enemyEntity = enemy(16f, 105f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertEquals(5f, ENEMY.get(enemyEntity).health, EPSILON);
        assertTrue(PLAYER.get(playerEntity).meleeHasHit);
        assertEquals(1, secretRects.size);
        assertEquals(1, collisionRects.size);
        assertNotNull(collisionLayer.getCell(0, 0).getTile());
    }

    @Test
    public void twoSwingAfterBreakDoesNotCrashAndKeepsWallGone() {
        addSecretWall(16f, 105f);
        Entity playerEntity = player(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(playerEntity);

        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);
        assertEquals(0, secretRects.size);

        playerComponent.meleeAttack.start(0.35f);
        engine.update(0f);

        assertEquals(0, secretRects.size);
        assertNull(collisionLayer.getCell(0, 0).getTile());
    }

    @Test
    public void breakingSecretRoomWallRevealsRoom() {
        SecretRoom secretRoom = new SecretRoom("secretRoom1", null);
        SecretRoomRevealer revealer = new SecretRoomRevealer(engine, mock(EntityFactory.class), new RoomState());
        revealer.setRooms(Array.with(secretRoom));
        engine.removeSystem(system);
        system = new MeleeAttackSystem(mockAssets(), secretRects, collisionRects, collisionLayer, null, revealer, 0);
        system.setUnitScale(1f);
        engine.addSystem(system);

        addSecretWall(16f, 105f);
        collisionLayer.getCell(0, 0).getTile().getProperties().put("secretRoom", "secretRoom1");
        Entity playerEntity = player(0f, 130f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertTrue(revealer.isRevealed("secretRoom1"));
        assertEquals(0, secretRects.size);
    }

    @Test
    public void breakingSecretRoomWallBlanksVeilAndSpawnsVeilSmoke() {
        TiledMapTileLayer hideLayer = new TiledMapTileLayer(16, 16, TILE, TILE);
        TiledMapTileLayer.Cell veilCell = new TiledMapTileLayer.Cell();
        veilCell.setTile(new StaticTiledMapTile(new TextureRegion()));
        hideLayer.setCell(0, 0, veilCell);
        TiledMapTileLayer.Cell neighborCell = new TiledMapTileLayer.Cell();
        neighborCell.setTile(new StaticTiledMapTile(new TextureRegion()));
        hideLayer.setCell(1, 1, neighborCell);
        SecretRoom secretRoom = new SecretRoom("secretRoom1", new Room(0f, 0f, TILE, TILE));
        secretRoom.veilCells.add(new Rectangle(0f, 0f, TILE, TILE));
        SecretRoomRevealer revealer = new SecretRoomRevealer(engine, mock(EntityFactory.class), new RoomState());
        revealer.setRooms(Array.with(secretRoom));
        revealer.setHideLayer(hideLayer);
        engine.removeSystem(system);
        system = new MeleeAttackSystem(mockAssets(), secretRects, collisionRects, collisionLayer, null, revealer, 0);
        system.setUnitScale(1f);
        engine.addSystem(system);

        addSecretWall(16f, 105f);
        collisionLayer.getCell(0, 0).getTile().getProperties().put("secretRoom", "secretRoom1");
        Entity playerEntity = player(0f, 130f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertNull(hideLayer.getCell(0, 0).getTile());
        assertNotNull(hideLayer.getCell(1, 1).getTile());
        assertEquals(2, particleEntityCount());
    }

    @Test
    public void revealSpawnsDeferredObjectsExactlyOnceAcrossTwoWalls() {
        MapObject coin = new RectangleMapObject(0f, 0f, TILE, TILE);
        SecretRoom secretRoom = new SecretRoom("secretRoom1", null);
        secretRoom.deferredObjects.add(coin);
        EntityFactory entityFactory = mock(EntityFactory.class);
        SecretRoomRevealer revealer = new SecretRoomRevealer(engine, entityFactory, new RoomState());
        revealer.setRooms(Array.with(secretRoom));
        engine.removeSystem(system);
        system = new MeleeAttackSystem(mockAssets(), secretRects, collisionRects, collisionLayer, null, revealer, 0);
        system.setUnitScale(1f);
        engine.addSystem(system);

        addSecretWall(16f, 105f);
        addSecretWall(144f, 105f);
        collisionLayer.getCell(0, 0).getTile().getProperties().put("secretRoom", "secretRoom1");
        collisionLayer.getCell(1, 0).getTile().getProperties().put("secretRoom", "secretRoom1");
        Entity playerEntity = player(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(playerEntity);

        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);
        assertEquals(1, secretRects.size);
        verify(entityFactory, times(1)).spawnObjects(any(com.badlogic.ashley.core.Engine.class), any(MapObjects.class), any(RoomState.class));

        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 160f, 130f);
        playerComponent.meleeHasHit = false;
        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);
        assertEquals(0, secretRects.size);
        verify(entityFactory, times(1)).spawnObjects(any(com.badlogic.ashley.core.Engine.class), any(MapObjects.class), any(RoomState.class));
    }

    @Test
    public void secretRoomWallWithoutRevealerStillBreaks() {
        addSecretWall(16f, 105f);
        collisionLayer.getCell(0, 0).getTile().getProperties().put("secretRoom", "secretRoom1");
        Entity playerEntity = player(0f, 130f);
        PLAYER.get(playerEntity).meleeAttack.start(0.15f);

        engine.update(0f);

        assertEquals(0, secretRects.size);
        assertEquals(0, collisionRects.size);
        assertNull(collisionLayer.getCell(0, 0).getTile());
        assertTrue(PLAYER.get(playerEntity).meleeHasHit);
    }
}
