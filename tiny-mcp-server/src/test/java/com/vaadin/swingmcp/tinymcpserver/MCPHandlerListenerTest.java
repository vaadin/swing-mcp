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
package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D_settable_listeners: MCPHandler exposes session-lifecycle listeners via fluent
 * setters with sensible no-op defaults. Settable until the first
 * session is accepted, then locked.
 */
class MCPHandlerListenerTest {

    @Test
    void defaultsAreNoopAcceptAll() {
        // No setters called: handler should still produce a working session.
        MCPHandler handler = new MCPHandler();
        MCPHandler.InitializeOutcome o = initOnce(handler);
        assertNotNull(o.sessionId());
        assertEquals(1, handler.getSessionCount());
    }

    @Test
    void onSessionStartedFiresAfterSessionAccepted() {
        AtomicInteger startedCount = new AtomicInteger();
        List<String> startedIds = new ArrayList<>();
        MCPHandler handler = new MCPHandler()
                .setOnSessionStarted(s -> {
                    startedCount.incrementAndGet();
                    startedIds.add(s.getId());
                });
        String sessionId = initOnce(handler).sessionId();
        assertEquals(1, startedCount.get());
        assertEquals(List.of(sessionId), startedIds);
    }

    @Test
    void onSessionStartedThrowingRollsBackTheSession() {
        // If the listener throws, the session must not remain in the map —
        // otherwise we'd advertise a session id the caller never received.
        MCPHandler handler = new MCPHandler()
                .setOnSessionStarted(s -> { throw new IllegalStateException("nope"); });
        assertThrows(IllegalStateException.class, () -> initOnce(handler));
        assertEquals(0, handler.getSessionCount());
    }

    @Test
    void settersLockAfterFirstSession() {
        MCPHandler handler = new MCPHandler();
        initOnce(handler);

        IllegalStateException accept = assertThrows(IllegalStateException.class,
                () -> handler.setAcceptNewSession(existing -> new SessionDecision.Accept()));
        assertTrue(accept.getMessage().toLowerCase().contains("lock"));

        assertThrows(IllegalStateException.class,
                () -> handler.setOnSessionStarted(s -> {}));
        assertThrows(IllegalStateException.class,
                () -> handler.setOnSessionClosed(s -> {}));
    }

    @Test
    void settersLockEvenWhenAcceptNewSessionRejects() {
        // The lock fires the moment a session is *accepted* — a rejected
        // initialize attempt does not lock the listeners.
        MCPHandler handler = new MCPHandler()
                .setAcceptNewSession(existing -> new SessionDecision.Reject());
        assertThrows(MCPServerException.class, () -> initOnce(handler));
        // Still settable — no session was accepted.
        handler.setAcceptNewSession(existing -> new SessionDecision.Accept());
        initOnce(handler);
        // Now locked.
        assertThrows(IllegalStateException.class,
                () -> handler.setOnSessionStarted(s -> {}));
    }

    @Test
    void settersRejectNull() {
        MCPHandler handler = new MCPHandler();
        assertThrows(NullPointerException.class, () -> handler.setAcceptNewSession(null));
        assertThrows(NullPointerException.class, () -> handler.setOnSessionStarted(null));
        assertThrows(NullPointerException.class, () -> handler.setOnSessionClosed(null));
    }

    @Test
    void fluentChainReturnsSameInstance() {
        MCPHandler handler = new MCPHandler();
        MCPHandler chained = handler
                .setAcceptNewSession(existing -> new SessionDecision.Accept())
                .setOnSessionStarted(s -> {})
                .setOnSessionClosed(s -> {});
        assertSame(handler, chained);
    }

    private static MCPHandler.InitializeOutcome initOnce(MCPHandler handler) {
        return handler.dispatchInitialize(initializeRequest());
    }

    private static MCPProtocol.JsonRpcRequest initializeRequest() {
        MCPProtocol.JsonRpcRequest req = new MCPProtocol.JsonRpcRequest();
        req.setJsonrpc("2.0");
        req.setMethod("initialize");
        req.setId(1L);
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", "2024-11-05");
        params.add("capabilities", new JsonObject());
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "test");
        clientInfo.addProperty("version", "1.0");
        params.add("clientInfo", clientInfo);
        req.setParams(params);
        return req;
    }
}
