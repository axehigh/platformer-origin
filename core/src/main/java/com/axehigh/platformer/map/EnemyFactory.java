package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.AnimationComponent.State.*;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL;

/**
 * Builds enemy entities from {@code enemy} object-layer markers: per-type sprite/collision data
 * comes from {@link EnemyType}, animations from the game atlas, and behavior tuning (AI mode,
 * speed, patrol range) from the marker's Tiled custom properties.
 */
class EnemyFactory {
    private static final float FRAME_DURATION = 0.1f;

    private final FactoryContext context;

    EnemyFactory(FactoryContext context) {
        this.context = context;
    }

    public Entity createEnemy(float x, float y, String enemyType, MapObject object, TiledMapTile tile, int roomIndex) {
        EnemyType type = EnemyType.fromTiledValue(enemyType);
        float enemyScale = context.unitScale * type.scale;

        Entity entity = new Entity();

        // Animations
        AnimationComponent animComp = new AnimationComponent();
        animComp.animations.put(IDLE, context.buildAnimation(FRAME_DURATION, type.atlasPrefix + "_idle", LOOP));
        animComp.animations.put(WALKING, context.buildAnimation(FRAME_DURATION, type.atlasPrefix + "_" + type.walkRegionName, LOOP));
        animComp.animations.put(ATTACKING, context.buildAnimation(FRAME_DURATION, type.atlasPrefix + "_attack", NORMAL));
        animComp.animations.put(HURT, context.buildAnimation(FRAME_DURATION, type.atlasPrefix + "_hurt", NORMAL));
        animComp.animations.put(DEATH, context.buildAnimation(FRAME_DURATION, type.atlasPrefix + "_death", NORMAL));
        animComp.currentState = IDLE;
        entity.add(animComp);

        Animation<TextureRegion> idleAnim = animComp.animations.get(IDLE);
        TextureRegion initialRegion = findInitialRegion(idleAnim, type.atlasPrefix);

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(enemyScale, enemyScale);
        transform.z = FactoryContext.DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = initialRegion;
        entity.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.maxSpeedX *= context.unitScale;
        movementComponent.maxSpeedY *= context.unitScale;
        entity.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(type.collisionWidth * enemyScale, type.collisionHeight * enemyScale);
        collisionComponent.baseOffsetX = (128f * enemyScale - collisionComponent.bounds.width) / 2f;
        collisionComponent.baseOffsetY = (128f * enemyScale - collisionComponent.bounds.height) / 2f;
        collisionComponent.currentOffsetY = type.collisionOffsetY * enemyScale;

        collisionComponent.bounds.setX(collisionComponent.baseOffsetX);
        collisionComponent.bounds.setY(collisionComponent.baseOffsetY + collisionComponent.currentOffsetY);
        entity.add(collisionComponent);

        EnemyComponent enemyComponent = new EnemyComponent();
        enemyComponent.originX = x;
        enemyComponent.roomIndex = roomIndex;
        String aiMode = TileProps.getProperty(object, tile, "aiMode", null);
        if ("side-to-side".equalsIgnoreCase(aiMode) || "sidetoside".equalsIgnoreCase(aiMode)) {
            enemyComponent.aiMode = EnemyComponent.AiMode.SIDE_TO_SIDE;
        }
        float speedOverride = TileProps.getFloatProperty(object, tile, "speed", Float.NaN);
        if (!Float.isNaN(speedOverride)) {
            enemyComponent.speed = speedOverride;
        }
        float patrolRangeOverride = TileProps.getFloatProperty(object, tile, "patrolRange", Float.NaN);
        if (!Float.isNaN(patrolRangeOverride)) {
            enemyComponent.patrolRange = patrolRangeOverride;
        }
        enemyComponent.speed *= context.unitScale;
        enemyComponent.patrolRange *= context.unitScale;
        // Desync the patrol cycle so enemies in the same room don't move/pause in lockstep:
        // randomize the initial facing (all enemies otherwise spawn walking right) and jitter the
        // patrol speed ±15%, so each enemy drifts out of phase over time and never re-syncs.
        enemyComponent.direction = MathUtils.randomBoolean() ? 1 : -1;
        enemyComponent.speed *= MathUtils.random(0.85f, 1.15f);
        enemyComponent.health = type.maxHealth;
        enemyComponent.maxHealth = type.maxHealth;

        switch (type) {
            case FLYER:
                FlyingEnemyComponent flying = new FlyingEnemyComponent();
                flying.bobAmplitude *= context.unitScale;
                // Random bob phase so flyers don't flap in unison (they all start at bobTime = 0).
                flying.bobTime = MathUtils.random(0f, MathUtils.PI2 / flying.bobFrequency);
                entity.add(flying);
                break;
            case SHOOTER:
                EnemyShooterComponent shooter = new EnemyShooterComponent();
                // Stagger the first shot across the interval so shooters don't all fire the same
                // frame the player enters their room.
                shooter.shootCooldown.start(MathUtils.random(0f, shooter.shootInterval));
                entity.add(shooter);
                break;
            default:
                break;
        }
        entity.add(enemyComponent);

        return entity;
    }

    private TextureRegion findInitialRegion(Animation<TextureRegion> idleAnim, String atlasPrefix) {
        TextureRegion initialRegion = idleAnim.getKeyFrame(0f);
        if (initialRegion == null) {
            initialRegion = context.originAtlas.findRegion(atlasPrefix + "_idle1");
        }
        return initialRegion;
    }
}
