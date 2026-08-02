package com.axehigh.platformer.util;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@code GamePreferences}: volume/enabled settings persist through the backing
 * {@link Preferences} store and keep their documented defaults.
 */
public class GamePreferencesTest {

    private Preferences preferences;
    private GamePreferences gamePreferences;

    @Before
    public void setUp() {
        Gdx.app = mock(Application.class);
        preferences = mock(Preferences.class);
        when(Gdx.app.getPreferences("axehigh-platformer-settings")).thenReturn(preferences);
        gamePreferences = new GamePreferences();
    }

    @After
    public void tearDown() {
        Gdx.app = null;
    }

    @Test
    public void musicAndSfxAreEnabledByDefault() {
        when(preferences.getBoolean("musicEnabled", true)).thenReturn(true);
        when(preferences.getBoolean("sfxEnabled", true)).thenReturn(true);

        assertTrue(gamePreferences.isMusicEnabled());
        assertTrue(gamePreferences.isSfxEnabled());
    }

    @Test
    public void wallClimbingIsEnabledByDefault() {
        when(preferences.getBoolean("wallClimbEnabled", true)).thenReturn(true);

        assertTrue(gamePreferences.isWallClimbingEnabled());
    }

    @Test
    public void disablingWallClimbingPersistsAndReloads() {
        gamePreferences.setWallClimbingEnabled(false);
        when(preferences.getBoolean("wallClimbEnabled", true)).thenReturn(false);

        verify(preferences).putBoolean("wallClimbEnabled", false);
        verify(preferences).flush();
        assertFalse(gamePreferences.isWallClimbingEnabled());
    }

    @Test
    public void disablingMusicPersistsAndReloads() {
        gamePreferences.setMusicEnabled(false);
        when(preferences.getBoolean("musicEnabled", true)).thenReturn(false);

        verify(preferences).putBoolean("musicEnabled", false);
        verify(preferences).flush();
        assertFalse(gamePreferences.isMusicEnabled());
    }

    @Test
    public void disablingSfxPersistsAndReloads() {
        gamePreferences.setSfxEnabled(false);
        when(preferences.getBoolean("sfxEnabled", true)).thenReturn(false);

        verify(preferences).putBoolean("sfxEnabled", false);
        verify(preferences).flush();
        assertFalse(gamePreferences.isSfxEnabled());
    }

    @Test
    public void volumesPersist() {
        gamePreferences.setMusicVolume(25f);
        gamePreferences.setSfxVolume(40f);
        when(preferences.getFloat("musicVolume", 100f)).thenReturn(25f);
        when(preferences.getFloat("sfxVolume", 100f)).thenReturn(40f);

        verify(preferences).putFloat("musicVolume", 25f);
        verify(preferences).putFloat("sfxVolume", 40f);
        verify(preferences, org.mockito.Mockito.times(2)).flush();
        assertEquals(25f, gamePreferences.getMusicVolume(), 0.001f);
        assertEquals(40f, gamePreferences.getSfxVolume(), 0.001f);
    }
}
