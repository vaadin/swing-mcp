package com.vaadin.swingmcp.tinymcpserver;

import org.jspecify.annotations.Nullable;

/**
 * A tool handler function that receives a {@link ToolRequest} and returns content.
 * Invoked synchronously on the dispatch thread that is serving the
 * {@code tools/call} request.
 *
 * <p>{@link ToolRequest#arguments()} is always non-null, even when no parameters
 * are defined or passed. Values are typed according to their schema:
 * {@code String} for string parameters, {@code Integer} for integer parameters,
 * {@code Double} for number parameters, {@code Boolean} for boolean parameters,
 * {@code List<Object>} for array parameters, and {@code Map<String, Object>} for
 * object parameters. Elements and values inside arrays and objects follow the same
 * Java type mapping recursively. The function never receives raw GSON
 * {@code JsonElement} instances. Optional parameters absent from the call are not
 * included in the map.
 *
 * <p>Verified against the MCP specification (2025-03-26 schema):
 * {@code CallToolResult.content} is a required JSON array with no {@code minItems}
 * constraint, so an empty array is valid. Returning {@code null} produces an empty
 * content array ({@code "content": []}) — useful for mutation tools that have
 * nothing to report. Returning a non-null {@link MCPProtocol.Content} produces a
 * single-element array.
 *
 * <p>Throwing {@link MCPErrorResponseException} produces {@code isError=true} with
 * the exception's message as the text content (no Java class name prefix).
 * Throwing any other exception also produces {@code isError=true} but uses
 * {@link Throwable#toString()} (class name + message, no stacktrace) as the text
 * content. Throwing {@link MCPServerException} sends a JSON-RPC protocol error
 * instead.
 */
@FunctionalInterface
public interface ToolFunction {
    /**
     * Invokes the tool.
     *
     * @return the result content, wrapped in a single-element array, or
     *         {@code null} for an empty result ({@code "content": []})
     * @throws MCPErrorResponseException to return {@code isError=true} with a clean message
     * @throws MCPServerException to return a JSON-RPC protocol error
     * @throws Exception if tool execution fails unexpectedly
     */
    MCPProtocol.@Nullable Content call(ToolRequest request) throws Exception;
}
