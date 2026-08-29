package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import static com.axehigh.platformer.GameConstants.FontScale;

/**
 * Credits screen displaying development team and assets info, following the unified MenuScreen layout pattern.
 */
public class CreditsScreen extends MenuScreen {

    private static final float ELEMENT_PAD = 48f;

    public CreditsScreen(Game game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();
        AudioManager.get().playMenuMusic();

        Table content = createMenuRoot();
        addMenuTitle(content, "Credits");

        Table creditsContent = new Table();
        creditsContent.center();

        addCreditLine(creditsContent, "ORIGIN: 2D PIXEL-ART PLATFORMER", FontScale * 1.2f, Color.GOLD);
        creditsContent.row();

        addCreditLine(creditsContent, "Lead Game Developer", FontScale, Color.LIGHT_GRAY);
        creditsContent.row();
        addCreditLine(creditsContent, "AxeHigh Games", FontScale * 1.1f, Color.WHITE);
        creditsContent.row().padBottom(ELEMENT_PAD);

        addCreditLine(creditsContent, "Engine & Framework", FontScale, Color.LIGHT_GRAY);
        creditsContent.row();
        addCreditLine(creditsContent, "libGDX & Ashley ECS", FontScale * 1.1f, Color.WHITE);
        creditsContent.row().padBottom(ELEMENT_PAD);

        addCreditLine(creditsContent, "Art & Music", FontScale, Color.LIGHT_GRAY);
        creditsContent.row();
        addCreditLine(creditsContent, "Medieval Dungeon Pixel Art Pack", FontScale * 1.1f, Color.WHITE);
        creditsContent.row().padBottom(ELEMENT_PAD);

        content.add(creditsContent).colspan(2).expand().center().padBottom(30f).row();

        addBackButton(content, () -> changeScreen(new MainMenuScreen(game)));
    }

    private void addCreditLine(Table table, String text, float fontScale, Color color) {
        Label label = new Label(text, skin);
        label.setFontScale(fontScale);
        label.setColor(color);
        table.add(label).padBottom(12f).row();
    }
}
