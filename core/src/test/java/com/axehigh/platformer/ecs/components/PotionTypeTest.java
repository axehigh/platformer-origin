package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.GameConstants;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link PotionType} durations, descriptions, region names, and parsing.
 */
public class PotionTypeTest {

    @Test
    public void potionDurationsMatchGameConstants() {
        assertEquals(GameConstants.FIRE_BREATH_DURATION, com.axehigh.platformer.ecs.components.PotionType.FIRE_BREATH.duration(), 0.001f);
        assertEquals(0f, com.axehigh.platformer.ecs.components.PotionType.HEALING.duration(), 0.001f);
        assertEquals(GameConstants.INVISIBILITY_DURATION, com.axehigh.platformer.ecs.components.PotionType.INVISIBILITY.duration(), 0.001f);
        assertEquals(GameConstants.INVULNERABILITY_DURATION, com.axehigh.platformer.ecs.components.PotionType.INVULNERABILITY.duration(), 0.001f);
        assertEquals(GameConstants.JUMP_BUFF_DURATION, com.axehigh.platformer.ecs.components.PotionType.JUMP.duration(), 0.001f);
        assertEquals(GameConstants.STRENGTH_BUFF_DURATION, com.axehigh.platformer.ecs.components.PotionType.STRENGTH.duration(), 0.001f);
        assertEquals(GameConstants.SPEED_BUFF_DURATION, com.axehigh.platformer.ecs.components.PotionType.SPEED.duration(), 0.001f);
        assertEquals(GameConstants.SPIDER_BUFF_DURATION, com.axehigh.platformer.ecs.components.PotionType.SPIDER.duration(), 0.001f);
    }

    @Test
    public void potionDescriptionsFormatDurationCorrectly() {
        for (com.axehigh.platformer.ecs.components.PotionType type : com.axehigh.platformer.ecs.components.PotionType.values()) {
            assertNotNull(type.description());
            if (type.duration() > 0f) {
                assertTrue(
                        "Description should contain formatted duration for " + type,
                        type.description().contains(Integer.toString((int) type.duration()))
                );
            }
        }
    }

    @Test
    public void regionNamesFollowExpectedFormat() {
        assertEquals("potion_fire_breath", com.axehigh.platformer.ecs.components.PotionType.FIRE_BREATH.regionName());
        assertEquals("potion_healing", com.axehigh.platformer.ecs.components.PotionType.HEALING.regionName());
        assertEquals("potion_invisibility", com.axehigh.platformer.ecs.components.PotionType.INVISIBILITY.regionName());
        assertEquals("potion_invulnerability", com.axehigh.platformer.ecs.components.PotionType.INVULNERABILITY.regionName());
        assertEquals("potion_jump", com.axehigh.platformer.ecs.components.PotionType.JUMP.regionName());
        assertEquals("potion_strength", com.axehigh.platformer.ecs.components.PotionType.STRENGTH.regionName());
        assertEquals("potion_speed", com.axehigh.platformer.ecs.components.PotionType.SPEED.regionName());
        assertEquals("potion_spider", com.axehigh.platformer.ecs.components.PotionType.SPIDER.regionName());
    }

    @Test
    public void parseResolvesKnownAndFallbackTypes() {
        assertEquals(com.axehigh.platformer.ecs.components.PotionType.HEALING, com.axehigh.platformer.ecs.components.PotionType.parse(null));
        assertEquals(com.axehigh.platformer.ecs.components.PotionType.HEALING, com.axehigh.platformer.ecs.components.PotionType.parse("unknown_potion"));
        assertEquals(com.axehigh.platformer.ecs.components.PotionType.FIRE_BREATH, com.axehigh.platformer.ecs.components.PotionType.parse("FIRE_BREATH"));
        assertEquals(com.axehigh.platformer.ecs.components.PotionType.FIRE_BREATH, com.axehigh.platformer.ecs.components.PotionType.parse("fire-breath"));
        assertEquals(com.axehigh.platformer.ecs.components.PotionType.FIRE_BREATH, com.axehigh.platformer.ecs.components.PotionType.parse("fire breath"));
        assertEquals(com.axehigh.platformer.ecs.components.PotionType.FIRE_BREATH, com.axehigh.platformer.ecs.components.PotionType.parse("firebreath"));
        assertEquals(com.axehigh.platformer.ecs.components.PotionType.SPIDER, com.axehigh.platformer.ecs.components.PotionType.parse("spider"));
    }
}
