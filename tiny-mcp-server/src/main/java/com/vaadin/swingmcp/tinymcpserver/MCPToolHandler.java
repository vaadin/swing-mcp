package com.vaadin.swingmcp.tinymcpserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the tool registry and handles {@code tools/list} and {@code tools/call}
 * JSON-RPC methods. Extracted from {@link TinyMCPServer} so that all
 * tool-related functionality lives in one place.
 */
class MCPToolHandler {

    private static final Logger LOG = Logger.getLogger(MCPToolHandler.class.getName());

    private static class RegisteredTool {
        final MCPParameterParser parser;
        final TinyMCPServer.ToolFunction function;
        final MCPProtocol.Tool descriptor;

        RegisteredTool(String name, String description, MCPProtocol.InputSchema inputSchema,
                TinyMCPServer.ToolFunction function) {
            this.parser = new MCPParameterParser(name, inputSchema);
            this.function = function;
            this.descriptor = new MCPProtocol.Tool();
            this.descriptor.setName(name);
            this.descriptor.setDescription(description);
            this.descriptor.setInputSchema(inputSchema);
        }
    }

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    /**
     * Registers a tool. Must be called before the server is started.
     *
     * @param name        the tool name; not null, not blank
     * @param description human-readable description of the tool; not null, not blank
     * @param inputSchema the parameter schema; not null
     * @param function    the handler to invoke when the tool is called; not null
     * @throws IllegalArgumentException if any argument is null or blank
     * @throws IllegalStateException    if a tool with the same name is already registered
     */
    void addTool(String name, String description, MCPProtocol.InputSchema inputSchema,
            TinyMCPServer.ToolFunction function) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be null or blank");
        }
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Tool name must start with a letter or underscore and contain only alphanumeric characters and underscores: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description must not be null or blank");
        }
        if (inputSchema == null) {
            throw new IllegalArgumentException("InputSchema must not be null");
        }
        if (function == null) {
            throw new IllegalArgumentException("ToolFunction must not be null");
        }
        if (tools.containsKey(name)) {
            throw new IllegalStateException("A tool with name '" + name + "' is already registered");
        }
        tools.put(name, new RegisteredTool(name, description, inputSchema, function));
    }

    void handleToolsList(JsonRpcExchange rpc) {
        MCPProtocol.ListToolsResult result = new MCPProtocol.ListToolsResult();
        List<MCPProtocol.Tool> toolList = new ArrayList<>();
        for (RegisteredTool rt : tools.values()) {
            toolList.add(rt.descriptor);
        }
        result.setTools(toolList);
        rpc.sendResponse(result);
    }

    void handleToolsCall(JsonRpcExchange rpc, MCPProtocol.JsonRpcRequest request) {
        MCPProtocol.CallToolParams params = request.getParamsAs(MCPProtocol.CallToolParams.class);
        if (params == null || params.getName() == null) {
            throw new MCPServerException(MCPServerException.METHOD_NOT_FOUND, "Method not found");
        }

        String toolName = params.getName();
        RegisteredTool tool = tools.get(toolName);
        if (tool == null) {
            throw new MCPServerException(MCPServerException.METHOD_NOT_FOUND, "Method not found: " + toolName);
        }

        Map<String, Object> rawArgs = params.getArguments() != null ? params.getArguments() : Collections.emptyMap();

        // Parse parameters and invoke the tool function
        try {
            Map<String, Object> callArgs = tool.parser.parse(rawArgs);
            ToolRequest req = new ToolRequest(toolName,
                    Collections.unmodifiableMap(callArgs),
                    rpc.getTransportHeaders(),
                    request.getMeta());
            MCPProtocol.Content content = tool.function.call(req);
            MCPProtocol.CallToolResult result = new MCPProtocol.CallToolResult();
            if (content == null) {
                result.setContent(Collections.emptyList());
            } else {
                result.setContent(Collections.singletonList(content));
            }
            rpc.sendResponse(result);
        } catch (MCPErrorResponseException e) {
            LOG.fine("Tool '" + toolName + "' returned error response: " + e.getMessage());
            rpc.sendToolError(e.getMessage());
        } catch (MCPServerException e) {
            // Protocol-level error — let MCPSession.handlePost translate to JSON-RPC error.
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Tool '" + toolName + "' threw an exception", e);
            rpc.sendToolError(e.toString());
        }
    }
}
