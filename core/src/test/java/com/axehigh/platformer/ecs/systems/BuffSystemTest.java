package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.BuffComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.BUFF;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Headless tests for the timed potion buffs ticked/applied by {@link BuffSystem}. */
public class BuffSystemTest extends SystemTestBase {

    private BuffSystem system;
    private Engine engine;

    @Before
    public void setUp() {
        system = new BuffSystem(0);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity buffedPlayer(float maxSpeedX) {
        PlayerComponent player = player();
        MovementComponent movement = movement();
        movement.maxSpeedX = maxSpeedX;
        Entity entity = entity(player, movement, new BuffComponent());
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void speedBuffScalesMaxSpeedWhileActive() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        buff.startSpeed();

        engine.update(DT);

        assertEquals(100f * GameConstants.SPEED_MULTIPLIER, movement.maxSpeedX, EPSILON);
    }

    @Test
    public void speedBuffRestoresBaseMaxSpeedOnExpiry() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        buff.startSpeed();

        engine.update(DT);
        assertEquals(100f * GameConstants.SPEED_MULTIPLIER, movement.maxSpeedX, EPSILON);

        int frames = (int) Math.ceil(GameConstants.SPEED_BUFF_DURATION / DT) + 1;
        for (int i = 0; i < frames; i++) {
            engine.update(DT);
        }

        assertFalse(buff.isSpeedActive());
        assertEquals(100f, movement.maxSpeedX, EPSILON);
    }

    @Test
    public void strengthAndInvulnerabilityBuffsExpireAfterTheirDurations() {
        Entity entity = buffedPlayer(100f);
        BuffComponent buff = BUFF.get(entity);
        buff.startStrength();
        buff.startInvulnerability();

        int frames = (int) Math.ceil(Math.max(GameConstants.STRENGTH_BUFF_DURATION, GameConstants.INVULNERABILITY_DURATION) / DT) + 1;
        for (int i = 0; i < frames; i++) {
            engine.update(DT);
        }

        assertFalse(buff.isStrengthActive());
        assertFalse(buff.isInvulnerabilityActive());
    }
}
