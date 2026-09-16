/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.tinymcpserver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates and coerces raw tool arguments against a tool's input schema.
 * Instances are immutable and created once per registered tool/prompt.
 * <p>
 * Internally stores the property map (name → {@link MCPProtocol.PropertySchema})
 * and the required-name set, derived either from a full tool
 * {@link MCPProtocol.InputSchema} or from a list of prompt
 * {@link MCPProtocol.PromptArgument}s.
 */
class MCPParameterParser {

    /** Qualified identifier used in error messages, e.g. {@code tool 'echo'} or {@code prompt 'greet'}. */
    private final String name;
    private final Map<String, MCPProtocol.PropertySchema> properties;
    private final Set<String> required;

    MCPParameterParser(String toolName, MCPProtocol.InputSchema schema) {
        Objects.requireNonNull(schema, "schema");
        this.name = "tool '" + toolName + "'";
        this.properties = schema.getProperties() != null
                ? new LinkedHashMap<>(schema.getProperties())
                : new LinkedHashMap<>();
        this.required = schema.getRequired() != null
                ? new HashSet<>(schema.getRequired())
                : new HashSet<>();
    }

    /**
     * Builds a parser for an MCP prompt. Every argument is modelled as a
     * {@code string} property — MCP prompts do not support other types.
     */
    MCPParameterParser(String promptName, List<MCPProtocol.PromptArgument> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        this.name = "prompt '" + promptName + "'";
        this.properties = new LinkedHashMap<>();
        this.required = new HashSet<>();
        for (MCPProtocol.PromptArgument arg : arguments) {
            MCPProtocol.PropertySchema prop = new MCPProtocol.PropertySchema();
            prop.setType("string");
            prop.setDescription(arg.getDescription());
            this.properties.put(arg.getName(), prop);
            if (Boolean.TRUE.equals(arg.getRequired())) {
                this.required.add(arg.getName());
            }
        }
    }

    /**
     * Validates and coerces raw tool arguments against the tool's input schema.
     *
     * @throws MCPErrorResponseException for unknown parameters (produces {@code isError: true})
     * @throws MCPServerException        with {@code INVALID_PARAMS} for missing required
     *                                   parameters or type coercion failures
     */
    Map<String, Object> parse(Map<String, Object> rawArgs) {
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
            msg.append(" for ").append(name).append(".");
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
        Map<String, Object> callArgs = new LinkedHashMap<>();
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
