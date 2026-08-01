package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.audio.AudioManager;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;

/**
 * Starts the in-game music track once per engine lifetime. The {@link Music} keeps playing on its
 * own through pauses, game-over, and level swaps, so this system only needs to kick it off.
 */
public class MusicSystem extends EntitySystem {
    private final AudioManager audio;
    private boolean started = false;

    public MusicSystem(AudioManager audio, int priority) {
        super(priority);
        this.audio = audio;
    }

    @Override
    public void update(float deltaTime) {
        if (!started) {
            started = true;
            audio.playGameMusic();
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        started = false;
    }
}
