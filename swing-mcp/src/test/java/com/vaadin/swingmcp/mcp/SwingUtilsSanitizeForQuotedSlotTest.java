package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SwingUtils#sanitizeForQuotedSlot(String)} — the
 * BR-13 / DR-quoted-slot-sanitizing helper that prepares strings for emission inside
 * double-quoted snapshot slots (name, description, inline text preview).
 */
class SwingUtilsSanitizeForQuotedSlotTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(SwingUtils.sanitizeForQuotedSlot(null));
    }

    @Test
    void emptyStringReturnsNull() {
        // Callers expect null ⇒ "omit the slot"; empty-string from upstream
        // should not emit "".
        assertNull(SwingUtils.sanitizeForQuotedSlot(""));
    }

    @Test
    void blankStringReturnsNull() {
        assertNull(SwingUtils.sanitizeForQuotedSlot("   "));
        assertNull(SwingUtils.sanitizeForQuotedSlot("\n\t\r"));
    }

    @Test
    void plainStringPassesThroughUnchanged() {
        assertEquals("Hello world", SwingUtils.sanitizeForQuotedSlot("Hello world"));
    }

    @Test
    void newlinesCollapseToSingleSpace() {
        assertEquals("Line 1 Line 2", SwingUtils.sanitizeForQuotedSlot("Line 1\nLine 2"));
        assertEquals("a b c", SwingUtils.sanitizeForQuotedSlot("a\nb\nc"));
    }

    @Test
    void tabsCollapseToSingleSpace() {
        assertEquals("col1 col2 col3", SwingUtils.sanitizeForQuotedSlot("col1\tcol2\tcol3"));
    }

    @Test
    void carriageReturnAndFormFeedCollapse() {
        assertEquals("a b", SwingUtils.sanitizeForQuotedSlot("a\rb"));
        assertEquals("a b", SwingUtils.sanitizeForQuotedSlot("a\fb"));
    }

    @Test
    void runsOfMixedWhitespaceCollapseToSingleSpace() {
        assertEquals("a b", SwingUtils.sanitizeForQuotedSlot("a \n\t\r b"));
        assertEquals("a b c", SwingUtils.sanitizeForQuotedSlot("a    b\t\tc"));
    }

    @Test
    void leadingAndTrailingWhitespaceStripped() {
        assertEquals("middle", SwingUtils.sanitizeForQuotedSlot("   middle   "));
        assertEquals("middle", SwingUtils.sanitizeForQuotedSlot("\nmiddle\n"));
    }

    @Test
    void embeddedQuotesEscapedAsBackslashQuote() {
        assertEquals("say \\\"hi\\\"", SwingUtils.sanitizeForQuotedSlot("say \"hi\""));
        assertEquals("\\\"quoted\\\"", SwingUtils.sanitizeForQuotedSlot("\"quoted\""));
    }

    @Test
    void backslashesPassThroughUnescaped() {
        // DR-quoted-slot-sanitizing "Alternatives considered": full JSON-style escaping
        // rejected. Backslashes stay literal so paths render readably.
        assertEquals("C:\\Users\\foo", SwingUtils.sanitizeForQuotedSlot("C:\\Users\\foo"));
    }

    @Test
    void quoteNextToBackslashRendersDoubleBackslashQuote() {
        // A literal `\"` in input produces `\\"` in output: the backslash
        // is preserved verbatim, the quote is escaped. DR-quoted-slot-sanitizing accepts the
        // ambiguity for the rare case where both characters collide.
        assertEquals("a\\\\\"b", SwingUtils.sanitizeForQuotedSlot("a\\\"b"));
    }

    @Test
    void newlineAndQuoteCombined() {
        // Both rules apply: \n collapses to space, " escapes to \".
        assertEquals("line1 \\\"quoted\\\" line2",
                SwingUtils.sanitizeForQuotedSlot("line1\n\"quoted\"\nline2"));
    }

    @Test
    void doubleSanitizationDoubleEscapesQuotes() {
        // Re-sanitising is deliberately NOT idempotent: because backslashes
        // are not escaped (see helper javadoc), the second pass would turn
        // `\"` into `\\"`. Call-site discipline (render path invokes the
        // sanitiser once per slot) is what prevents double-escape.
        String once = SwingUtils.sanitizeForQuotedSlot("say \"hi\"");
        String twice = SwingUtils.sanitizeForQuotedSlot(once);
        assertEquals("say \\\"hi\\\"", once);
        assertEquals("say \\\\\"hi\\\\\"", twice);
    }

    @Test
    void unicodeLineSeparatorsCollapse() {
        // U+2028 LINE SEPARATOR and U+2029 PARAGRAPH SEPARATOR are matched
        // by Java's \s and must collapse alongside \n/\t.
        assertEquals("a b", SwingUtils.sanitizeForQuotedSlot("a\u2028b"));
        assertEquals("a b", SwingUtils.sanitizeForQuotedSlot("a\u2029b"));
    }

    @Test
    void bracketsAndSpecialCharsPassThroughUnchanged() {
        // Brackets and other punctuation are not special; only whitespace
        // and quotes get transformed.
        assertEquals("[ref=99] fake", SwingUtils.sanitizeForQuotedSlot("[ref=99] fake"));
        assertEquals("a & b < c > d", SwingUtils.sanitizeForQuotedSlot("a & b < c > d"));
    }
}
