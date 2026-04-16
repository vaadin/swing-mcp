package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

import java.util.ArrayList;
import java.util.List;
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
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Parameter '" + key + "' must be an integer, got '" + value + "'");
            }
        }
        if (!(value instanceof Number)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be an integer, got " + value.getClass().getSimpleName());
        }
        Number num = (Number) value;
        if (num.doubleValue() % 1 != 0) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be an integer, got " + num);
        }
        return num.intValue();
    }

    /**
     * Returns the value of a required numeric parameter as a raw {@link Number}.
     * Unlike {@link #getInt(String)}, this does not convert to {@code int} —
     * it preserves the original numeric type (typically {@link Double} from Gson).
     *
     * @throws MCPServerException if the key is missing or the value is not a Number
     */
    public Number getNumber(String key) {
        Object value = raw.get(key);
        if (value == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Required parameter '" + key + "' is missing");
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Parameter '" + key + "' must be a number, got '" + value + "'");
            }
        }
        if (!(value instanceof Number)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be a number, got " + value.getClass().getSimpleName());
        }
        return (Number) value;
    }

    /**
     * Returns the value of a required integer-array parameter.
     * Gson deserializes JSON arrays as {@code List<?>} with numbers as
     * {@link Double}. This method validates that every element is a
     * whole number (no fractional part) and converts via
     * {@link Number#intValue()}.
     *
     * @throws MCPServerException if the key is missing, the value is not a
     *         List, or any element is not a whole number
     */
    public List<Integer> getIntArray(String key) {
        Object value = raw.get(key);
        if (value == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Required parameter '" + key + "' is missing");
        }
        if (!(value instanceof List)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be an array of integers, got " + value.getClass().getSimpleName());
        }
        List<?> list = (List<?>) value;
        List<Integer> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            Number num;
            if (element instanceof Number) {
                num = (Number) element;
            } else if (element instanceof String) {
                try {
                    num = Integer.parseInt((String) element);
                } catch (NumberFormatException e) {
                    throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                            "Parameter '" + key + "' must be an array of integers, but element at index " + i + " is '" + element + "'");
                }
            } else {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Parameter '" + key + "' must be an array of integers");
            }
            if (num.doubleValue() % 1 != 0) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Parameter '" + key + "' must be an array of integers, but element at index " + i + " is " + num);
            }
            result.add(num.intValue());
        }
        return result;
    }

    /**
     * Returns the value of an optional integer-array parameter, or {@code null} if absent.
     * Same validation as {@link #getIntArray(String)} but returns {@code null} instead
     * of throwing when the key is missing.
     *
     * @throws MCPServerException if the value is present but not a List of whole numbers
     */
    public List<Integer> getIntArrayOrNull(String key) {
        if (!raw.containsKey(key)) {
            return null;
        }
        return getIntArray(key);
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
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Parameter '" + key + "' must be an integer, got '" + value + "'");
            }
        }
        if (!(value instanceof Number)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be an integer, got " + value.getClass().getSimpleName());
        }
        Number num = (Number) value;
        if (num.doubleValue() % 1 != 0) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter '" + key + "' must be an integer, got " + num);
        }
        return num.intValue();
    }
}
