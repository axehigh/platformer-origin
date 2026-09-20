package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.AnimationComponent.State.*;
import static com.axehigh.platformer.ecs.components.EnemyComponent.Size.*;
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
        animComp.animations.put(ATTACKING, context.buildAnimation(FRAME_DURATION, type == EnemyType.FLYER ? type.atlasPrefix + "_attack2" : type.atlasPrefix + "_attack", NORMAL));
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
        float patrolRangeOverride = TileProps.getTileXProperty(object, tile, "patrolRange", Float.NaN, context.tileWidth);
        if (!Float.isNaN(patrolRangeOverride)) {
            enemyComponent.patrolRange = patrolRangeOverride * context.unitScale;
        }
        enemyComponent.speed *= context.unitScale;
        enemyComponent.direction = MathUtils.randomBoolean() ? 1 : -1;
        enemyComponent.speed *= MathUtils.random(0.85f, 1.15f);
        enemyComponent.health = type.maxHealth;
        enemyComponent.maxHealth = type.maxHealth;

        String sizeStr = TileProps.getProperty(object, tile, "size", null);
        if ("default".equalsIgnoreCase(sizeStr)) {
            enemyComponent.size = DEFAULT;
        } else if ("medium".equalsIgnoreCase(sizeStr)) {
            enemyComponent.size = MEDIUM;
        } else if ("large".equalsIgnoreCase(sizeStr)) {
            enemyComponent.size = LARGE;
        }
        enemyComponent.health *= enemyComponent.size.hpMultiplier;
        enemyComponent.maxHealth *= enemyComponent.size.hpMultiplier;

        switch (type) {
            case FLYER:
                FlyingEnemyComponent flying = new FlyingEnemyComponent();
                flying.bobAmplitude *= context.unitScale;
                // Random bob phase so flyers don't flap in unison (they all start at bobTime = 0).
                flying.bobTime = MathUtils.random(0f, MathUtils.PI2 / flying.bobFrequency);
                // Retreat target = the flyer's WORLD-SPACE collision center at spawn. worldBounds
                // is recomputed from transform + local bounds every frame, so the center must
                // include the map x/y plus the base/current offsets and half-dimensions — otherwise
                // the flyer retreats to a wrong (or ground-level) point.
                flying.spawnX = x + collisionComponent.baseOffsetX + collisionComponent.bounds.width / 2f;
                flying.spawnY = y + collisionComponent.baseOffsetY + collisionComponent.currentOffsetY + collisionComponent.bounds.height / 2f;
                entity.add(flying);
                break;
            case SHOOTER:
                EnemyShooterComponent shooter = new EnemyShooterComponent();
                // Per-marker shoot/detection range overrides. The Tiled values are TILE COUNTS
                // (e.g. 8 = 8 tiles); getTileXProperty converts to world units (× tileWidth).
                // Unlike patrolRange/attackRange there is deliberately NO extra × unitScale —
                // shootRange/detectionRange = tile count × tileWidth, per the agreed design.
                // (tileWidth must reflect the active map's real tile size — GameScreen and
                // LevelManager both push it via setTileDimensions; the 16f default is a base only.)
                shooter.shootRange = TileProps.getTileXProperty(object, tile, "shootRange", shooter.shootRange, context.tileWidth);
                shooter.detectionRange = TileProps.getTileXProperty(object, tile, "detectionRange", shooter.detectionRange, context.tileWidth);
                // Per-marker wind-up telegraph: a TIME in REAL SECONDS (default 0.5), deliberately
                // NOT a tile count — read via getFloatProperty, never getTileXProperty (that would
                // multiply by tileWidth). Matches the melee windUpDuration raw-seconds convention.
                shooter.windUpSeconds = TileProps.getFloatProperty(object, tile, "windUp", shooter.windUpSeconds);
                // Stagger the first shot across the interval so shooters don't all fire the same
                // frame the player enters their room.
                shooter.shootCooldown.start(MathUtils.random(0f, shooter.shootInterval));
                entity.add(shooter);
                break;
            default:
                break;
        }
        entity.add(enemyComponent);
        entity.add(new HitFlashComponent());

        String lootStr = TileProps.getProperty(object, tile, "loot", null);
        if (lootStr != null) {
            LootComponent loot = new LootComponent();
            String[] items = lootStr.split(",");
            for (String item : items) {
                String[] parts = item.trim().replace('=', ':').split(":");
                if (parts.length != 2) continue;
                String itemType = parts[0].trim();
                String value = parts[1].trim();

                LootComponent.LootEntry entry = new LootComponent.LootEntry();
                if ("coin".equalsIgnoreCase(itemType)) {
                    entry.type = LootComponent.LootType.COIN;
                    entry.amount = Integer.parseInt(value);
                } else if ("ammo".equalsIgnoreCase(itemType)) {
                    entry.type = LootComponent.LootType.AMMO;
                    entry.amount = Integer.parseInt(value);
                } else if ("potion".equalsIgnoreCase(itemType)) {
                    entry.type = LootComponent.LootType.POTION;
                    entry.potionType = value;
                }
                if (entry.type != null) {
                    loot.drops.add(entry);
                }
            }
            if (loot.drops.size > 0) {
                entity.add(loot);
            }
        }

        // Read attack type from Tiled marker property (default: "melee" for non-shooters, null for shooters)
        String attackTypeStr = TileProps.getProperty(object, tile, "attackType", null);
        if (attackTypeStr == null) {
            if (type != EnemyType.SHOOTER) {
                attackTypeStr = "melee";
            }
        }
        if ("melee".equalsIgnoreCase(attackTypeStr)) {
            EnemyAttackComponent attack = new EnemyAttackComponent();
            attack.attackType = EnemyAttackComponent.AttackType.MELEE;
            float intervalOverride = TileProps.getFloatProperty(object, tile, "attackInterval", Float.NaN);
            if (!Float.isNaN(intervalOverride)) attack.attackInterval = intervalOverride;
            float rangeOverride = TileProps.getTileXProperty(object, tile, "attackRange", Float.NaN, context.tileWidth);
            if (Float.isNaN(rangeOverride)) {
                rangeOverride = TileProps.getTileXProperty(object, tile, "meleeRange", Float.NaN, context.tileWidth); // legacy alias
            }
            if (!Float.isNaN(rangeOverride)) attack.attackRange = rangeOverride * context.unitScale;
            float windUpOverride = TileProps.getFloatProperty(object, tile, "windUpDuration", Float.NaN);
            if (!Float.isNaN(windUpOverride)) attack.windUpDuration = windUpOverride;
            attack.attackCooldown.start(MathUtils.random(0f, attack.attackInterval));
            entity.add(attack);
        }

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
