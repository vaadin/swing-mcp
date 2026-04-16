package com.vaadin.swingmcp.tinymcpserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerUtilsTest {

    @Test
    void levenshteinIdenticalStrings() {
        assertEquals(0, ServerUtils.levenshteinDistance("abc", "abc"));
    }

    @Test
    void levenshteinSingleEdit() {
        assertEquals(1, ServerUtils.levenshteinDistance("mesage", "message"));
    }

    @Test
    void levenshteinCompletelyDifferent() {
        assertEquals(3, ServerUtils.levenshteinDistance("abc", "xyz"));
    }

    @Test
    void levenshteinEmptyStrings() {
        assertEquals(0, ServerUtils.levenshteinDistance("", ""));
        assertEquals(3, ServerUtils.levenshteinDistance("abc", ""));
        assertEquals(3, ServerUtils.levenshteinDistance("", "abc"));
    }
}
