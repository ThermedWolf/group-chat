package net.thermedwolf.groupchat.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parser behaviour tests for {@link MarkdownFormat}. Expectations are written
 * with '&' in place of the section sign for readability; {@link MarkdownFormat}
 * emits the real section sign.
 */
class MarkdownFormatTest {

    private static String convert(String input) {
        return MarkdownFormat.toMinecraft(input).replace('\u00A7', '&');
    }

    @Test
    void boldAndItalicMarkers() {
        assertEquals("&lbold&r and &oit&r", convert("**bold** and *it*"));
    }

    @Test
    void underlineStrikeSpoilerCode() {
        assertEquals("&nunder&r", convert("__under__"));
        assertEquals("&mgone&r", convert("~~gone~~"));
        assertEquals("&kspoiler&r", convert("||spoiler||"));
        assertEquals("&7code&r", convert("`code`"));
    }

    @Test
    void colourTagWithNestedStyleReappliesColour() {
        assertEquals("&chi &lb&r&c bye&r", convert("<red>hi <bold>b</bold> bye</red>"));
        assertEquals("&ba &lb&r&b c&r", convert("<aqua>a <b>b</b> c</aqua>"));
    }

    @Test
    void markerInsideTag() {
        assertEquals("&6&lFest&r&6&r", convert("<gold>**Fest**</gold>"));
    }

    @Test
    void boldItalicTripleMarker() {
        assertEquals("&l&oboth&r&o&r", convert("***both***"));
    }

    @Test
    void whitespaceGuardsKeepArithmeticLiteral() {
        assertEquals("2 * 3 + 4", convert("2 * 3 + 4"));
    }

    @Test
    void intrawordMarkersWork() {
        assertEquals("2&l3&r4", convert("2**3**4"));
    }

    @Test
    void unknownTagsStayLiteral() {
        assertEquals("<foo>bar</foo>", convert("<foo>bar</foo>"));
        assertEquals("a < b", convert("a < b"));
    }

    @Test
    void unclosedMarkerRunsToEndOfLine() {
        assertEquals("&lunclosed", convert("**unclosed"));
    }

    @Test
    void closingTagWithoutOpenIsSwallowed() {
        assertEquals("ab", convert("a</red>b"));
    }

    @Test
    void shortStyleAliasesWork() {
        assertEquals("a &lc&r", convert("a <b>c</b>"));
    }

    @Test
    void legacyAmpersandCodesPassThroughUntouched() {
        assertEquals("plain &code stays", convert("plain &code stays"));
    }

    @Test
    void rawSectionSignsAreStrippedFirst() {
        assertEquals("?cclean", convert("\u00A7cclean"));
    }

    @Test
    void nullAndEmptyInputReturnAsIs() {
        assertEquals(null, MarkdownFormat.toMinecraft(null));
        assertEquals("", MarkdownFormat.toMinecraft(""));
    }
}
