package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectMap;

/** Holds per-state animations (Idle, Running, Jumping, Attacking, ...) and playback timing. */
public class AnimationComponent implements Component {
    public enum State {
        IDLE, RUNNING, JUMPING, DOUBLE_JUMPING, WALL_CLIMBING, ATTACKING
    }

    public final ObjectMap<State, Animation<TextureRegion>> animations = new ObjectMap<>();
    public State currentState = State.IDLE;
    public State previousState = State.IDLE;
    public float stateTime = 0f;
}
