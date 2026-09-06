package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.map.CrumblingTile;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/**
 * Draws the visible shake of every {@link CrumblingTile} currently in {@code State.SHAKING} with a
 * random integer-pixel jitter, so a crumbling platform vibrates in place instead of freezing the
 * static tile texture. The tile's collision-layer cell is blanked the moment it arms (see {@code
 * CrumblingTileSystem}), so this system redraws it from the captured {@code CrumblingTile#originalCell}
 * at a per-frame random offset. No art needed — it reuses the original cell's {@code TextureRegion}.
 *
 * <p>Pure renderer: touches no component, timer, or gameplay state (only reads — {@code
 * MathUtils}, no {@code Gdx} statics). It renders at priority {@code 21}, directly after the map
 * renderer ({@code 20}) and before entities ({@code 30}), above whatever background layer the cell
 * normally covers. It holds the SAME shared {@code Array<CrumblingTile>} instance the
 * {@code CrumblingTileSystem} and {@code LevelManager} use — the array is refilled in place on
 * every level swap, so this system follows level changes automatically with no re-wiring.
 */
public class CrumblingTileRenderSystem extends EntitySystem {
    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private final Array<CrumblingTile> crumblingTiles;

    public CrumblingTileRenderSystem(SpriteBatch batch, OrthographicCamera camera,
                                     Array<CrumblingTile> crumblingTiles, int priority) {
        super(priority);
        this.batch = batch;
        this.camera = camera;
        this.crumblingTiles = crumblingTiles;
    }

    @Override
    public void update(float deltaTime) {
        if (!hasShakingTile()) {
            return;
        }
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (CrumblingTile tile : crumblingTiles) {
            if (tile.state != CrumblingTile.State.SHAKING) {
                continue;
            }
            if (tile.originalCell.getTile() == null) {
                continue;
            }
            TextureRegion region = tile.originalCell.getTile().getTextureRegion();
            if (region == null) {
                continue;
            }
            // Jitter magnitude decays with the remaining shake time (3px at arm -> 1px floor), so
            // the vibration visibly dies down right before the collapse. Kept integer-pixel so the
            // pixel art never lands on a fractional texel.
            float jitter = Math.max(1f, GameConstants.CRUMBLE_SHAKE_MAX_JITTER
                * (tile.shakeTimer.getRemaining() / GameConstants.CRUMBLE_SHAKE_DURATION));
            int offset = MathUtils.random(-1, 1) * MathUtils.round(jitter);
            batch.draw(region,
                tile.rect.x + offset,
                tile.rect.y + offset,
                tile.rect.width,
                tile.rect.height);
        }
        batch.end();
    }

    private boolean hasShakingTile() {
        for (CrumblingTile tile : crumblingTiles) {
            if (tile.state == CrumblingTile.State.SHAKING) {
                return true;
            }
        }
        return false;
    }
}