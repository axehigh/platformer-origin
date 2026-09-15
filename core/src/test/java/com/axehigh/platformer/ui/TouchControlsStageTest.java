package com.axehigh.platformer.ui;

import com.axehigh.platformer.ecs.systems.PlayerInputSystem;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.IntBuffer;

import static com.badlogic.gdx.graphics.GL20.GL_COMPILE_STATUS;
import static com.badlogic.gdx.graphics.GL20.GL_LINK_STATUS;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Headless tests for {@link TouchControlsStage#setHighlightedControl(String, float)} — the
 * tutorial/touch-highlight dispatch that replaced greedy single-letter {@code contains()} matching
 * with exact {@code equals()} matching (regression: "attack"/"dagger"/"bag" used to mis-highlight
 * the JUMP button because they contain the letter "a").
 *
 * <p>{@link TouchControlsStage} extends {@link com.badlogic.gdx.scenes.scene2d.Stage}, whose
 * super constructor spins up a real {@link com.badlogic.gdx.graphics.g2d.SpriteBatch}; that path
 * reads {@code Gdx.graphics} and compiles a default shader via {@code Gdx.gl20}, so those statics
 * are mocked (verified on libGDX 1.14.2: with a mock GL20, {@code glCreateShader} returns 0,
 * shader compilation silently fails without throwing, and construction succeeds headless).
 *
 * <p>Note: libGDX 1.14.2 has no {@code NamedDrawable} in its core jar, so the test carries its own
 * minimal named drawable for the bag icon. {@code Skin.getDrawable} returns registered drawables
 * unwrapped, so buttons are identified by object identity against the exact instances this test
 * registers (plus the bag icon instance it passes into the constructor).
 */
public class TouchControlsStageTest {

    static {
        // SpriteBatch construction allocates its vertex/index buffers through
        // BufferUtils.newDisposableByteBuffer (a JNI native), so the desktop natives must be loaded.
        GdxNativesLoader.load();
    }

    private static final float EPSILON = 0.001f;

    /** Minimal named drawable stand-in used for the bag (inventory) icon. */
    private static class NamedDrawable extends BaseDrawable {
        final String name;

        NamedDrawable(String name) {
            this.name = name;
        }
    }

    private Skin skin;
    private ObjectMap<Drawable, String> drawableNames;
    private AssetManager assetManager;
    private TouchControlsStage stage;
    private Array<TouchButton> buttons;

@Before
    public void setUp() {
        // Gdx.files is needed by Scene2D's Cell.defaults() singleton — the guard
        // "files == null || files != Gdx.files" recurses endlessly when Gdx.files is null because
        // the Cell constructor re-enters defaults() before the outer assignment completes. A mock
        // (non-null, identity-stable) breaks the cycle.
        Gdx.files = mock(com.badlogic.gdx.Files.class);
        // SpriteBatch (created by Stage's super ctor) reads Gdx.graphics and compiles its default
        // shader through Gdx.gl20. The GL mock makes glCreateShader/glCreateProgram return non-zero
        // handles and reports successful compile+link into the IntBuffers, so the ShaderProgram
        // "compiles" and Stage construction succeeds headless.
        Gdx.app = mock(Application.class);
        Gdx.graphics = mock(Graphics.class);
        when(Gdx.graphics.getWidth()).thenReturn(480);
        when(Gdx.graphics.getHeight()).thenReturn(272);
        Gdx.gl20 = mock(GL20.class);
        Gdx.gl = Gdx.gl20;
        when(Gdx.gl20.glCreateShader(anyInt())).thenReturn(1);
        when(Gdx.gl20.glCreateProgram()).thenReturn(1);
        when(Gdx.gl20.glGenBuffer()).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<IntBuffer>getArgument(2).put(0, GL20.GL_TRUE);
            return 0;
        }).when(Gdx.gl20).glGetShaderiv(anyInt(), eq(GL_COMPILE_STATUS), any(IntBuffer.class));
        doAnswer(invocation -> {
            invocation.<IntBuffer>getArgument(2).put(0, GL20.GL_TRUE);
            return 0;
        }).when(Gdx.gl20).glGetProgramiv(anyInt(), eq(GL_LINK_STATUS), any(IntBuffer.class));

        skin = new Skin();
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.up = new BaseDrawable();
        style.down = new BaseDrawable();
        style.over = new BaseDrawable();
        skin.add("gameplay", style);

        drawableNames = new ObjectMap<>();
        // The 8 drawables the TouchButton constructors look up by name. Skin keys resources by the
        // registered type class, and getDrawable() looks up under Drawable.class, so the drawables
        // must be added with the explicit Drawable.class type to be retrievable unwrapped.
        for (String name : new String[]{"left", "right", "potion", "daggers", "sword", "jump", "door", "down"}) {
            BaseDrawable drawable = new BaseDrawable();
            skin.add(name, drawable, Drawable.class);
            drawableNames.put(drawable, name);
        }

        assetManager = mock(AssetManager.class);
        PlayerInputSystem inputSystem = new PlayerInputSystem(assetManager);
        NamedDrawable bagIcon = new NamedDrawable("bag");
        stage = new TouchControlsStage(mock(Viewport.class), skin, inputSystem, bagIcon, () -> {
        });
        buttons = findButtons(stage.getRoot());
    }

    @After
    public void tearDown() {
        Gdx.app = null;
        Gdx.files = null;
        Gdx.graphics = null;
        Gdx.gl20 = null;
        Gdx.gl = null;
    }

    @Test
    public void setHighlightedControl_attackHighlightsSwordNotJump() {
        stage.setHighlightedControl("attack", 0f);

        TouchButton sword = findButton("sword");
        TouchButton jump = findButton("jump");
        assertTrue("attack should highlight sword", isHighlighted(sword));
        assertFalse("attack must NOT highlight jump (regression)", isHighlighted(jump));
        assertOnlyHighlighted("sword");
    }

    @Test
    public void setHighlightedControl_daggerHighlightsDaggersNotJump() {
        stage.setHighlightedControl("dagger", 0f);

        TouchButton daggers = findButton("daggers");
        TouchButton jump = findButton("jump");
        assertTrue("dagger should highlight daggers", isHighlighted(daggers));
        assertFalse("dagger must NOT highlight jump (regression)", isHighlighted(jump));
        assertOnlyHighlighted("daggers");
    }

    @Test
    public void setHighlightedControl_bagNameVariantsHighlightBagNotJump() {
        for (String variant : new String[]{"bag", "inventory", "potion"}) {
            stage.setHighlightedControl(variant, 0f);

            assertTrue(variant + " should highlight bag", isHighlighted(findButton("bag")));
            assertFalse(variant + " must NOT highlight jump (regression)", isHighlighted(findButton("jump")));
            assertOnlyHighlighted("bag");
        }
    }

    @Test
    public void setHighlightedControl_jumpNameVariantsHighlightJump() {
        for (String variant : new String[]{"jump", "a", "j"}) {
            stage.setHighlightedControl(variant, 0f);

            assertTrue(variant + " should highlight jump", isHighlighted(findButton("jump")));
            assertOnlyHighlighted("jump");
        }
    }

    @Test
    public void setHighlightedControl_leftRightHighlightDpadButtons() {
        stage.setHighlightedControl("left", 0f);
        assertTrue("left should highlight the left D-pad button", isHighlighted(findButton("left")));
        assertOnlyHighlighted("left");

        stage.setHighlightedControl("right", 0f);
        assertTrue("right should highlight the right D-pad button", isHighlighted(findButton("right")));
        assertOnlyHighlighted("right");
    }

    @Test
    public void setHighlightedControl_emptyAndNullResetAllButtonsToWhite() {
        stage.setHighlightedControl("jump", 0f);
        stage.setHighlightedControl("", 0f);
        assertAllButtonsWhite();

        stage.setHighlightedControl("bag", 0f);
        stage.setHighlightedControl(null, 0f);
        assertAllButtonsWhite();
    }

    @Test
    public void setHighlightedControl_unknownStringResetsAllButtonsToWhite() {
        stage.setHighlightedControl("jump", 0f);
        stage.setHighlightedControl("frobnicate", 0f);

        assertAllButtonsWhite();
    }

    @Test
    public void setHighlightedControl_resetAfterHighlightTurnsJumpBackToWhite() {
        stage.setHighlightedControl("jump", 0f);
        assertTrue("jump should be highlighted before reset", isHighlighted(findButton("jump")));

        stage.setHighlightedControl("", 0f);

        assertFalse("jump should return to white after reset", isHighlighted(findButton("jump")));
        assertAllButtonsWhite();
    }

    @Test
    public void setHighlightedControl_timerZeroAppliesSymmetricHighlightColor() {
        // At timer=0: pulse = 0.75 + 0.25*sin(0) = 0.75 → setHighlightColor(0.75, 0.75, 0.2).
        // The highlight signature is b==0.2 (gold) on the visible Image child.
        stage.setHighlightedControl("jump", 0f);
        TouchButton jump = findButton("jump");
        assertEquals(0.2f, jump.getImage().getColor().b, 0.0001f);
        assertEquals(0.75f, jump.getImage().getColor().r, 0.0001f);
        assertEquals(0.75f, jump.getImage().getColor().g, 0.0001f);
        assertTrue("button should be highlighted", isHighlighted(jump));
    }

    @Test
    public void setHighlightedControl_pulseVariesWithTimer() {
        // Use timer=0.5 → sin(4.0)≈-0.757 → pulse≈0.5608 (dim gold).
        // This proves the timer parameter actually reaches Math.sin rather than being ignored.
        stage.setHighlightedControl("jump", 0.5f);
        float expectedPulse = 0.75f + 0.25f * (float) Math.sin(0.5f * 8.0);
        TouchButton jump = findButton("jump");
        assertEquals(expectedPulse, jump.getImage().getColor().r, EPSILON);
        assertEquals(expectedPulse, jump.getImage().getColor().g, EPSILON);
        assertEquals(0.2f, jump.getImage().getColor().b, 0.0001f);
        assertTrue(isHighlighted(jump));
    }

    @Test
    public void iconNameFor_jumpVariantsReturnsJumpDrawable() {
        assertEquals("jump", TouchControlsStage.iconNameFor("jump"));
        assertEquals("jump", TouchControlsStage.iconNameFor("A"));
        assertEquals("jump", TouchControlsStage.iconNameFor("J"));
        assertEquals("jump", TouchControlsStage.iconNameFor("  jump "));
    }

    @Test
    public void iconNameFor_attackVariantsReturnsSwordDrawable() {
        assertEquals("sword", TouchControlsStage.iconNameFor("attack"));
        assertEquals("sword", TouchControlsStage.iconNameFor("sword"));
        assertEquals("sword", TouchControlsStage.iconNameFor("B"));
        assertEquals("sword", TouchControlsStage.iconNameFor("melee"));
    }

    @Test
    public void iconNameFor_specialVariantsReturnsDaggersDrawable() {
        assertEquals("daggers", TouchControlsStage.iconNameFor("special"));
        assertEquals("daggers", TouchControlsStage.iconNameFor("ranged"));
        assertEquals("daggers", TouchControlsStage.iconNameFor("Y"));
        assertEquals("daggers", TouchControlsStage.iconNameFor("dagger"));
        assertEquals("daggers", TouchControlsStage.iconNameFor("throw"));
    }

    @Test
    public void iconNameFor_inventoryVariantsReturnsPotionDrawable() {
        assertEquals("potion", TouchControlsStage.iconNameFor("inventory"));
        assertEquals("potion", TouchControlsStage.iconNameFor("bag"));
        assertEquals("potion", TouchControlsStage.iconNameFor("potion"));
    }

    @Test
    public void iconNameFor_leftRightReturnsDpadDrawables() {
        assertEquals("left", TouchControlsStage.iconNameFor("left"));
        assertEquals("right", TouchControlsStage.iconNameFor("right"));
    }

    @Test
    public void iconNameFor_unknownReturnsNull() {
        assertNull(TouchControlsStage.iconNameFor("frobnicate"));
        assertNull(TouchControlsStage.iconNameFor(""));
        assertNull(TouchControlsStage.iconNameFor(null));
    }

    @Test
    public void setHighlightedControl_boostsAlphaWhileActive() {
        stage.setAlpha(0.4f);
        stage.setHighlightedControl("jump", 0.5f);
        assertEquals("root alpha should rise to solid while a highlight is active",
            0.85f, stage.getAlpha(), 0.0001f);
    }

    @Test
    public void setHighlightedControl_clearingRestoresBaseAlpha() {
        stage.setAlpha(0.4f);
        stage.setHighlightedControl("jump", 0.5f);
        stage.setHighlightedControl("", 0f);
        assertEquals("root alpha should return to base once the highlight clears",
            0.4f, stage.getAlpha(), 0.0001f);

        stage.setAlpha(0.25f);
        stage.setHighlightedControl("sword", 0.5f);
        stage.setHighlightedControl(null, 0f);
        assertEquals("root alpha should restore the updated base alpha", 0.25f, stage.getAlpha(), 0.0001f);
    }

    // --- helpers -------------------------------------------------------------------------------

    private Array<TouchButton> findButtons(Group group) {
        // Index-based: libGDX Array's iterator cannot be nested/re-entered, and findButton() below
        // re-iterates the same Array from within these loops.
        Array<TouchButton> result = new Array<>();
        Array<Actor> children = group.getChildren();
        for (int i = 0; i < children.size; i++) {
            Actor actor = children.get(i);
            if (actor instanceof TouchButton) {
                result.add((TouchButton) actor);
            }
            if (actor instanceof Group) {
                result.addAll(findButtons((Group) actor));
            }
        }
        return result;
    }

    private String buttonName(TouchButton button) {
        Drawable up = button.getStyle().imageUp;
        if (up instanceof NamedDrawable) {
            return ((NamedDrawable) up).name;
        }
        String name = drawableNames.get(up);
        return name;
    }

    private TouchButton findButton(String name) {
        for (int i = 0; i < buttons.size; i++) {
            TouchButton button = buttons.get(i);
            if (name.equals(buttonName(button))) {
                return button;
            }
        }
        fail("no touch button with drawable name '" + name + "'");
        return null;
    }

    private boolean isHighlighted(TouchButton button) {
        // Highlight tint is applied to the visible Image child (b==0.2); reset buttons are white
        // (b==1.0).
        return button.getImage().getColor().b < 0.5f;
    }

    private void assertOnlyHighlighted(String... names) {
        for (int i = 0; i < buttons.size; i++) {
            TouchButton button = buttons.get(i);
            boolean expect = false;
            for (String name : names) {
                expect |= findButton(name) == button;
            }
            assertEquals("button '" + buttonName(button) + "'", expect, isHighlighted(button));
        }
    }

    private void assertAllButtonsWhite() {
        for (TouchButton button : buttons) {
            assertEquals("button '" + buttonName(button) + "' should be white",
                1f, button.getImage().getColor().b, 0.0001f);
        }
    }
}
