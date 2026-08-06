package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.audio.AudioManager;
import com.badlogic.ashley.core.EntitySystem;

/**
 * ECS entry point for one-shot sound effects during gameplay (coin pickup chirp, secret-wall
 * break). UI-level clicks (menus, dialogs) go straight to {@link AudioManager#playClick()}.
 */
public class SfxSystem extends EntitySystem {
    private final AudioManager audio;

    public SfxSystem(AudioManager audio, int priority) {
        super(priority);
        this.audio = audio;
    }

    public void playCoin() {
        audio.playCoin();
    }

    public void playWallBreak() {
        audio.playWallBreak();
    }
}
