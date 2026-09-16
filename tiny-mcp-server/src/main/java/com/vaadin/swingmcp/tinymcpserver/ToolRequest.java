package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Bundle of inputs delivered to a {@link ToolFunction}
 * invocation. Carries the tool name (so a single forwarding lambda
 * registered against many upstream tools can tell which one was invoked),
 * the parsed arguments, transport-layer headers, and the JSON-RPC
 * {@code params._meta} object from the request envelope.
 *
 * <p>{@code arguments} is a typed {@link Parameters} wrapper around the
 * parsed argument map; use its accessors for type-checked extraction, or
 * {@link Parameters#raw()} when forwarding the map unchanged (as a
 * proxy does). {@code transportHeaders} carries HTTP request headers in
 * HTTP mode and is empty in stdio mode (no out-of-band metadata in
 * newline-delimited JSON); it is unmodifiable. {@code jsonRpcMeta} is the
 * parsed {@code params._meta} GSON {@link JsonObject} if present in the
 * request, or {@code null} otherwise.
 *
 * <p>See DR-request-records for the rationale behind preferring a record over
 * positional SAM arguments.
 */
public record ToolRequest(
        String name,
        Parameters arguments,
        Map<String, String> transportHeaders,
        @Nullable JsonObject jsonRpcMeta) {

    public ToolRequest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(transportHeaders, "transportHeaders");
    }

    /** Convenience constructor that wraps the raw argument map in a {@link Parameters}. */
    public ToolRequest(String name,
                       Map<String, Object> arguments,
                       Map<String, String> transportHeaders,
                       @Nullable JsonObject jsonRpcMeta) {
        this(name, new Parameters(arguments), transportHeaders, jsonRpcMeta);
    }
}
