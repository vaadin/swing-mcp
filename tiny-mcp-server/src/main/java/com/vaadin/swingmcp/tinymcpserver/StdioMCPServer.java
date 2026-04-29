package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP server transport speaking newline-delimited JSON-RPC over an
 * {@link InputStream}/{@link OutputStream} pair (typically
 * {@code System.in}/{@code System.out}). Sibling of {@link HttpMCPServer}:
 * both drive an {@link MCPHandler}, but only one transport is active per
 * handler instance.
 * <p>
 * Use case: a standalone process that an MCP client (e.g. Claude Code)
 * spawns as a subprocess. There is no port to coordinate, no other code in
 * the JVM writing to stdout, and exactly one session for the lifetime of
 * the process. See DR-007.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>Build and configure an {@link MCPHandler} (tools/resources/prompts)</li>
 *   <li>{@code new StdioMCPServer(handler)}</li>
 *   <li>{@code runStdio(System.in, System.out)} — blocks until EOF on input</li>
 * </ol>
 * No repeated runs.
 */
public class StdioMCPServer {

    private static final Logger LOG = Logger.getLogger(StdioMCPServer.class.getName());

    /**
     * Used for serializing JSON-RPC error envelopes. JSON-RPC 2.0 requires
     * the {@code id} field to be present on every response, even when the
     * server could not parse the id from the request (in which case it is
     * sent as {@code null}). Default GSON drops nulls, so we keep a
     * dedicated configured instance for error rendering.
     */
    private static final Gson GSON_WITH_NULLS = new GsonBuilder().serializeNulls().create();

    private final MCPHandler handler;

    /**
     * The current (and only) active session, populated lazily when the
     * client sends {@code initialize}. A second {@code initialize} replaces
     * the session — the old one is removed from the handler's session map.
     */
    private MCPSession currentSession;

    /**
     * The protocol writer. Wraps the {@code out} stream passed to
     * {@link #runStdio}. When {@code out == System.out}, this writer
     * captures the original stdout reference; the global {@code System.out}
     * is then redirected to {@code System.err} so stray prints from tools
     * or libraries cannot corrupt the wire framing. See DR-007.
     */
    private BufferedWriter writer;

    /** Convenience: builds a fresh empty handler. */
    public StdioMCPServer() {
        this(new MCPHandler());
    }

    /**
     * @param handler the configured {@link MCPHandler}; not null. Tools and
     *                other registrations should be added before
     *                {@link #runStdio} is called. Stdio is single-session by
     *                definition (one process = one session), so the
     *                handler's {@code acceptNewSession} predicate is typically
     *                left at default (always-accept).
     */
    public StdioMCPServer(MCPHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Parameter handler: must not be null");
        }
        this.handler = handler;
    }

    /**
     * Returns the {@link MCPHandler} this server delegates protocol dispatch to.
     */
    public MCPHandler getHandler() {
        return handler;
    }

    /**
     * Reads newline-delimited JSON-RPC messages from {@code in}, dispatches
     * each one through the embedded {@link MCPHandler}, and writes the
     * response (if any) to {@code out}. Blocks the calling thread until
     * {@code in} reaches EOF, then returns.
     * <p>
     * If {@code out} is the JVM's {@code System.out}, the writer captures
     * the original reference and global {@code System.out} is redirected to
     * {@code System.err} so stray {@code System.out.println} calls from
     * tools or third-party libraries cannot corrupt the framing. See DR-007.
     * <p>
     * Notifications (JSON-RPC requests with no {@code id}) produce no
     * response. Malformed input, unknown methods, and other protocol errors
     * are reported as JSON-RPC error envelopes on {@code out}; transport
     * I/O failures abandon the response and exit the loop.
     */
    public void runStdio(InputStream in, OutputStream out) {
        if (in == null || out == null) {
            throw new IllegalArgumentException("in and out must not be null");
        }
        if (out == System.out) {
            // Capture the real stdout for the protocol writer, then divert
            // System.out so accidental println calls hit stderr instead of
            // the wire. Per DR-007, no auto-restore on shutdown — stdio-mode
            // processes exit when stdin closes.
            System.setOut(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        }
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        handler.start();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                handleMessage(line);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Stdio input I/O failed; exiting read loop", e);
        } finally {
            handler.stop();
            currentSession = null;
        }
    }

    private void handleMessage(String line) {
        Object requestId = null;
        try {
            JsonElement element;
            try {
                element = MCPProtocol.fromJson(line, JsonElement.class);
            } catch (JsonSyntaxException e) {
                throw new MCPServerException(MCPServerException.PARSE_ERROR, "Parse error", e);
            }
            if (element instanceof JsonArray) {
                throw new MCPServerException(MCPServerException.INVALID_REQUEST,
                        "Batch requests are not supported");
            }

            MCPProtocol.JsonRpcRequest request;
            try {
                request = MCPProtocol.gson().fromJson(element, MCPProtocol.JsonRpcRequest.class);
            } catch (JsonSyntaxException e) {
                throw new MCPServerException(MCPServerException.INVALID_REQUEST, "Invalid Request", e);
            }

            // Notifications (no id) produce no response per JSON-RPC 2.0.
            if (request.getId() == null) {
                LOG.fine("Received notification: " + request.getMethod());
                return;
            }
            requestId = request.getId();

            Object result = dispatch(request);
            writeResponse(requestId, result);
        } catch (MCPServerException e) {
            LOG.log(Level.FINE, "Request produced MCP error (code=" + e.getCode() + ")", e);
            writeError(requestId, e.getCode(), e.getMessage());
        } catch (TransportIOException e) {
            // Already past the response — propagate up to the read loop's
            // IOException catch via re-wrap so we exit cleanly.
            LOG.log(Level.WARNING, "Stdio output I/O failed", e);
            throw e;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error handling stdio message", e);
            writeError(requestId, MCPServerException.INTERNAL_ERROR, "Internal error");
        }
    }

    private Object dispatch(MCPProtocol.JsonRpcRequest request) {
        String method = request.getMethod();
        if ("initialize".equals(method)) {
            // Drop the previous session, if any — stdio is single-session,
            // and a re-initialize replaces the old session rather than
            // accumulating orphans in the handler's session map.
            if (currentSession != null) {
                handler.removeSession(currentSession.getId());
                currentSession = null;
            }
            MCPHandler.InitializeOutcome outcome = handler.dispatchInitialize(request);
            currentSession = handler.getSession(outcome.sessionId());
            return outcome.result();
        }
        if ("ping".equals(method)) {
            return handler.dispatchPing();
        }
        if (currentSession == null) {
            throw new MCPServerException(MCPServerException.SERVER_NOT_INITIALIZED,
                    "Server not initialized. Send 'initialize' first.");
        }
        return currentSession.handlePost(request, Collections.emptyMap());
    }

    private void writeResponse(Object requestId, Object result) {
        MCPProtocol.JsonRpcResponse response = new MCPProtocol.JsonRpcResponse();
        response.setId(requestId);
        response.setResultFrom(result);
        writeLine(response.toJson());
    }

    private void writeError(Object requestId, int code, String message) {
        MCPProtocol.ErrorObject errorObj = new MCPProtocol.ErrorObject();
        errorObj.setCode(code);
        errorObj.setMessage(message);

        MCPProtocol.JsonRpcError error = new MCPProtocol.JsonRpcError();
        error.setId(requestId);
        error.setError(errorObj);
        writeLine(GSON_WITH_NULLS.toJson(error));
    }

    private void writeLine(String json) {
        try {
            writer.write(json);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            throw new TransportIOException(e);
        }
    }
}
