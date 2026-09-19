package com.axehigh.platformer.ui;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Guards {@link TutorialHighlight} as the single source of truth for tutorial {@code highlight}
 * keywords by asserting the enum agrees — in both directions — with the duplicate alias→icon table
 * duplicated in {@code resources/docs-ai/map-design-for-tiled.md} §5.8 ("Tutorial Signs"},
 * {@code | `jump`, `a`, `j` | A (jump) | `jump` |} style rows).
 *
 * <p>Pure enum + file parsing: no libGDX runtime, no Mockito, no headless backend bootstrap.
 * ({@link TouchControlsStageTest} needs the {@code Gdx} mocks because {@code Stage}'s constructor
 * spins up a real {@code SpriteBatch}; this test only touches the enum and a markdown file.)
 */
public class TutorialHighlightDocsTest {

    /** The §5.8 heading that anchors the duplicated keyword table. */
    private static final String SECTION_HEADING = "### 5.8 Tutorial Signs";
    private static final String DOC_PATH = "resources/docs-ai/map-design-for-tiled.md";

    /** Frozen snapshot of the enum surface: constant name → exact alias set, in declared order. */
    private static final Map<String, String[]> EXPECTED_DEFINITION = new LinkedHashMap<>();

    static {
        EXPECTED_DEFINITION.put("JUMP", new String[]{"jump", "a", "j"});
        EXPECTED_DEFINITION.put("ATTACK", new String[]{"attack", "sword", "b", "melee"});
        EXPECTED_DEFINITION.put("SPECIAL", new String[]{"special", "ranged", "y", "dagger", "throw"});
        EXPECTED_DEFINITION.put("INVENTORY", new String[]{"inventory", "bag", "potion"});
        EXPECTED_DEFINITION.put("LEFT", new String[]{"left"});
        EXPECTED_DEFINITION.put("RIGHT", new String[]{"right"});
        EXPECTED_DEFINITION.put("INTERACT", new String[]{"enter", "exit", "up", "door", "interact"});
        EXPECTED_DEFINITION.put("DROP", new String[]{"down", "drop"});
    }

    // --- docs ⇄ enum equality ---------------------------------------------------------------

    @Test
    public void docsTable_containsEveryEnumKeywordWithTheSameIcon() {
        Map<String, String> doc = docKeywordToIcon();

        for (TutorialHighlight highlight : TutorialHighlight.values()) {
            for (String keyword : highlight.keywords()) {
                assertTrue("docs §5.8 table is missing keyword '" + keyword + "' of " + highlight.name(),
                    doc.containsKey(keyword));
                assertEquals("docs keyword '" + keyword + "' must map to the same icon as " + highlight.name(),
                    highlight.icon(), doc.get(keyword));
            }
        }
    }

    @Test
    public void docsTable_resolvesBackToTheEnumWithNoExtraOrMissingKeywords() {
        Map<String, String> doc = docKeywordToIcon();

        int enumKeywordCount = 0;
        for (TutorialHighlight highlight : TutorialHighlight.values()) {
            enumKeywordCount += highlight.keywords().length;
        }
        assertEquals("docs §5.8 table must contain exactly the enum's keyword count (no leftovers, none missing)",
            enumKeywordCount, doc.size());

        for (Map.Entry<String, String> entry : doc.entrySet()) {
            TutorialHighlight resolved = TutorialHighlight.fromKeyword(entry.getKey());
            assertNotNull("docs keyword '" + entry.getKey() + "' must be resolvable by the enum", resolved);
            assertEquals("docs keyword '" + entry.getKey() + "' declares icon '" + entry.getValue() + "'",
                entry.getValue(), resolved.icon());
        }
    }

    // --- static snapshot --------------------------------------------------------------------

    @Test
    public void enum_isExactlyTheEightDocumentedHighlights() {
        TutorialHighlight[] actual = TutorialHighlight.values();

        assertEquals("enum constant count", EXPECTED_DEFINITION.size(), actual.length);

        String[] actualNames = new String[actual.length];
        for (int i = 0; i < actual.length; i++) {
            actualNames[i] = actual[i].name();
        }
        assertArrayEquals("enum constant names/order",
            EXPECTED_DEFINITION.keySet().toArray(new String[0]), actualNames);

        for (TutorialHighlight highlight : actual) {
            assertArrayEquals("keyword aliases of " + highlight.name(),
                EXPECTED_DEFINITION.get(highlight.name()), highlight.keywords());
        }
    }

    // --- behavioral sanity ------------------------------------------------------------------

    @Test
    public void fromKeyword_isCaseInsensitiveAndTrimsWhitespace() {
        assertEquals("'  JUMP  ' must resolve to JUMP", TutorialHighlight.JUMP,
            TutorialHighlight.fromKeyword("  JUMP  "));
        assertEquals("'  JUMP  ' must resolve to the jump icon", "jump",
            TutorialHighlight.iconNameFor("  JUMP  "));
        assertEquals("'  Door ' must resolve to INTERACT", TutorialHighlight.INTERACT,
            TutorialHighlight.fromKeyword("  Door "));
    }

    @Test
    public void fromKeyword_roundTripsEveryDeclaredKeyword() {
        for (TutorialHighlight highlight : TutorialHighlight.values()) {
            for (String keyword : highlight.keywords()) {
                assertEquals("keyword '" + keyword + "' must resolve to " + highlight.name(),
                    highlight, TutorialHighlight.fromKeyword(keyword));
            }
        }
    }

    @Test
    public void fromKeyword_unknownKeywordReturnsNull() {
        assertNull(TutorialHighlight.fromKeyword("frobnicate"));
        assertNull("unknown keyword with whitespace must also return null",
            TutorialHighlight.fromKeyword("  frobnicate  "));
    }

    @Test
    public void iconNameFor_unknownKeywordReturnsNull() {
        assertNull(TutorialHighlight.iconNameFor("frobnicate"));
    }

    @Test
    public void fromKeyword_nullAndBlankReturnNull() {
        assertNull(TutorialHighlight.fromKeyword(null));
        assertNull(TutorialHighlight.fromKeyword(""));
        assertNull(TutorialHighlight.fromKeyword("   "));
    }

    @Test
    public void iconNameFor_nullAndBlankReturnNull() {
        assertNull(TutorialHighlight.iconNameFor(null));
        assertNull(TutorialHighlight.iconNameFor(""));
        assertNull(TutorialHighlight.iconNameFor("   "));
    }

    // --- file + table parsing ----------------------------------------------------------------

    /**
     * Parses the alias→icon cheat table in docs §5.8 into {@code keyword → icon}. Table rows look
     * like {@code | `jump`, `a`, `j` | A (jump) | `jump` |}: keywords are the backtick tokens of the
     * first cell, the icon is the bare backtick-stripped third cell. Only rows whose third cell is a
     * single backtick-delimited icon token qualify, which excludes the section's "Properties"
     * table, the table header, and the separator row.
     */
    private static Map<String, String> docKeywordToIcon() {
        Path doc = findDocFile();
        List<String> lines = readAllLines(doc);

        int headingIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(SECTION_HEADING)) {
                headingIndex = i;
                break;
            }
        }
        if (headingIndex == -1) {
            fail("'" + SECTION_HEADING + "' heading not found in " + doc.toAbsolutePath());
        }

        Map<String, String> docTable = new HashMap<>();
        for (int i = headingIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ")) {
                break; // §5.8 ends at the next markdown heading
            }
            if (!line.startsWith("|")) {
                continue;
            }
            List<String> cells = splitRow(line);
            if (cells.size() < 3) {
                continue; // row without both a keyword cell and an icon cell
            }
            String keywordCell = cells.get(0);
            String icon = iconCellValue(cells.get(2));
            if (!keywordCell.contains("`") || icon.isEmpty()) {
                continue; // not a cheat row (Properties/header/separator rows have no backticked aliases or bare icon cell)
            }
            for (String raw : keywordCell.split(",")) {
                String keyword = stripOuterBackticks(raw.trim());
                if (keyword.isEmpty()) {
                    continue;
                }
                String previous = docTable.put(keyword, icon);
                if (previous != null) {
                    fail("docs §5.8 table lists duplicate keyword '" + keyword + "'");
                }
            }
        }
        return docTable;
    }

    /**
     * The Gradle test JVM runs from the {@code core/} module dir by default, so the docs file
     * usually sits at {@code ../resources/...} relative to it; also accept {@code resources/...}
     * (repo-root working dir). Fail with a clear message naming every candidate if neither exists.
     */
    private static Path findDocFile() {
        List<Path> candidates = Arrays.asList(
            Paths.get("..", DOC_PATH),
            Paths.get(DOC_PATH));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        fail("Could not find " + DOC_PATH + " (needed to guard the §5.8 tutorial-highlight table). Tried"
            + " from user.dir='" + System.getProperty("user.dir") + "': " + candidates);
        return null;
    }

    private static List<String> readAllLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file.toAbsolutePath(), e);
        }
    }

    /**
     * Splits a markdown table row like {@code | `jump`, `a`, `j` | A (jump) | `jump` |} into its
     * cells, trimmed, with the leading/trailing empty edge cells (markdown row pipes) dropped.
     */
    private static List<String> splitRow(String line) {
        String[] parts = line.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (int i = 1; i < parts.length - 1; i++) {
            cells.add(parts[i].trim());
        }
        return cells;
    }

    /**
     * The icon is the third cell with its backticks stripped; only bare single-token values (no
     * spaces) are accepted, returning {@code ""} otherwise.
     */
    private static String iconCellValue(String cell) {
        String icon = stripOuterBackticks(cell.trim());
        return icon.contains(" ") ? "" : icon;
    }

    private static String stripOuterBackticks(String token) {
        if (token.length() >= 2 && token.startsWith("`") && token.endsWith("`")) {
            return token.substring(1, token.length() - 1);
        }
        return token;
    }
}
