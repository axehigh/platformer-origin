package com.axehigh.platformer.util;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@code FeatureFlags}: the wall-climb flag defaults to enabled, flips at
 * runtime, and seeds from / persists to {@link GamePreferences} once a Gdx application exists.
 */
public class FeatureFlagsTest {

    private Preferences preferences;

    @Before
    public void setUp() {
        FeatureFlags.resetForTests();
        Gdx.app = mock(Application.class);
        preferences = mock(Preferences.class);
        when(Gdx.app.getPreferences("axehigh-platformer-settings")).thenReturn(preferences);
    }

    @After
    public void tearDown() {
        FeatureFlags.resetForTests();
        Gdx.app = null;
    }

    @Test
    public void wallClimbingIsEnabledByDefault() {
        Gdx.app = null;

        assertTrue(FeatureFlags.isWallClimbingEnabled());
    }

    @Test
    public void disablingWallClimbingFlipsTheStaticValue() {
        FeatureFlags.setWallClimbingEnabled(false);

        assertFalse(FeatureFlags.isWallClimbingEnabled());
    }

    @Test
    public void persistedValueSeedsTheStaticOnFirstRead() {
        when(preferences.getBoolean("wallClimbEnabled", true)).thenReturn(false);

        assertFalse(FeatureFlags.isWallClimbingEnabled());
    }

    @Test
    public void disablingWallClimbingPersistsTheChoice() {
        FeatureFlags.setWallClimbingEnabled(false);

        verify(preferences).putBoolean("wallClimbEnabled", false);
        verify(preferences).flush();
    }
}
