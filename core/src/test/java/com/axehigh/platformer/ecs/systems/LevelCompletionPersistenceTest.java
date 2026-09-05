package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.LevelExitComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.LevelManager;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Array;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Reproduces the player-visible report "completed world 1 but only some levels marked complete":
 * drives the real {@link LevelExitSystem} save accumulation, a death snapshot, a continue, and a
 * New Game reset, asserting which stars survive in the durable {@code ProgressData} store each
 * time (stars are PERMANENT account record — never touched by the run-save lifecycle).
 */
public class LevelCompletionPersistenceTest extends SystemTestBase {

    private final java.util.Map<String, String> store = new java.util.HashMap<>();
    private Engine engine;
    private LevelManager levelManager;
    private LevelExitSystem system;
    private Preferences preferences;

    private static final String[] WORLD1_PATHS = {
        "maps/world1/level_01.tmx", "maps/world1/level_02.tmx", "maps/world1/level_03.tmx",
        "maps/world1/level_04.tmx", "maps/world1/level_05.tmx", "maps/world1/level_06.tmx",
        "maps/world1/level_07.tmx", "maps/world1/level_08.tmx", "maps/world1/level_09.tmx",
        "maps/world1/level_10.tmx"
    };

    @Before
    public void setUp() {
        Gdx.app = mock(Application.class);
        preferences = mock(Preferences.class);
        // In-memory Preferences backed by a map: getString returns what putString stored.
        when(preferences.getString(anyString())).thenAnswer(inv -> store.getOrDefault((String) inv.getArgument(0), ""));
        when(preferences.getString(anyString(), anyString())).thenAnswer(inv ->
            store.getOrDefault((String) inv.getArgument(0), inv.getArgument(1)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(preferences).putString(anyString(), anyString());
        doAnswer(inv -> {
            store.remove(inv.getArgument(0));
            return null;
        }).when(preferences).remove(anyString());
        when(preferences.contains(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
        when(Gdx.app.getPreferences(anyString())).thenReturn(preferences);

        levelManager = mock(LevelManager.class);
        system = new LevelExitSystem(levelManager, 0, () -> {});
        system.setOnTransition((next, player) -> { /* swallow in-place swap */ });
        engine = newEngine();
        engine.addSystem(system);
    }

    @After
    public void tearDown() {
        Gdx.app = null;
    }

    private Entity gate(float x, float y, String nextLevelPath) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 32f, 48f);
        place(transform, collision, x, y);
        LevelExitComponent exit = new LevelExitComponent();
        exit.nextLevelPath = nextLevelPath;
        Entity entity = entity(transform, collision, exit);
        engine.addEntity(entity);
        return entity;
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 20f, 40f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    /** Interact with the gate of level {@code i} (index into WORLD1_PATHS) to complete that level. */
    private void completeLevel(int i) {
        when(levelManager.getCurrentLevelPath()).thenReturn(WORLD1_PATHS[i]);
        String next = i + 1 < WORLD1_PATHS.length ? WORLD1_PATHS[i + 1] : WORLD1_PATHS[0];
        Entity g = gate(100f, 50f, next);
        Entity p = player(100f, 50f);
        PLAYER.get(p).interactPressed = true;
        engine.update(DT);
        engine.removeEntity(g);
        engine.removeEntity(p);
    }

    @Test
    public void starsAccumulateAcrossSequentialLevelCompletions() {
        for (int i = 0; i < 4; i++) {
            completeLevel(i);
        }
        SaveData save = SaveManager.load();
        assertNotNull(save);
        Array<String> starIds = SaveManager.loadProgress().completedLevelIds;
        assertEquals("Stars must accumulate 4 completions",
            4, starIds.size);
        assertTrue(starIds.contains("world1_level01", false));
        assertTrue(starIds.contains("world1_level04", false));
    }

    @Test
    public void deathSnapshotKeepsPreviouslyEarnedStars() {
        for (int i = 0; i < 4; i++) {
            completeLevel(i);
        }

        // onPlayerDeath semantics: load existing save, keep stats, re-persist.
        SaveData death = SaveManager.hasSave() ? SaveManager.load() : new SaveData();
        death.levelPath = "maps/world1/level_05.tmx";
        SaveManager.save(death);

        SaveData after = SaveManager.load();
        assertEquals("maps/world1/level_05.tmx", after.levelPath);
        assertEquals("Death must not erase earned stars",
            4, SaveManager.loadProgress().completedLevelIds.size);
    }

    @Test
    public void newGameKeepsEarnedStars() {
        for (int i = 0; i < 4; i++) {
            completeLevel(i);
        }
        assertEquals(4, SaveManager.loadProgress().completedLevelIds.size);

        // StoryIntroScreen.newGame(): clear() (run-only) then a fresh SaveData.
        SaveManager.clear();
        assertFalse("New Game must start save-less", SaveManager.hasSave());

        SaveData fresh = new SaveData();
        fresh.levelPath = "maps/world1/level_01.tmx";
        SaveManager.save(fresh);
        assertTrue("New Game starts a fresh run save", SaveManager.hasSave());
        assertEquals("New Game must NOT erase earned stars",
            4, SaveManager.loadProgress().completedLevelIds.size);
    }

    @Test
    public void retryWorldRestoresTriesAndKeepsProgress() {
        for (int i = 0; i < 4; i++) {
            completeLevel(i);
        }

        // onRetryWorld semantics: run save back to the world's first level, budget restored to 3.
        SaveData save = SaveManager.hasSave() ? SaveManager.load() : new SaveData();
        save.triesRemaining = 3;
        save.levelPath = "maps/world1/level_01.tmx";
        SaveManager.save(save);

        assertEquals("Retry World keeps earned stars",
            4, SaveManager.loadProgress().completedLevelIds.size);
        assertEquals("Retry World restores the tries budget",
            3, SaveManager.load().triesRemaining);
    }

    @Test
    public void starsSurviveJsonRoundTripToStringAndBack() {
        for (int i = 0; i < 4; i++) {
            completeLevel(i);
        }
        SaveData loaded = SaveManager.load();
        assertNotNull(loaded);
        assertEquals(4, SaveManager.loadProgress().completedLevelIds.size);
    }
}
