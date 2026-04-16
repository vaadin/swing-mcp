package com.vaadin.swingmcp.tinymcpserver;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Minimal fake {@link HttpExchange} for unit-testing {@link JsonRpcExchange}
 * and {@link MCPSession} without starting an HTTP server.
 * <p>
 * Captures the response status code, headers, and body for assertions.
 */
class FakeHttpExchange extends HttpExchange {

    private final byte[] requestBody;
    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    private int responseCode;

    FakeHttpExchange(String requestBody) {
        this.requestBody = requestBody.getBytes(StandardCharsets.UTF_8);
    }

    // --- Used by JsonRpcExchange ---

    @Override public InputStream getRequestBody() { return new ByteArrayInputStream(requestBody); }
    @Override public Headers getRequestHeaders() { return requestHeaders; }
    @Override public Headers getResponseHeaders() { return responseHeaders; }
    @Override public OutputStream getResponseBody() { return responseBody; }
    @Override public void sendResponseHeaders(int rCode, long responseLength) { this.responseCode = rCode; }
    @Override public void close() { }

    // --- Assertions ---

    @Override public int getResponseCode() { return responseCode; }
    String getResponseBodyString() { return responseBody.toString(StandardCharsets.UTF_8); }

    // --- Unused stubs ---

    @Override public String getRequestMethod() { return "POST"; }
    @Override public URI getRequestURI() { return URI.create("/mcp"); }
    @Override public HttpContext getHttpContext() { return null; }
    @Override public InetSocketAddress getRemoteAddress() { return null; }
    @Override public InetSocketAddress getLocalAddress() { return null; }
    @Override public String getProtocol() { return "HTTP/1.1"; }
    @Override public Object getAttribute(String name) { return null; }
    @Override public void setAttribute(String name, Object value) { }
    @Override public void setStreams(InputStream i, OutputStream o) { }
    @Override public HttpPrincipal getPrincipal() { return null; }
}
