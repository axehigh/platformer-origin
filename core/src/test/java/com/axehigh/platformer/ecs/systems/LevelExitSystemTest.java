package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.LevelManager;
import com.axehigh.platformer.map.ProgressData;
import com.axehigh.platformer.map.SaveData;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Headless unit tests for {@link LevelExitSystem}: proximity detection, door glow attachment/detachment
 * via {@link LightComponent}, and interact-driven level transitions.
 */
public class LevelExitSystemTest extends SystemTestBase {

    private Engine engine;
    private LevelManager levelManager;
    private com.axehigh.platformer.ecs.systems.LevelExitSystem system;
    private Preferences preferences;

    @Before
    public void setUp() {
        Gdx.app = mock(Application.class);
        preferences = mock(Preferences.class);
        when(Gdx.app.getPreferences(Mockito.anyString())).thenReturn(preferences);

        levelManager = Mockito.mock(LevelManager.class);
        system = new com.axehigh.platformer.ecs.systems.LevelExitSystem(levelManager);
        engine = newEngine();
        engine.addSystem(system);
    }

    @After
    public void tearDown() {
        Gdx.app = null;
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 20f, 40f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    private Entity gate(float x, float y, float width, float height, String nextLevelPath) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, width, height);
        place(transform, collision, x, y);
        LevelExitComponent exit = new LevelExitComponent();
        exit.nextLevelPath = nextLevelPath;
        Entity entity = entity(transform, collision, exit);
        engine.addEntity(entity);
        return entity;
    }

    private Entity finalGate(float x, float y, float width, float height, String nextLevelPath) {
        Entity entity = gate(x, y, width, height, nextLevelPath);
        entity.getComponent(LevelExitComponent.class).isFinalLevel = true;
        return entity;
    }

    @Test
    public void playerNearDoorFadesInLightComponentAndSetsNearExit() {
        Entity gateEntity = gate(100f, 50f, 32f, 48f, "maps/level2.tmx");
        Entity playerEntity = player(100f, 50f);

        // Step 1 frame
        engine.update(DT);

        PlayerComponent player = PLAYER.get(playerEntity);
        LevelExitComponent exit = gateEntity.getComponent(LevelExitComponent.class);
        assertTrue("Player should be marked nearExit", player.nearExit);
        assertTrue("Gate should have LightComponent when fading in", LIGHT.has(gateEntity));
        assertTrue("Fade progress should start increasing", exit.fadeProgress > 0f && exit.fadeProgress < 1f);

        LightComponent light = LIGHT.get(gateEntity);
        assertNotNull(light);
        assertEquals(16f, light.offset.x, EPSILON);
        assertEquals(24f, light.offset.y, EPSILON);
        assertEquals(1f, light.color.r, EPSILON);
        assertEquals(0.85f, light.color.g, EPSILON);
        assertEquals(0.5f, light.color.b, EPSILON);

        // Step until full fade-in (0.3s)
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }

        assertEquals(1f, exit.fadeProgress, EPSILON);
        assertEquals(24f, light.radius, EPSILON);
        assertEquals(0.5f, light.baseAlpha, EPSILON);
    }

    @Test
    public void playerMovingAwayFadesOutAndDetachesLightComponent() {
        Entity gateEntity = gate(100f, 50f, 32f, 48f, "maps/level2.tmx");
        Entity playerEntity = player(100f, 50f);

        // Fully fade in
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }
        assertTrue(LIGHT.has(gateEntity));
        assertTrue(PLAYER.get(playerEntity).nearExit);

        // Move player away
        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 300f, 50f);

        // Halfway fade-out (0.15s = 9 frames of DT)
        for (int i = 0; i < 9; i++) {
            engine.update(DT);
        }
        assertFalse("Player should not be marked nearExit", PLAYER.get(playerEntity).nearExit);
        assertTrue("Gate LightComponent should still exist while fading out", LIGHT.has(gateEntity));
        LevelExitComponent exit = gateEntity.getComponent(LevelExitComponent.class);
        assertTrue("Fade progress should be between 0 and 1", exit.fadeProgress > 0f && exit.fadeProgress < 1f);

        // Complete fade-out
        for (int i = 0; i < 15; i++) {
            engine.update(DT);
        }
        assertEquals(0f, exit.fadeProgress, EPSILON);
        assertFalse("Gate LightComponent should be removed once fade reaches 0", LIGHT.has(gateEntity));
    }

    @Test
    public void continuousReversalMidFade() {
        Entity gateEntity = gate(100f, 50f, 32f, 48f, "maps/level2.tmx");
        Entity playerEntity = player(100f, 50f);
        LevelExitComponent exit = gateEntity.getComponent(LevelExitComponent.class);

        // Fade in partially (9 frames ~= 0.15s, progress ~= 0.5)
        for (int i = 0; i < 9; i++) {
            engine.update(DT);
        }
        float progressMid = exit.fadeProgress;
        assertTrue(progressMid > 0.4f && progressMid < 0.6f);

        // Move away for 3 frames (fading out)
        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 300f, 50f);
        for (int i = 0; i < 3; i++) {
            engine.update(DT);
        }
        float progressDecreased = exit.fadeProgress;
        assertTrue(progressDecreased < progressMid);

        // Move back in range (fading in again continuously)
        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 100f, 50f);
        for (int i = 0; i < 5; i++) {
            engine.update(DT);
        }
        assertTrue(exit.fadeProgress > progressDecreased);
    }

    @Test
    public void interactTriggersLevelTransition() {
        Entity gateEntity = gate(100f, 50f, 32f, 48f, "maps/level2.tmx");
        Entity playerEntity = player(100f, 50f);
        PlayerComponent player = PLAYER.get(playerEntity);
        player.interactPressed = true;

        engine.update(DT);

        assertFalse("interactPressed should be reset after triggering transition", player.interactPressed);
        verify(levelManager).loadLevel("maps/level2.tmx", playerEntity);
    }

    @Test
    public void removingPlayerFadesOutAndDetachesGateLightComponent() {
        Entity gateEntity = gate(100f, 50f, 32f, 48f, "maps/level2.tmx");
        Entity playerEntity = player(100f, 50f);

        // Fully fade in
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }
        assertTrue(LIGHT.has(gateEntity));

        engine.removeEntity(playerEntity);

        // Still fading out
        engine.update(DT);
        assertTrue(LIGHT.has(gateEntity));

        // Complete fade-out
        for (int i = 0; i < 20; i++) {
            engine.update(DT);
        }
        assertFalse(LIGHT.has(gateEntity));
    }

    @Test
    public void interactOnFinalGateTriggersVictoryNotLoadLevel() {
        AtomicBoolean victoryFired = new AtomicBoolean(false);
        system = new com.axehigh.platformer.ecs.systems.LevelExitSystem(levelManager, 0, () -> victoryFired.set(true));
        engine = newEngine();
        engine.addSystem(system);

        Entity gateEntity = finalGate(100f, 50f, 32f, 48f, "maps/world2/level_01.tmx");
        Entity playerEntity = player(100f, 50f);
        PlayerComponent player = PLAYER.get(playerEntity);
        player.interactPressed = true;

        engine.update(DT);

        assertTrue("Victory callback should fire on a final gate", victoryFired.get());
        assertFalse("interactPressed should be reset after victory trigger", player.interactPressed);
        assertNotNull("Gate should still have its LevelExitComponent after victory", gateEntity.getComponent(LevelExitComponent.class));
        verify(levelManager, never()).loadLevel(any(), any());
        verify(preferences).putString(eq("save"), anyString());
    }

    @Test
    public void interactOnNonFinalGateStillLoadsLevel() {
        AtomicBoolean victoryFired = new AtomicBoolean(false);
        system = new com.axehigh.platformer.ecs.systems.LevelExitSystem(levelManager, 0, () -> victoryFired.set(true));
        engine = newEngine();
        engine.addSystem(system);

        Entity gateEntity = gate(100f, 50f, 32f, 48f, "maps/level2.tmx");
        Entity playerEntity = player(100f, 50f);
        PlayerComponent player = PLAYER.get(playerEntity);
        player.interactPressed = true;

        engine.update(DT);

        assertFalse("Victory callback must not fire on a non-final gate", victoryFired.get());
        verify(levelManager).loadLevel("maps/level2.tmx", playerEntity);
    }

    @Test
    public void setOnTransitionCallbackInvokedInsteadOfDirectLoadLevel() {
        AtomicBoolean transitionFired = new AtomicBoolean(false);
        final String[] capturedPath = new String[1];
        final Entity[] capturedPlayer = new Entity[1];
        system.setOnTransition((nextLevelPath, playerEntity) -> {
            transitionFired.set(true);
            capturedPath[0] = nextLevelPath;
            capturedPlayer[0] = playerEntity;
        });
        engine = newEngine();
        engine.addSystem(system);

        Entity gateEntity = gate(100f, 50f, 32f, 48f, "maps/level2.tmx");
        Entity playerEntity = player(100f, 50f);
        PLAYER.get(playerEntity).interactPressed = true;

        engine.update(DT);

        assertTrue("Custom transition callback should fire on a non-final gate", transitionFired.get());
        assertEquals("maps/level2.tmx", capturedPath[0]);
        assertSame("Transition callback should receive the player entity", playerEntity, capturedPlayer[0]);
        assertFalse("interactPressed should be reset after transition", PLAYER.get(playerEntity).interactPressed);
        verify(levelManager, never()).loadLevel(any(), any());
    }

    @Test
    public void finalGateSavePersistsCompletedWorldIds() {
        when(levelManager.getCurrentLevelPath()).thenReturn("maps/world1/level_10.tmx");

        system = new LevelExitSystem(levelManager, 0, () -> {});
        engine = newEngine();
        engine.addSystem(system);

        finalGate(100f, 50f, 32f, 48f, "maps/world2/level_01.tmx");
        Entity playerEntity = player(100f, 50f);
        PLAYER.get(playerEntity).interactPressed = true;

        engine.update(DT);

        // Durable progress record carries the just-completed world key.
        ArgumentCaptor<String> progressCaptor = ArgumentCaptor.forClass(String.class);
        verify(preferences).putString(eq("progress"), progressCaptor.capture());
        ProgressData progress = new Json().fromJson(ProgressData.class, progressCaptor.getValue());
        assertTrue("Progress should contain the just-completed world key",
            progress.completedWorldIds.contains("world1", false));

        // The run save is a separate blob and must NOT contain star data anymore.
        ArgumentCaptor<String> saveCaptor = ArgumentCaptor.forClass(String.class);
        verify(preferences).putString(eq("save"), saveCaptor.capture());
        String saveJson = saveCaptor.getValue();
        assertFalse("Run save JSON must not carry completedWorldIds",
            saveJson.contains("completedWorldIds"));
        assertFalse("Run save JSON must not carry completedLevelIds",
            saveJson.contains("completedLevelIds"));
        SaveData saved = new Json().fromJson(SaveData.class, saveJson);
        assertEquals("maps/world2/level_01.tmx", saved.levelPath);
    }
}
