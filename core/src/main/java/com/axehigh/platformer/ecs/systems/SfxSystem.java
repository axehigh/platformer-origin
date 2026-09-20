package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.audio.AudioManager;
import com.badlogic.ashley.core.EntitySystem;

/**
 * ECS entry point for one-shot sound effects during gameplay (coin pickup chirp, secret-wall
 * break). UI-level clicks (menus, dialogs) go straight to {@link AudioManager#playClick()}.
 *
 * <p><b>Placeholder SFX:</b> the swing/shoot/drink/hit/pickup/hurt methods below are wired but currently
 * all play the same placeholder asset ({@code sfx/PowerUp11.mp3} via {@code AudioManager}); swap the
 * matching {@code AudioManager.SFX_*} constant to give an event its real sound.
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

    public void playPotionPickup() {
        audio.playPotionPickup();
    }

    public void playPotionDrink() {
        audio.playPotionDrink();
    }

    public void playSwordSwing() {
        audio.playSwordSwing();
    }

    public void playSwordHit() {
        audio.playSwordHit();
    }

    public void playShoot() {
        audio.playShoot();
    }

    public void playAmmoPickup() {
        audio.playAmmoPickup();
    }

    public void playPlayerHurt() {
        audio.playPlayerHurt();
    }

    public void playPlayerHazard() {
        audio.playPlayerHazard();
    }
}
