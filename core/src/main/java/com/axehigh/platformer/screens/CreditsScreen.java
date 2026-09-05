package com.axehigh.platformer.screens;

import com.axehigh.platformer.audio.AudioManager;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import static com.axehigh.platformer.GameConstants.*;
import static com.axehigh.platformer.screens.GameConstantText.*;

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

        addCreditLine(creditsContent, ORIGIN_TITLE, TitleFontScale, Color.GOLD);
        creditsContent.row();

        addCreditLine(creditsContent, LEAD_DEV, SmallFontScale, Color.LIGHT_GRAY);
        creditsContent.row();
        addCreditLine(creditsContent, AXEHIGH_GAMES, BodyFontScale, Color.WHITE);
        creditsContent.row().padBottom(ELEMENT_PAD);

        addCreditLine(creditsContent, ENGINE_FRAMEWORK, SmallFontScale, Color.LIGHT_GRAY);
        creditsContent.row();
        addCreditLine(creditsContent, LIBGDX_ASHLEY, BodyFontScale, Color.WHITE);
        creditsContent.row().padBottom(ELEMENT_PAD);

        addCreditLine(creditsContent, ART_MUSIC, SmallFontScale, Color.LIGHT_GRAY);
        creditsContent.row();
        addCreditLine(creditsContent, MEDIEVAL_DUNGEON_PACK, BodyFontScale, Color.WHITE);
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
