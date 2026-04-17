package com.vaadin.swingmcp.tinymcpserver;

import java.io.IOException;

/**
 * Unchecked wrapper around {@link IOException}. Lets the request-handling
 * code path in tiny-mcp-server propagate I/O failures without having to
 * declare {@code throws IOException} on every handler method — callers
 * that care about the underlying cause can retrieve it via
 * {@link #getCause()} (always an {@link IOException}).
 */
public class RuntimeIOException extends RuntimeException {

    public RuntimeIOException(IOException cause) {
        super(cause);
    }

    public RuntimeIOException(String message, IOException cause) {
        super(message, cause);
    }
}
