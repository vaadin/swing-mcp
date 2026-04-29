package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.ToolDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the tool registry and handles {@code tools/list} and {@code tools/call}
 * JSON-RPC methods. Extracted from {@link MCPHandler} so that all
 * tool-related functionality lives in one place.
 * <p>
 * The {@code handle*} methods are transport-agnostic: they consume parsed
 * JSON-RPC requests and return the corresponding result POJO. The caller
 * (HTTP or stdio transport) writes the response.
 */
class MCPToolHandler {

    private static final Logger LOG = Logger.getLogger(MCPToolHandler.class.getName());

    private static class RegisteredTool {
        final MCPParameterParser parser;
        final ToolFunction function;
        final MCPProtocol.Tool descriptor;

        RegisteredTool(ToolDescriptor descriptor, ToolFunction function) {
            this.parser = new MCPParameterParser(descriptor.name(), descriptor.inputSchema());
            this.function = function;
            this.descriptor = new MCPProtocol.Tool();
            this.descriptor.setName(descriptor.name());
            this.descriptor.setDescription(descriptor.description());
            this.descriptor.setInputSchema(descriptor.inputSchema());
        }
    }

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    /**
     * Registers a tool. Must be called before the server is started.
     * Field-level validation (null, blank, name pattern) is performed by
     * {@link ToolDescriptor}'s constructor; this method only enforces
     * registration-level invariants.
     *
     * @param descriptor the tool descriptor; not null
     * @param function   the handler to invoke when the tool is called; not null
     * @throws NullPointerException  if {@code descriptor} or {@code function} is null
     * @throws IllegalStateException if a tool with the same name is already registered
     */
    void addTool(ToolDescriptor descriptor, ToolFunction function) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(function, "function");
        if (tools.containsKey(descriptor.name())) {
            throw new IllegalStateException("A tool with name '" + descriptor.name() + "' is already registered");
        }
        tools.put(descriptor.name(), new RegisteredTool(descriptor, function));
    }

    MCPProtocol.ListToolsResult handleToolsList() {
        MCPProtocol.ListToolsResult result = new MCPProtocol.ListToolsResult();
        List<MCPProtocol.Tool> toolList = new ArrayList<>();
        for (RegisteredTool rt : tools.values()) {
            toolList.add(rt.descriptor);
        }
        result.setTools(toolList);
        return result;
    }

    /**
     * Dispatches {@code tools/call}. Tool-application errors
     * ({@link MCPErrorResponseException} or any non-{@link MCPServerException}
     * thrown by the tool function) are returned as {@code CallToolResult}
     * with {@code isError=true} (DR-004 layer 3). Protocol errors throw
     * {@link MCPServerException}.
     *
     * @param request          the parsed JSON-RPC request envelope
     * @param transportHeaders headers from the underlying transport (HTTP
     *                         request headers; empty for stdio)
     */
    MCPProtocol.CallToolResult handleToolsCall(MCPProtocol.JsonRpcRequest request,
            Map<String, String> transportHeaders) {
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

        try {
            Map<String, Object> callArgs = tool.parser.parse(rawArgs);
            ToolRequest req = new ToolRequest(toolName,
                    Collections.unmodifiableMap(callArgs),
                    transportHeaders,
                    request.getMeta());
            MCPProtocol.Content content = tool.function.call(req);
            MCPProtocol.CallToolResult result = new MCPProtocol.CallToolResult();
            if (content == null) {
                result.setContent(Collections.emptyList());
            } else {
                result.setContent(Collections.singletonList(content));
            }
            return result;
        } catch (MCPErrorResponseException e) {
            LOG.fine("Tool '" + toolName + "' returned error response: " + e.getMessage());
            return toolError(e.getMessage());
        } catch (MCPServerException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Tool '" + toolName + "' threw an exception", e);
            return toolError(e.toString());
        }
    }

    private static MCPProtocol.CallToolResult toolError(String message) {
        MCPProtocol.CallToolResult result = new MCPProtocol.CallToolResult();
        result.setIsError(true);
        result.setContent(Collections.singletonList(MCPProtocol.Content.text(message)));
        return result;
    }
}
