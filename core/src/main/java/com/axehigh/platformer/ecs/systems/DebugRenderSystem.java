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
 * cyan, with the currently active one highlighted in orange) and the player's current melee
 * strike hitbox ({@code MeleeAttackSystem#getActiveStrikeBounds()}, in red, while a swing is live),
 * as outlined rectangles via a {@code ShapeRenderer}, toggled with SHIFT+D (desktop) or from the
 * pause menu's "Collision Debug" button (all platforms, see {@code GameScreen#showPauseDialog}),
 * see AGENTS.md "Debugging". The toggle is static so it survives level reloads within a session.
 * Disabled by default; drawing is skipped entirely while off, so there's no per-frame cost in
 * normal play. Must run after {@code RenderSystem} so its {@code ShapeRenderer} block never
 * overlaps the {@code SpriteBatch} block (the two can never be open at the same time).
 */
public class DebugRenderSystem extends EntitySystem implements Disposable {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final OrthographicCamera camera;
    private final Array<Rectangle> staticCollisionRects;
    private final Array<Rectangle> oneWayRects;
    private final Array<Rectangle> hazardRects;
    private final RoomState roomState;
    private ImmutableArray<Entity> collidables;
    private MeleeAttackSystem meleeAttackSystem;
    private static boolean debugEnabled = false;

    public DebugRenderSystem(OrthographicCamera camera, Array<Rectangle> staticCollisionRects, Array<Rectangle> oneWayRects, Array<Rectangle> hazardRects, RoomState roomState) {
        this(camera, staticCollisionRects, oneWayRects, hazardRects, roomState, 0);
    }

    public DebugRenderSystem(OrthographicCamera camera, Array<Rectangle> staticCollisionRects, Array<Rectangle> oneWayRects, Array<Rectangle> hazardRects, RoomState roomState, int priority) {
        super(priority);
        this.camera = camera;
        this.staticCollisionRects = staticCollisionRects;
        this.oneWayRects = oneWayRects;
        this.hazardRects = hazardRects;
        this.roomState = roomState;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    @Override
    public void addedToEngine(Engine engine) {
        collidables = engine.getEntitiesFor(Family.all(TransformComponent.class, CollisionComponent.class).get());
        meleeAttackSystem = engine.getSystem(MeleeAttackSystem.class);
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

        shapeRenderer.setColor(Color.CYAN);
        for (Rectangle rect : oneWayRects) {
            shapeRenderer.rect(rect.x, rect.y, rect.width, rect.height);
        }

        shapeRenderer.setColor(Color.RED);
        for (Rectangle rect : hazardRects) {
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

        if (meleeAttackSystem != null) {
            Rectangle strike = meleeAttackSystem.getActiveStrikeBounds();
            if (strike != null) {
                shapeRenderer.setColor(Color.RED);
                shapeRenderer.rect(strike.x, strike.y, strike.width, strike.height);
            }
        }

        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
