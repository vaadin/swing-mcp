package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Bundle of inputs delivered to a {@link PromptFunction}
 * invocation. Mirrors {@link ToolRequest} for prompts; arguments are always
 * strings per the MCP spec.
 *
 * <p>{@code transportHeaders} carries HTTP request headers in HTTP mode and
 * is empty in stdio mode. {@code jsonRpcMeta} is the parsed
 * {@code params._meta} GSON {@link JsonObject} if present, or {@code null}
 * otherwise. Both {@code arguments} and {@code transportHeaders} are
 * unmodifiable.
 *
 * <p>See DR-009 for the rationale.
 */
public record PromptRequest(
        String name,
        Map<String, String> arguments,
        Map<String, String> transportHeaders,
        JsonObject jsonRpcMeta) {
}
