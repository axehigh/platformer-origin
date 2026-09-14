package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Draws a subtle screen/room-framed vignette over the whole world: a box-gradient that darkens the
 * visible frame edges (corners darkest) and fades to transparent towards the center. The quad is
 * stretched over the camera's *effective* view ({@code viewportWidth/Height * zoom}), so it tracks
 * flip-screen and dead-zone scroll framing identically — because {@code CameraSystem} locks flip
 * rooms to center and clamps scroll rooms at their bounds, the visible frame edge IS the
 * room/map edge, so this reads as "the edges of the map get darker". Uses normal blending, runs
 * after {@code LightRenderSystem} (so torch glows near the frame also get dimmed) and before
 * {@code DebugRenderSystem}. Gated on {@code FeatureFlags.isVignetteEnabled()} (toggled from the
 * pause dialog's Debug tab and the Settings screen's Debug tab) — when OFF the batch is untouched.
 * Also draws a short-lived RED pulse overlay (same gradient) triggered by
 * {@link #triggerPulse()} whenever the player takes damage (see {@code PlayerDamageResolver});
 * the pulse decays over {@code GameConstants.VIGNETTE_PULSE_DURATION} and always draws exactly one
 * quad per frame (pulse texture while active, black vignette otherwise).
 * The gradient textures are generated procedurally once (mirroring {@code
 * LightRenderSystem}'s lazy glow texture; skipped entirely when {@code Gdx.gl == null} for headless
 * tests).
 */
public class VignetteRenderSystem extends EntitySystem implements com.badlogic.gdx.utils.Disposable {
    private static final int GRADIENT_SIZE = 256;

    /** Peak black alpha at the very frame edge (tunable). */
    private static final float VIGNETTE_STRENGTH = 0.8f;

    /** How far in from the frame the fade extends, as a fraction of the half-axis. */
    private static final float VIGNETTE_INNER_FRACTION = 0.28f;

    /** Static request channel: {@code PlayerDamageResolver} raises it; the system drains it in update(). */
    private static float pendingPulse = 0f;

    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private Texture vignetteTexture;
    private Texture pulseTexture;
    /** Remaining red-pulse fade time, drained by update(). */
    private float pulseSeconds = 0f;

    public VignetteRenderSystem(SpriteBatch batch, OrthographicCamera camera, int priority) {
        super(priority);
        this.batch = batch;
        this.camera = camera;
    }

    /** Raises the damage-pulse request (idempotent, refreshes to the full duration). */
    public static void triggerPulse() {
        pendingPulse = Math.max(pendingPulse, GameConstants.VIGNETTE_PULSE_DURATION);
    }

    private void ensureTextures() {
        if (vignetteTexture != null || Gdx.gl == null) {
            return;
        }
        Pixmap pixmap = new Pixmap(GRADIENT_SIZE, GRADIENT_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < GRADIENT_SIZE; y++) {
            for (int x = 0; x < GRADIENT_SIZE; x++) {
                float nx = (x + 0.5f) / GRADIENT_SIZE;
                float ny = (y + 0.5f) / GRADIENT_SIZE;
                // Distance to the nearest frame edge on each axis (0 at the edge, 0.5 at center).
                float edgeX = Math.min(nx, 1f - nx);
                float edgeY = Math.min(ny, 1f - ny);
                // Box falloff: use whichever axis is nearer the frame, so corners darken the most.
                float border = 1f - Math.min(edgeX, edgeY) * 2f;
                float ramp = MathUtils.clamp((border - (1f - VIGNETTE_INNER_FRACTION)) / VIGNETTE_INNER_FRACTION, 0f, 1f);
                float alpha = VIGNETTE_STRENGTH * ramp * ramp;
                pixmap.drawPixel(x, y, Color.rgba8888(0f, 0f, 0f, alpha));
            }
        }
        vignetteTexture = new Texture(pixmap);
        vignetteTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // Red pulse texture: identical falloff ramp, red tint at the same peak alpha.
        for (int y = 0; y < GRADIENT_SIZE; y++) {
            for (int x = 0; x < GRADIENT_SIZE; x++) {
                float nx = (x + 0.5f) / GRADIENT_SIZE;
                float ny = (y + 0.5f) / GRADIENT_SIZE;
                float edgeX = Math.min(nx, 1f - nx);
                float edgeY = Math.min(ny, 1f - ny);
                float border = 1f - Math.min(edgeX, edgeY) * 2f;
                float ramp = MathUtils.clamp((border - (1f - VIGNETTE_INNER_FRACTION)) / VIGNETTE_INNER_FRACTION, 0f, 1f);
                float alpha = VIGNETTE_STRENGTH * ramp * ramp;
                pixmap.drawPixel(x, y, Color.rgba8888(
                    GameConstants.VIGNETTE_PULSE_COLOR[0],
                    GameConstants.VIGNETTE_PULSE_COLOR[1],
                    GameConstants.VIGNETTE_PULSE_COLOR[2], alpha));
            }
        }
        pulseTexture = new Texture(pixmap);
        pulseTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
    }

    @Override
    public void update(float deltaTime) {
        if (!FeatureFlags.isVignetteEnabled()) {
            return;
        }
        if (pendingPulse > 0f) {
            pulseSeconds = Math.max(pulseSeconds, pendingPulse);
            pendingPulse = 0f;
        }
        pulseSeconds = Math.max(0f, pulseSeconds - deltaTime);

        ensureTextures();
        if (vignetteTexture == null) {
            return;
        }
        float viewW = camera.viewportWidth * camera.zoom;
        float viewH = camera.viewportHeight * camera.zoom;
        float x = camera.position.x - viewW / 2f;
        float y = camera.position.y - viewH / 2f;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (pulseSeconds > 0f) {
            batch.setColor(1f, 1f, 1f, GameConstants.VIGNETTE_PULSE_STRENGTH * (pulseSeconds / GameConstants.VIGNETTE_PULSE_DURATION));
            batch.draw(pulseTexture, x, y, viewW, viewH);
        } else {
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(vignetteTexture, x, y, viewW, viewH);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        if (vignetteTexture != null) {
            vignetteTexture.dispose();
            vignetteTexture = null;
        }
        if (pulseTexture != null) {
            pulseTexture.dispose();
            pulseTexture = null;
        }
    }
}
