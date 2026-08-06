package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.audio.AudioManager;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Headless tests for {@code SfxSystem}: coin SFX requests are delegated to the shared
 * {@link AudioManager}.
 */
public class SfxSystemTest extends SystemTestBase {

    @Test
    public void playCoinDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playCoin();

        verify(audio).playCoin();
    }

    @Test
    public void playWallBreakDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playWallBreak();

        verify(audio).playWallBreak();
    }
}
