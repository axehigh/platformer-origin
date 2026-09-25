package com.axehigh.platformer.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
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
        com.axehigh.platformer.ui.DeviceClass.setSimulated(null);
        Gdx.app = null;
    }

    @Test
    public void noSimulationFallsBackToRealPlatformDetection() {
        when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Android);
        assertTrue(com.axehigh.platformer.ui.LayoutMode.isTouchDevice());
        assertFalse(com.axehigh.platformer.ui.LayoutMode.isDesktop());

        when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Desktop);
        assertFalse(com.axehigh.platformer.ui.LayoutMode.isTouchDevice());
        assertTrue(com.axehigh.platformer.ui.LayoutMode.isDesktop());

        when(Gdx.app.getType()).thenReturn(Application.ApplicationType.WebGL);
        assertFalse(com.axehigh.platformer.ui.LayoutMode.isTouchDevice());
        assertTrue(com.axehigh.platformer.ui.LayoutMode.isDesktop());
    }

    @Test
    public void realPlatformDetectionDefaultsToBandZoom() {
        assertEquals(com.axehigh.platformer.ui.LayoutMode.BAND_ZOOM, com.axehigh.platformer.ui.LayoutMode.defaultForDevice());
    }

    @Test
    public void phoneSimulationEnablesTouchAndDefaultsToBandZoom() {
        com.axehigh.platformer.ui.DeviceClass.setSimulated(com.axehigh.platformer.ui.DeviceClass.PHONE);

        assertTrue(com.axehigh.platformer.ui.DeviceClass.isSimulating());
        assertTrue(com.axehigh.platformer.ui.LayoutMode.isTouchDevice());
        assertEquals(com.axehigh.platformer.ui.LayoutMode.BAND_ZOOM, com.axehigh.platformer.ui.LayoutMode.defaultForDevice());
    }

    @Test
    public void tabletSimulationEnablesTouchAndDefaultsToBandZoom() {
        com.axehigh.platformer.ui.DeviceClass.setSimulated(com.axehigh.platformer.ui.DeviceClass.TABLET);

        assertTrue(com.axehigh.platformer.ui.LayoutMode.isTouchDevice());
        assertEquals(com.axehigh.platformer.ui.LayoutMode.BAND_ZOOM, com.axehigh.platformer.ui.LayoutMode.defaultForDevice());
    }

    @Test
    public void desktopSimulationDisablesTouchButDefaultsToBandZoom() {
        com.axehigh.platformer.ui.DeviceClass.setSimulated(com.axehigh.platformer.ui.DeviceClass.DESKTOP);

        assertFalse(com.axehigh.platformer.ui.LayoutMode.isTouchDevice());
        assertEquals(com.axehigh.platformer.ui.LayoutMode.BAND_ZOOM, com.axehigh.platformer.ui.LayoutMode.defaultForDevice());
    }

    @Test
    public void onlyDesktopReportsNonTouch() {
        assertTrue(com.axehigh.platformer.ui.DeviceClass.PHONE.isTouch());
        assertTrue(com.axehigh.platformer.ui.DeviceClass.TABLET.isTouch());
        assertFalse(com.axehigh.platformer.ui.DeviceClass.DESKTOP.isTouch());
    }

    @Test
    public void nextCyclesInOrderAndWraps() {
        assertEquals(com.axehigh.platformer.ui.DeviceClass.PHONE, com.axehigh.platformer.ui.DeviceClass.DESKTOP.next());
        assertEquals(com.axehigh.platformer.ui.DeviceClass.TABLET, com.axehigh.platformer.ui.DeviceClass.PHONE.next());
        assertEquals(com.axehigh.platformer.ui.DeviceClass.DESKTOP, com.axehigh.platformer.ui.DeviceClass.TABLET.next());
    }

    @Test
    public void nextWithAutoCyclesThroughNullForAuto() {
        assertEquals(com.axehigh.platformer.ui.DeviceClass.DESKTOP, com.axehigh.platformer.ui.DeviceClass.nextWithAuto(null));
        assertEquals(com.axehigh.platformer.ui.DeviceClass.PHONE, com.axehigh.platformer.ui.DeviceClass.nextWithAuto(com.axehigh.platformer.ui.DeviceClass.DESKTOP));
        assertEquals(com.axehigh.platformer.ui.DeviceClass.TABLET, com.axehigh.platformer.ui.DeviceClass.nextWithAuto(com.axehigh.platformer.ui.DeviceClass.PHONE));
        assertNull(com.axehigh.platformer.ui.DeviceClass.nextWithAuto(com.axehigh.platformer.ui.DeviceClass.TABLET));
    }

    @Test
    public void nextWithAutoWrapsBackToAutoAfterTablet() {
        com.axehigh.platformer.ui.DeviceClass current = null;
        current = com.axehigh.platformer.ui.DeviceClass.nextWithAuto(current);
        current = com.axehigh.platformer.ui.DeviceClass.nextWithAuto(current);
        current = com.axehigh.platformer.ui.DeviceClass.nextWithAuto(current);
        current = com.axehigh.platformer.ui.DeviceClass.nextWithAuto(current);
        assertNull(current);
    }

    @Test
    public void clearingSimulationRestoresNull() {
        com.axehigh.platformer.ui.DeviceClass.setSimulated(com.axehigh.platformer.ui.DeviceClass.PHONE);
        com.axehigh.platformer.ui.DeviceClass.setSimulated(null);

        assertFalse(com.axehigh.platformer.ui.DeviceClass.isSimulating());
        assertNull(com.axehigh.platformer.ui.DeviceClass.simulated());
    }
}
