package com.axehigh.platformer.util;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Headless tests for {@code FeatureFlags}: the wall-climb flag defaults to enabled, flips at
 * runtime, and seeds from / persists to {@link GamePreferences} once a Gdx application exists.
 */
public class FeatureFlagsTest {

    private Preferences preferences;

    @Before
    public void setUp() {
        com.axehigh.platformer.util.FeatureFlags.resetForTests();
        Gdx.app = mock(Application.class);
        preferences = mock(Preferences.class);
        when(Gdx.app.getPreferences("axehigh-platformer-settings")).thenReturn(preferences);
    }

    @After
    public void tearDown() {
        com.axehigh.platformer.util.FeatureFlags.resetForTests();
        Gdx.app = null;
    }

    @Test
    public void wallClimbingIsEnabledByDefault() {
        Gdx.app = null;

        assertTrue(com.axehigh.platformer.util.FeatureFlags.isWallClimbingEnabled());
    }

    @Test
    public void vignettesAreEnabledByDefault() {
        Gdx.app = null;

        assertTrue(com.axehigh.platformer.util.FeatureFlags.isVignettePlayerCentricEnabled());
        assertTrue(com.axehigh.platformer.util.FeatureFlags.isVignetteCinematicEnabled());
    }

    @Test
    public void disablingWallClimbingFlipsTheStaticValue() {
        com.axehigh.platformer.util.FeatureFlags.setWallClimbingEnabled(false);

        assertFalse(com.axehigh.platformer.util.FeatureFlags.isWallClimbingEnabled());
    }

    @Test
    public void togglingVignettesFlipsTheStaticValues() {
        com.axehigh.platformer.util.FeatureFlags.setVignettePlayerCentricEnabled(false);
        com.axehigh.platformer.util.FeatureFlags.setVignetteCinematicEnabled(false);

        assertFalse(com.axehigh.platformer.util.FeatureFlags.isVignettePlayerCentricEnabled());
        assertFalse(com.axehigh.platformer.util.FeatureFlags.isVignetteCinematicEnabled());
    }

    @Test
    public void persistedValueSeedsTheStaticOnFirstRead() {
        when(preferences.getBoolean("wallClimbEnabled", true)).thenReturn(false);

        assertFalse(com.axehigh.platformer.util.FeatureFlags.isWallClimbingEnabled());
    }

    @Test
    public void disablingWallClimbingPersistsTheChoice() {
        com.axehigh.platformer.util.FeatureFlags.setWallClimbingEnabled(false);

        verify(preferences).putBoolean("wallClimbEnabled", false);
        verify(preferences).flush();
    }
}
