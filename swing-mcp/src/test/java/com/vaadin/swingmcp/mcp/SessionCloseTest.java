/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ref map lives in a {@link com.vaadin.swingmcp.mcp.tools.SwingToolContext} attached to each
 * {@link com.vaadin.swingmcp.tinymcpserver.MCPSession}, never in {@link SwingMCP} itself: a client
 * that connects after another never resolves the refs the earlier client's snapshot handed out,
 * whether that session was deleted or evicted (D_single_session).
 */
class SessionCloseTest {

    private static final String EMPTY_REF_MAP =
            "Component with ref 1 invalid — the ref map is empty: no swing_snapshot yet, or a successful mutation cleared it. Call swing_snapshot to rebuild it.";

    private FakeSwingMCP server;
    private HttpClient http;
    private URI serverUri;

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeSwingMCP(0, "/mcp", false);
        server.setConsideredComponents(List.of(new JButton("Test")));
        server.start();
        http = HttpClient.newHttpClient();
        serverUri = URI.create(server.getUrl());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    // ===== Helpers =====

    private HttpResponse<String> post(String body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String initialize() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        HttpResponse<String> resp = post(body, null);
        assertEquals(200, resp.statusCode());
        String sessionId = resp.headers().firstValue("Mcp-Session-Id").orElse(null);
        assertNotNull(sessionId);
        return sessionId;
    }

    private void delete(String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(serverUri).DELETE()
                .header("Mcp-Session-Id", sessionId)
                .build();
        assertEquals(200, http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    /** Snapshots without mutating, so the session's ref map is left holding ref 1. */
    private void snapshot(String sessionId) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"swing_snapshot\",\"arguments\":{}}}";
        HttpResponse<String> resp = post(body, sessionId);
        assertEquals(200, resp.statusCode());
        assertEquals("- JButton (push_button) \"Test\" [ref=1] actions: click", resultText(resp));
    }

    private HttpResponse<String> click(int ref, String sessionId) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"swing_click\",\"arguments\":{\"ref\":" + ref + "}}}";
        return post(body, sessionId);
    }

    /** Asserts {@code resp} is a tool refusal, and returns its text. */
    @SuppressWarnings("unchecked")
    private static String refusalText(HttpResponse<String> resp) {
        assertEquals(200, resp.statusCode());
        Map<String, Object> result = (Map<String, Object>) parseJson(resp.body()).get("result");
        assertEquals(true, result.get("isError"), resp.body());
        return resultText(resp);
    }

    @SuppressWarnings("unchecked")
    private static String resultText(HttpResponse<String> resp) {
        Map<String, Object> result = (Map<String, Object>) parseJson(resp.body()).get("result");
        assertNotNull(result, resp.body());
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        return (String) content.get(0).get("text");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        return MCPProtocol.fromJson(json, Map.class);
    }

    // ===== Tests =====

    @Test
    void sessionAfterDeleteDoesNotInheritRefs() throws Exception {
        String session1 = initialize();
        snapshot(session1);
        delete(session1);

        String session2 = initialize();
        assertNotEquals(session1, session2);
        assertEquals(EMPTY_REF_MAP, refusalText(click(1, session2)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void newClientEvictsTheOldOneAndDoesNotInheritItsRefs() throws Exception {
        String session1 = initialize();
        snapshot(session1);

        String session2 = initialize();
        assertEquals(EMPTY_REF_MAP, refusalText(click(1, session2)));

        HttpResponse<String> evicted = click(1, session1);
        assertEquals(404, evicted.statusCode());
        Map<String, Object> error = (Map<String, Object>) parseJson(evicted.body()).get("error");
        assertEquals(MCPServerException.SERVER_NOT_INITIALIZED, ((Number) error.get("code")).intValue());
        assertEquals(SwingMCP.EVICTION_REASON, error.get("message"));
    }
}
