package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.ecs.components.TrapComponent;
import com.axehigh.platformer.ecs.components.TrapComponent.TrapType;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;
import static com.axehigh.platformer.assets.SpriteConstants.AcidDropCollisionHeight;
import static com.axehigh.platformer.assets.SpriteConstants.AcidDropCollisionWidth;
import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless unit tests for {@code TrapSystem}: the acid-drop spawner drops projectiles centered on
 * the acid tube (a full 128px tile at the spawner's marker, so the drop's center sits half a tile
 * up/right from the marker corner), then the drops hang briefly (drip build), fall/accelerate, and
 * either vanish on a wall or spawn a lingering acid pool when they land on the ground (down
 * direction).
 */
public class TrapSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private RoomState roomState;
    private com.axehigh.platformer.ecs.systems.TrapSystem system;
    private com.badlogic.ashley.core.PooledEngine engine;

    @Before
    public void setUp() {
        roomState = new RoomState();
        // Mock the atlas so `TrapSystem`'s constructor lookup and every `findRegion` resolve:
        // the trapped atlas has no real assets headless, and a bare `AssetManager.get` throws.
        TextureAtlas atlas = mock(TextureAtlas.class);
        Texture fakeTexture = mock(Texture.class);
        AtlasRegion fakeRegion = new AtlasRegion(fakeTexture, 0, 0, 128, 128);
        when(atlas.findRegion(anyString())).thenReturn(fakeRegion);
        AssetManager assetManager = mock(AssetManager.class);
        when(assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class)).thenReturn(atlas);
        system = new com.axehigh.platformer.ecs.systems.TrapSystem(collisionRects, roomState, assetManager, 0);
        engine = new com.badlogic.ashley.core.PooledEngine();
        engine.addSystem(system);
    }

    private Entity acidSpawner(float x, float y) {
        return acidSpawner(x, y, TrapComponent.TrapDirection.DOWN);
    }

    private Entity acidSpawner(float x, float y, TrapComponent.TrapDirection direction) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 16f, 16f);
        place(transform, collision, x, y);
        TrapComponent trap = new TrapComponent();
        trap.type = TrapType.ACID_DROP_SPAWNER;
        trap.spawnDirection = direction;
        // Tiny first-spawn timer so the tube's discharge starts on the next update, then a long
        // interval so it won't respawn within the same test frame.
        trap.spawnTimer.start(0.001f);
        trap.spawnInterval = 10f;
        Entity entity = entity(transform, collision, trap);
        engine.addEntity(entity);
        return entity;
    }

    private Entity singleDrop() {
        for (Entity e : engine.getEntities()) {
            TrapComponent trap = TRAP.get(e);
            if (trap != null && trap.type == TrapType.ACID_DROP) {
                return e;
            }
        }
        return null;
    }

    private Entity singlePool() {
        for (Entity e : engine.getEntities()) {
            TrapComponent trap = TRAP.get(e);
            if (trap != null && trap.type == TrapType.ACID_POOL) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void dropSpawnsCenteredOnAcidTube() {
        acidSpawner(100f, 200f);

        // The drop only spawns once the tube's discharging animation (0.4s windup) completes.
        for (int i = 0; i < 25; i++) {
            engine.update(DT);
        }

        // Spawner + one drop = 2 entities. At scale 1 the tube is a 16-unit tile at the maker, so
        // its center is (x+8, y+8); the drop's collision box is sized from constants, so its
        // bottom-left sits half-width/half-height below-left of that center (the center is the
        // drop's center).
        assertEquals(2, engine.getEntities().size());
        for (Entity e : engine.getEntities()) {
            TrapComponent trap = TRAP.get(e);
            if (trap != null && trap.type == TrapType.ACID_DROP) {
                assertEquals(100f + 8f - AcidDropCollisionWidth / 2f, TRANSFORM.get(e).position.x, EPSILON);
                assertEquals(200f + 8f - AcidDropCollisionHeight / 2f, TRANSFORM.get(e).position.y, EPSILON);
                return;
            }
        }
        throw new AssertionError("acid drop was not spawned");
    }

    @Test
    public void dropOverlappingWallSurvivesSpawnGraceThenIsRemoved() {
        // Huge wall covering the drop's spawn box (spawner at corner, drop centered on it, box 8x12).
        collisionRects.add(new Rectangle(-100f, -200f, 200f, 400f));
        // Travel LEFT so an overlap is a wall-vanish (no pool); a DOWN landing would instead spawn a pool.
        acidSpawner(0f, 0f, TrapComponent.TrapDirection.LEFT);

        // Once the windup completes the drop spawns already overlapping the wall; the drip-build hang
        // plus the spawn-grace window must keep it alive so it can clear the wall instead of being culled.
        for (int i = 0; i < 25; i++) {
            engine.update(DT);
        }
        assertEquals(2, engine.getEntities().size());

        // Once it releases and the grace window elapses the drop is still inside the huge wall and
        // must be removed like any wall-hitting drop.
        for (int i = 0; i < 40; i++) {
            engine.update(DT);
        }
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void dropRemovedWhenHittingWallAfterGrace() {
        // Wall placed just to the left of the spawn so the drop travels left and hits it after
        // clearing the drip-build + spawn-grace windows.
        collisionRects.add(new Rectangle(-50f, -300f, 40f, 600f));
        acidSpawner(0f, 0f, TrapComponent.TrapDirection.LEFT);

        // Step far enough that the grace has ended and the drop has travelled left and hit the wall.
        for (int i = 0; i < 60; i++) {
            engine.update(DT);
        }

        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void dropHangsDuringDripBuildBeforeFalling() {
        // No walls: the drop must not move while the drip-build timer is active.
        acidSpawner(100f, 200f);

        // Step through the tube windup so the drop exists, then it is in its drip-build hang.
        for (int i = 0; i < 25; i++) {
            engine.update(DT);
        }

        Entity drop = singleDrop();
        assertNotNull("acid drop was not spawned", drop);
        assertEquals(100f + 8f - AcidDropCollisionWidth / 2f, TRANSFORM.get(drop).position.x, EPSILON);
        assertEquals(200f + 8f - AcidDropCollisionHeight / 2f, TRANSFORM.get(drop).position.y, EPSILON);

        // A few frames in, still inside the drip-build hang: position must be unchanged.
        for (int i = 0; i < 5; i++) {
            engine.update(DT);
        }
        assertEquals(100f + 8f - AcidDropCollisionWidth / 2f, TRANSFORM.get(drop).position.x, EPSILON);
        assertEquals(200f + 8f - AcidDropCollisionHeight / 2f, TRANSFORM.get(drop).position.y, EPSILON);
    }

    @Test
    public void downDropAcceleratesUnderGravityThenSpawnsPool() {
        // Wall below the spawn so the falling (DOWN) drop lands on it and spawns an acid pool.
        collisionRects.add(new Rectangle(-100f, -300f, 200f, 140f)); // top edge at y=-160
        acidSpawner(0f, 0f, TrapComponent.TrapDirection.DOWN);

        // Run long enough for drip build + fall + landing (the tube-centered spawn sits 8 units
        // higher than the tile corner, so the fall takes longer).
        for (int i = 0; i < 80; i++) {
            engine.update(DT);
        }

        Entity pool = singlePool();
        assertNotNull("DOWN landing should spawn an acid pool", pool);
        assertEquals(-160f, TRANSFORM.get(pool).position.y, EPSILON);
        assertNotNull("pool entity missing collision", COLLISION.get(pool));

        // The pool sits for ~1.5s, then disappears.
        for (int i = 0; i < 120; i++) {
            engine.update(DT);
        }
        assertEquals("pool should be removed after its lifetime", 1, engine.getEntities().size());
    }

    @Test
    public void downLandingSpawnsPoolButLeftLandingDoesNot() {
        // Down drop onto a floor spawns a pool.
        collisionRects.add(new Rectangle(-100f, -300f, 200f, 140f));
        acidSpawner(0f, 0f, TrapComponent.TrapDirection.DOWN);
        for (int i = 0; i < 80; i++) {
            engine.update(DT);
        }
        assertNotNull("DOWN landing should spawn a pool", singlePool());

        // Reset and confirm a LEFT drop instead just vanishes (no pool).
        engine.removeAllEntities();
        collisionRects.clear();
        collisionRects.add(new Rectangle(-100f, -300f, 40f, 600f));
        acidSpawner(0f, 0f, TrapComponent.TrapDirection.LEFT);
        for (int i = 0; i < 60; i++) {
            engine.update(DT);
        }
        assertEquals("LEFT landing must not spawn a pool", 1, engine.getEntities().size());
    }

    @Test
    public void poolGetsOneShotSplashAnimationOnLanding() {
        collisionRects.add(new Rectangle(-100f, -300f, 200f, 140f));
        acidSpawner(0f, 0f, TrapComponent.TrapDirection.DOWN);
        for (int i = 0; i < 80; i++) {
            engine.update(DT);
        }

        Entity pool = singlePool();
        assertNotNull("DOWN landing should spawn an acid pool", pool);

        AnimationComponent anim = ANIMATION.get(pool);
        assertNotNull("pool should carry a one-shot splash animation", anim);
        assertEquals("pool splash state", AnimationComponent.State.SPLASHING, anim.currentState);
        Animation<TextureRegion> clip = anim.animations.get(AnimationComponent.State.SPLASHING);
        assertNotNull("pool splash clip missing", clip);
        // libGDX's `Animation.getKeyFrames()` erases to `Object[]`, so assert via duration instead:
        // 7 frames at 0.05s/frame == the full acid_blob1..7 clip played exactly once.
        assertEquals("splash clip should play the full acid_blob1..7 once", 7 * 0.05f, clip.getAnimationDuration(), EPSILON);
        assertEquals("splash clip must not loop (one round)", Animation.PlayMode.NORMAL, clip.getPlayMode());
    }

    @Test
    public void subsequentPoolAnimatesCorrectly() {
        collisionRects.add(new Rectangle(-100f, -300f, 200f, 140f));
        Entity spawner = acidSpawner(0f, 0f, TrapComponent.TrapDirection.DOWN);
        TRAP.get(spawner).spawnInterval = 1.2f;

        // First drop lands and spawns pool 1
        for (int i = 0; i < 80; i++) {
            engine.update(DT);
        }
        Entity pool1 = singlePool();
        assertNotNull("pool 1 should spawn", pool1);
        AnimationComponent anim1 = ANIMATION.get(pool1);
        assertEquals(0f, anim1.stateTime, EPSILON);

        // Wait until pool 1 expires and disappears, plus wait for the next drop from spawner to fall and land
        for (int i = 0; i < 150; i++) {
            engine.update(DT);
        }

        // Second pool should eventually spawn and have stateTime reset (animating from frame 0)
        Entity pool2 = singlePool();
        assertNotNull("pool 2 should spawn", pool2);
        AnimationComponent anim2 = ANIMATION.get(pool2);
        assertNotNull("pool 2 should carry animation", anim2);
        assertEquals("pool 2 animation should start at stateTime = 0", 0f, anim2.stateTime, 0.1f);
    }
}
