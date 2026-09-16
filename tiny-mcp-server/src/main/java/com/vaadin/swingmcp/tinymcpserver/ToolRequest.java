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
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Bundle of inputs delivered to a {@link ToolFunction}
 * invocation. Carries the tool name (so a single forwarding lambda
 * registered against many upstream tools can tell which one was invoked),
 * the parsed arguments, transport-layer headers, and the JSON-RPC
 * {@code params._meta} object from the request envelope.
 *
 * <p>{@code arguments} is a typed {@link Parameters} wrapper around the
 * parsed argument map; use its accessors for type-checked extraction, or
 * {@link Parameters#raw()} when forwarding the map unchanged (as a
 * proxy does). {@code transportHeaders} carries HTTP request headers in
 * HTTP mode and is empty in stdio mode (no out-of-band metadata in
 * newline-delimited JSON); it is unmodifiable. {@code jsonRpcMeta} is the
 * parsed {@code params._meta} GSON {@link JsonObject} if present in the
 * request, or {@code null} otherwise.
 *
 * <p>See D_request_records for the rationale behind preferring a bundle type
 * over positional SAM arguments.
 *
 * <p>Immutable.
 */
public final class ToolRequest {

    private final String name;
    private final Parameters arguments;
    private final Map<String, String> transportHeaders;
    private final @Nullable JsonObject jsonRpcMeta;

    /**
     * @param transportHeaders unmodifiable; empty in stdio mode
     * @param jsonRpcMeta      the request's {@code params._meta}, or {@code null} if absent
     */
    public ToolRequest(String name,
                       Parameters arguments,
                       Map<String, String> transportHeaders,
                       @Nullable JsonObject jsonRpcMeta) {
        this.name = Objects.requireNonNull(name, "name");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.transportHeaders = Objects.requireNonNull(transportHeaders, "transportHeaders");
        this.jsonRpcMeta = jsonRpcMeta;
    }

    /** Convenience constructor that wraps the raw argument map in a {@link Parameters}. */
    public ToolRequest(String name,
                       Map<String, Object> arguments,
                       Map<String, String> transportHeaders,
                       @Nullable JsonObject jsonRpcMeta) {
        this(name, new Parameters(arguments), transportHeaders, jsonRpcMeta);
    }

    public String name() {
        return name;
    }

    public Parameters arguments() {
        return arguments;
    }

    public Map<String, String> transportHeaders() {
        return transportHeaders;
    }

    public @Nullable JsonObject jsonRpcMeta() {
        return jsonRpcMeta;
    }
}
