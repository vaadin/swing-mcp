package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParametersTest {

    // ── getString ────────────────────────────────────────────────────────────

    @Test
    void getStringReturnsValue() {
        var params = new Parameters(Map.of("name", "Alice"));
        assertEquals("Alice", params.getString("name"));
    }

    @Test
    void getStringThrowsWhenMissing() {
        var params = new Parameters(Map.of());
        var ex = assertThrows(MCPServerException.class, () -> params.getString("name"));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Required parameter 'name' is missing", ex.getMessage());
    }

    @Test
    void getStringThrowsWhenWrongType() {
        var params = new Parameters(Map.of("name", 42));
        var ex = assertThrows(MCPServerException.class, () -> params.getString("name"));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Parameter 'name' must be a string", ex.getMessage());
    }

    // ── getStringOrNull ──────────────────────────────────────────────────────

    @Test
    void getStringOrNullReturnsValue() {
        var params = new Parameters(Map.of("name", "Bob"));
        assertEquals("Bob", params.getStringOrNull("name"));
    }

    @Test
    void getStringOrNullReturnsNullWhenMissing() {
        var params = new Parameters(Map.of());
        assertNull(params.getStringOrNull("name"));
    }

    @Test
    void getStringOrNullThrowsWhenWrongType() {
        var params = new Parameters(Map.of("name", 3.14));
        var ex = assertThrows(MCPServerException.class, () -> params.getStringOrNull("name"));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Parameter 'name' must be a string", ex.getMessage());
    }

    // ── getInt ───────────────────────────────────────────────────────────────

    @Test
    void getIntReturnsValueFromInteger() {
        var params = new Parameters(Map.of("ref", 7));
        assertEquals(7, params.getInt("ref"));
    }

    @Test
    void getIntReturnsValueFromDouble() {
        var params = new Parameters(Map.of("ref", 3.0));
        assertEquals(3, params.getInt("ref"));
    }

    @Test
    void getIntThrowsWhenMissing() {
        var params = new Parameters(Map.of());
        var ex = assertThrows(MCPServerException.class, () -> params.getInt("ref"));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Required parameter 'ref' is missing", ex.getMessage());
    }

    @Test
    void getIntThrowsWhenWrongType() {
        var params = new Parameters(Map.of("ref", "notanumber"));
        var ex = assertThrows(MCPServerException.class, () -> params.getInt("ref"));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Parameter 'ref' must be an integer", ex.getMessage());
    }

    // ── getIntOrNull ─────────────────────────────────────────────────────────

    @Test
    void getIntOrNullReturnsValueFromInteger() {
        var params = new Parameters(Map.of("offset", 5));
        assertEquals(5, params.getIntOrNull("offset"));
    }

    @Test
    void getIntOrNullReturnsValueFromDouble() {
        var params = new Parameters(Map.of("offset", 10.0));
        assertEquals(10, params.getIntOrNull("offset"));
    }

    @Test
    void getIntOrNullReturnsNullWhenMissing() {
        var params = new Parameters(Map.of());
        assertNull(params.getIntOrNull("offset"));
    }

    @Test
    void getIntOrNullThrowsWhenWrongType() {
        var params = new Parameters(Map.of("offset", "five"));
        var ex = assertThrows(MCPServerException.class, () -> params.getIntOrNull("offset"));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Parameter 'offset' must be an integer", ex.getMessage());
    }

    // ── null map ─────────────────────────────────────────────────────────────

    @Test
    void nullMapIsTreatedAsEmpty() {
        var params = new Parameters(null);
        assertNull(params.getStringOrNull("key"));
        assertNull(params.getIntOrNull("key"));
        assertThrows(MCPServerException.class, () -> params.getString("key"));
        assertThrows(MCPServerException.class, () -> params.getInt("key"));
    }

    // ── explicit null value in map ───────────────────────────────────────────

    @Test
    void explicitNullValueTreatedAsMissing() {
        var map = new HashMap<String, Object>();
        map.put("key", null);
        var params = new Parameters(map);
        assertNull(params.getStringOrNull("key"));
        assertNull(params.getIntOrNull("key"));
        assertThrows(MCPServerException.class, () -> params.getString("key"));
        assertThrows(MCPServerException.class, () -> params.getInt("key"));
    }
}
