package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Bundle of inputs delivered to a {@link ResourceFunction}
 * invocation. The {@code uri} is the identity slot for resources (the
 * analogue of {@code name} on {@link ToolRequest} / {@link PromptRequest}).
 *
 * <p>{@code transportHeaders} carries HTTP request headers in HTTP mode and
 * is empty in stdio mode. {@code jsonRpcMeta} is the parsed
 * {@code params._meta} GSON {@link JsonObject} if present, or {@code null}
 * otherwise. {@code transportHeaders} is unmodifiable.
 *
 * <p>See D_request_records for the rationale.
 */
public record ResourceRequest(
        String uri,
        Map<String, String> transportHeaders,
        JsonObject jsonRpcMeta) {
}
