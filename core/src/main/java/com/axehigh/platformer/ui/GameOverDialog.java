package com.axehigh.platformer.ui;

import com.axehigh.platformer.audio.AudioManager;
import com.axehigh.platformer.map.SaveData;
import com.axehigh.platformer.util.SaveManager;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Game-over dialog: offers a Continue (consuming one saved try) when tries remain, plus exit
 * to the main menu. The continue/exit actions themselves are screen-owned and reached through
 * {@link Listener}.
 */
public class GameOverDialog extends Dialog {

    public interface Listener {
        /** Reloads the current level with the player revived (health restored, not dead). */
        void onContinue();

        void onExit();
    }

    public GameOverDialog(Skin skin, Listener listener) {
        super("Game Over", skin);
        getTitleLabel().setFontScale(FontScale);
        Label deathLabel = new Label("You died!", skin);
        deathLabel.setFontScale(FontScale);
        text(deathLabel);
        getContentTable().row();

        SaveData currentSave = SaveManager.hasSave() ? SaveManager.load() : new SaveData();

        Label triesLabel = new Label("Tries remaining: " + currentSave.triesRemaining, skin);
        triesLabel.setFontScale(FontScale);
        getContentTable().add(triesLabel).padTop(5f).row();

        if (currentSave.triesRemaining > 0) {
            TextButton continueButton = new TextButton("Continue (uses 1 try)", skin);
            continueButton.getLabel().setFontScale(FontScale);
            continueButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    AudioManager.get().playClick();
                    currentSave.triesRemaining--;
                    SaveManager.save(currentSave);
                    listener.onContinue();
                    hide();
                }
            });
            button(continueButton);
        }

        TextButton exitButton = new TextButton("Exit to Main Menu", skin);
        exitButton.getLabel().setFontScale(FontScale);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                AudioManager.get().playClick();
                listener.onExit();
            }
        });
        button(exitButton);
    }
}
