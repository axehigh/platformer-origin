package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;
import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_UI_GFX;
import static com.axehigh.platformer.ecs.components.Mappers.*;

/**
 * Renders small blinking potion icons above the player character when any timed potion effect
 * (Strength, Speed, Invulnerability, Invisibility, Jump, Fire Breath) is expiring
 * (remaining time < {@link GameConstants#BUFF_BLINK_THRESHOLD}).
 */
public class BuffBlinkRenderSystem extends IteratingSystem {
    private final AssetManager assetManager;
    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private float blinkClock;

    public BuffBlinkRenderSystem(AssetManager assetManager, SpriteBatch batch, OrthographicCamera camera, int priority) {
        super(Family.all(PlayerComponent.class, TransformComponent.class, BuffComponent.class).get(), priority);
        this.assetManager = assetManager;
        this.batch = batch;
        this.camera = camera;
    }

    @Override
    public void update(float deltaTime) {
        blinkClock += deltaTime;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        super.update(deltaTime);
        batch.end();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = TRANSFORM.get(entity);
        CollisionComponent collision = COLLISION.get(entity);
        BuffComponent buff = BUFF.get(entity);
        if (buff == null) {
            return;
        }

        float centerX = transform.position.x;
        float topY = transform.position.y;
        if (collision != null) {
            centerX += collision.bounds.x + collision.bounds.width / 2f;
            topY += collision.bounds.y + collision.bounds.height;
        } else {
            topY += 32f;
        }
        topY += 8f;

        PotionType[] timedPotions = {
            PotionType.STRENGTH, PotionType.SPEED, PotionType.INVULNERABILITY,
            PotionType.INVISIBILITY, PotionType.JUMP, PotionType.FIRE_BREATH,
            PotionType.SPIDER
        };

        Array<PotionType> expiring = new Array<>();
        for (PotionType type : timedPotions) {
            Timer timer = buffTimer(buff, type);
            if (timer != null && timer.isActive() && timer.getRemaining() < GameConstants.BUFF_BLINK_THRESHOLD) {
                expiring.add(type);
            }
        }

        if (expiring.size > 0) {
            boolean blinkPhase = (int) (blinkClock / GameConstants.BUFF_BLINK_INTERVAL) % 2 == 0;
            if (blinkPhase) {
                float iconSize = 36f;
                float spacing = 6f;
                float totalWidth = expiring.size * iconSize + (expiring.size - 1) * spacing;
                float startX = centerX - totalWidth / 2f;
                for (int i = 0; i < expiring.size; i++) {
                    PotionType type = expiring.get(i);
                    TextureRegion region = getPotionRegion(type);
                    if (region != null) {
                        batch.draw(region, startX + i * (iconSize + spacing), topY, iconSize, iconSize);
                    }
                }
            }
        }
    }

    private Timer buffTimer(BuffComponent buff, PotionType type) {
        switch (type) {
            case STRENGTH:
                return buff.strength;
            case SPEED:
                return buff.speed;
            case INVULNERABILITY:
                return buff.invulnerability;
            case INVISIBILITY:
                return buff.invisibility;
            case JUMP:
                return buff.jump;
            case FIRE_BREATH:
                return buff.fireBreath;
            case SPIDER:
                return buff.spider;
            default:
                return null;
        }
    }

    private TextureRegion getPotionRegion(PotionType type) {
        if (assetManager == null) {
            return null;
        }
        TextureAtlas atlas = assetManager.isLoaded(ORIGIN_UI_GFX) ? assetManager.get(ORIGIN_UI_GFX, TextureAtlas.class) : null;
        if (atlas != null) {
            TextureAtlas.AtlasRegion region = atlas.findRegion(type.regionName());
            if (region != null) {
                return region;
            }
        }
        TextureAtlas gameAtlas = assetManager.isLoaded(ORIGIN_GAME_GFX) ? assetManager.get(ORIGIN_GAME_GFX, TextureAtlas.class) : null;
        if (gameAtlas != null) {
            TextureAtlas.AtlasRegion region = gameAtlas.findRegion(type.regionName());
            if (region != null) {
                return region;
            }
        }
        return null;
    }
}
