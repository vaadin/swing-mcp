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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the prompt registry and handles {@code prompts/list} and
 * {@code prompts/get} JSON-RPC methods. Parallel to {@link MCPToolHandler}.
 * <p>
 * Prompt arguments are always strings per the MCP spec, so registration
 * takes a {@link PromptArgumentsBuilder}. Argument validation
 * (missing-required, unknown args with did-you-mean hints) is delegated
 * to {@link MCPParameterParser}, the same parser used for tool calls —
 * it has a dedicated constructor for prompt-argument lists.
 * <p>
 * The {@code handle*} methods are transport-agnostic: they consume parsed
 * JSON-RPC requests and return the corresponding result POJO. The caller
 * (HTTP or stdio transport) writes the response.
 */
class MCPPromptHandler {

    private static final Logger LOG = Logger.getLogger(MCPPromptHandler.class.getName());

    private static class RegisteredPrompt {
        final MCPParameterParser parser;
        final PromptFunction function;
        final MCPProtocol.Prompt descriptor;

        RegisteredPrompt(String name, String description, List<MCPProtocol.PromptArgument> arguments,
                PromptFunction function) {
            this.parser = new MCPParameterParser(name, arguments);
            this.function = function;
            this.descriptor = new MCPProtocol.Prompt();
            this.descriptor.setName(name);
            this.descriptor.setDescription(description);
            this.descriptor.setArguments(new ArrayList<>(arguments));
        }
    }

    private final Map<String, RegisteredPrompt> prompts = new LinkedHashMap<>();

    /**
     * Registers a prompt. Must be called before the server is started.
     *
     * @param name        the prompt name; not null, not blank, matches
     *                    {@code [a-zA-Z_][a-zA-Z0-9_]*}
     * @param description human-readable description of the prompt; not null,
     *                    not blank
     * @param arguments   the argument builder; not null (use an empty
     *                    builder for a zero-argument prompt)
     * @param function    the handler to invoke when the prompt is fetched;
     *                    not null
     * @throws IllegalArgumentException if any argument is null/blank or the
     *                                  name shape is wrong
     * @throws IllegalStateException    if a prompt with the same name is
     *                                  already registered
     */
    void addPrompt(String name, String description, PromptArgumentsBuilder arguments,
            PromptFunction function) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Prompt name must not be null or blank");
        }
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Prompt name must start with a letter or underscore and contain only alphanumeric characters and underscores: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Prompt description must not be null or blank");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("PromptArgumentsBuilder must not be null (pass an empty builder for a zero-argument prompt)");
        }
        if (function == null) {
            throw new IllegalArgumentException("PromptFunction must not be null");
        }
        if (prompts.containsKey(name)) {
            throw new IllegalStateException("A prompt with name '" + name + "' is already registered");
        }
        prompts.put(name, new RegisteredPrompt(name, description, arguments.build(), function));
    }

    MCPProtocol.ListPromptsResult handlePromptsList() {
        MCPProtocol.ListPromptsResult result = new MCPProtocol.ListPromptsResult();
        List<MCPProtocol.Prompt> descriptors = new ArrayList<>();
        for (RegisteredPrompt rp : prompts.values()) {
            descriptors.add(rp.descriptor);
        }
        result.setPrompts(descriptors);
        return result;
    }

    /**
     * Dispatches {@code prompts/get}. Like resources, prompts have no
     * tool-layer "isError" channel, so failures become JSON-RPC protocol
     * errors.
     *
     * @param request          the parsed JSON-RPC request envelope
     * @param transportHeaders headers from the underlying transport
     */
    MCPProtocol.GetPromptResult handlePromptsGet(MCPProtocol.JsonRpcRequest request,
            Map<String, String> transportHeaders) {
        MCPProtocol.GetPromptParams params = request.getParamsAs(MCPProtocol.GetPromptParams.class);
        if (params == null || params.getName() == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Missing required parameter: name");
        }
        String promptName = params.getName();
        RegisteredPrompt prompt = prompts.get(promptName);
        if (prompt == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Unknown prompt: " + promptName);
        }

        // MCP sends arguments as Map<String, String>; MCPParameterParser
        // operates on Map<String, Object> but accepts strings transparently.
        Map<String, Object> rawArgs = new LinkedHashMap<>();
        if (params.getArguments() != null) {
            rawArgs.putAll(params.getArguments());
        }

        Map<String, Object> parsed;
        try {
            parsed = prompt.parser.parse(rawArgs);
        } catch (MCPErrorResponseException e) {
            // Parser uses isError:true for unknown args in the tool path;
            // for prompts there is no isError, so surface as INVALID_PARAMS.
            throw new MCPServerException(MCPServerException.INVALID_PARAMS, e.getMessage());
        }
        Map<String, String> typedArgs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            typedArgs.put(entry.getKey(), (String) entry.getValue());
        }

        PromptRequest req = new PromptRequest(promptName,
                Collections.unmodifiableMap(typedArgs),
                transportHeaders,
                request.getMeta());
        MCPProtocol.GetPromptResult result;
        try {
            result = prompt.function.call(req);
        } catch (MCPServerException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Prompt '" + promptName + "' threw an exception", e);
            throw new MCPServerException(MCPServerException.INTERNAL_ERROR,
                    "Prompt '" + promptName + "' failed: " + e);
        }
        if (result == null) {
            throw new MCPServerException(MCPServerException.INTERNAL_ERROR,
                    "Prompt '" + promptName + "' returned null");
        }
        return result;
    }
}
