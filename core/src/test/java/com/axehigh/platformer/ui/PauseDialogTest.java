package com.axehigh.platformer.ui;

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

    private static class TestListener implements PauseDialog.Listener {
        boolean resumed = false;
        boolean exited = false;
        boolean touchDebug = false;
        String device = "Auto";
        String layout = "Band";

        @Override
        public void onResume() {
            resumed = true;
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
        Gdx.app = null;
        Gdx.files = null;
        Gdx.audio = null;
    }

    @Test
    public void pauseDialogInitializesWithGameplayTabAndToggles() {
        PauseDialog dialog = new PauseDialog(skin, listener);
        assertNotNull(dialog);
        assertEquals("Paused", dialog.getTitleLabel().getText().toString());

        // Find checkboxes in content
        Array<CheckBox> checkBoxes = new Array<>();
        findCheckBoxes(dialog, checkBoxes);

        assertTrue("Should have initialized with checkboxes", checkBoxes.size >= 3);
    }

    @Test
    public void resumeAndExitButtonsTriggerCallbacks() {
        PauseDialog dialog = new PauseDialog(skin, listener);
        Array<TextButton> textButtons = new Array<>();
        findTextButtons(dialog.getButtonTable(), textButtons);

        TextButton resumeBtn = null;
        TextButton exitBtn = null;
        for (TextButton btn : textButtons) {
            if ("Resume".equals(btn.getText().toString())) {
                resumeBtn = btn;
            } else if ("Exit".equals(btn.getText().toString())) {
                exitBtn = btn;
            }
        }

        assertNotNull("Resume button should exist in footer", resumeBtn);
        assertNotNull("Exit button should exist in footer", exitBtn);

        resumeBtn.toggle();
        assertTrue("Resume callback should be fired", listener.resumed);

        exitBtn.toggle();
        assertTrue("Exit callback should be fired", listener.exited);
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
