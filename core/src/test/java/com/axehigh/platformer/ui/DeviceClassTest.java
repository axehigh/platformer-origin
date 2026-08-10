package com.axehigh.platformer.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@code DeviceClass}: the static simulation override drives both touch
 * detection and the shipped default layout, short-circuiting before the real platform is queried.
 */
public class DeviceClassTest {

    @Before
    public void setUp() {
        Gdx.app = mock(Application.class);
    }

    @After
    public void tearDown() {
        DeviceClass.setSimulated(null);
        Gdx.app = null;
    }

    @Test
    public void noSimulationFallsBackToRealPlatformDetection() {
        when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Android);
        assertTrue(LayoutMode.isTouchDevice());

        when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Desktop);
        assertFalse(LayoutMode.isTouchDevice());
    }

    @Test
    public void phoneSimulationEnablesTouchAndDefaultsToBandZoom() {
        DeviceClass.setSimulated(DeviceClass.PHONE);

        assertTrue(DeviceClass.isSimulating());
        assertTrue(LayoutMode.isTouchDevice());
        assertEquals(LayoutMode.BAND_ZOOM, LayoutMode.defaultForDevice());
    }

    @Test
    public void tabletSimulationEnablesTouchAndDefaultsToBand() {
        DeviceClass.setSimulated(DeviceClass.TABLET);

        assertTrue(LayoutMode.isTouchDevice());
        assertEquals(LayoutMode.BAND, LayoutMode.defaultForDevice());
    }

    @Test
    public void desktopSimulationDisablesTouch() {
        DeviceClass.setSimulated(DeviceClass.DESKTOP);

        assertFalse(LayoutMode.isTouchDevice());
    }

    @Test
    public void onlyDesktopReportsNonTouch() {
        assertTrue(DeviceClass.PHONE.isTouch());
        assertTrue(DeviceClass.TABLET.isTouch());
        assertFalse(DeviceClass.DESKTOP.isTouch());
    }

    @Test
    public void nextCyclesInOrderAndWraps() {
        assertEquals(DeviceClass.PHONE, DeviceClass.DESKTOP.next());
        assertEquals(DeviceClass.TABLET, DeviceClass.PHONE.next());
        assertEquals(DeviceClass.DESKTOP, DeviceClass.TABLET.next());
    }

    @Test
    public void clearingSimulationRestoresNull() {
        DeviceClass.setSimulated(DeviceClass.PHONE);
        DeviceClass.setSimulated(null);

        assertFalse(DeviceClass.isSimulating());
        assertNull(DeviceClass.simulated());
    }
}
