package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.audio.AudioManager;
import com.badlogic.ashley.core.Engine;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Headless tests for {@code MusicSystem}: the in-game music track is started exactly once per
 * engine lifetime, no matter how many frames elapse.
 */
public class MusicSystemTest extends SystemTestBase {

    private Engine engine;
    private AudioManager audio;

    @Before
    public void setUp() {
        audio = mock(AudioManager.class);
        engine = newEngine();
        engine.addSystem(new MusicSystem(audio, 0));
    }

    @Test
    public void startsGameMusicOnFirstUpdate() {
        engine.update(DT);

        verify(audio).playGameMusic();
    }

    @Test
    public void doesNotRestartGameMusicOnLaterUpdates() {
        engine.update(DT);
        engine.update(DT);
        engine.update(DT);

        verify(audio, times(1)).playGameMusic();
    }
}
