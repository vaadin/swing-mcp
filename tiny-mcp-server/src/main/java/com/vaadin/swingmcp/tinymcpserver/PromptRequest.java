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

import java.util.Map;

/**
 * Bundle of inputs delivered to a {@link PromptFunction}
 * invocation. Mirrors {@link ToolRequest} for prompts; arguments are always
 * strings per the MCP spec.
 *
 * <p>{@code transportHeaders} carries HTTP request headers in HTTP mode and
 * is empty in stdio mode. {@code jsonRpcMeta} is the parsed
 * {@code params._meta} GSON {@link JsonObject} if present, or {@code null}
 * otherwise. Both {@code arguments} and {@code transportHeaders} are
 * unmodifiable.
 *
 * <p>See D_request_records for the rationale.
 *
 * <p>Immutable.
 */
public final class PromptRequest {

    private final String name;
    private final Map<String, String> arguments;
    private final Map<String, String> transportHeaders;
    private final JsonObject jsonRpcMeta;

    /**
     * @param jsonRpcMeta the request's {@code params._meta}, or {@code null} if absent
     */
    public PromptRequest(String name,
                         Map<String, String> arguments,
                         Map<String, String> transportHeaders,
                         JsonObject jsonRpcMeta) {
        this.name = name;
        this.arguments = arguments;
        this.transportHeaders = transportHeaders;
        this.jsonRpcMeta = jsonRpcMeta;
    }

    public String name() {
        return name;
    }

    public Map<String, String> arguments() {
        return arguments;
    }

    public Map<String, String> transportHeaders() {
        return transportHeaders;
    }

    public JsonObject jsonRpcMeta() {
        return jsonRpcMeta;
    }
}
