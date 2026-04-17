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
 * Prompt arguments are always strings per the MCP spec; registration
 * rejects schemas that declare anything other than {@code string}
 * properties. Argument validation (missing-required, unknown args with
 * did-you-mean hints) is delegated to {@link MCPParameterParser}, which
 * is also used for tool calls.
 */
class MCPPromptHandler {

    private static final Logger LOG = Logger.getLogger(MCPPromptHandler.class.getName());

    private static class RegisteredPrompt {
        final MCPParameterParser parser;
        final TinyMCPServer.PromptFunction function;
        final MCPProtocol.Prompt descriptor;

        RegisteredPrompt(String name, String description, MCPProtocol.InputSchema arguments,
                TinyMCPServer.PromptFunction function) {
            this.parser = new MCPParameterParser(name, arguments);
            this.function = function;
            this.descriptor = new MCPProtocol.Prompt();
            this.descriptor.setName(name);
            this.descriptor.setDescription(description);
            this.descriptor.setArguments(toPromptArguments(arguments));
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
     * @param arguments   the argument schema — every property must declare
     *                    type {@code string}; may declare zero arguments
     * @param function    the handler to invoke when the prompt is fetched;
     *                    not null
     * @throws IllegalArgumentException if any argument is null/blank, the
     *                                  name shape is wrong, or the schema
     *                                  declares a non-string property
     * @throws IllegalStateException    if a prompt with the same name is
     *                                  already registered
     */
    void addPrompt(String name, String description, MCPProtocol.InputSchema arguments,
            TinyMCPServer.PromptFunction function) {
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
            throw new IllegalArgumentException("Prompt arguments schema must not be null");
        }
        if (function == null) {
            throw new IllegalArgumentException("PromptFunction must not be null");
        }
        Map<String, MCPProtocol.PropertySchema> properties =
                arguments.getProperties() != null ? arguments.getProperties() : Collections.emptyMap();
        for (Map.Entry<String, MCPProtocol.PropertySchema> entry : properties.entrySet()) {
            String type = entry.getValue().getType();
            if (!"string".equals(type)) {
                throw new IllegalArgumentException("Prompt argument '" + entry.getKey()
                        + "' must be of type 'string' (MCP prompts only accept string arguments), got: " + type);
            }
        }
        if (prompts.containsKey(name)) {
            throw new IllegalStateException("A prompt with name '" + name + "' is already registered");
        }
        prompts.put(name, new RegisteredPrompt(name, description, arguments, function));
    }

    void handlePromptsList(JsonRpcExchange rpc) {
        MCPProtocol.ListPromptsResult result = new MCPProtocol.ListPromptsResult();
        List<MCPProtocol.Prompt> descriptors = new ArrayList<>();
        for (RegisteredPrompt rp : prompts.values()) {
            descriptors.add(rp.descriptor);
        }
        result.setPrompts(descriptors);
        rpc.sendResponse(result);
    }

    void handlePromptsGet(JsonRpcExchange rpc, MCPProtocol.JsonRpcRequest request) {
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

        MCPProtocol.GetPromptResult result;
        try {
            result = prompt.function.call(typedArgs);
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
        rpc.sendResponse(result);
    }

    private static List<MCPProtocol.PromptArgument> toPromptArguments(MCPProtocol.InputSchema schema) {
        Map<String, MCPProtocol.PropertySchema> properties =
                schema.getProperties() != null ? schema.getProperties() : Collections.emptyMap();
        List<String> required =
                schema.getRequired() != null ? schema.getRequired() : Collections.emptyList();
        List<MCPProtocol.PromptArgument> args = new ArrayList<>();
        for (Map.Entry<String, MCPProtocol.PropertySchema> entry : properties.entrySet()) {
            MCPProtocol.PromptArgument arg = new MCPProtocol.PromptArgument();
            arg.setName(entry.getKey());
            arg.setDescription(entry.getValue().getDescription());
            arg.setRequired(required.contains(entry.getKey()));
            args.add(arg);
        }
        return args;
    }
}
