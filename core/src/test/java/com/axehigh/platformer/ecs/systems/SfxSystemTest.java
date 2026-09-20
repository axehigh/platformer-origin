package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.audio.AudioManager;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Headless tests for {@code SfxSystem}: each one-shot SFX facade method is delegated to the shared
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

    @Test
    public void playPotionPickupDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playPotionPickup();

        verify(audio).playPotionPickup();
    }

    @Test
    public void playPotionDrinkDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playPotionDrink();

        verify(audio).playPotionDrink();
    }

    @Test
    public void playSwordSwingDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playSwordSwing();

        verify(audio).playSwordSwing();
    }

    @Test
    public void playSwordHitDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playSwordHit();

        verify(audio).playSwordHit();
    }

    @Test
    public void playShootDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playShoot();

        verify(audio).playShoot();
    }

    @Test
    public void playAmmoPickupDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playAmmoPickup();

        verify(audio).playAmmoPickup();
    }

    @Test
    public void playPlayerHurtDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playPlayerHurt();

        verify(audio).playPlayerHurt();
    }

    @Test
    public void playPlayerHazardDelegatesToAudioManager() {
        AudioManager audio = mock(AudioManager.class);
        SfxSystem system = new SfxSystem(audio, 0);

        system.playPlayerHazard();

        verify(audio).playPlayerHazard();
    }
}
