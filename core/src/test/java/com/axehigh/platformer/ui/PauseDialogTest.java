package com.axehigh.platformer.ui;

import com.axehigh.platformer.audio.AudioManager;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.Array;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PauseDialogTest {

    private Skin skin;
    private TestListener listener;
    private Preferences preferences;

    private static class TestListener implements com.axehigh.platformer.ui.PauseDialog.Listener {
        boolean resumed = false;
        boolean exited = false;
        boolean screenshot = false;
        boolean touchDebug = false;
        String device = "Auto";
        String layout = "Band";

        @Override
        public void onResume() {
            resumed = true;
        }

        @Override
        public void onScreenshot() {
            screenshot = true;
        }

        @Override
        public boolean isTouchDebugOn() {
            return touchDebug;
        }

        @Override
        public void setTouchDebugOn(boolean on) {
            touchDebug = on;
        }

        @Override
        public String deviceLabel() {
            return device;
        }

        @Override
        public void cycleDevice() {
            device = "Phone";
        }

        @Override
        public String layoutLabel() {
            return layout;
        }

        @Override
        public void cycleLayout() {
            layout = "Standard";
        }

        @Override
        public void onExit() {
            exited = true;
        }

        @Override
        public int getTriesRemaining() {
            return 3;
        }
    }

    @Before
    public void setUp() {
        Gdx.app = mock(Application.class);
        Gdx.files = mock(com.badlogic.gdx.Files.class);
        Gdx.audio = mock(com.badlogic.gdx.Audio.class);
        preferences = mock(Preferences.class);
        when(Gdx.app.getPreferences(Mockito.anyString())).thenReturn(preferences);

        // The real AudioManager constructor loads real audio files via Gdx.files/Gdx.audio and
        // spins forever on these mocks (null handles) until the heap dies. Install a mock stand-in
        // so PauseDialog construction stays headless.
        AudioManager.setInstance(mock(AudioManager.class));

        skin = new Skin();
        BitmapFont.BitmapFontData fontData = new BitmapFont.BitmapFontData();
        Array<com.badlogic.gdx.graphics.g2d.TextureRegion> pageRegions = new Array<>();
        pageRegions.add(new com.badlogic.gdx.graphics.g2d.TextureRegion());
        BitmapFont font = new BitmapFont(fontData, pageRegions, true);
        skin.add("default", font);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = com.badlogic.gdx.graphics.Color.WHITE;
        skin.add("default", labelStyle);

        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOn = new BaseDrawable();
        checkBoxStyle.checkboxOff = new BaseDrawable();
        checkBoxStyle.font = font;
        checkBoxStyle.fontColor = com.badlogic.gdx.graphics.Color.WHITE;
        skin.add("default", checkBoxStyle);

        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.font = font;
        textButtonStyle.fontColor = com.badlogic.gdx.graphics.Color.WHITE;
        skin.add("default", textButtonStyle);

        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = font;
        windowStyle.titleFontColor = com.badlogic.gdx.graphics.Color.BLACK;
        windowStyle.background = new BaseDrawable();
        skin.add("default", windowStyle);

        listener = new TestListener();
    }

    @After
    public void tearDown() {
        AudioManager.setInstance(null);
        Gdx.app = null;
        Gdx.files = null;
        Gdx.audio = null;
    }

    @Test
    public void pauseDialogInitializesWithGameplayTabAndToggles() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        assertNotNull(dialog);
        assertEquals("Paused", dialog.getTitleLabel().getText().toString());

        // Find checkboxes in gameplay content (Music, Sound Effects)
        Array<CheckBox> checkBoxes = new Array<>();
        findCheckBoxes(dialog, checkBoxes);

        assertEquals("Gameplay tab should have 2 audio checkboxes", 2, checkBoxes.size);
    }

    @Test
    public void resumeAndExitButtonsTriggerCallbacks() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        dialog.pack();
        Array<TextButton> textButtons = new Array<>();
        findTextButtons(dialog.getButtonTable(), textButtons);

        TextButton resumeBtn = null;
        TextButton exitBtn = null;
        for (TextButton btn : textButtons) {
            if ("Resume".equals(btn.getText().toString())) {
                resumeBtn = btn;
            } else if ("Menu".equals(btn.getText().toString()) || "Exit".equals(btn.getText().toString())) {
                exitBtn = btn;
            }
        }

        assertNotNull("Resume button should exist in footer", resumeBtn);
        assertNotNull("Menu/Exit button should exist in footer", exitBtn);
        assertEquals("Footer button width should match configured size", 180f, resumeBtn.getWidth(), 0.01f);
        assertEquals("Footer button height should match configured size", 60f, resumeBtn.getHeight(), 0.01f);

        resumeBtn.toggle();
        assertTrue("Resume callback should be fired", listener.resumed);

        exitBtn.toggle();
        assertTrue("Exit callback should be fired", listener.exited);
    }

    @Test
    public void visualTabShowsVisualToggles() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        TextButton visualTab = findTextButton(dialog, "Visual");
        assertNotNull("Visual tab button should exist", visualTab);
        visualTab.toggle();

        Array<CheckBox> checkBoxes = new Array<>();
        findCheckBoxes(dialog, checkBoxes);
        assertEquals("Visual tab should show 5 visual toggles", 5, checkBoxes.size);
    }

    @Test
    public void betaTabShowsFeatureFlagAndDebugToggles() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        TextButton betaTab = findTextButton(dialog, "Beta");
        assertNotNull("Beta tab button should exist", betaTab);
        betaTab.toggle();

        Array<CheckBox> checkBoxes = new Array<>();
        findCheckBoxes(dialog, checkBoxes);
        assertEquals("Beta tab should show 5 feature-flag and debug toggles", 5, checkBoxes.size);
    }

    @Test
    public void deviceTabShowsSimulationButtonsAndCycles() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        TextButton deviceTab = findTextButton(dialog, "Device");
        assertNotNull("Device tab button should exist", deviceTab);
        deviceTab.toggle();

        assertNotNull("Device title label should exist in Device tab", findLabel(dialog, "Device"));
        assertNotNull("Mobile title label should exist in Device tab", findLabel(dialog, "Mobile"));

        TextButton deviceBtn = findTextButton(dialog, "Auto");
        assertNotNull("Device button should exist in Device tab", deviceBtn);
        deviceBtn.toggle();
        assertEquals("Phone", listener.device);
        assertEquals("Phone", deviceBtn.getText().toString());

        TextButton layoutBtn = findTextButton(dialog, "Band");
        assertNotNull("Layout button should exist in Device tab", layoutBtn);
        layoutBtn.toggle();
        assertEquals("Standard", listener.layout);
        assertEquals("Standard", layoutBtn.getText().toString());
    }

    @Test
    public void screenshotButtonHidesDialogAndArmsCapture() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        TextButton screenshotButton = findTextButton(dialog, "Screenshot");
        assertNotNull("Screenshot button should exist in the Gameplay tab", screenshotButton);

        screenshotButton.toggle();

        assertTrue("Screenshot callback should be fired", listener.screenshot);
        assertTrue("Screenshot capture should also close the dialog", listener.resumed);
    }

    @Test
    public void buttonsHavePaddingInsidePanel() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        dialog.setSize(600f, 400f);
        dialog.layout();

        // Button table should have positive bottom padding to keep footer buttons inside panel
        assertTrue("Footer button table should have bottom padding", dialog.getButtonTable().getPadBottom() > 0f);

        // Header table cell in content table should have positive top padding
        com.badlogic.gdx.scenes.scene2d.ui.Cell<?> headerCell = dialog.getContentTable().getCells().first();
        assertNotNull(headerCell);
        assertTrue("Tab header cell should have top padding", headerCell.getPadTop() > 0f);
    }

    @Test
    public void tabHeaderRemainsFixedAtTopAcrossTabs() {
        com.axehigh.platformer.ui.PauseDialog dialog = new com.axehigh.platformer.ui.PauseDialog(skin, listener);
        dialog.setSize(600f, 400f);
        dialog.layout();

        TextButton gameplayTab = findTextButton(dialog, "Gameplay");
        assertNotNull(gameplayTab);
        float gameplayY = gameplayTab.getY();

        TextButton visualTab = findTextButton(dialog, "Visual");
        visualTab.toggle();
        dialog.layout();
        assertEquals("Visual tab button Y position should remain fixed", gameplayY, findTextButton(dialog, "Visual").getY(), 0.01f);

        TextButton betaTab = findTextButton(dialog, "Beta");
        betaTab.toggle();
        dialog.layout();
        assertEquals("Beta tab button Y position should remain fixed", gameplayY, findTextButton(dialog, "Beta").getY(), 0.01f);

        TextButton deviceTab = findTextButton(dialog, "Device");
        deviceTab.toggle();
        dialog.layout();
        assertEquals("Device tab button Y position should remain fixed", gameplayY, findTextButton(dialog, "Device").getY(), 0.01f);
    }

    private com.badlogic.gdx.scenes.scene2d.ui.Label findLabel(com.badlogic.gdx.scenes.scene2d.Group group, String text) {
        for (com.badlogic.gdx.scenes.scene2d.Actor actor : group.getChildren()) {
            if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Label
                && text.equals(((com.badlogic.gdx.scenes.scene2d.ui.Label) actor).getText().toString())) {
                return (com.badlogic.gdx.scenes.scene2d.ui.Label) actor;
            }
            if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group) {
                com.badlogic.gdx.scenes.scene2d.ui.Label found = findLabel((com.badlogic.gdx.scenes.scene2d.Group) actor, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private TextButton findTextButton(com.badlogic.gdx.scenes.scene2d.Group group, String text) {
        for (com.badlogic.gdx.scenes.scene2d.Actor actor : group.getChildren()) {
            if (actor instanceof TextButton && !(actor instanceof CheckBox)
                && text.equals(((TextButton) actor).getText().toString())) {
                return (TextButton) actor;
            }
            if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group) {
                TextButton found = findTextButton((com.badlogic.gdx.scenes.scene2d.Group) actor, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void findCheckBoxes(com.badlogic.gdx.scenes.scene2d.Group group, Array<CheckBox> result) {
        for (com.badlogic.gdx.scenes.scene2d.Actor actor : group.getChildren()) {
            if (actor instanceof CheckBox) {
                result.add((CheckBox) actor);
            } else if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group) {
                findCheckBoxes((com.badlogic.gdx.scenes.scene2d.Group) actor, result);
            }
        }
    }

    private void findTextButtons(com.badlogic.gdx.scenes.scene2d.Group group, Array<TextButton> result) {
        for (com.badlogic.gdx.scenes.scene2d.Actor actor : group.getChildren()) {
            if (actor instanceof TextButton && !(actor instanceof CheckBox)) {
                result.add((TextButton) actor);
            } else if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group) {
                findTextButtons((com.badlogic.gdx.scenes.scene2d.Group) actor, result);
            }
        }
    }
}
