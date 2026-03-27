package com.vaadin.swingmcp.tinymcpserver;

/**
 * Exception thrown by the MCP server, carrying a JSON-RPC error code.
 * <p>
 * Error codes follow the
 * <a href="https://www.jsonrpc.org/specification#error_object">JSON-RPC 2.0</a>
 * and <a href="https://modelcontextprotocol.io">MCP</a> specifications.
 */
public class MCPServerException extends RuntimeException {

    /** Invalid JSON was received by the server. */
    public static final int PARSE_ERROR = -32700;

    /** The JSON sent is not a valid Request object. */
    public static final int INVALID_REQUEST = -32600;

    /** The method does not exist or is not available. */
    public static final int METHOD_NOT_FOUND = -32601;

    /** Invalid method parameter(s). */
    public static final int INVALID_PARAMS = -32602;

    /** Internal JSON-RPC error. */
    public static final int INTERNAL_ERROR = -32603;

    private final int code;

    public MCPServerException(int code, String message) {
        super(message);
        this.code = code;
    }

    public MCPServerException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
