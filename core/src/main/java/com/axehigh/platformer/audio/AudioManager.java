package com.axehigh.platformer.audio;

import com.axehigh.platformer.util.GamePreferences;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

/**
 * App-scoped audio service: owns the music and sound assets, the currently playing music track,
 * and the volume/enabled state persisted via {@link GamePreferences}. Screens reach it through
 * {@link #get()}; the in-game ECS routes playback through this class via {@code MusicSystem} /
 * {@code SfxSystem}.
 */
public class AudioManager {

    public static final String MUSIC_MENU = "music/Game-Menu_Looping.mp3";
    public static final String MUSIC_GAME = "music/Dark-Things.ogg";
    public static final String SFX_COIN = "sfx/Creepy1.mp3";
    public static final String SFX_CLICK = "sfx/Clank_8.mp3";

    private static AudioManager instance;

    private final AssetManager assetManager = new AssetManager();
    private final GamePreferences preferences = new GamePreferences();

    private Music menuMusic;
    private Music gameMusic;
    private Sound coinSound;
    private Sound clickSound;
    private Music currentMusic;

    private AudioManager() {
        assetManager.load(MUSIC_MENU, Music.class);
        assetManager.load(MUSIC_GAME, Music.class);
        assetManager.load(SFX_COIN, Sound.class);
        assetManager.load(SFX_CLICK, Sound.class);
        assetManager.finishLoading();

        menuMusic = assetManager.get(MUSIC_MENU, Music.class);
        gameMusic = assetManager.get(MUSIC_GAME, Music.class);
        menuMusic.setLooping(true);
        gameMusic.setLooping(true);
        coinSound = assetManager.get(SFX_COIN, Sound.class);
        clickSound = assetManager.get(SFX_CLICK, Sound.class);
    }

    public static AudioManager get() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    public void playMenuMusic() {
        switchMusic(menuMusic);
    }

    public void playGameMusic() {
        switchMusic(gameMusic);
    }

    public void stopMusic() {
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

    public boolean isMusicEnabled() {
        return preferences.isMusicEnabled();
    }

    public void setMusicEnabled(boolean musicEnabled) {
        preferences.setMusicEnabled(musicEnabled);
        if (musicEnabled) {
            if (currentMusic != null) {
                currentMusic.play();
            }
        } else if (currentMusic != null) {
            currentMusic.pause();
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
        assetManager.dispose();
        instance = null;
    }

    private void switchMusic(Music music) {
        if (currentMusic == music) {
            return;
        }
        if (currentMusic != null) {
            currentMusic.stop();
        }
        currentMusic = music;
        if (preferences.isMusicEnabled()) {
            currentMusic.setVolume(preferences.getMusicVolume() / 100f);
            currentMusic.play();
        }
    }

    private void playSfx(Sound sound) {
        if (preferences.isSfxEnabled()) {
            sound.play(preferences.getSfxVolume() / 100f);
        }
    }
}
