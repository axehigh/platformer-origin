package com.axehigh.platformer.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@code LayoutPrefs}: the saved device/layout channel returns {@code null}
 * headless or when unset, round-trips through {@code GamePreferences}, and {@code save} persists.
 */
public class LayoutPrefsTest {

    private Preferences preferences;

    @Before
    public void setUp() {
        Gdx.app = mock(Application.class);
        preferences = mock(Preferences.class);
        when(Gdx.app.getPreferences("axehigh-platformer-settings")).thenReturn(preferences);
    }

    @After
    public void tearDown() {
        Gdx.app = null;
    }

    @Test
    public void readsAreNullHeadless() {
        Gdx.app = null;

        assertNull(LayoutPrefs.savedDevice());
        assertNull(LayoutPrefs.savedLayout());
    }

    @Test
    public void nothingSavedReadsNull() {
        when(preferences.getString("deviceClass", "")).thenReturn("");
        when(preferences.getString("layoutMode", "")).thenReturn("");

        assertNull(LayoutPrefs.savedDevice());
        assertNull(LayoutPrefs.savedLayout());
    }

    @Test
    public void savedDeviceAndLayoutRoundTrip() {
        when(preferences.getString("deviceClass", "")).thenReturn("PHONE");
        when(preferences.getString("layoutMode", "")).thenReturn("BAND_ZOOM");

        assertEquals(DeviceClass.PHONE, LayoutPrefs.savedDevice());
        assertEquals(LayoutMode.BAND_ZOOM, LayoutPrefs.savedLayout());
    }

    @Test
    public void savePersistsBothValues() {
        LayoutPrefs.save(DeviceClass.TABLET, LayoutMode.BAND);

        verify(preferences).putString("deviceClass", "TABLET");
        verify(preferences).putString("layoutMode", "BAND");
    }

    @Test
    public void saveAutoClearsDevice() {
        LayoutPrefs.save(null, LayoutMode.BAND_ZOOM);

        verify(preferences).putString("deviceClass", "");
        verify(preferences).putString("layoutMode", "BAND_ZOOM");
    }
}
