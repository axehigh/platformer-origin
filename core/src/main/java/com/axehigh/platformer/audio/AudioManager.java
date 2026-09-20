package com.axehigh.platformer.audio;

import com.axehigh.platformer.util.GamePreferences;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;

/**
 * App-scoped audio service: owns the music and sound assets, the currently playing music track,
 * and the volume/enabled state persisted via {@link GamePreferences}. Screens reach it through
 * {@link #get()}; the in-game ECS routes playback through this class via {@code MusicSystem} /
 * {@code SfxSystem}.
 *
 * <p>Track changes crossfade (no silent gap) through {@link MusicFadeController} over
 * {@link MusicFadeController#MUSIC_FADE_DURATION}: the new track ramps up while the old ramps
 * down, then the old stops. The crossfade is frame-driven — call {@link #update(float)} once per
 * frame (see {@code BaseScreen.render}).
 */
public class AudioManager {

    private static AudioManager instance;

    private final AssetManager assetManager = new AssetManager();
    private final GamePreferences preferences = new GamePreferences();
    private final MusicFadeController musicFade = new MusicFadeController();

    public static final String GAME_OVER_MUSIC = "music/Game-Menu_Looping.mp3";
    public static final String MUSIC_MENU = "music/Tower-Defense_Looping.mp3";

    public static final String MUSIC_GAME = "music/Scary-Things-Ahead.mp3";
    public static final String MUSIC_GAME_2 = "music/Sewer-Monsters-Town-Hall-Meeting_Looping.mp3";


    public static final String SFX_COIN = "sfx/Creepy1.mp3";
    public static final String SFX_CLICK = "sfx/Clank_8.mp3";
    public static final String SFX_POWERUP_11 = "sfx/PowerUp11.mp3";
    public static final String SFX_POWERUP_29 = "sfx/PowerUp29.mp3";

    public static final String SFX_WALL_BREAK = "sfx/Explosion1.mp3";

    // Placeholder SFX: every gameplay event currently maps to the same asset so a real sound can be
    // dropped in per event by changing just the constant (and nothing else — the wiring is done).
    public static final String SFX_POTION_PICKUP = "sfx/PowerUp11.mp3";
    public static final String SFX_POTION_DRINK = "sfx/PowerUp11.mp3";
    public static final String SFX_SWORD_SWING = "sfx/PowerUp11.mp3";
    public static final String SFX_SWORD_HIT = "sfx/PowerUp11.mp3";
    public static final String SFX_SHOOT = "sfx/PowerUp11.mp3";
    public static final String SFX_AMMO_PICKUP = "sfx/PowerUp11.mp3";
    public static final String SFX_PLAYER_HURT = "sfx/PowerUp11.mp3";
    public static final String SFX_PLAYER_HAZARD = "sfx/PowerUp11.mp3";

    private Music menuMusic;
    private Music gameMusic;
    private Music gameMusic2;
    private Music gameOverMusic;
    private Sound coinSound;
    private Sound clickSound;
    private Sound wallBreakSound;
    private Music currentMusic;

    // Placeholder SFX fields — same asset until a real sound is assigned per constant above.
    private Sound potionPickupSound;
    private Sound potionDrinkSound;
    private Sound swordSwingSound;
    private Sound swordHitSound;
    private Sound shootSound;
    private Sound ammoPickupSound;
    private Sound playerHurtSound;
    private Sound playerHazardSound;

    private AudioManager() {
        assetManager.load(MUSIC_MENU, Music.class);
        assetManager.load(MUSIC_GAME, Music.class);
        assetManager.load(MUSIC_GAME_2, Music.class);
        assetManager.load(GAME_OVER_MUSIC, Music.class);
        assetManager.load(SFX_COIN, Sound.class);
        assetManager.load(SFX_CLICK, Sound.class);
        assetManager.load(SFX_WALL_BREAK, Sound.class);
        assetManager.load(SFX_POTION_PICKUP, Sound.class);
        assetManager.load(SFX_POTION_DRINK, Sound.class);
        assetManager.load(SFX_SWORD_SWING, Sound.class);
        assetManager.load(SFX_SWORD_HIT, Sound.class);
        assetManager.load(SFX_SHOOT, Sound.class);
        assetManager.load(SFX_AMMO_PICKUP, Sound.class);
        assetManager.load(SFX_PLAYER_HURT, Sound.class);
        assetManager.load(SFX_PLAYER_HAZARD, Sound.class);
        assetManager.finishLoading();

        menuMusic = assetManager.get(MUSIC_MENU, Music.class);
        gameMusic = assetManager.get(MUSIC_GAME, Music.class);
        gameMusic2 = assetManager.get(MUSIC_GAME_2, Music.class);
        gameOverMusic = assetManager.get(GAME_OVER_MUSIC, Music.class);
        menuMusic.setLooping(true);
        gameMusic.setLooping(true);
        gameMusic2.setLooping(true);
        gameOverMusic.setLooping(true);
        coinSound = assetManager.get(SFX_COIN, Sound.class);
        clickSound = assetManager.get(SFX_CLICK, Sound.class);
        wallBreakSound = assetManager.get(SFX_WALL_BREAK, Sound.class);
        potionPickupSound = assetManager.get(SFX_POTION_PICKUP, Sound.class);
        potionDrinkSound = assetManager.get(SFX_POTION_DRINK, Sound.class);
        swordSwingSound = assetManager.get(SFX_SWORD_SWING, Sound.class);
        swordHitSound = assetManager.get(SFX_SWORD_HIT, Sound.class);
        shootSound = assetManager.get(SFX_SHOOT, Sound.class);
        ammoPickupSound = assetManager.get(SFX_AMMO_PICKUP, Sound.class);
        playerHurtSound = assetManager.get(SFX_PLAYER_HURT, Sound.class);
        playerHazardSound = assetManager.get(SFX_PLAYER_HAZARD, Sound.class);
    }

    public static AudioManager get() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    /**
     * Replaces the singleton. Test seam used by headless UI tests to install a
     * lightweight stand-in (the real constructor synchronously loads audio assets and spins
     * forever when no real files/audio backend exist).
     */
    public static void setInstance(AudioManager manager) {
        instance = manager;
    }

    /**
     * Advances the music crossfade ({@code MusicFadeController}) by {@code deltaTime}. Call once
     * per frame — {@code BaseScreen.render} drives it for every screen.
     */
    public void update(float deltaTime) {
        musicFade.update(deltaTime);
    }

    public void playMenuMusic() {
        switchMusic(menuMusic);
    }

    public void playGameOverMusic() {
        switchMusic(gameOverMusic);
    }

    /**
     * Starts a random in-game track: one of {@link #MUSIC_GAME} or {@link #MUSIC_GAME_2},
     * picked per call (per game session, since {@code MusicSystem} calls this once per engine).
     */
    public void playGameMusic() {
        switchMusic(MathUtils.randomBoolean() ? gameMusic : gameMusic2);
    }

    public void stopMusic() {
        musicFade.cancel();
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic = null;
        }
    }

    public void playCoin() {
        playSfx(coinSound);
    }

    public void playClick() {
        playSfx(clickSound);
    }

    public void playWallBreak() {
        playSfx(wallBreakSound);
    }

    public void playPotionPickup() {
        playSfx(potionPickupSound);
    }

    public void playPotionDrink() {
        playSfx(potionDrinkSound);
    }

    public void playSwordSwing() {
        playSfx(swordSwingSound);
    }

    public void playSwordHit() {
        playSfx(swordHitSound);
    }

    public void playShoot() {
        playSfx(shootSound);
    }

    public void playAmmoPickup() {
        playSfx(ammoPickupSound);
    }

    public void playPlayerHurt() {
        playSfx(playerHurtSound);
    }

    public void playPlayerHazard() {
        playSfx(playerHazardSound);
    }

    public boolean isMusicEnabled() {
        return preferences.isMusicEnabled();
    }

    public void setMusicEnabled(boolean musicEnabled) {
        preferences.setMusicEnabled(musicEnabled);
        if (musicEnabled) {
            if (currentMusic != null) {
                currentMusic.play();
            }
        } else {
            musicFade.cancel();
            if (currentMusic != null) {
                currentMusic.pause();
            }
        }
    }

    public boolean isSfxEnabled() {
        return preferences.isSfxEnabled();
    }

    public void setSfxEnabled(boolean sfxEnabled) {
        preferences.setSfxEnabled(sfxEnabled);
    }

    public void setMusicVolume(float musicVolume) {
        preferences.setMusicVolume(musicVolume);
        if (currentMusic != null) {
            currentMusic.setVolume(musicVolume / 100f);
        }
    }

    public void setSfxVolume(float sfxVolume) {
        preferences.setSfxVolume(sfxVolume);
    }

    public void dispose() {
        stopMusic();
        if (assetManager != null) {
            assetManager.dispose();
        }
        instance = null;
    }

    private void switchMusic(Music music) {
        if (music == null) {
            return;
        }
        if (currentMusic == music) {
            return;
        }
        if (preferences.isMusicEnabled()) {
            // Crossfade over currentMusic → music: new track ramps up, old ramps down and stops.
            musicFade.start(currentMusic, music, preferences.getMusicVolume() / 100f);
        } else {
            // Music disabled: skip the fade, stop the old track, don't play the new one.
            musicFade.cancel();
            if (currentMusic != null) {
                currentMusic.stop();
            }
        }
        currentMusic = music;
    }

    private void playSfx(Sound sound) {
        if (sound == null) {
            return;
        }
        if (preferences.isSfxEnabled()) {
            sound.play(preferences.getSfxVolume() / 100f);
        }
    }
}
