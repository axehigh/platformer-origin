package com.axehigh.platformer.particles;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.utils.ObjectMap;

import static com.axehigh.platformer.particles.GlobalParticles.SMOKE;
import static com.axehigh.platformer.particles.GlobalParticles.SPARKS;

/**
 * Helper class for managing and spawning particle effects defined in GlobalParticles.
 */
public class ParticleHelper {
    private static final ObjectMap<String, ParticleEffect> templates = new ObjectMap<>();

    /** Scale (fraction of the sparks template's native size) for the crumble-collapse chip burst. */
    private static final float STONE_CHIPS_SCALE = 0.8f;
    /** Hard lifetime cap (seconds) for the crumble-collapse chip burst. */
    private static final float STONE_CHIPS_MAX_LIFETIME = 0.7f;

    /** Scale for the colored burst spawned on enemy death. */
    private static final float DEATH_BURST_SCALE = 1.0f;
    /** Hard lifetime cap (seconds) for the death-burst colored spark. */
    private static final float DEATH_BURST_MAX_LIFETIME = 0.6f;
    /** Scale for the smoke puff spawned alongside the death-burst colored spark. */
    private static final float DEATH_BURST_SMOKE_SCALE = 6.0f;

    /** Scale (fraction of the smoke template's native size) for ambient ember motes. */
    private static final float AMBIENT_MOTE_SCALE = 0.6f;
    /** Hard lifetime cap (seconds) for an ambient ember mote. */
    private static final float AMBIENT_MOTE_MAX_LIFETIME = 2.5f;

    /**
     * Loads all particle effects defined in GlobalParticles into the internal template registry.
     * Use this when assets are already loaded via AssetManager.
     */
    public static void load(AssetManager assetManager) {
        if (Gdx.gl == null) return; // Skip in headless mode

        registerTemplate(assetManager, GlobalParticles.EXPLOSION);
        registerTemplate(assetManager, GlobalParticles.GHOST);
        registerTemplate(assetManager, SMOKE);
        registerTemplate(assetManager, GlobalParticles.SPARKS);
    }

    private static void registerTemplate(AssetManager assetManager, String path) {
        if (assetManager.isLoaded(path, ParticleEffect.class)) {
            templates.put(path, assetManager.get(path, ParticleEffect.class));
        }
    }

    /**
     * Spawns a particle effect at the given coordinates.
     *
     * @param engine       The Ashley ECS engine to add the entity to.
     * @param particlePath The path from GlobalParticles.
     * @param x            X coordinate in world space.
     * @param y            Y coordinate in world space.
     */
    public static void spawnParticle(PooledEngine engine, String particlePath, float x, float y) {
        spawnParticle(engine, particlePath, x, y, 0, 2f);
    }

    public static void spawnParticle(PooledEngine engine, String particlePath, float x, float y, float delay) {
        spawnParticle(engine, particlePath, x, y, delay, 2f);
    }

    public static void spawnParticle(PooledEngine engine, String particlePath, float x, float y, float delay, float scale) {
        spawnParticle(engine, particlePath, x, y, delay, scale, 4f);
    }

    public static void spawnParticle(PooledEngine engine, String particlePath, float x, float y, float delay, float scale, float maxLifetime) {
        if (Gdx.gl == null) {
            // For headless tests, create a dummy entity mirroring the production shape
            // (TransformComponent + ParticleComponent) to allow testing if particles were triggered.
            Entity dummy = engine.createEntity();
            TransformComponent tc = engine.createComponent(TransformComponent.class);
            tc.position.x = x;
            tc.position.y = y;
            dummy.add(tc);
            com.axehigh.platformer.ecs.components.ParticleComponent pc = engine.createComponent(ParticleComponent.class);
            pc.delay = delay;
            pc.scale = scale;
            pc.maxLifetime = maxLifetime;

            dummy.add(pc);
            engine.addEntity(dummy);
            return;
        }

        ParticleEffect template = templates.get(particlePath);
        if (template == null) {
            Gdx.app.error("ParticleHelper", "Particle template not found for path: " + particlePath);
            return;
        }

        Entity particleEntity = engine.createEntity();

        TransformComponent tc = engine.createComponent(TransformComponent.class);
        tc.position.x = x;
        tc.position.y = y;
        particleEntity.add(tc);

        ParticleComponent pc = engine.createComponent(ParticleComponent.class);
        pc.effect = new ParticleEffect(template);
        pc.effect.scaleEffect(scale);
        pc.delay = delay;
        pc.scale = scale;
        pc.maxLifetime = maxLifetime;
        particleEntity.add(pc);

        engine.addEntity(particleEntity);
    }

    public static void spawnExplosion(PooledEngine engine, float x, float y, float delay, float scale) {
        spawnParticle(engine, GlobalParticles.EXPLOSION, x, y, delay, scale);
    }

    public static void spawnExplosion(PooledEngine engine, float x, float y, float delay) {
        spawnExplosion(engine, x, y, delay, 2f);
    }

    public static void spawnExplosion(PooledEngine engine, float x, float y) {
        spawnExplosion(engine, x, y, 0, 2f);
    }

    /**
     * Spawns a small smoke puff, typically for machine gun hits on infantry or end-of-range misses.
     */
    public static void spawnSmallSmoke(PooledEngine engine, float x, float y) {
        spawnSmallSmoke(engine, x, y, 5.0f);
    }

    /** Spawns a small smoke puff with an explicit scale (used to size landing puffs by fall speed). */
    public static void spawnSmallSmoke(PooledEngine engine, float x, float y, float scale) {
        spawnParticle(engine, SMOKE, x, y, 0, scale);
    }

    /**
     * Spawns a small stone-chip debris burst at the given coordinates — a recolored clone of the
     * {@link GlobalParticles#SPARKS} template (dark gray → lighter stone gradient), so
     * crumbling-tile collapse debris needs no new art.
     *
     * @param engine The Ashley ECS engine to add the entity to.
     * @param x      X coordinate in world space.
     * @param y      Y coordinate in world space.
     */
    public static void spawnStoneChips(PooledEngine engine, float x, float y) {
        if (Gdx.gl == null) {
            // For headless tests, create a dummy entity mirroring the production shape
            // (TransformComponent + ParticleComponent) to allow testing if particles were triggered.
            Entity dummy = engine.createEntity();
            TransformComponent tc = engine.createComponent(TransformComponent.class);
            tc.position.x = x;
            tc.position.y = y;
            dummy.add(tc);
            ParticleComponent pc = engine.createComponent(ParticleComponent.class);
            pc.delay = 0;
            pc.scale = STONE_CHIPS_SCALE;
            pc.maxLifetime = STONE_CHIPS_MAX_LIFETIME;
            dummy.add(pc);
            engine.addEntity(dummy);
            return;
        }

        ParticleEffect template = templates.get(SPARKS);
        if (template == null) {
            Gdx.app.error("ParticleHelper", "Particle template not found for path: " + SPARKS);
            return;
        }

        ParticleEffect chips = new ParticleEffect(template);
        for (ParticleEmitter emitter : chips.getEmitters()) {
            emitter.getTint().setColors(new float[]{0.45f, 0.42f, 0.40f, 0.70f, 0.68f, 0.64f});
        }

        Entity particleEntity = engine.createEntity();
        TransformComponent tc = engine.createComponent(TransformComponent.class);
        tc.position.x = x;
        tc.position.y = y;
        particleEntity.add(tc);

        ParticleComponent pc = engine.createComponent(ParticleComponent.class);
        pc.effect = chips;
        pc.effect.scaleEffect(STONE_CHIPS_SCALE);
        pc.delay = 0;
        pc.scale = STONE_CHIPS_SCALE;
        pc.maxLifetime = STONE_CHIPS_MAX_LIFETIME;
        particleEntity.add(pc);

        engine.addEntity(particleEntity);
    }

    /**
     * Spawns a death-burst VFX at the given coordinates — a smoke poof plus a short-lived colored
     * spark burst (cloned from the SPARKS template and tinted to the caller-supplied color).
     * Mirrors {@link #spawnStoneChips} including its headless dummy branch so tests can count entities.
     *
     * @param engine The Ashley ECS engine to add the entities to (nullable; early-return if null).
     * @param x      X coordinate in world space.
     * @param y      Y coordinate in world space.
     * @param color  RGB triplet (3 floats) used as both the start and end tint of the burst.
     */
    public static void spawnDeathBurst(PooledEngine engine, float x, float y, float[] color) {
        if (engine == null) return;

        if (Gdx.gl == null) {
            // Headless: spawn two dummy entities (smoke + burst) so tests can count them.
            Entity smokeDummy = engine.createEntity();
            TransformComponent smokeTc = engine.createComponent(TransformComponent.class);
            smokeTc.position.x = x;
            smokeTc.position.y = y;
            smokeDummy.add(smokeTc);
            ParticleComponent smokePc = engine.createComponent(ParticleComponent.class);
            smokePc.delay = 0;
            smokePc.scale = DEATH_BURST_SMOKE_SCALE;
            smokePc.maxLifetime = DEATH_BURST_MAX_LIFETIME;
            smokeDummy.add(smokePc);
            engine.addEntity(smokeDummy);

            Entity burstDummy = engine.createEntity();
            TransformComponent burstTc = engine.createComponent(TransformComponent.class);
            burstTc.position.x = x;
            burstTc.position.y = y;
            burstDummy.add(burstTc);
            ParticleComponent burstPc = engine.createComponent(ParticleComponent.class);
            burstPc.delay = 0;
            burstPc.scale = DEATH_BURST_SCALE;
            burstPc.maxLifetime = DEATH_BURST_MAX_LIFETIME;
            burstDummy.add(burstPc);
            engine.addEntity(burstDummy);
            return;
        }

        // Smoke poof
        spawnSmallSmoke(engine, x, y, DEATH_BURST_SMOKE_SCALE);

        // Colored spark burst (recolored SPARKS clone)
        ParticleEffect template = templates.get(SPARKS);
        if (template == null) {
            Gdx.app.error("ParticleHelper", "Particle template not found for path: " + SPARKS);
            return;
        }

        ParticleEffect burst = new ParticleEffect(template);
        for (ParticleEmitter emitter : burst.getEmitters()) {
            emitter.getTint().setColors(new float[]{color[0], color[1], color[2], color[0], color[1], color[2]});
        }

        Entity particleEntity = engine.createEntity();
        TransformComponent tc = engine.createComponent(TransformComponent.class);
        tc.position.x = x;
        tc.position.y = y;
        particleEntity.add(tc);

        ParticleComponent pc = engine.createComponent(ParticleComponent.class);
        pc.effect = burst;
        pc.effect.scaleEffect(DEATH_BURST_SCALE);
        pc.delay = 0;
        pc.scale = DEATH_BURST_SCALE;
        pc.maxLifetime = DEATH_BURST_MAX_LIFETIME;
        particleEntity.add(pc);

        engine.addEntity(particleEntity);
    }

    /**
     * Spawns a single ambient ember/dust mote at the given coordinates — a warm-tinted clone of
     * the {@link GlobalParticles#SMOKE} template (which reads as slow drifting dust), sized down.
     * Purely decorative; motes self-remove via {@code ParticleSystem}'s hard lifetime cap. Mirrors
     * {@link #spawnDeathBurst} including its headless dummy branch so tests can count entities.
     *
     * @param engine The Ashley ECS engine to add the entity to (nullable; early-return if null).
     * @param x      X coordinate in world space.
     * @param y      Y coordinate in world space.
     */
    public static void spawnAmbientMote(PooledEngine engine, float x, float y) {
        if (engine == null) return;

        if (Gdx.gl == null) {
            // Headless: spawn a single dummy entity so tests can count it.
            Entity dummy = engine.createEntity();
            TransformComponent tc = engine.createComponent(TransformComponent.class);
            tc.position.x = x;
            tc.position.y = y;
            dummy.add(tc);
            ParticleComponent pc = engine.createComponent(ParticleComponent.class);
            pc.delay = 0;
            pc.scale = AMBIENT_MOTE_SCALE;
            pc.maxLifetime = AMBIENT_MOTE_MAX_LIFETIME;
            dummy.add(pc);
            engine.addEntity(dummy);
            return;
        }

        ParticleEffect template = templates.get(SMOKE);
        if (template == null) {
            Gdx.app.error("ParticleHelper", "Particle template not found for path: " + SMOKE);
            return;
        }

        ParticleEffect mote = new ParticleEffect(template);
        float[] emberRgb = GameConstants.AMBIENT_MOTE_COLOR;
        for (ParticleEmitter emitter : mote.getEmitters()) {
            emitter.getTint().setColors(new float[]{emberRgb[0], emberRgb[1], emberRgb[2], emberRgb[0], emberRgb[1], emberRgb[2]});
        }

        Entity particleEntity = engine.createEntity();
        TransformComponent tc = engine.createComponent(TransformComponent.class);
        tc.position.x = x;
        tc.position.y = y;
        particleEntity.add(tc);

        ParticleComponent pc = engine.createComponent(ParticleComponent.class);
        pc.effect = mote;
        pc.effect.scaleEffect(AMBIENT_MOTE_SCALE);
        pc.delay = 0;
        pc.scale = AMBIENT_MOTE_SCALE;
        pc.maxLifetime = AMBIENT_MOTE_MAX_LIFETIME;
        particleEntity.add(pc);

        engine.addEntity(particleEntity);
    }

    /**
     * Spawns multiple explosions within a circle.
     *
     * @param engine The Ashley ECS engine to add the entity to.
     * @param x      X coordinate in world space.
     * @param y      Y coordinate in world space.
     * @param radius Radius of the spawning area.
     * @param count  Number of explosions to spawn.
     */
    public static void spawnMultipleExplosions(PooledEngine engine, float x, float y, float radius, int count) {
        for (int i = 0; i < count; i++) {
            // Use a square root for distance to ensure more even distribution within the circle
            // and avoid clustering near the center.
            float angle = (float) (Math.random() * Math.PI * 2);
            float distance = (float) (Math.sqrt(Math.random()) * radius);
            float offsetX = (float) (Math.cos(angle) * distance);
            float offsetY = (float) (Math.sin(angle) * distance);

            // Randomly delay explosions between 0 and 0.5 seconds for visual variety
            float delay = (float) (Math.random() * 0.5f);
            spawnParticle(engine, GlobalParticles.EXPLOSION, x + offsetX, y + offsetY, delay);
        }
    }

    /**
     * Spawns multiple explosions within a rectangular area.
     * Useful for large objects like houses where the explosion should be internal.
     *
     * @param engine The Ashley ECS engine to add the entity to.
     * @param bounds The rectangular bounds of the area.
     * @param count  Number of explosions to spawn.
     */
    public static void spawnAreaExplosions(PooledEngine engine, com.badlogic.gdx.math.Rectangle bounds, int count) {
        for (int i = 0; i < count; i++) {
            float x = bounds.x + (float) (Math.random() * bounds.width);
            float y = bounds.y + (float) (Math.random() * bounds.height);

            // Randomly delay explosions between 0 and 0.5 seconds for visual variety
            float delay = (float) (Math.random() * 0.5f);
            spawnParticle(engine, GlobalParticles.EXPLOSION, x, y, delay);
        }
    }

    /**
     * Spawns multiple explosions specifically around the perimeter of a rectangular area.
     * Useful for obstacles where we want the explosions to appear "outside" or along the edges.
     *
     * @param engine  The Ashley ECS engine to add the entity to.
     * @param bounds  The rectangular bounds of the area.
     * @param padding Additional padding around the bounds.
     * @param count   Number of explosions to spawn.
     */
    public static void spawnPerimeterExplosions(PooledEngine engine, com.badlogic.gdx.math.Rectangle bounds, float padding, int count) {
        for (int i = 0; i < count; i++) {
            // Pick a side: 0=Top, 1=Bottom, 2=Left, 3=Right
            int side = (int) (Math.random() * 4);
            float x, y;
            float minX = bounds.x - padding;
            float minY = bounds.y - padding;
            float maxX = bounds.x + bounds.width + padding;
            float maxY = bounds.y + bounds.height + padding;
            float width = maxX - minX;
            float height = maxY - minY;

            if (side == 0) { // Top
                x = minX + (float) (Math.random() * width);
                y = maxY;
            } else if (side == 1) { // Bottom
                x = minX + (float) (Math.random() * width);
                y = minY;
            } else if (side == 2) { // Left
                x = minX;
                y = minY + (float) (Math.random() * height);
            } else { // Right
                x = maxX;
                y = minY + (float) (Math.random() * height);
            }

            // Randomly delay explosions between 0 and 0.5 seconds for visual variety
            float delay = (float) (Math.random() * 0.5f);
            spawnParticle(engine, GlobalParticles.EXPLOSION, x, y, delay);
        }
    }

    public void spawnMultipleExplosions(float x, float y, float radius, int count, PooledEngine engine) {
        ParticleHelper.spawnMultipleExplosions(engine, x, y, radius, count);
    }

    /**
     * Spawns multiple explosions within a rectangular area.
     * Useful for large objects like houses where the explosion should be internal.
     */
    public void spawnAreaExplosions(com.badlogic.gdx.math.Rectangle bounds, int count, PooledEngine engine) {
        ParticleHelper.spawnAreaExplosions(engine, bounds, count);
    }

    /**
     * Spawns multiple explosions specifically around the perimeter of a rectangular area.
     * Useful for obstacles where we want the explosions to appear "outside" or along the edges.
     */
    public void spawnPerimeterExplosions(com.badlogic.gdx.math.Rectangle bounds, float padding, int count, PooledEngine engine) {
        ParticleHelper.spawnPerimeterExplosions(engine, bounds, padding, count);
    }

    /**
     * Clears the template registry.
     */
    public static void dispose() {
        templates.clear();
    }
}
