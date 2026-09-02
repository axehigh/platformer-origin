package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Holds per-state animations (Idle, Running, Jumping, Attacking, ...) and playback timing. */
public class AnimationComponent implements Component, Poolable {
    public enum State {
        IDLE, WALKING, RUNNING, JUMPING, DOUBLE_JUMPING, WALL_CLIMBING, ATTACKING, DEATH, HURT, SPLASHING
    }

    public final ObjectMap<State, Animation<TextureRegion>> animations = new ObjectMap<>();
    public State currentState = State.IDLE;
    public State previousState = State.IDLE;
    public float stateTime = 0f;
    /**
     * Elapsed-time accumulator driving the invulnerability blink (toggling sprite visibility at a
     * fixed frequency) after the HURT clip finishes but while {@code hitInvulnerability} is still
     * active. Reset to 0 whenever the player is not in the blink phase.
     */
    public float blinkTimer = 0f;

    @Override
    public void reset() {
        animations.clear();
        currentState = State.IDLE;
        previousState = State.IDLE;
        stateTime = 0f;
        blinkTimer = 0f;
    }
}
