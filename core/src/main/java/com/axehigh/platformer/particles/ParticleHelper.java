package com.axehigh.platformer.particles;

import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.utils.ObjectMap;

import static com.axehigh.platformer.particles.GlobalParticles.SMOKE;

/**
 * Helper class for managing and spawning particle effects defined in GlobalParticles.
 */
public class ParticleHelper {
    private static final ObjectMap<String, ParticleEffect> templates = new ObjectMap<>();

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
