package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Bundle of inputs delivered to a {@link TinyMCPServer.ToolFunction}
 * invocation. Carries the tool name (so a single forwarding lambda
 * registered against many upstream tools can tell which one was invoked),
 * the parsed arguments, transport-layer headers, and the JSON-RPC
 * {@code params._meta} object from the request envelope.
 *
 * <p>{@code transportHeaders} carries HTTP request headers in HTTP mode and
 * is empty in stdio mode (no out-of-band metadata in newline-delimited
 * JSON). {@code jsonRpcMeta} is the parsed {@code params._meta} GSON
 * {@link JsonObject} if present in the request, or {@code null} otherwise.
 * Both {@code arguments} and {@code transportHeaders} are unmodifiable.
 *
 * <p>See DR-009 for the rationale behind preferring a record over
 * positional SAM arguments.
 */
public record ToolRequest(
        String name,
        Map<String, Object> arguments,
        Map<String, String> transportHeaders,
        JsonObject jsonRpcMeta) {
}
