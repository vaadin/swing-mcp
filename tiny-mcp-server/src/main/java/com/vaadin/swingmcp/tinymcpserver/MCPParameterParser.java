package com.vaadin.swingmcp.tinymcpserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and coerces raw tool arguments against a tool's input schema.
 * Instances are immutable and created once per registered tool.
 */
class MCPParameterParser {

    private final String toolName;
    private final MCPProtocol.InputSchema schema;

    MCPParameterParser(String toolName, MCPProtocol.InputSchema schema) {
        this.toolName = toolName;
        this.schema = schema;
    }

    /**
     * Validates and coerces raw tool arguments against the tool's input schema.
     *
     * @throws MCPErrorResponseException for unknown parameters (produces {@code isError: true})
     * @throws MCPServerException        with {@code INVALID_PARAMS} for missing required
     *                                   parameters or type coercion failures
     */
    Map<String, Object> parse(Map<String, Object> rawArgs) {
        Map<String, MCPProtocol.PropertySchema> properties =
                schema.getProperties() != null ? schema.getProperties() : Collections.emptyMap();
        List<String> required =
                schema.getRequired() != null ? schema.getRequired() : Collections.emptyList();

        // Reject unknown parameters with isError:true and did-you-mean hints
        List<String> unknownParams = new ArrayList<>();
        for (String key : rawArgs.keySet()) {
            if (!properties.containsKey(key)) {
                unknownParams.add(key);
            }
        }
        if (!unknownParams.isEmpty()) {
            StringBuilder msg = new StringBuilder("Unknown parameter");
            msg.append(unknownParams.size() == 1 ? " " : "s ");
            for (int i = 0; i < unknownParams.size(); i++) {
                if (i > 0) msg.append(", ");
                String unknown = unknownParams.get(i);
                msg.append("'").append(unknown).append("'");
                if (!properties.isEmpty()) {
                    String closest = null;
                    int bestDist = Integer.MAX_VALUE;
                    for (String known : properties.keySet()) {
                        int dist = ServerUtils.levenshteinDistance(unknown, known);
                        if (dist < bestDist) {
                            bestDist = dist;
                            closest = known;
                        }
                    }
                    int threshold = Math.max(unknown.length(), closest.length()) / 2;
                    if (bestDist <= threshold) {
                        msg.append(" (did you mean '").append(closest).append("'?)");
                    }
                }
            }
            msg.append(" for tool '").append(toolName).append("'.");
            if (!properties.isEmpty()) {
                msg.append(" Valid parameters: ");
                int i = 0;
                for (String name : properties.keySet()) {
                    if (i++ > 0) msg.append(", ");
                    msg.append(name);
                }
            }

            throw new MCPErrorResponseException(msg.toString());
        }

        // Validate and coerce known parameters
        Map<String, Object> callArgs = new HashMap<>();
        for (Map.Entry<String, MCPProtocol.PropertySchema> entry : properties.entrySet()) {
            String paramName = entry.getKey();
            String paramType = entry.getValue().getType();
            Object value = rawArgs.get(paramName);
            boolean isRequired = required.contains(paramName);

            if (value == null) {
                if (isRequired) {
                    throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                            "Missing required parameter '" + paramName + "'");
                }
                // optional and absent: omit from callArgs
                continue;
            }

            if ("integer".equals(paramType)) {
                if (value instanceof Long) {
                    long l = (Long) value;
                    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                        throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                                "Parameter '" + paramName + "' value " + l + " is out of 32-bit integer range");
                    }
                    value = (int) l;
                } else if (value instanceof Double) {
                    double d = (Double) value;
                    if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.floor(d)) {
                        throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                                "Parameter '" + paramName + "' must be a whole number, got " + d);
                    }
                    long l = (long) d;
                    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                        throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                                "Parameter '" + paramName + "' value " + l + " is out of 32-bit integer range");
                    }
                    value = (int) l;
                }
            }

            callArgs.put(paramName, value);
        }
        return callArgs;
    }
}
