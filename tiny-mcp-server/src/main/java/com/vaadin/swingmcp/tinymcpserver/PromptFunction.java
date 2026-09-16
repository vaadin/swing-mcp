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
 * Functional interface for prompt handlers. Invoked when a client
 * requests {@code prompts/get}. Arguments have already been validated
 * against the registered schema (required / unknown-arg checks), so
 * implementations can read them directly.
 */
@FunctionalInterface
public interface PromptFunction {
    /**
     * @param request the request bundle (name, arguments, transport headers, JSON-RPC {@code _meta});
     *                {@link PromptRequest#arguments()} is always non-null and contains only declared
     *                keys — missing optional arguments are simply absent
     * @return the prompt result; must not be null
     * @throws MCPServerException to return a JSON-RPC protocol error
     * @throws Exception          if prompt expansion fails unexpectedly
     */
    MCPProtocol.GetPromptResult call(PromptRequest request) throws Exception;
}
