package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.CrumblingTile;
import com.axehigh.platformer.particles.ParticleHelper;
import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Drives the crumbling-platform state machine for every collision-layer tile flagged
 * {@code crumble=true} (see {@code CrumblingTile}). While {@code INTACT} the tile behaves like a
 * one-way platform (its shared {@code oneWayRects} rect stands); when the player lands on top of it,
 * stays in continuous contact past a short landing grace, is grounded, and isn't mid drop-through,
 * the tile arms: its cell is blanked the moment it starts {@code SHAKING} (the static cell can't be
 * double-drawn) and it visibly jitters for {@link GameConstants#CRUMBLE_SHAKE_DURATION} (redrawn
 * with a random pixel offset by {@code CrumblingTileRenderSystem}), then {@code COLLAPSED}s — the
 * rect is identity-removed from the shared {@code oneWayRects} so the player falls through the same
 * frame (this system runs before {@code MovementSystem}), and the collapse marks the spot with a
 * smoke puff plus a stone-chip debris burst. After a respawn delay the tile restores its original
 * cell and rect (blocked while the player or any enemy still occupies the cell), then returns to
 * {@code INTACT} — but a settle grace plus the {@code wasOverlapped} contact edge prevents it from
 * re-crumbling instantly under a player still standing there.
 */
public class CrumblingTileSystem extends EntitySystem {
    /**
     * Touch tolerance: {@code Rectangle.overlaps} is strict (needs positive overlap), but
     * {@code MovementSystem} snaps a landing player's feet to <i>exactly</i> the platform top edge,
     * so a standing body only ever touches the tile rect — it must still count as contact, both to
     * arm the crumble and to block a respawn under a standing/enemy body.
     */
    private static final float TOUCH_EPSILON = 0.01f;

    private final Array<CrumblingTile> crumblingTiles;
    private final Array<Rectangle> oneWayRects;
    private final Rectangle touchRect = new Rectangle();
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> enemies;
    private Engine engine;

    public CrumblingTileSystem(Array<CrumblingTile> crumblingTiles) {
        this(crumblingTiles, new Array<>(), 0);
    }

    public CrumblingTileSystem(Array<CrumblingTile> crumblingTiles, int priority) {
        this(crumblingTiles, new Array<>(), priority);
    }

    public CrumblingTileSystem(Array<CrumblingTile> crumblingTiles, Array<Rectangle> oneWayRects) {
        this(crumblingTiles, oneWayRects, 0);
    }

    public CrumblingTileSystem(Array<CrumblingTile> crumblingTiles, Array<Rectangle> oneWayRects, int priority) {
        super(priority);
        this.crumblingTiles = crumblingTiles;
        this.oneWayRects = oneWayRects;
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class, CollisionComponent.class, MovementComponent.class).get());
        enemies = engine.getEntitiesFor(Family.all(EnemyComponent.class, TransformComponent.class, CollisionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() == 0) {
            return;
        }
        Entity playerEntity = players.first();
        PlayerComponent playerComp = PLAYER.get(playerEntity);
        if (playerComp.isDead) {
            return;
        }
        TransformComponent transform = TRANSFORM.get(playerEntity);
        CollisionComponent collision = COLLISION.get(playerEntity);
        MovementComponent movement = MOVEMENT.get(playerEntity);

        for (CrumblingTile tile : crumblingTiles) {
            switch (tile.state) {
                case INTACT:
                    boolean overlaps = touches(collision.worldBounds, tile);
                    tile.settleTimer.update(deltaTime);
                    if (overlaps) {
                        // Fresh contact after a break (or after respawn's settle grace) starts the
                        // arming grace; sustained contact just keeps ticking it down.
                        if (!tile.settleTimer.isActive() && !tile.wasOverlapped) {
                            if (!tile.contactTimer.isActive()) {
                                tile.contactTimer.start(GameConstants.CRUMBLE_LANDING_GRACE);
                            }
                        }
                        tile.contactTimer.update(deltaTime);
                    } else {
                        // Contact broke: wipe the armed grace so a graze (passing under/through)
                        // never builds toward a crumble.
                        tile.contactTimer.reset();
                    }

                    if (overlaps
                        && !tile.settleTimer.isActive()
                        && tile.contactTimer.isDone()
                        && (transform.position.y + collision.bounds.y) >= (tile.rect.y + tile.rect.height - 1.0f)
                        && movement.grounded
                        && !playerComp.dropWindow.isActive()) {
                        tile.state = CrumblingTile.State.SHAKING;
                        tile.shakeTimer.start(GameConstants.CRUMBLE_SHAKE_DURATION);
                        tile.contactTimer.reset();
                        // Blank the static cell the moment the shake starts: the tile stays solid
                        // (its oneWayRects rect still stands) but is redrawn jittered by
                        // CrumblingTileRenderSystem from the captured original cell, so the map
                        // never double-draws it at its resting position.
                        tile.layer.setCell(tile.cellX, tile.cellY, null);
                    }
                    tile.wasOverlapped = overlaps;
                    break;

                case SHAKING:
                    tile.shakeTimer.update(deltaTime);
                    if (tile.shakeTimer.isDone()) {
                        tile.state = CrumblingTile.State.COLLAPSED;
                        oneWayRects.removeValue(tile.rect, true);
                        // The cell was already blanked at arm (SHAKING); only the rect removal,
                        // debris burst, and respawn timer happen here.
                        if (engine instanceof PooledEngine) {
                            ParticleHelper.spawnSmallSmoke((PooledEngine) engine,
                                tile.rect.x + tile.rect.width / 2f, tile.rect.y + tile.rect.height / 2f);
                            ParticleHelper.spawnStoneChips((PooledEngine) engine,
                                tile.rect.x + tile.rect.width / 2f, tile.rect.y + tile.rect.height / 2f);
                        }
                        tile.respawnTimer.start(GameConstants.CRUMBLE_RESPAWN_DURATION);
                    }
                    break;

                case COLLAPSED:
                    tile.respawnTimer.update(deltaTime);
                    if (tile.respawnTimer.isDone()
                        && tile.layer.getCell(tile.cellX, tile.cellY) == null
                        && !isOccupied(tile, collision)) {
                        tile.layer.setCell(tile.cellX, tile.cellY, tile.originalCell);
                        oneWayRects.add(tile.rect);
                        tile.state = CrumblingTile.State.INTACT;
                        tile.wasOverlapped = false;
                        tile.contactTimer.reset();
                        tile.settleTimer.start(GameConstants.CRUMBLE_SETTLE_GRACE);
                        if (engine instanceof PooledEngine) {
                            ParticleHelper.spawnSmallSmoke((PooledEngine) engine,
                                tile.rect.x + tile.rect.width / 2f, tile.rect.y + tile.rect.height / 2f);
                        }
                    }
                    break;
            }
        }
    }

    /**
     * Whether {@code a} occupies the tile's cell, treating an exactly-shared edge (a player/enemy
     * standing on the tile's top, which movement snaps flush) as contact.
     */
    private boolean touches(Rectangle a, CrumblingTile tile) {
        touchRect.set(tile.rect);
        touchRect.x -= TOUCH_EPSILON;
        touchRect.y -= TOUCH_EPSILON;
        touchRect.width += 2f * TOUCH_EPSILON;
        touchRect.height += 2f * TOUCH_EPSILON;
        return a.overlaps(touchRect);
    }

    /**
     * Whether the player or any enemy AABB overlaps the tile — blocks a respawn while the cell is in
     * use, so a collapse never restores a floor underneath a standing body.
     */
    private boolean isOccupied(CrumblingTile tile, CollisionComponent playerCollision) {
        if (touches(playerCollision.worldBounds, tile)) {
            return true;
        }
        for (Entity enemy : enemies) {
            if (touches(COLLISION.get(enemy).worldBounds, tile)) {
                return true;
            }
        }
        return false;
    }
}
