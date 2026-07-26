package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Draws every active {@code CollisionComponent} AABB (both entity bounds and the static map
 * {@code collisionRects}), plus the current level's Room rectangles ({@code RoomState.rooms}, in
 * cyan, with the currently active one highlighted in orange), as outlined rectangles via a
 * {@code ShapeRenderer}, toggled on/off with SHIFT+D (see AGENTS.md "Debugging"). Disabled by
 * default; drawing is skipped entirely while off, so there's no per-frame cost in normal play.
 * Must run after {@code RenderSystem} so its {@code ShapeRenderer} block never overlaps the
 * {@code SpriteBatch} block (the two can never be open at the same time).
 */
public class DebugRenderSystem extends EntitySystem implements Disposable {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final OrthographicCamera camera;
    private final Array<Rectangle> staticCollisionRects;
    private final RoomState roomState;
    private ImmutableArray<Entity> collidables;
    private boolean debugEnabled = false;

    public DebugRenderSystem(OrthographicCamera camera, Array<Rectangle> staticCollisionRects, RoomState roomState) {
        this(camera, staticCollisionRects, roomState, 0);
    }

    public DebugRenderSystem(OrthographicCamera camera, Array<Rectangle> staticCollisionRects, RoomState roomState, int priority) {
        super(priority);
        this.camera = camera;
        this.staticCollisionRects = staticCollisionRects;
        this.roomState = roomState;
    }

    @Override
    public void addedToEngine(Engine engine) {
        collidables = engine.getEntitiesFor(Family.all(TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        boolean shiftHeld = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (shiftHeld && Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            debugEnabled = !debugEnabled;
        }
        if (!debugEnabled) {
            return;
        }

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeType.Line);

        shapeRenderer.setColor(Color.YELLOW);
        for (Rectangle rect : staticCollisionRects) {
            shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height);
        }

        shapeRenderer.setColor(Color.LIME);
        for (Entity entity : collidables) {
            CollisionComponent collision = COLLISION.get(entity);
            shapeRenderer.rect(collision.worldBounds.x, collision.worldBounds.y, collision.worldBounds.width, collision.worldBounds.height);
        }

        for (int i = 0; i < roomState.rooms.size; i++) {
            Rectangle room = roomState.rooms.get(i);
            shapeRenderer.setColor(i == roomState.activeRoomIndex ? Color.ORANGE : Color.CYAN);
            shapeRenderer.rect(room.x, room.y, room.width, room.height);
        }

        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
