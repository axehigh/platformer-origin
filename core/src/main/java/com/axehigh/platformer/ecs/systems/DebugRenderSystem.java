package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyAttackComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
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

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Draws every active {@code CollisionComponent} AABB (both entity bounds and the static map
 * {@code collisionRects}), plus the current level's Room rectangles ({@code RoomState.rooms}, in
 * cyan, with the currently active one highlighted in orange) and the player's current melee
 * strike hitbox ({@code MeleeAttackSystem#getActiveStrikeBounds()}, in red, while a swing is live),
 * as outlined rectangles via a {@code ShapeRenderer}, toggled with SHIFT+D (desktop) or from the
 * pause menu's "Collision Debug" button (all platforms, see {@code GameScreen#showPauseDialog}),
 * see AGENTS.md "Debugging". Also draws, per melee-capable enemy, its omni-directional detection
 * box (magenta, centered on the enemy, sized exactly like {@code EnemyAttackSystem}'s runtime
 * check: {@code attackRange × 3 × unitScale} per horizontal side, {@code detectionHeight} (= 1.25
 * tiles, default 20u) × unitScale tall total), its attack-range commit rectangle (green,
 * {@code attackRange × unitScale} wide adjacent to the enemy's current facing edge, collision
 * height tall), and its live strike hitbox (red, enemy collision width × height, only while the
 * strike window is active — pulled from {@code EnemyAttackSystem#getActiveStrikeBounds()}, resolved
 * once in {@code addedToEngine}, mirroring the player's live strike) so trigger/commit/strike
 * distances are visible alongside the AABBs. The toggle is static so it survives level reloads
 * within a session. Disabled by default; drawing is skipped entirely while off, so there's no
 * per-frame cost in normal play. Must run after {@code RenderSystem} so its {@code ShapeRenderer}
 * block never overlaps the {@code SpriteBatch} block (the two can never be open at the same time).
 */
public class DebugRenderSystem extends EntitySystem implements Disposable {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final OrthographicCamera camera;
    private final Array<Rectangle> staticCollisionRects;
    private final Array<Rectangle> oneWayRects;
    private final Array<Rectangle> hazardRects;
    private final RoomState roomState;
    private ImmutableArray<Entity> collidables;
    private ImmutableArray<Entity> enemyAttackers;
    private MeleeAttackSystem meleeAttackSystem;
    private EnemyAttackSystem enemyAttackSystem;
    private static boolean debugEnabled = false;
    private float unitScale = 1f;

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

    public void setUnitScale(float unitScale) {
        this.unitScale = unitScale;
    }

    @Override
    public void addedToEngine(Engine engine) {
        collidables = engine.getEntitiesFor(Family.all(TransformComponent.class, CollisionComponent.class).get());
        meleeAttackSystem = engine.getSystem(MeleeAttackSystem.class);
        enemyAttackSystem = engine.getSystem(EnemyAttackSystem.class);
        enemyAttackers = engine.getEntitiesFor(Family.all(EnemyComponent.class, EnemyAttackComponent.class, CollisionComponent.class).get());
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

        // Per melee-capable enemy: detection box (magenta) + attack-range commit rect (green) + live
        // strike hitbox (red, only while the strike window is live), sized exactly like
        // EnemyAttackSystem's runtime checks.
        for (Entity entity : enemyAttackers) {
            CollisionComponent collision = COLLISION.get(entity);
            EnemyAttackComponent attack = ENEMY_ATTACK.get(entity);
            EnemyComponent enemy = ENEMY.get(entity);

            // Detection box (magenta): centered on the enemy's AABB center, attackRange*3 wide
            // per side, detectionHeight (1.25 tiles) tall total — opens exactly like the runtime check.
            float detectWidth = attack.attackRange * 3f * unitScale * 2f;
            float detectHeight = attack.detectionHeight * unitScale;
            float centerX = collision.worldBounds.x + collision.worldBounds.width / 2f;
            float centerY = collision.worldBounds.y + collision.worldBounds.height / 2f;
            shapeRenderer.setColor(Color.MAGENTA);
            shapeRenderer.rect(centerX - detectWidth / 2f, centerY - detectHeight / 2f, detectWidth, detectHeight);

            // Commit distance (green): attackRange wide adjacent to the enemy's facing edge.
            float commitWidth = attack.attackRange * unitScale;
            float commitX = enemy.direction > 0
                ? collision.worldBounds.x + collision.worldBounds.width
                : collision.worldBounds.x - commitWidth;
            shapeRenderer.setColor(Color.GREEN);
            shapeRenderer.rect(commitX, collision.worldBounds.y, commitWidth, collision.worldBounds.height);

            // Live strike (red): only while the strike window is active — pulled from
            // EnemyAttackSystem, mirroring the player's live strike.
            Rectangle live = enemyAttackSystem != null ? enemyAttackSystem.getActiveStrikeBounds() : null;
            if (live != null) {
                shapeRenderer.setColor(Color.RED);
                shapeRenderer.rect(live.x, live.y, live.width, live.height);
            }
        }

        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
