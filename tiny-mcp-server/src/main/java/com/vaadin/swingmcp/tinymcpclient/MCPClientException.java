package com.vaadin.swingmcp.tinymcpclient;

/**
 * Unchecked exception thrown when the server returns a JSON-RPC protocol
 * error in response to a client request. Carries the JSON-RPC numeric
 * {@link #getCode() code} and the server-supplied message.
 *
 * <p>Also used for HTTP 4xx / 5xx responses other than 404: those are
 * rendered as synthetic JSON-RPC errors so callers see a single exception
 * path. HTTP 404 from a non-{@code initialize} call surfaces as the
 * subclass {@link MCPSessionLostException}.
 *
 * <p>Transport failures (connection refused, broken pipe, timeout) are
 * <em>not</em> represented by this exception; they propagate as
 * {@link java.io.IOException} so the caller can decide whether retrying
 * makes sense.
 */
public class MCPClientException extends RuntimeException {

    private final int code;

    public MCPClientException(int code, String message) {
        super(message);
        this.code = code;
    }

    public MCPClientException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** The JSON-RPC error code returned by the server. */
    public int getCode() {
        return code;
    }
}
