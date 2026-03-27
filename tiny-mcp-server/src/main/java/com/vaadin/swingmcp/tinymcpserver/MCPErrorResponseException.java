package com.vaadin.swingmcp.tinymcpserver;

/**
 * Exception thrown by a tool to return an MCP error response
 * ({@code isError: true}) with a clean, human-readable message.
 *
 * <p>Unlike a bare {@link RuntimeException}, the error text sent to the client
 * is exactly {@link #getMessage()} — no Java class name prefix is included.
 * Use this when the error is a well-understood application-level condition
 * (e.g. "no visible windows") that the AI client should act on.</p>
 *
 * <p>Contrast with {@link MCPServerException}, which sends a JSON-RPC
 * protocol error and is intended for infrastructure-level failures.</p>
 */
public class MCPErrorResponseException extends RuntimeException {

    public MCPErrorResponseException(String message) {
        super(message);
    }
}
