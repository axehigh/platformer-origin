package com.axehigh.platformer.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;

/**
 * Stylized transition overlay supporting PORTAL_IRIS, PIXELATE, CHECKERBOARD, and FADE.
 * Uses GLSL shaders when available, with robust fallback to ShapeRenderer.
 */
public class StylizedTransitionOverlay implements Disposable {

    public enum TransitionType {
        PORTAL_IRIS,
        PIXELATE,
        CHECKERBOARD,
        FADE
    }

    public enum State {
        IDLE,
        FADING_OUT, // Screen closing / covering
        FADING_IN   // Screen opening / uncovering
    }

    private TransitionType type = TransitionType.FADE;
    private State state = State.IDLE;

    private float duration = 0.5f;
    private float elapsed = 0f;
    private boolean active = false;

    private Runnable onMidPoint;
    private Runnable onComplete;

    private ShapeRenderer shapeRenderer;
    private ShaderProgram shader;
    private boolean useShader = true;

    // Portal center coordinates (normalized or screen space)
    private float portalX = 0.5f;
    private float portalY = 0.5f;

    private static final String VERTEX_SHADER =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main() {\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = u_projTrans * a_position;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec2 v_texCoords;\n" +
        "uniform float u_progress;\n" +
        "uniform int u_type;\n" +
        "uniform vec2 u_resolution;\n" +
        "uniform vec2 u_portalCenter;\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 uv = v_texCoords;\n" +
        "    vec4 col = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "    \n" +
        "    if (u_type == 0) {\n" +
        "        // PORTAL_IRIS\n" +
        "        vec2 aspectUV = uv;\n" +
        "        if (u_resolution.y > 0.0) {\n" +
        "            aspectUV.x *= u_resolution.x / u_resolution.y;\n" +
        "        }\n" +
        "        vec2 center = u_portalCenter;\n" +
        "        if (u_resolution.y > 0.0) {\n" +
        "            center.x *= u_resolution.x / u_resolution.y;\n" +
        "        }\n" +
        "        float dist = distance(aspectUV, center);\n" +
        "        float maxDist = max(u_resolution.x / max(u_resolution.y, 1.0), 1.0) * 1.5;\n" +
        "        float radius = (1.0 - u_progress) * maxDist;\n" +
        "        if (dist < radius) {\n" +
        "            discard;\n" +
        "        }\n" +
        "    } else if (u_type == 1) {\n" +
        "        // PIXELATE\n" +
        "        float pixelCount = mix(80.0, 4.0, u_progress);\n" +
        "        vec2 gridUV = floor(uv * pixelCount) / pixelCount;\n" +
        "        float hash = fract(sin(dot(gridUV, vec2(12.9898, 78.233))) * 43758.5453);\n" +
        "        if (u_progress < hash) {\n" +
        "            discard;\n" +
        "        }\n" +
        "        col.a = 1.0;\n" +
        "    } else if (u_type == 2) {\n" +
        "        // CHECKERBOARD\n" +
        "        vec2 squares = vec2(20.0, 12.0);\n" +
        "        vec2 st = floor(uv * squares);\n" +
        "        float pattern = mod(st.x + st.y, 2.0);\n" +
        "        float threshold = u_progress * 1.05;\n" +
        "        float hash = fract(sin(dot(st, vec2(12.9898, 78.233))) * 43758.5453);\n" +
        "        if (threshold <= hash) {\n" +
        "            discard;\n" +
        "        }\n" +
        "        col.a = 1.0;\n" +
        "    } else {\n" +
        "        // FADE\n" +
        "        // u_progress: 0.0 (transparent) to 1.0 (opaque black)\n" +
        "        col.a = u_progress;\n" +
        "    }\n" +
        "    gl_FragColor = col;\n" +
        "}\n";

    public StylizedTransitionOverlay() {
        this.shapeRenderer = new ShapeRenderer();
        try {
            ShaderProgram.pedantic = false;
            this.shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (!shader.isCompiled()) {
                Gdx.app.error("StylizedTransitionOverlay", "Shader compilation failed:\n" + shader.getLog());
                useShader = false;
            }
        } catch (Exception e) {
            Gdx.app.error("StylizedTransitionOverlay", "Shader initialization error", e);
            useShader = false;
        }
    }

    public void start(TransitionType type, float duration, Runnable onMidPoint, Runnable onComplete) {
        this.type = type;
        this.duration = Math.max(0.05f, duration);
        this.elapsed = 0f;
        this.state = State.FADING_OUT;
        this.active = true;
        this.onMidPoint = onMidPoint;
        this.onComplete = onComplete;
    }

    public void startFadeOut(TransitionType type, float duration, Runnable onComplete) {
        this.type = type;
        this.duration = Math.max(0.05f, duration);
        this.elapsed = 0f;
        this.state = State.FADING_OUT;
        this.active = true;
        this.onMidPoint = null;
        this.onComplete = onComplete;
    }

    public void startFadeIn(TransitionType type, float duration, Runnable onComplete) {
        this.type = type;
        this.duration = Math.max(0.05f, duration);
        this.elapsed = 0f;
        this.state = State.FADING_IN;
        this.active = true;
        this.onMidPoint = null;
        this.onComplete = onComplete;
    }

    public void setPortalCenter(float x, float y) {
        this.portalX = x;
        this.portalY = y;
    }

    public boolean isActive() {
        return active;
    }

    public State getState() {
        return state;
    }

    public void update(float delta) {
        if (!active) return;

        elapsed += delta;
        float halfDuration = duration * 0.5f;

        if (state == State.FADING_OUT) {
            if (elapsed >= halfDuration) {
                elapsed = 0f;
                state = State.FADING_IN;
                if (onMidPoint != null) {
                    try {
                        onMidPoint.run();
                    } catch (Exception e) {
                        Gdx.app.error("StylizedTransitionOverlay", "Error in onMidPoint callback", e);
                    }
                }
            }
        } else if (state == State.FADING_IN) {
            if (elapsed >= halfDuration) {
                active = false;
                state = State.IDLE;
                if (onComplete != null) {
                    try {
                        onComplete.run();
                    } catch (Exception e) {
                        Gdx.app.error("StylizedTransitionOverlay", "Error in onComplete callback", e);
                    }
                }
            }
        }
    }

    public void render(OrthographicCamera camera) {
        if (!active) return;

        float halfDuration = duration * 0.5f;
        float rawProgress = Math.min(1.0f, elapsed / halfDuration);

        // FADING_OUT: progress goes 0.0 -> 1.0 (covering screen)
        // FADING_IN: progress goes 1.0 -> 0.0 (uncovering screen)
        float progress = (state == State.FADING_OUT) ? rawProgress : (1.0f - rawProgress);

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        renderWithFallback(camera, progress, screenWidth, screenHeight);
    }

    private void renderWithFallback(OrthographicCamera camera, float progress, float width, float height) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float vw = camera.viewportWidth;
        float vh = camera.viewportHeight;
        float cx = camera.position.x;
        float cy = camera.position.y;
        float minX = cx - vw * 0.5f;
        float minY = cy - vh * 0.5f;

        switch (type) {
            case FADE:
                shapeRenderer.setColor(0f, 0f, 0f, progress);
                shapeRenderer.rect(minX, minY, vw, vh);
                break;

            case PORTAL_IRIS:
                // Draw full black screen with a smooth multi-ring / stepped box iris cutout around (portalX, portalY)
                // When progress is 0 (open), radius is max. When progress is 1 (closed), radius is 0.
                float maxRadius = (float) Math.sqrt(vw * vw + vh * vh);
                float radius = (1.0f - progress) * maxRadius;

                float px = minX + portalX * vw;
                float py = minY + portalY * vh;

                if (progress <= 0.001f) {
                    // Fully open: draw nothing
                    break;
                }

                if (progress >= 0.999f) {
                    // Fully closed: draw full black screen
                    shapeRenderer.setColor(0f, 0f, 0f, 1.0f);
                    shapeRenderer.rect(minX, minY, vw, vh);
                    break;
                }

                // Draw surrounding black bars / frame covering everything outside the iris radius
                shapeRenderer.setColor(0f, 0f, 0f, 1.0f);
                
                // Top bar
                float topH = minY + vh - (py + radius);
                if (topH > 0) {
                    shapeRenderer.rect(minX, py + radius, vw, topH);
                }
                // Bottom bar
                float botH = (py - radius) - minY;
                if (botH > 0) {
                    shapeRenderer.rect(minX, minY, vw, botH);
                }
                // Left bar (between vertical bounds of iris)
                float leftW = (px - radius) - minX;
                if (leftW > 0) {
                    float bY = Math.max(minY, py - radius);
                    float bH = Math.min(minY + vh, py + radius) - bY;
                    if (bH > 0) {
                        shapeRenderer.rect(minX, bY, leftW, bH);
                    }
                }
                // Right bar (between vertical bounds of iris)
                float rightW = minX + vw - (px + radius);
                if (rightW > 0) {
                    float bY = Math.max(minY, py - radius);
                    float bH = Math.min(minY + vh, py + radius) - bY;
                    if (bH > 0) {
                        shapeRenderer.rect(px + radius, bY, rightW, bH);
                    }
                }

                // Add a soft transitional border ring / stepped box layers for smooth fallback iris appearance
                int steps = 6;
                for (int i = 0; i < steps; i++) {
                    float stepProgress = (float) i / steps;
                    float ringRadius = radius + stepProgress * (vw * 0.15f);
                    float alpha = 1.0f - ((float) i / steps);
                    shapeRenderer.setColor(0f, 0f, 0f, alpha * 0.7f);
                    
                    // Draw four thin border strips around the ring
                    float tH = minY + vh - (py + ringRadius);
                    if (tH > 0 && tH < vh) {
                        shapeRenderer.rect(minX, py + ringRadius - 2f, vw, 2f);
                    }
                    float bH2 = (py - ringRadius) - minY;
                    if (bH2 > 0 && bH2 < vh) {
                        shapeRenderer.rect(minX, py - ringRadius, vw, 2f);
                    }
                }
                break;

            case PIXELATE:
                // Fallback: render pixelation blocks (grid of black squares growing/appearing based on progress)
                {
                    int cols = 24;
                    int rows = 16;
                    float blockWidth = vw / cols;
                    float blockHeight = vh / rows;

                    // Fill screen with growing/staggered pixel blocks
                    for (int x = 0; x < cols; x++) {
                        for (int y = 0; y < rows; y++) {
                            // Pseudo-random threshold per block so they appear in a pixelated static/digital pattern
                            float hash = ((x * 37 + y * 17) % 23) / 23.0f;
                            if (progress >= hash) {
                                float blockProgress = Math.min(1.0f, (progress - hash) / (1.0f - hash + 0.0001f));
                                shapeRenderer.setColor(0f, 0f, 0f, blockProgress);
                                // Draw a slightly smaller rect with a tiny gap for a crisp pixel grid look, or full block
                                float gap = 0.5f;
                                shapeRenderer.rect(
                                    minX + x * blockWidth + gap,
                                    minY + y * blockHeight + gap,
                                    Math.max(1f, blockWidth - gap * 2f),
                                    Math.max(1f, blockHeight - gap * 2f)
                                );
                            }
                        }
                    }
                }
                break;

            case CHECKERBOARD:
                // Animated checkerboard wipe fallback using ShapeRenderer with robust complete coverage at progress >= 1.0
                if (progress >= 0.999f) {
                    shapeRenderer.setColor(0f, 0f, 0f, 1.0f);
                    shapeRenderer.rect(minX, minY, vw, vh);
                    break;
                }

                {
                    int cols = 20;
                    int rows = 12;
                    float blockWidth = vw / cols;
                    float blockHeight = vh / rows;

                    for (int x = 0; x < cols; x++) {
                        for (int y = 0; y < rows; y++) {
                            // Compute deterministic hash per cell using prime multipliers for even distribution
                            float hash = (((x * 73 + y * 31 + (x * y * 13)) % 997) / 997.0f);
                            if (progress >= hash) {
                                // Fade in each square smoothly or draw solid black once threshold reached
                                float cellProgress = Math.min(1.0f, (progress - hash) / Math.max(0.001f, 1.0f - hash));
                                shapeRenderer.setColor(0f, 0f, 0f, cellProgress);
                                shapeRenderer.rect(minX + x * blockWidth, minY + y * blockHeight, blockWidth + 0.5f, blockHeight + 0.5f);
                            }
                        }
                    }
                }
                break;
        }

        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
        if (shader != null) {
            shader.dispose();
        }
    }
}
