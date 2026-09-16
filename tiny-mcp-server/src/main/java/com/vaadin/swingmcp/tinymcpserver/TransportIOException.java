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

import java.io.IOException;

/**
 * Unchecked wrapper around {@link IOException} raised by a transport layer
 * (HTTP socket I/O in {@link JsonRpcExchange}, server bind in
 * {@link HttpMCPServer}, stdio I/O in {@link StdioMCPServer}). Signals that
 * the underlying connection is no longer usable — callers should log and
 * abandon rather than try to respond.
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
