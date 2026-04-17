package com.vaadin.swingmcp.tinymcpserver;

import java.io.IOException;

/**
 * Unchecked wrapper around {@link IOException} raised by the HTTP transport
 * layer (socket I/O in {@link JsonRpcExchange}, server bind in
 * {@link TinyMCPServer}). Signals that the underlying connection is no
 * longer usable — callers should log and abandon rather than try to respond.
 * Handler code (tools, prompts, resources) must not throw this; wrap any
 * handler-side I/O failures in {@link MCPServerException} instead.
 */
class TransportIOException extends RuntimeException {

    TransportIOException(IOException cause) {
        super(cause);
    }

    TransportIOException(String message, IOException cause) {
        super(message, cause);
    }
}
