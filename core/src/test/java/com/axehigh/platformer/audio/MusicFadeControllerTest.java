package com.axehigh.platformer.audio;

import com.axehigh.platformer.ecs.systems.SystemTestBase;
import com.badlogic.gdx.audio.Music;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

/**
 * Headless unit tests for {@link MusicFadeController}, the frame-driven two-track crossfade helper.
 * {@code Music} is mocked (plain Mockito defaults: {@code isPlaying()} false, {@code getVolume()} 0)
 * and stubbed where the ramp start level depends on the real track state.
 */
public class MusicFadeControllerTest extends SystemTestBase {

    private static final float TARGET = 0.6f;

    private MusicFadeController fade;

    @Before
    public void setUp() {
        fade = new MusicFadeController();
    }

    @Test
    public void start_nullNewTrack_noOpAndNeverActivates() {
        Music previous = mock(Music.class);

        fade.start(previous, null, TARGET);
        fade.update(1f);

        assertFalse(fade.isActive());
        verify(previous, never()).play();
        verify(previous, never()).setVolume(anyFloat());
        verify(previous, never()).stop();
    }

    @Test
    public void start_nullPrevious_fadesInOnly() {
        Music incoming = mock(Music.class);

        fade.start(null, incoming, TARGET);

        assertTrue(fade.isActive());
        verify(incoming).setVolume(0f);
        verify(incoming).play();

        fade.update(0.5f);
        assertTrue(fade.isActive());
        verify(incoming).setVolume(0.3f);

        fade.update(0.5f);
        assertFalse(fade.isActive());
        // Final frame ramps to target*1.0 and then applies the exact target volume.
        verify(incoming, times(2)).setVolume(TARGET);
        verify(incoming, never()).stop();
        verify(incoming, times(1)).play();
    }

    @Test
    public void start_previousTrack_readsPlayingVolumeAndRampsToStop() {
        Music previous = mock(Music.class);
        Music incoming = mock(Music.class);
        when(previous.isPlaying()).thenReturn(true);
        when(previous.getVolume()).thenReturn(0.8f);

        fade.start(previous, incoming, TARGET);

        assertTrue(fade.isActive());
        verify(previous, never()).stop();
        verify(incoming).setVolume(0f);
        verify(incoming).play();

        fade.update(0.5f);
        assertTrue(fade.isActive());
        verify(previous).setVolume(0.4f);
        verify(incoming).setVolume(0.3f);

        fade.update(0.5f);
        assertFalse(fade.isActive());
        verify(previous).setVolume(0f);
        verify(previous).stop();
        verify(incoming, times(2)).setVolume(TARGET);
        verify(incoming, never()).stop();
    }

    @Test
    public void start_previousNotPlaying_rampsPreviousFromTargetVolume() {
        Music previous = mock(Music.class);
        Music incoming = mock(Music.class);
        // Defaults: isPlaying() false, getVolume() 0f -> ramp start falls back to targetVolume.

        fade.start(previous, incoming, TARGET);
        fade.update(0.5f);

        verify(previous).setVolume(0.3f);
    }

    @Test
    public void start_midFadeRestart_stopsInFlightPreviousAndFadeInTrackBecomesPrevious() {
        Music first = mock(Music.class);
        Music second = mock(Music.class);
        Music third = mock(Music.class);
        when(first.isPlaying()).thenReturn(true);
        when(first.getVolume()).thenReturn(0.8f);

        fade.start(first, second, 0.6f);
        fade.update(0.5f);
        verify(first).setVolume(0.4f);
        verify(second).setVolume(0.3f);

        // Restart mid-fade: second (in-flight fade-in, current volume 0.3) becomes the new previous.
        when(second.isPlaying()).thenReturn(true);
        when(second.getVolume()).thenReturn(0.3f);
        fade.start(second, third, 1f);

        verify(first).stop();
        verify(third).setVolume(0f);
        verify(third).play();
        verify(second, never()).stop();

        fade.update(0.5f);
        assertTrue(fade.isActive());
        verify(second).setVolume(0.15f);
        verify(third).setVolume(0.5f);

        fade.update(0.5f);
        assertFalse(fade.isActive());
        verify(second, times(2)).setVolume(0f);
        verify(second).stop();
        verify(third, times(2)).setVolume(1f);
    }

    @Test
    public void cancel_stopsBothTracksAndClearsState() {
        Music previous = mock(Music.class);
        Music incoming = mock(Music.class);
        when(previous.isPlaying()).thenReturn(true);
        fade.start(previous, incoming, TARGET);
        fade.update(0.3f);

        fade.cancel();

        assertFalse(fade.isActive());
        verify(previous).stop();
        verify(incoming).stop();

        // Idempotent + inert afterwards: no further interaction on repeated call/update.
        fade.cancel();
        fade.update(1f);
        verify(previous, times(1)).stop();
        verify(incoming, times(1)).stop();
        verify(incoming, times(1)).play();
    }

    @Test
    public void update_zeroDeltaAndPartialTicks_keepFadeActive() {
        Music previous = mock(Music.class);
        Music incoming = mock(Music.class);
        when(previous.isPlaying()).thenReturn(true);
        when(previous.getVolume()).thenReturn(0.8f);
        fade.start(previous, incoming, TARGET);

        fade.update(0f);
        assertTrue(fade.isActive());
        verify(incoming, times(2)).setVolume(0f);

        fade.update(0.5f);
        assertTrue(fade.isActive());
        fade.update(0.25f);
        assertTrue("partial ticks must not complete the fade", fade.isActive());

        // Cumulative ramp: 0.6*0=0, 0.6*0.5=0.3, 0.6*0.75=0.45 (progress is elapsed/duration).
        ArgumentCaptor<Float> volumes = ArgumentCaptor.forClass(Float.class);
        verify(incoming, times(4)).setVolume(volumes.capture());
        float[] expected = {0f, 0f, 0.3f, 0.45f};
        for (int i = 0; i < expected.length; i++) {
            assertEquals("ramp value " + i, expected[i], volumes.getAllValues().get(i), EPSILON);
        }
    }

    @Test
    public void update_manySmallTicksSummingToDuration_completeFade() {
        Music previous = mock(Music.class);
        Music incoming = mock(Music.class);
        when(previous.isPlaying()).thenReturn(true);
        when(previous.getVolume()).thenReturn(0.8f);
        fade.start(previous, incoming, TARGET);

        float elapsed = 0f;
        int ticks = 0;
        while (fade.isActive()) {
            fade.update(DT);
            elapsed += DT;
            ticks++;
            assertTrue("tick " + ticks, ticks <= 1_000);
        }

        // Float accumulation of 1/60f can land a hair under 1.0, so allow one frame of slack.
        assertEquals(MusicFadeController.MUSIC_FADE_DURATION, elapsed, DT);
        assertFalse(fade.isActive());
        verify(previous).stop();
        verify(incoming, times(2)).setVolume(TARGET);
    }

    @Test
    public void update_hugeDelta_volumesStayClampedInRange() {
        Music previous = mock(Music.class);
        Music incoming = mock(Music.class);
        when(previous.isPlaying()).thenReturn(true);
        when(previous.getVolume()).thenReturn(0.8f);
        fade.start(previous, incoming, TARGET);

        fade.update(0.5f);
        fade.update(0.3f);
        fade.update(10f);

        ArgumentCaptor<Float> incomingVolumes = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> previousVolumes = ArgumentCaptor.forClass(Float.class);
        verify(incoming, times(5)).setVolume(incomingVolumes.capture());
        verify(previous, times(3)).setVolume(previousVolumes.capture());

        for (float v : incomingVolumes.getAllValues()) {
            assertTrue("incoming volume " + v, v >= 0f && v <= TARGET);
        }
        for (float v : previousVolumes.getAllValues()) {
            assertTrue("previous volume " + v, v >= 0f && v <= 0.8f);
        }
        assertFalse(fade.isActive());
        verify(incoming, times(2)).setVolume(TARGET);
        verify(previous).stop();
    }

    @Test
    public void update_beforeAnyStart_isInert() {
        Music track = mock(Music.class);

        fade.update(1f);

        assertFalse(fade.isActive());
        verify(track, never()).play();
        verify(track, never()).setVolume(anyFloat());
        verify(track, never()).stop();
    }
}
