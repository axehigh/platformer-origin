package com.axehigh.platformer.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.math.MathUtils;

/**
 * Frame-driven music crossfade helper: ramps the new track 0 → {@code targetVolume} while the
 * previous track ramps its current volume → 0, then stops the previous track. Plays no audio on
 * its own beyond the two {@link Music} handles passed in, and depends on no Gdx/AssetManager, so
 * it can be constructed and driven headless in tests.
 *
 * <p>Deterministic behaviors:
 * <ul>
 *   <li>{@link #start(Music, Music, float)} with {@code newTrack == null} is a no-op.</li>
 *   <li>{@code previousTrack == null} just fades the new track in (covers the first track ever).</li>
 *   <li>A {@code start(...)} call while a fade is already active cancels the in-flight fade by
 *       stopping its previous track (the in-flight fade-in track keeps playing as the new
 *       "previous", ramping down from wherever its volume currently is).</li>
 *   <li>The previous track ramps from {@code getVolume()} when it is still playing, else from
 *       {@code targetVolume} (its volume is unset/irrelevant once stopped).</li>
 * </ul>
 */
public class MusicFadeController {

    /** Crossfade length in seconds. */
    public static final float MUSIC_FADE_DURATION = 1.0f;

    private Music previousTrack;
    private Music newTrack;
    private float previousStartVolume;
    private float targetVolume;
    private float elapsed;

    /**
     * Begins a crossfade from {@code previousTrack} to {@code newTrack} over
     * {@link #MUSIC_FADE_DURATION} seconds. The previous track keeps playing and ramps down while
     * the new track starts at 0 volume and ramps up; the previous track is stopped on completion.
     *
     * @param previousTrack the outgoing track, already playing (may be null)
     * @param newTrack the incoming track, ramped in and played; null short-circuits to a no-op
     * @param targetVolume the volume both tracks converge on, in the {@link Music} 0..1 scale
     */
    public void start(Music previousTrack, Music newTrack, float targetVolume) {
        if (newTrack == null) {
            return;
        }
        if (isActive()) {
            // Restart mid-fade: stop the in-flight previous track, keep the in-flight fade-in
            // track playing (it becomes this fade's "previous", ramping down from current volume).
            if (this.previousTrack != null) {
                this.previousTrack.stop();
            }
            clear();
        }
        this.previousTrack = previousTrack;
        this.newTrack = newTrack;
        this.previousStartVolume = (previousTrack != null && previousTrack.isPlaying())
            ? previousTrack.getVolume() : targetVolume;
        this.targetVolume = targetVolume;
        elapsed = 0f;

        newTrack.setVolume(0f);
        newTrack.play();
    }

    /** Advances the crossfade by {@code deltaTime}; stops the previous track once done. */
    public void update(float deltaTime) {
        if (!isActive()) {
            return;
        }
        elapsed += deltaTime;
        float progress = MathUtils.clamp(elapsed / MUSIC_FADE_DURATION, 0f, 1f);

        if (previousTrack != null) {
            previousTrack.setVolume(previousStartVolume * (1f - progress));
        }
        newTrack.setVolume(targetVolume * progress);

        if (progress >= 1f) {
            if (previousTrack != null) {
                previousTrack.stop();
            }
            newTrack.setVolume(targetVolume);
            clear();
        }
    }

    /** {@code true} while a crossfade is in progress. */
    public boolean isActive() {
        return newTrack != null;
    }

    /** Immediately stops both tracks and clears state (music toggle-off / dispose path). */
    public void cancel() {
        if (previousTrack != null) {
            previousTrack.stop();
        }
        if (newTrack != null) {
            newTrack.stop();
        }
        clear();
    }

    private void clear() {
        previousTrack = null;
        newTrack = null;
        previousStartVolume = 0f;
        targetVolume = 0f;
        elapsed = 0f;
    }
}