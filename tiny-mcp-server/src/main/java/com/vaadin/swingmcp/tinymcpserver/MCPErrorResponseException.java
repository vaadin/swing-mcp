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

/**
 * Exception thrown by a tool to return an MCP error response
 * ({@code isError: true}) with a clean, human-readable message.
 *
 * <p>Unlike a bare {@link RuntimeException}, the error text sent to the client
 * is exactly {@link #getMessage()} — no Java class name prefix is included.
 * Use this when the error is a well-understood application-level condition
 * (e.g. "no visible windows") that the AI client should act on.</p>
 *
 * <p>Contrast with {@link MCPServerException}, which sends a JSON-RPC
 * protocol error and is intended for infrastructure-level failures.</p>
 */
public class MCPErrorResponseException extends RuntimeException {

    public MCPErrorResponseException(String message) {
        super(message);
    }
}
