package com.axehigh.platformer.ui;

import com.axehigh.platformer.particles.GlobalParticles;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/**
 * Ambient visual effects for menu screens, all built from one shared runtime-generated radial-glow
 * texture: slow Ken Burns drift on the backdrop, drifting ember motes, an additive pulsing glow
 * behind a title label, and looping spark bursts. Nothing here intercepts input. Call
 * {@link #dispose()} when the owning screen is disposed.
 */
public class MenuEffects {

    private static final float KEN_BURNS_ZOOM = 1.06f;
    private static final float KEN_BURNS_SECONDS = 9f;

    private static final float GLOW_WIDTH_SCALE = 2.4f;
    private static final float GLOW_HEIGHT_SCALE = 3f;
    private static final float GLOW_ALPHA_HIGH = 0.6f;
    private static final float GLOW_ALPHA_LOW = 0.25f;
    private static final float GLOW_PULSE_SECONDS = 1.6f;

    private static final float SPARK_BURST_INTERVAL = 1.3f;
    private static final float SPARK_BURST_SCALE = 2.5f;

    /** Distance below the stage top over which embers fade out before wrapping. */
    private static final float EMBER_FADE_DISTANCE = 120f;

    /** Warm ember tints, picked per mote. */
    private enum EmberTint {
        AMBER("FFA640"),
        GOLD("FFD27F"),
        EMBER("FF7A2E"),
        PALE("FFE9B2");

        final Color color;

        EmberTint(String hex) {
            color = new Color(Color.valueOf(hex));
        }
    }

    private Texture glowTexture;
    private ParticleEffect sparkTemplate;
    private final Array<ParticleEffect> sparkCopies = new Array<>();
    private Group kenBurnsWrap;

    public MenuEffects() {
        glowTexture = createGlowTexture();
        sparkTemplate = loadSparkTemplate();
    }

    /**
     * Applies the slow zoom-in/zoom-out drift to a full-bleed background image. The image is
     * re-parented into a center-origin wrapper group (plain Images anchor their scale
     * bottom-left) sized to and added onto {@code stage}.
     */
    public void applyKenBurns(Stage stage, Image background) {
        Group wrap = new Group();
        wrap.setOrigin(Align.center);
        wrap.setSize(stage.getWidth(), stage.getHeight());
        background.setFillParent(true);
        wrap.addActor(background);
        wrap.addAction(Actions.forever(Actions.sequence(
            Actions.scaleTo(KEN_BURNS_ZOOM, KEN_BURNS_ZOOM, KEN_BURNS_SECONDS),
            Actions.scaleTo(1f, 1f, KEN_BURNS_SECONDS)
        )));
        kenBurnsWrap = wrap;
        stage.addActor(wrap);
    }

    /** Re-fits the Ken Burns wrapper after a viewport resize; the fill-parent backdrop follows. */
    public void resize(float width, float height) {
        if (kenBurnsWrap != null) {
            kenBurnsWrap.setSize(width, height);
        }
    }

    /** Adds {@code count} drifting ember motes above the backdrop (call right after addBackground). */
    public void addEmbers(Stage stage, int count) {
        for (int i = 0; i < count; i++) {
            EmberActor ember = new EmberActor(glowTexture,
                MathUtils.random(0f, stage.getWidth()),
                MathUtils.random(-stage.getHeight() * 0.1f, stage.getHeight()));
            stage.addActor(ember);
        }
    }

    /**
     * Wraps a title label with an additive pulsing glow behind it.
     *
     * @return a group sized to the label's preferred size — put this in the table instead of the label
     */
    public Group createGlowBehind(Label title) {
        title.pack();
        Group wrap = new Group();
        wrap.setSize(title.getPrefWidth(), title.getPrefHeight());
        wrap.setTouchable(Touchable.disabled);

        AdditiveImage glow = new AdditiveImage(textureDrawable(glowTexture));
        glow.setSize(wrap.getWidth() * GLOW_WIDTH_SCALE, wrap.getHeight() * GLOW_HEIGHT_SCALE);
        glow.setPosition((wrap.getWidth() - glow.getWidth()) / 2f, (wrap.getHeight() - glow.getHeight()) / 2f);
        glow.setColor(1f, 0.85f, 0.55f, GLOW_ALPHA_HIGH);
        glow.setTouchable(Touchable.disabled);
        glow.addAction(Actions.forever(Actions.sequence(
            Actions.alpha(GLOW_ALPHA_LOW, GLOW_PULSE_SECONDS),
            Actions.alpha(GLOW_ALPHA_HIGH, GLOW_PULSE_SECONDS)
        )));

        wrap.addActor(glow);
        wrap.addActor(title);
        title.setPosition((wrap.getWidth() - title.getWidth()) / 2f, (wrap.getHeight() - title.getHeight()) / 2f);
        return wrap;
    }

    /** Adds one looping spark-burst actor per given UI-space position. */
    public void addSparkBursts(Stage stage, Array<Vector2> positions) {
        if (sparkTemplate == null) {
            return;
        }
        for (Vector2 pos : positions) {
            SparkBurstActor burst = new SparkBurstActor(sparkTemplate, SPARK_BURST_SCALE);
            burst.setPosition(pos.x, pos.y);
            sparkCopies.add(burst.effect);
            stage.addActor(burst);
        }
    }

    public void dispose() {
        glowTexture.dispose();
        glowTexture = null;
        if (sparkTemplate != null) {
            sparkTemplate.dispose();
            sparkTemplate = null;
        }
        for (ParticleEffect copy : sparkCopies) {
            copy.dispose();
        }
        sparkCopies.clear();
    }

    private static TextureRegionDrawable textureDrawable(Texture texture) {
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    /** 32x32 radial gradient (white core, squared falloff) used by embers and the title glow. */
    private static Texture createGlowTexture() {
        int size = 32;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float radius = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - radius + 0.5f;
                float dy = y - radius + 0.5f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) / radius;
                float falloff = Math.max(0f, 1f - dist);
                pixmap.setColor(1f, 1f, 1f, falloff * falloff);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static ParticleEffect loadSparkTemplate() {
        ParticleEffect effect = new ParticleEffect();
        try {
            effect.load(Gdx.files.internal(GlobalParticles.SPARKS),
                Gdx.files.internal("particles/sparks"));
        } catch (Exception e) {
            Gdx.app.error("MenuEffects", "Failed to load sparks particle effect", e);
            effect.dispose();
            return null;
        }
        return effect;
    }

    /** A mote of ember light rising from the bottom of the screen with a sinusoidal sway. */
    private static class EmberActor extends Image {
        private final float speed;
        private final float swayAmplitude;
        private final float swayFrequency;
        private final float phase;
        private final float size;
        private final float baseAlpha;
        private final Color tint = new Color();
        private float baseX;
        private float time;

        EmberActor(Texture glowTexture, float startX, float startY) {
            super(textureDrawable(glowTexture));
            size = MathUtils.random(6f, 14f);
            baseX = startX;
            speed = MathUtils.random(60f, 140f);
            swayAmplitude = MathUtils.random(10f, 40f);
            swayFrequency = MathUtils.random(1f, 3f);
            phase = MathUtils.random(0f, MathUtils.PI2);
            EmberTint chosen = EmberTint.values()[MathUtils.random(EmberTint.values().length - 1)];
            tint.set(chosen.color);
            baseAlpha = MathUtils.random(0.25f, 0.6f);
            setSize(size, size);
            setPosition(baseX, startY);
            setTouchable(Touchable.disabled);
        }

        @Override
        public void act(float delta) {
            time += delta;
            setY(getY() + speed * delta);
            setX(baseX + MathUtils.sin(phase + time * swayFrequency) * swayAmplitude);
            Stage stage = getStage();
            if (getY() > stage.getHeight() + size) {
                setY(-size);
                baseX = MathUtils.random(0f, stage.getWidth());
            }
            // Fade out approaching the top so the wrap-around is seamless.
            float fade = MathUtils.clamp((stage.getHeight() - getY()) / EMBER_FADE_DISTANCE, 0f, 1f);
            setColor(tint.r, tint.g, tint.b, baseAlpha * fade);
        }
    }

    /** Image drawn with additive blending so glows brighten what's behind them. */
    private static class AdditiveImage extends Image {
        AdditiveImage(Drawable drawable) {
            super(drawable);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            super.draw(batch, parentAlpha);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /** A periodically-refiring copy of the sparks particle template at a fixed screen position. */
    private static class SparkBurstActor extends Actor {
        private final ParticleEffect effect;

        SparkBurstActor(ParticleEffect template, float scale) {
            effect = new ParticleEffect(template);
            effect.scaleEffect(scale);
            setTouchable(Touchable.disabled);
            addAction(Actions.forever(Actions.sequence(
                Actions.delay(SPARK_BURST_INTERVAL),
                Actions.run(new Runnable() {
                    @Override
                    public void run() {
                        effect.start();
                    }
                })
            )));
            effect.start();
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            effect.setPosition(getX(), getY());
            effect.update(delta);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            effect.draw(batch);
        }
    }
}
