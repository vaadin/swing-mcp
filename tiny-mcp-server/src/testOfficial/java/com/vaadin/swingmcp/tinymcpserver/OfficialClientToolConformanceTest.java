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

import com.vaadin.swingmcp.tinymcpclient.MCPClient;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The same conformance suite driven by the official MCP SDK — the authority
 * leg. A disagreement between this class and {@code TinyClientToolConformanceTest}
 * is either a bug in this server or a bug in this project's own client, and
 * neither would show up if the suite only ever ran against itself.
 *
 * <p>Java 17+ only: the SDK publishes no Java 11 build, which is why this lives
 * in {@code src/testOfficial} rather than beside the suite it runs.
 */
class OfficialClientToolConformanceTest extends AbstractToolConformanceTest {

    @Override
    protected MCPClient newClient(String url) {
        return new OfficialMCPClient(url);
    }

    @Override
    protected RpcError rpcErrorOf(Executable call) {
        // The SDK wraps the protocol error in whatever the transport threw.
        final Exception thrown = assertThrows(Exception.class, call);
        final McpError error = assertInstanceOf(McpError.class, McpError.findRootCause(thrown));
        return new RpcError(error.getJsonRpcError().code(), error.getJsonRpcError().message());
    }
}
