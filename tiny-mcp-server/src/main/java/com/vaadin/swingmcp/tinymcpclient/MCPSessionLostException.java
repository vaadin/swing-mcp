package com.vaadin.swingmcp.tinymcpclient;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

/**
 * Thrown by {@link TinyMCPClient} when the server returns HTTP 404 in
 * response to a non-{@code initialize} call, signalling that the session
 * the client was bound to no longer exists (the server was restarted, or
 * the session was evicted by the idle-cleanup tick).
 *
 * <p>Per DR-embedded-mcp-client this is a typed exception rather than a generic protocol
 * error because session loss is a recoverable condition with a specific
 * meaning: re-initialization is required to continue. Stateless callers
 * that opt in via {@link MCPClient#autoRetry()} let
 * {@link AutoRetryMCPClient} catch this exception and recover
 * transparently. Stateful callers (whose session-bound state would not
 * survive a re-initialize) catch it themselves and surface the failure
 * to the caller.
 */
public class MCPSessionLostException extends MCPClientException {

    public MCPSessionLostException(String message) {
        super(MCPServerException.SERVER_NOT_INITIALIZED, message);
    }

    public MCPSessionLostException(String message, Throwable cause) {
        super(MCPServerException.SERVER_NOT_INITIALIZED, message, cause);
    }
}
