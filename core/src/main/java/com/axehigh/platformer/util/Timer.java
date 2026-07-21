package com.axehigh.platformer.util;

/**
 * Reusable countdown-timer helper: encapsulates "count down from X, know when it's done" logic
 * used across cooldowns, attack windows, disappear delays, and invulnerability grace periods.
 */
public class Timer {
    private float remaining = 0f;

    /** Starts (or restarts) the countdown from {@code duration} seconds. */
    public void start(float duration) {
        remaining = duration;
    }

    /** Ticks the countdown down by {@code deltaTime}, clamped at 0; no-op once already done. */
    public void update(float deltaTime) {
        if (remaining > 0f) {
            remaining = Math.max(0f, remaining - deltaTime);
        }
    }

    /** {@code true} while there's still time remaining on the countdown. */
    public boolean isActive() {
        return remaining > 0f;
    }

    /** {@code true} once the countdown has reached 0. */
    public boolean isDone() {
        return remaining <= 0f;
    }

    /** The remaining time in seconds. */
    public float getRemaining() {
        return remaining;
    }

    /** Immediately marks the countdown as done. */
    public void reset() {
        remaining = 0f;
    }
}
