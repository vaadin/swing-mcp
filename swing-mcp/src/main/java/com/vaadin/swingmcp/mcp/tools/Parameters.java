package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

import java.util.Map;

/**
 * Typed wrapper around the raw {@code Map<String, Object>} received from MCP
 * tool requests. Provides type-safe accessors that throw
 * {@link MCPServerException} with {@link MCPServerException#INVALID_PARAMS}
 * when a required parameter is missing or has the wrong type.
 */
public class Parameters {

    private final Map<String, Object> raw;

    public Parameters(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    /**
     * Returns the value of a required string parameter.
     *
     * @throws MCPServerException if the key is missing or the value is not a String
     */
    public String getString(String key) {
        Object value = raw.get(key);
        if (value == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Required parameter '" + key + "' is missing");
        }
        if (!(value instanceof String)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be a string");
        }
        return (String) value;
    }

    /**
     * Returns the value of an optional string parameter, or {@code null} if absent.
     *
     * @throws MCPServerException if the value is present but not a String
     */
    public String getStringOrNull(String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be a string");
        }
        return (String) value;
    }

    /**
     * Returns the value of a required integer parameter.
     * JSON numbers arrive as {@link Number} (typically {@link Double} from
     * Gson); this method converts via {@link Number#intValue()}.
     *
     * @throws MCPServerException if the key is missing or the value is not a Number
     */
    public int getInt(String key) {
        Object value = raw.get(key);
        if (value == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Required parameter '" + key + "' is missing");
        }
        if (!(value instanceof Number)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be an integer");
        }
        return ((Number) value).intValue();
    }

    /**
     * Returns the value of an optional integer parameter, or {@code null} if absent.
     *
     * @throws MCPServerException if the value is present but not a Number
     */
    public Integer getIntOrNull(String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be an integer");
        }
        return ((Number) value).intValue();
    }
}
