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
package com.vaadin.swingmcp.tinymcpclient;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

/**
 * Thrown by {@link TinyMCPClient} when the server returns HTTP 404 in
 * response to a non-{@code initialize} call, signalling that the session
 * the client was bound to no longer exists (the server was restarted, or
 * the session was evicted by the idle-cleanup tick).
 *
 * <p>A typed exception rather than a generic protocol error, because session
 * loss has a specific recovery — call {@link MCPClient#initialize()} again —
 * and the client never takes it on the caller's behalf (D_no_auto_retry).
 */
public class MCPSessionLostException extends MCPClientException {

    public MCPSessionLostException(String message) {
        super(MCPServerException.SERVER_NOT_INITIALIZED, message);
    }

    public MCPSessionLostException(String message, Throwable cause) {
        super(MCPServerException.SERVER_NOT_INITIALIZED, message, cause);
    }
}
